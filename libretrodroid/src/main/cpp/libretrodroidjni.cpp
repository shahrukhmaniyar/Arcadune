/*
 *     Copyright (C) 2021  Filippo Scognamiglio
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include <jni.h>

#include <EGL/egl.h>

#include <memory>
#include <string>
#include <vector>
#include <unordered_set>
#include <mutex>
#include <optional>

#include "libretrodroid.h"
#include "log.h"
#include "core.h"
#include "audio.h"
#include "video.h"
#include "renderers/renderer.h"
#include "fpssync.h"
#include "input.h"
#include "rumble.h"
#include "shadermanager.h"
#include "utils/javautils.h"
#include "errorcodes.h"
#include "environment.h"
#include "renderers/es3/framebufferrenderer.h"
#include "renderers/es2/imagerendereres2.h"
#include "renderers/es3/imagerendereres3.h"
#include "utils/jnistring.h"
#include "achievements.h"

namespace libretrodroid {

extern "C" {
#include "utils/utils.h"
#include "libretro.h"
#include "utils/libretrodroidexception.h"
}

namespace {

jstring safeJStringFromUtf8(JNIEnv* env, const std::string& str) {
    if (str.empty()) {
        return env->NewString(nullptr, 0);
    }
    std::u16string utf16;
    utf16.reserve(str.size());
    const auto* bytes = reinterpret_cast<const uint8_t*>(str.c_str());
    size_t i = 0;
    const size_t n = str.size();
    while (i < n) {
        uint32_t cp = 0;
        uint8_t b0 = bytes[i];
        if (b0 < 0x80) {
            cp = b0;
            i += 1;
        } else if (i + 1 < n && (b0 & 0xE0) == 0xC0 && (bytes[i + 1] & 0xC0) == 0x80) {
            cp = ((b0 & 0x1F) << 6) | (bytes[i + 1] & 0x3F);
            i += 2;
        } else if (i + 2 < n && (b0 & 0xF0) == 0xE0 &&
                   (bytes[i + 1] & 0xC0) == 0x80 &&
                   (bytes[i + 2] & 0xC0) == 0x80) {
            cp = ((b0 & 0x0F) << 12) | ((bytes[i + 1] & 0x3F) << 6) | (bytes[i + 2] & 0x3F);
            i += 3;
        } else if (i + 3 < n && (b0 & 0xF8) == 0xF0 &&
                   (bytes[i + 1] & 0xC0) == 0x80 &&
                   (bytes[i + 2] & 0xC0) == 0x80 &&
                   (bytes[i + 3] & 0xC0) == 0x80) {
            cp = ((b0 & 0x07) << 18) |
                 ((bytes[i + 1] & 0x3F) << 12) |
                 ((bytes[i + 2] & 0x3F) << 6) |
                 (bytes[i + 3] & 0x3F);
            i += 4;
        } else {
            cp = 0xFFFD;
            i += 1;
        }

        if (cp <= 0xFFFF) {
            utf16.push_back(static_cast<char16_t>(cp));
        } else if (cp <= 0x10FFFF) {
            cp -= 0x10000;
            utf16.push_back(static_cast<char16_t>(0xD800 + (cp >> 10)));
            utf16.push_back(static_cast<char16_t>(0xDC00 + (cp & 0x3FF)));
        } else {
            utf16.push_back(static_cast<char16_t>(0xFFFD));
        }
    }
    return env->NewString(
        reinterpret_cast<const jchar*>(utf16.data()),
        static_cast<jsize>(utf16.size())
    );
}

} // namespace

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK || env == nullptr) {
        return JNI_ERR;
    }
    Achievements::getInstance().initJni(vm, env);
    return JNI_VERSION_1_6;
}

JNIEXPORT jint JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_availableDisks(
    JNIEnv* env,
    jclass obj
) {
    return LibretroDroid::getInstance().availableDisks();
}

JNIEXPORT jint JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_currentDisk(
    JNIEnv* env,
    jclass obj
) {
    return LibretroDroid::getInstance().currentDisk();
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_changeDisk(
    JNIEnv* env,
    jclass obj,
    jint index
) {
    return LibretroDroid::getInstance().changeDisk(index);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_updateVariable(
    JNIEnv* env,
    jclass obj,
    jobject variable
) {
    Variable v = JavaUtils::variableFromJava(env, variable);
    Environment::getInstance().updateVariable(v.key, v.value);
}

JNIEXPORT jobjectArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_getVariables(
    JNIEnv* env,
    jclass obj
) {
    jclass variableClass = env->FindClass("com/swordfish/libretrodroid/Variable");
    jmethodID variableMethodID = env->GetMethodID(variableClass, "<init>", "()V");

    auto variables = Environment::getInstance().getVariables();
    jobjectArray result = env->NewObjectArray(variables.size(), variableClass, nullptr);

    for (int i = 0; i < variables.size(); i++) {
        jobject jVariable = env->NewObject(variableClass, variableMethodID);

        jfieldID jKeyField = env->GetFieldID(variableClass, "key", "Ljava/lang/String;");
        jfieldID jValueField = env->GetFieldID(variableClass, "value", "Ljava/lang/String;");
        jfieldID jDescriptionField = env->GetFieldID(
            variableClass,
            "description",
            "Ljava/lang/String;"
        );

        env->SetObjectField(jVariable, jKeyField, env->NewStringUTF(variables[i].key.data()));
        env->SetObjectField(jVariable, jValueField, env->NewStringUTF(variables[i].value.data()));
        env->SetObjectField(
            jVariable,
            jDescriptionField,
            env->NewStringUTF(variables[i].description.data()));

        env->SetObjectArrayElement(result, i, jVariable);
    }
    return result;
}

JNIEXPORT jobjectArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_getControllers(
    JNIEnv* env,
    jclass obj
) {
    jclass variableClass = env->FindClass("[Lcom/swordfish/libretrodroid/Controller;");

    auto controllers = Environment::getInstance().getControllers();
    jobjectArray result = env->NewObjectArray(controllers.size(), variableClass, nullptr);

    for (int i = 0; i < controllers.size(); i++) {
        jclass variableClass2 = env->FindClass("com/swordfish/libretrodroid/Controller");
        jobjectArray controllerArray = env->NewObjectArray(
            controllers[i].size(),
            variableClass2,
            nullptr
        );
        jmethodID variableMethodID = env->GetMethodID(variableClass2, "<init>", "()V");

        for (int j = 0; j < controllers[i].size(); j++) {
            jobject jController = env->NewObject(variableClass2, variableMethodID);

            jfieldID jIdField = env->GetFieldID(variableClass2, "id", "I");
            jfieldID jDescriptionField = env->GetFieldID(
                variableClass2,
                "description",
                "Ljava/lang/String;"
            );

            env->SetIntField(jController, jIdField, (int) controllers[i][j].id);
            env->SetObjectField(
                jController,
                jDescriptionField,
                env->NewStringUTF(controllers[i][j].description.data()));

            env->SetObjectArrayElement(controllerArray, j, jController);
        }

        env->SetObjectArrayElement(result, i, controllerArray);
    }
    return result;
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setControllerType(
    JNIEnv* env,
    jclass obj,
    jint port,
    jint type
) {
    LibretroDroid::getInstance().setControllerType(port, type);
}

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_unserializeState(
    JNIEnv* env,
    jclass obj,
    jbyteArray state
) {
    try {
        jboolean isCopy = JNI_FALSE;
        jbyte* data = env->GetByteArrayElements(state, &isCopy);
        jsize size = env->GetArrayLength(state);

        bool result = LibretroDroid::getInstance().unserializeState(data, size);
        env->ReleaseByteArrayElements(state, data, JNI_ABORT);

        return result ? JNI_TRUE : JNI_FALSE;

    } catch (std::exception &exception) {
        LOGE("Error in unserializeState: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_SERIALIZATION);
        return JNI_FALSE;
    }
}

JNIEXPORT jbyteArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_serializeState(
    JNIEnv* env,
    jclass obj
) {
    try {
        auto [data, size] = LibretroDroid::getInstance().serializeState();

        jbyteArray result = env->NewByteArray(size);
        if (size > 0 && data != nullptr) {
            env->SetByteArrayRegion(result, 0, size, data);
        }
        delete[] data;

        return result;

    } catch (std::exception &exception) {
        LOGE("Error in serializeState: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_SERIALIZATION);
    }

    return nullptr;
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setCheat(
    JNIEnv* env,
    jclass obj,
    jint index,
    jboolean enabled,
    jstring code
) {
    try {
        auto codeString = JniString(env, code);
        LibretroDroid::getInstance().setCheat(index, enabled, codeString.stdString());
    } catch (std::exception &exception) {
        LOGE("Error in setCheat: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_CHEAT);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_resetCheat(
    JNIEnv* env,
    jclass obj
) {
    try {
        LibretroDroid::getInstance().resetCheat();
    } catch (std::exception &exception) {
        LOGE("Error in resetCheat: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_CHEAT);
    }
}

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_unserializeSRAM(
    JNIEnv* env,
    jclass obj,
    jbyteArray sram
) {
    try {
        jboolean isCopy = JNI_FALSE;
        jbyte* data = env->GetByteArrayElements(sram, &isCopy);
        jsize size = env->GetArrayLength(sram);

        LibretroDroid::getInstance().unserializeSRAM(data, size);

        env->ReleaseByteArrayElements(sram, data, JNI_ABORT);

    } catch (std::exception &exception) {
        LOGE("Error in unserializeSRAM: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_SERIALIZATION);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

JNIEXPORT jbyteArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_serializeSRAM(
    JNIEnv* env,
    jclass obj
) {
    try {
        auto [data, size] = LibretroDroid::getInstance().serializeSRAM();

        jbyteArray result = env->NewByteArray(size);
        if (size > 0 && data != nullptr) {
            env->SetByteArrayRegion(result, 0, size, (jbyte *) data);
        }
        delete[] data;

        return result;

    } catch (std::exception &exception) {
        LOGE("Error in serializeSRAM: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_SERIALIZATION);
    }

    return nullptr;
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_reset(
    JNIEnv* env,
    jclass obj
) {
    try {
        LibretroDroid::getInstance().reset();
    } catch (std::exception &exception) {
        LOGE("Error in clear: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_onSurfaceChanged(
    JNIEnv* env,
    jclass obj,
    jint width,
    jint height
) {
    LibretroDroid::getInstance().onSurfaceChanged(width, height);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_onSurfaceCreated(
    JNIEnv* env,
    jclass obj
) {
    LibretroDroid::getInstance().onSurfaceCreated();
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_onMotionEvent(
    JNIEnv* env,
    jclass obj,
    jint port,
    jint source,
    jfloat xAxis,
    jfloat yAxis
) {
    LibretroDroid::getInstance().onMotionEvent(port, source, xAxis, yAxis);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_onTouchEvent(
    JNIEnv* env,
    jclass obj,
    jfloat xAxis,
    jfloat yAxis
) {
    LibretroDroid::getInstance().onTouchEvent(xAxis, yAxis);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_onKeyEvent(
    JNIEnv* env,
    jclass obj,
    jint port,
    jint action,
    jint keyCode
) {
    LibretroDroid::getInstance().onKeyEvent(port, action, keyCode);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_create(
    JNIEnv* env,
    jclass obj,
    jint GLESVersion,
    jstring soFilePath,
    jstring systemDir,
    jstring savesDir,
    jobjectArray jVariables,
    jobject shaderConfig,
    jfloat refreshRate,
    jboolean preferLowLatencyAudio,
    jboolean enableVirtualFileSystem,
    jboolean enableMicrophone,
    jboolean skipDuplicateFrames,
    jobject immersiveMode,
    jstring language
) {
    try {
        auto corePath = JniString(env, soFilePath);
        auto deviceLanguage = JniString(env, language);
        auto systemDirectory = JniString(env, systemDir);
        auto savesDirectory = JniString(env, savesDir);

        std::vector<Variable> variables;
        int size = env->GetArrayLength(jVariables);
        for (int i = 0; i < size; i++) {
            auto jVariable = (jobject) env->GetObjectArrayElement(jVariables, i);
            auto variable = JavaUtils::variableFromJava(env, jVariable);
            variables.push_back(variable);
        }

        std::optional<ImmersiveMode::Config> parsedConfig = std::nullopt;
        if (immersiveMode != nullptr) {
            jclass configClass = env->GetObjectClass(immersiveMode);
            jfieldID downscaledWidthField = env->GetFieldID(configClass, "downscaledWidth", "I");
            jfieldID downscaledHeightField = env->GetFieldID(configClass, "downscaledHeight", "I");
            jfieldID blurMaskSizeField = env->GetFieldID(configClass, "blurMaskSize", "I");
            jfieldID blurBrightnessField = env->GetFieldID(configClass, "blurBrightness", "F");
            jfieldID blurSkipUpdateField = env->GetFieldID(configClass, "blurSkipUpdate", "I");
            jfieldID blendFactorField = env->GetFieldID(configClass, "blendFactor", "F");

            ImmersiveMode::Config config {};
            config.downscaledWidth = env->GetIntField(immersiveMode, downscaledWidthField);
            config.downscaledHeight = env->GetIntField(immersiveMode, downscaledHeightField);
            config.blurMaskSize = env->GetIntField(immersiveMode, blurMaskSizeField);
            config.blurBrightness = env->GetFloatField(immersiveMode, blurBrightnessField);
            config.blurSkipUpdate = env->GetIntField(immersiveMode, blurSkipUpdateField);
            config.blendFactor = env->GetFloatField(immersiveMode, blendFactorField);
            parsedConfig = config;
        }

        LibretroDroid::getInstance().create(
            GLESVersion,
            corePath.stdString(),
            systemDirectory.stdString(),
            savesDirectory.stdString(),
            variables,
            JavaUtils::shaderFromJava(env, shaderConfig),
            refreshRate,
            preferLowLatencyAudio,
            enableVirtualFileSystem,
            enableMicrophone,
            skipDuplicateFrames,
            parsedConfig,
            deviceLanguage.stdString()
        );

    } catch (libretrodroid::LibretroDroidError& exception) {
        LOGE("Error in create: %s", exception.what());
        JavaUtils::throwRetroException(env, exception.getErrorCode());
    } catch (std::exception &exception) {
        LOGE("Error in create: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_LOAD_LIBRARY);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_loadGameFromPath(
    JNIEnv* env,
    jclass obj,
    jstring gameFilePath
) {
    auto gamePath = JniString(env, gameFilePath);

    try {
        LibretroDroid::getInstance().loadGameFromPath(gamePath.stdString());
    } catch (std::exception &exception) {
        LOGE("Error in loadGameFromPath: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_LOAD_GAME);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_loadGameFromBytes(
    JNIEnv* env,
    jclass obj,
    jbyteArray gameFileBytes
) {
    try {
        size_t size = env->GetArrayLength(gameFileBytes);
        std::vector<int8_t> data(size);
        if (size > 0) {
            env->GetByteArrayRegion(
                gameFileBytes,
                0,
                static_cast<jsize>(size),
                reinterpret_cast<jbyte*>(data.data())
            );
        }
        LibretroDroid::getInstance().loadGameFromBytes(
            data.empty() ? nullptr : data.data(),
            size
        );
    } catch (std::exception &exception) {
        LOGE("Error in loadGameFromBytes: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_LOAD_GAME);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_loadGameFromVirtualFiles(
        JNIEnv* env,
        jclass obj,
        jobject virtualFileList
) {

    try {
        jmethodID getVirtualFileMethodID = env->GetMethodID(
                env->FindClass("com/swordfish/libretrodroid/DetachedVirtualFile"),
                "getVirtualPath",
                "()Ljava/lang/String;"
        );
        jmethodID getFileDescriptorMethodID = env->GetMethodID(
                env->FindClass("com/swordfish/libretrodroid/DetachedVirtualFile"),
                "getFileDescriptor",
                "()I"
        );

        std::vector<VFSFile> virtualFiles;

        JavaUtils::forEachOnJavaIterable(env, virtualFileList, [&](jobject item) {
            JniString virtualFileName(env,(jstring) env->CallObjectMethod(
                item,
                getVirtualFileMethodID
            ));

            int fileDescriptor = env->CallIntMethod(item, getFileDescriptorMethodID);
            virtualFiles.emplace_back(VFSFile(virtualFileName.stdString(), fileDescriptor));
        });

        LibretroDroid::getInstance().loadGameFromVirtualFiles(std::move(virtualFiles));
    } catch (std::exception &exception) {
        LOGE("Error in loadGameFromDescriptors: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_LOAD_GAME);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_destroy(
    JNIEnv* env,
    jclass obj
) {
    try {
        LibretroDroid::getInstance().destroy();
    } catch (std::exception &exception) {
        LOGE("Error in destroy: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_resume(
    JNIEnv* env,
    jclass obj
) {
    try {
        LibretroDroid::getInstance().resume();
    } catch (std::exception &exception) {
        LOGE("Error in resume: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_pause(
    JNIEnv* env,
    jclass obj
) {
    try {
        LibretroDroid::getInstance().pause();
    } catch (std::exception &exception) {
        LOGE("Error in pause: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_step(
    JNIEnv* env,
    jclass obj,
    jobject glRetroView
) {
    LibretroDroid::getInstance().step();

    if (LibretroDroid::getInstance().requiresVideoRefresh()) {
        LibretroDroid::getInstance().clearRequiresVideoRefresh();
        jclass cls = env->GetObjectClass(glRetroView);
        jmethodID requestAspectRatioUpdate = env->GetMethodID(cls, "refreshAspectRatio", "()V");
        env->CallVoidMethod(glRetroView, requestAspectRatioUpdate);
    }

    if (LibretroDroid::getInstance().isRumbleEnabled()) {
        LibretroDroid::getInstance().handleRumbleUpdates([&](int port, float weak, float strong) {
            jclass cls = env->GetObjectClass(glRetroView);
            jmethodID sendRumbleStrengthMethodID = env->GetMethodID(cls, "sendRumbleEvent", "(IFF)V");
            env->CallVoidMethod(glRetroView, sendRumbleStrengthMethodID, port, weak, strong);
        });
    }

    Achievements::getInstance().notifyJavaEventsIfPending();
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setRumbleEnabled(
    JNIEnv* env,
    jclass obj,
    jboolean enabled
) {
    LibretroDroid::getInstance().setRumbleEnabled(enabled);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setFrameSpeed(
    JNIEnv* env,
    jclass obj,
    jint speed
) {
    LibretroDroid::getInstance().setFrameSpeed(speed);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setFastForwardAudioMode(
    JNIEnv* env,
    jclass obj,
    jint mode
) {
    LibretroDroid::getInstance().setFastForwardAudioMode(mode);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setAudioEnabled(
    JNIEnv* env,
    jclass obj,
    jboolean enabled
) {
    LibretroDroid::getInstance().setAudioEnabled(enabled);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setShaderConfig(
    JNIEnv* env,
    jclass obj,
    jobject shaderConfig
) {
    LibretroDroid::getInstance().setShaderConfig(JavaUtils::shaderFromJava(env, shaderConfig));
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setViewport(
    JNIEnv* env,
    jclass obj,
    jfloat x,
    jfloat y,
    jfloat width,
    jfloat height
) {
    LibretroDroid::getInstance().setViewport(Rect(x, y, width, height));
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setViewportAlignment(
        JNIEnv* env,
        jclass obj,
        jint viewportAlignment
) {
    LibretroDroid::getInstance().setViewportAlignment(viewportAlignment);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setScreenRegions(
    JNIEnv* env,
    jclass /*obj*/,
    jfloatArray regionsFlat
) {
    std::vector<VideoLayout::ScreenRegion> regions;
    if (regionsFlat != nullptr) {
        jsize len = env->GetArrayLength(regionsFlat);
        jsize count = len / 8;
        if (count > 0) {
            regions.reserve(static_cast<size_t>(count));
            jfloat* raw = env->GetFloatArrayElements(regionsFlat, nullptr);
            if (raw != nullptr) {
                for (jsize i = 0; i < count; ++i) {
                    const jsize base = i * 8;
                    VideoLayout::ScreenRegion r {};
                    r.srcX = raw[base + 0];
                    r.srcY = raw[base + 1];
                    r.srcW = raw[base + 2];
                    r.srcH = raw[base + 3];
                    r.dstX = raw[base + 4];
                    r.dstY = raw[base + 5];
                    r.dstW = raw[base + 6];
                    r.dstH = raw[base + 7];
                    regions.push_back(r);
                }
                env->ReleaseFloatArrayElements(regionsFlat, raw, JNI_ABORT);
            }
        }
    }
    LibretroDroid::getInstance().setScreenRegions(regions);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_refreshAspectRatio(
    JNIEnv* env,
    jclass obj
) {
    LibretroDroid::getInstance().refreshAspectRatio();
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_configureAchievements(
    JNIEnv* env,
    jclass /*obj*/,
    jboolean enabled,
    jstring username,
    jstring token,
    jint consoleId,
    jboolean hardcoreEnabled,
    jboolean encoreEnabled,
    jboolean unofficialEnabled,
    jstring serverHost,
    jstring appVersion,
    jstring osRelease,
    jboolean coreHardcoreAllowed,
    jstring coreUnsupportedReason
) {
    AchievementsConfig config {};
    config.enabled = enabled == JNI_TRUE;
    config.username = username != nullptr ? JniString(env, username).stdString() : std::string();
    config.token = token != nullptr ? JniString(env, token).stdString() : std::string();
    config.consoleId = static_cast<uint32_t>(consoleId);
    config.hardcoreEnabled = hardcoreEnabled == JNI_TRUE;
    config.encoreEnabled = encoreEnabled == JNI_TRUE;
    config.unofficialEnabled = unofficialEnabled == JNI_TRUE;
    config.serverHost = serverHost != nullptr ? JniString(env, serverHost).stdString() : std::string();
    config.appVersion = appVersion != nullptr ? JniString(env, appVersion).stdString() : std::string();
    config.osRelease = osRelease != nullptr ? JniString(env, osRelease).stdString() : std::string();
    config.coreHardcoreAllowed = coreHardcoreAllowed == JNI_TRUE;
    config.coreUnsupportedReason =
        coreUnsupportedReason != nullptr ? JniString(env, coreUnsupportedReason).stdString() : std::string();
    LibretroDroid::getInstance().configureAchievements(config);
}

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setHardcoreMode(
    JNIEnv* /*env*/,
    jclass /*obj*/,
    jboolean enabled
) {
    return LibretroDroid::getInstance().setHardcoreMode(enabled == JNI_TRUE) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_canPauseAchievements(
    JNIEnv* /*env*/,
    jclass /*obj*/
) {
    int remainingFrames = 0;
    bool allowed = LibretroDroid::getInstance().canPauseAchievements(&remainingFrames);
    if (allowed) {
        return 0;
    }
    return remainingFrames > 0 ? static_cast<jint>(remainingFrames) : 1;
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_idleAchievements(
    JNIEnv* /*env*/,
    jclass /*obj*/
) {
    LibretroDroid::getInstance().idleAchievements();
}

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_isCoreSettingAllowedInHardcore(
    JNIEnv* env,
    jclass /*obj*/,
    jstring libraryName,
    jint consoleId,
    jstring key,
    jstring value
) {
    std::string libStr = libraryName != nullptr ? JniString(env, libraryName).stdString() : std::string();
    std::string keyStr = key != nullptr ? JniString(env, key).stdString() : std::string();
    std::string valStr = value != nullptr ? JniString(env, value).stdString() : std::string();
    bool allowed = Environment::isSettingAllowedInHardcore(
        libStr,
        static_cast<uint32_t>(consoleId),
        keyStr,
        valStr
    );
    return allowed ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_completeAchievementHttpRequest(
    JNIEnv* env,
    jclass /*obj*/,
    jlong requestId,
    jint httpStatusCode,
    jbyteArray responseBytes
) {
    if (responseBytes == nullptr) {
        LibretroDroid::getInstance().completeAchievementHttpRequest(
            static_cast<uint64_t>(requestId),
            httpStatusCode,
            nullptr,
            0
        );
        return;
    }

    jsize len = env->GetArrayLength(responseBytes);
    jboolean isCopy = JNI_FALSE;
    jbyte* raw = env->GetByteArrayElements(responseBytes, &isCopy);
    LibretroDroid::getInstance().completeAchievementHttpRequest(
        static_cast<uint64_t>(requestId),
        httpStatusCode,
        reinterpret_cast<const char*>(raw),
        static_cast<size_t>(len)
    );
    env->ReleaseByteArrayElements(responseBytes, raw, JNI_ABORT);
}

JNIEXPORT jobjectArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_pollAchievementEvents(
    JNIEnv* env,
    jclass /*obj*/
) {
    auto events = Achievements::getInstance().pollEvents();
    jclass eventClass = env->FindClass("com/swordfish/libretrodroid/AchievementEvent");
    if (eventClass == nullptr) {
        return nullptr;
    }
    jmethodID ctor = env->GetMethodID(
        eventClass,
        "<init>",
        "(IILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;FIIIIZFI)V"
    );

    jobjectArray array = env->NewObjectArray(static_cast<jsize>(events.size()), eventClass, nullptr);
    for (size_t i = 0; i < events.size(); i++) {
        const auto& ev = events[i];
        jstring jTitle = safeJStringFromUtf8(env, ev.title);
        jstring jDesc = safeJStringFromUtf8(env, ev.description);
        jstring jBadge = safeJStringFromUtf8(env, ev.badgeUrl);
        jstring jProgress = safeJStringFromUtf8(env, ev.measuredProgress);

        jobject item = env->NewObject(
            eventClass,
            ctor,
            static_cast<jint>(ev.type),
            static_cast<jint>(ev.id),
            jTitle,
            jDesc,
            jBadge,
            jProgress,
            static_cast<jfloat>(ev.measuredPercent),
            static_cast<jint>(ev.points),
            static_cast<jint>(ev.totalPoints),
            static_cast<jint>(ev.unlockedCount),
            static_cast<jint>(ev.totalCount),
            ev.hardcore ? JNI_TRUE : JNI_FALSE,
            static_cast<jfloat>(ev.rarity),
            static_cast<jint>(ev.achievementType)
        );

        env->SetObjectArrayElement(array, static_cast<jsize>(i), item);

        env->DeleteLocalRef(jTitle);
        env->DeleteLocalRef(jDesc);
        env->DeleteLocalRef(jBadge);
        env->DeleteLocalRef(jProgress);
        env->DeleteLocalRef(item);
    }

    env->DeleteLocalRef(eventClass);
    return array;
}

JNIEXPORT jobject JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_getAchievementsSnapshot(
    JNIEnv* env,
    jclass /*obj*/
) {
    NativeAchievementsSnapshot snap = LibretroDroid::getInstance().captureAchievementsSnapshot();

    jclass defClass = env->FindClass("com/swordfish/libretrodroid/AchievementDef");
    jclass lbClass = env->FindClass("com/swordfish/libretrodroid/LeaderboardDef");
    jclass trackerClass = env->FindClass("com/swordfish/libretrodroid/LeaderboardTracker");
    jclass snapClass = env->FindClass("com/swordfish/libretrodroid/AchievementsSnapshot");
    if (defClass == nullptr || lbClass == nullptr || trackerClass == nullptr || snapClass == nullptr) {
        return nullptr;
    }

    jmethodID defCtor = env->GetMethodID(
        defClass,
        "<init>",
        "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IIIIIJLjava/lang/String;FFFI)V"
    );
    jmethodID lbCtor = env->GetMethodID(
        lbClass,
        "<init>",
        "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;IIZ)V"
    );
    jmethodID trackerCtor = env->GetMethodID(
        trackerClass,
        "<init>",
        "(ILjava/lang/String;)V"
    );
    jmethodID snapCtor = env->GetMethodID(
        snapClass,
        "<init>",
        "(ILjava/lang/String;ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IIIIZ[Lcom/swordfish/libretrodroid/AchievementDef;[Lcom/swordfish/libretrodroid/LeaderboardDef;[Lcom/swordfish/libretrodroid/LeaderboardTracker;)V"
    );

    jobjectArray defsArray = env->NewObjectArray(
        static_cast<jsize>(snap.achievements.size()),
        defClass,
        nullptr
    );

    for (size_t i = 0; i < snap.achievements.size(); i++) {
        const auto& d = snap.achievements[i];
        jstring jTitle = safeJStringFromUtf8(env, d.title);
        jstring jDesc = safeJStringFromUtf8(env, d.description);
        jstring jBadge = safeJStringFromUtf8(env, d.badgeUrl);
        jstring jBadgeLocked = safeJStringFromUtf8(env, d.badgeLockedUrl);
        jstring jProgress = safeJStringFromUtf8(env, d.measuredProgress);

        jobject item = env->NewObject(
            defClass,
            defCtor,
            static_cast<jint>(d.id),
            jTitle,
            jDesc,
            jBadge,
            jBadgeLocked,
            static_cast<jint>(d.points),
            static_cast<jint>(d.state),
            static_cast<jint>(d.category),
            static_cast<jint>(d.bucket),
            static_cast<jint>(d.unlockedMask),
            static_cast<jlong>(d.unlockTimeSeconds),
            jProgress,
            static_cast<jfloat>(d.measuredPercent),
            static_cast<jfloat>(d.rarity),
            static_cast<jfloat>(d.rarityHardcore),
            static_cast<jint>(d.type)
        );

        env->SetObjectArrayElement(defsArray, static_cast<jsize>(i), item);

        env->DeleteLocalRef(jTitle);
        env->DeleteLocalRef(jDesc);
        env->DeleteLocalRef(jBadge);
        env->DeleteLocalRef(jBadgeLocked);
        env->DeleteLocalRef(jProgress);
        env->DeleteLocalRef(item);
    }

    jobjectArray lbsArray = env->NewObjectArray(
        static_cast<jsize>(snap.leaderboards.size()),
        lbClass,
        nullptr
    );
    for (size_t i = 0; i < snap.leaderboards.size(); i++) {
        const auto& lb = snap.leaderboards[i];
        jstring jTitle = safeJStringFromUtf8(env, lb.title);
        jstring jDesc = safeJStringFromUtf8(env, lb.description);
        jstring jTrackerVal = safeJStringFromUtf8(env, lb.trackerValue);

        jobject item = env->NewObject(
            lbClass,
            lbCtor,
            static_cast<jint>(lb.id),
            jTitle,
            jDesc,
            jTrackerVal,
            static_cast<jint>(lb.state),
            static_cast<jint>(lb.format),
            lb.lowerIsBetter ? JNI_TRUE : JNI_FALSE
        );
        env->SetObjectArrayElement(lbsArray, static_cast<jsize>(i), item);

        env->DeleteLocalRef(jTitle);
        env->DeleteLocalRef(jDesc);
        env->DeleteLocalRef(jTrackerVal);
        env->DeleteLocalRef(item);
    }

    jobjectArray trackersArray = env->NewObjectArray(
        static_cast<jsize>(snap.leaderboardTrackers.size()),
        trackerClass,
        nullptr
    );
    for (size_t i = 0; i < snap.leaderboardTrackers.size(); i++) {
        const auto& tr = snap.leaderboardTrackers[i];
        jstring jDisplay = safeJStringFromUtf8(env, tr.display);
        jobject item = env->NewObject(
            trackerClass,
            trackerCtor,
            static_cast<jint>(tr.id),
            jDisplay
        );
        env->SetObjectArrayElement(trackersArray, static_cast<jsize>(i), item);
        env->DeleteLocalRef(jDisplay);
        env->DeleteLocalRef(item);
    }

    jstring jStatusMsg = safeJStringFromUtf8(env, snap.statusMessage);
    jstring jGameTitle = safeJStringFromUtf8(env, snap.gameTitle);
    jstring jGameBadge = safeJStringFromUtf8(env, snap.gameBadgeUrl);
    jstring jGameHash = safeJStringFromUtf8(env, snap.gameHash);
    jstring jRichPresence = safeJStringFromUtf8(env, snap.richPresence);
    jstring jUserAgent = safeJStringFromUtf8(env, snap.userAgent);

    jobject result = env->NewObject(
        snapClass,
        snapCtor,
        static_cast<jint>(snap.sessionStatus),
        jStatusMsg,
        static_cast<jint>(snap.gameId),
        jGameTitle,
        jGameBadge,
        jGameHash,
        jRichPresence,
        jUserAgent,
        static_cast<jint>(snap.totalCount),
        static_cast<jint>(snap.unlockedCount),
        static_cast<jint>(snap.totalPoints),
        static_cast<jint>(snap.unlockedPoints),
        snap.hardcoreActive ? JNI_TRUE : JNI_FALSE,
        defsArray,
        lbsArray,
        trackersArray
    );

    env->DeleteLocalRef(jStatusMsg);
    env->DeleteLocalRef(jGameTitle);
    env->DeleteLocalRef(jGameBadge);
    env->DeleteLocalRef(jGameHash);
    env->DeleteLocalRef(jRichPresence);
    env->DeleteLocalRef(jUserAgent);
    env->DeleteLocalRef(defsArray);
    env->DeleteLocalRef(lbsArray);
    env->DeleteLocalRef(trackersArray);
    env->DeleteLocalRef(defClass);
    env->DeleteLocalRef(lbClass);
    env->DeleteLocalRef(trackerClass);
    env->DeleteLocalRef(snapClass);

    return result;
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setImmersiveBackgroundVisible(
        JNIEnv* env,
        jclass obj,
        jboolean visible
) {
    LibretroDroid::getInstance().setImmersiveBackgroundVisible(visible);
}

}

}
