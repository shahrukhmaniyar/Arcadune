/*
 *     Copyright (C) 2019  Filippo Scognamiglio
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

#include <algorithm>
#include <cstring>
#include <string>
#include <strings.h>
#include <unistd.h>
#include <utility>
#include <vector>
#include <unordered_set>

#include "libretrodroid.h"
#include "utils/libretrodroidexception.h"
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
#include "environment.h"
#include "renderers/es3/framebufferrenderer.h"
#include "renderers/es2/imagerendereres2.h"
#include "renderers/es3/imagerendereres3.h"
#include "utils/utils.h"
#include "utils/rect.h"
#include "errorcodes.h"
#include "vfs/vfs.h"
#include "achievements.h"

namespace libretrodroid {

namespace {
constexpr char kAchievementsProgressMagic[8] = {'A', 'R', 'C', 'A', '_', 'R', 'C', 'H'};
} // namespace

uintptr_t LibretroDroid::callback_get_current_framebuffer() {
    return LibretroDroid::getInstance().handleGetCurrentFrameBuffer();
}

void LibretroDroid::callback_hw_video_refresh(
    const void *data,
    unsigned width,
    unsigned height,
    size_t pitch
) {
    LOGD("hw video refresh callback called %i %i", width, height);
    LibretroDroid::getInstance().handleVideoRefresh(data, width, height, pitch);
}

void LibretroDroid::callback_audio_sample(int16_t left, int16_t right) {
    int16_t frame[2] = { left, right };
    LibretroDroid::getInstance().handleAudioCallback(frame, 1);
}

size_t LibretroDroid::callback_set_audio_sample_batch(const int16_t *data, size_t frames) {
    return LibretroDroid::getInstance().handleAudioCallback(data, frames);
}

void LibretroDroid::callback_retro_set_input_poll() {
    // Do nothing in here...
}

int16_t LibretroDroid::callback_set_input_state(
    unsigned int port,
    unsigned int device,
    unsigned int index,
    unsigned int id
) {
    return LibretroDroid::getInstance().handleSetInputState(port, device, index, id);
}

void LibretroDroid::updateAudioSampleRateMultiplier() {
    if (audio) {
        audio->setPlaybackSpeed(frameSpeed);
        audio->setFastForwardAudioMode(fastForwardAudioMode);
    }
}

// TODO... Do we really need this?
void LibretroDroid::resetGlobalVariables() {
    core = nullptr;
    audio = nullptr;
    video = nullptr;
    fpsSync = nullptr;
    input = nullptr;
    rumble = nullptr;
    gameLoaded = false;
    sramLoadFailedSizeMismatch_ = false;
    coreSoFilePath_.clear();
    loadedGameMode_ = LoadedGameMode::NONE;
    loadedGamePath_.clear();
    pristineRomBytes_.clear();
    activeRomBytes_.clear();
    activeGamePathCString_.clear();
    lastSurfaceWidth_ = 0;
    lastSurfaceHeight_ = 0;
    controllerTypes_.clear();
}

int LibretroDroid::availableDisks() {
    std::lock_guard<std::mutex> lock(coreLock);
    return Environment::getInstance().getRetroDiskControlCallback() != nullptr
           ? Environment::getInstance().getRetroDiskControlCallback()->get_num_images()
           : 0;
}

int LibretroDroid::currentDisk() {
    std::lock_guard<std::mutex> lock(coreLock);
    return Environment::getInstance().getRetroDiskControlCallback() != nullptr
           ? Environment::getInstance().getRetroDiskControlCallback()->get_image_index()
           : 0;
}

void LibretroDroid::changeDisk(unsigned int index) {
    {
        std::lock_guard<std::mutex> lock(coreLock);
        auto* diskControl = Environment::getInstance().getRetroDiskControlCallback();
        if (diskControl == nullptr) {
            LOGE("Cannot swap disk. This platform does not support it.");
            return;
        }

        if (index >= diskControl->get_num_images()) {
            LOGE("Requested image index is not valid.");
            return;
        }

        if (diskControl->get_image_index() != index) {
            diskControl->set_eject_state(true);
            diskControl->set_image_index(index);
            diskControl->set_eject_state(false);

            std::string diskPath = Environment::getInstance().getDiskImagePath(index);
            if (diskPath.empty() && VFS::getInstance().getVirtualFileCount() > 1) {
                std::string primary = VFS::getInstance().getVirtualFilePath(0);
                auto dot = primary.find_last_of('.');
                bool primaryIsPlaylist = dot != std::string::npos &&
                    strcasecmp(primary.c_str() + dot + 1, "m3u") == 0;
                size_t vfsIndex = primaryIsPlaylist ? (static_cast<size_t>(index) + 1) : static_cast<size_t>(index);
                diskPath = VFS::getInstance().getVirtualFilePath(vfsIndex);
            }
            if (!diskPath.empty()) {
                Achievements::getInstance().changeMediaLocked(diskPath, nullptr, 0);
            }
        }
    }
    Achievements::getInstance().notifyJavaEventsIfPending();
}

void LibretroDroid::updateVariable(const Variable& variable) {
    Environment::getInstance().updateVariable(variable.key, variable.value);
}

std::vector<Variable> LibretroDroid::getVariables() {
    return Environment::getInstance().getVariables();
}

std::vector<std::vector<struct Controller>> LibretroDroid::getControllers() {
    return Environment::getInstance().getControllers();
}

void LibretroDroid::setControllerType(unsigned int port, unsigned int type) {
    std::lock_guard<std::mutex> lock(coreLock);
    controllerTypes_[port] = type;
    if (core) {
        core->retro_set_controller_port_device(port, type);
    }
}

bool LibretroDroid::unserializeState(int8_t *data, size_t size) {
    std::lock_guard<std::mutex> lock(coreLock);

    if (!core || data == nullptr || size == 0) {
        return false;
    }
    if (Achievements::getInstance().isHardcoreEnforcedLocked()) {
        LOGW("Blocked unserializeState while RetroAchievements Hardcore mode is active.");
        return false;
    }

    size_t coreSize = size;
    const uint8_t* progressPtr = nullptr;
    size_t progressLen = 0;

    if (size >= 12 && memcmp(data + size - 8, kAchievementsProgressMagic, 8) == 0) {
        const auto* lenBytes = reinterpret_cast<const uint8_t*>(data + size - 12);
        uint32_t parsedLen =
            static_cast<uint32_t>(lenBytes[0]) |
            (static_cast<uint32_t>(lenBytes[1]) << 8) |
            (static_cast<uint32_t>(lenBytes[2]) << 16) |
            (static_cast<uint32_t>(lenBytes[3]) << 24);
        if (static_cast<size_t>(parsedLen) + 12 <= size) {
            coreSize = size - 12 - static_cast<size_t>(parsedLen);
            progressPtr = reinterpret_cast<const uint8_t*>(data + coreSize);
            progressLen = static_cast<size_t>(parsedLen);
        }
    }

    bool ok = core->retro_unserialize(data, coreSize);
    if (ok) {
        Achievements::getInstance().refreshMemoryRegionsLocked(
            core.get(),
            Environment::getInstance().getMemoryMap()
        );
        Achievements::getInstance().onStateUnserialized(progressPtr, progressLen);
    }
    return ok;
}

JNIEXPORT jboolean JNICALL LibretroDroid::unserializeSRAM(int8_t* data, size_t size) {
    std::lock_guard<std::mutex> lock(coreLock);

    if (!core || data == nullptr || size == 0) {
        return false;
    }

    size_t sramSize = core->retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    void *sramState = core->retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);

    if (sramState == nullptr || sramSize == 0) {
        LOGE("Cannot load SRAM: nullptr or 0 size in retro_get_memory_data");
        return false;
    }

    // mGBA appends a 16-byte RTC footer onto 128KB Flash saves (131088 vs 131072 bytes).
    size_t copySize = size;
    if (size == 131088 && sramSize == 131072) {
        copySize = 131072;
    }

    if (copySize > sramSize) {
        LOGE("Cannot load SRAM: size mismatch (%zu > %zu); preserving existing .srm on disk", size, sramSize);
        sramLoadFailedSizeMismatch_ = true;
        return false;
    }

    sramLoadFailedSizeMismatch_ = false;
    memcpy(sramState, data, copySize);

    return true;
}

std::pair<int8_t*, size_t> LibretroDroid::serializeSRAM() {
    std::lock_guard<std::mutex> lock(coreLock);

    if (!core) {
        return std::pair(nullptr, 0);
    }
    if (sramLoadFailedSizeMismatch_) {
        LOGW("Skipping serializeSRAM because initial SRAM load was rejected due to cross-core size mismatch.");
        return std::pair(nullptr, 0);
    }

    size_t size = core->retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    void* sramPtr = core->retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    if (size == 0 || sramPtr == nullptr) {
        return std::pair(nullptr, 0);
    }

    auto* data = new int8_t[size];
    memcpy(data, reinterpret_cast<const int8_t*>(sramPtr), size);

    return std::pair(data, size);
}

void LibretroDroid::onSurfaceChanged(unsigned int width, unsigned int height) {
    LOGD("Performing libretrodroid onSurfaceChanged");
    lastSurfaceWidth_ = width;
    lastSurfaceHeight_ = height;
    if (video) {
        video->updateScreenSize(width, height);
    }
}

void LibretroDroid::recreateVideoSurfaceLocked() {
    if (!core) {
        return;
    }

    struct retro_system_av_info system_av_info {};
    core->retro_get_system_av_info(&system_av_info);

    video = nullptr;

    Video::RenderingOptions renderingOptions {
        Environment::getInstance().isUseHwAcceleration(),
        system_av_info.geometry.base_width,
        system_av_info.geometry.base_height,
        Environment::getInstance().isUseDepth(),
        Environment::getInstance().isUseStencil(),
        openglESVersion,
        Environment::getInstance().getPixelFormat()
    };

    auto newVideo = new Video(
        renderingOptions,
        fragmentShaderConfig,
        Environment::getInstance().isBottomLeftOrigin(),
        Environment::getInstance().getScreenRotation(),
        skipDuplicateFrames,
        immersiveModeEnabled,
        viewportRect,
        immersiveModeConfig,
        viewportAlignment
    );

    video = std::unique_ptr<Video>(newVideo);
    if (lastSurfaceWidth_ > 0 && lastSurfaceHeight_ > 0) {
        video->updateScreenSize(lastSurfaceWidth_, lastSurfaceHeight_);
    }
    if (!screenRegions.empty()) {
        video->updateScreenRegions(screenRegions);
    }

    if (Environment::getInstance().getHwContextReset() != nullptr) {
        Environment::getInstance().getHwContextReset()();
    }
}

void LibretroDroid::onSurfaceCreated() {
    LOGD("Performing libretrodroid onSurfaceCreated");
    std::lock_guard<std::mutex> lock(coreLock);
    recreateVideoSurfaceLocked();
}

void LibretroDroid::onMotionEvent(
    unsigned int port,
    unsigned int source,
    float xAxis,
    float yAxis
) {
    LOGD("Received motion event: %d %.2f, %.2f", source, xAxis, yAxis);
    if (input) {
        input->onMotionEvent(port, source, xAxis, yAxis);
    }
}

void LibretroDroid::onTouchEvent(float xAxis, float yAxis) {
    LOGD("Received touch event: %.2f, %.2f", xAxis, yAxis);
    if (input && video) {
        auto [x, y] = video->getLayout().getRelativePosition(xAxis, yAxis);
        input->onMotionEvent(0, Input::MOTION_SOURCE_POINTER, x, y);
    }
}

void LibretroDroid::onKeyEvent(unsigned int port, int action, int keyCode) {
    LOGD("Received key event with action (%d) and keycode (%d)", action, keyCode);
    if (input) {
        input->onKeyEvent(port, action, keyCode);
    }
}

void LibretroDroid::create(
    unsigned int GLESVersion,
    const std::string& soFilePath,
    const std::string& systemDir,
    const std::string& savesDir,
    std::vector<Variable> variables,
    const ShaderManager::Config& shaderConfig,
    float refreshRate,
    bool lowLatencyAudio,
    bool enableVirtualFileSystem,
    bool enableMicrophone,
    bool duplicateFrames,
    std::optional<ImmersiveMode::Config> immersiveModeConfig,
    const std::string& language
) {
    LOGD("Performing libretrodroid create");

    resetGlobalVariables();

    Environment::getInstance().initialize(systemDir, savesDir, &callback_get_current_framebuffer);
    Environment::getInstance().setLanguage(language);
    Environment::getInstance().setEnableVirtualFileSystem(enableVirtualFileSystem);
    Environment::getInstance().setEnableMicrophone(enableMicrophone);

    openglESVersion = GLESVersion;
    screenRefreshRate = refreshRate;
    skipDuplicateFrames = duplicateFrames;
    immersiveModeEnabled = GLESVersion >= 3 && immersiveModeConfig.has_value();
    this->immersiveModeConfig = immersiveModeConfig.value_or(ImmersiveMode::Config{});
    audioEnabled = true;
    frameSpeed = 1;
    screenRegions.clear();

    coreSoFilePath_ = soFilePath;
    core = std::make_unique<Core>(soFilePath);

    Achievements::getInstance().updateCoreMetadataLocked(core.get());
    Environment::getInstance().setHardcoreContext(
        Achievements::getInstance().isHardcoreEnforcedLocked(),
        Achievements::getInstance().getCoreLibraryNameLocked(),
        Achievements::getInstance().getEffectiveConsoleIdLocked()
    );

    core->retro_set_video_refresh(&callback_hw_video_refresh);
    core->retro_set_environment(&Environment::callback_environment);
    core->retro_set_audio_sample(&callback_audio_sample);
    core->retro_set_audio_sample_batch(&callback_set_audio_sample_batch);
    core->retro_set_input_poll(&callback_retro_set_input_poll);
    core->retro_set_input_state(&callback_set_input_state);

    std::for_each(variables.begin(), variables.end(), [&](const Variable& v) {
        updateVariable(v);
    });

    core->retro_init();
    Achievements::getInstance().updateCoreMetadataLocked(core.get());
    Environment::getInstance().setHardcoreContext(
        Achievements::getInstance().isHardcoreEnforcedLocked(),
        Achievements::getInstance().getCoreLibraryNameLocked(),
        Achievements::getInstance().getEffectiveConsoleIdLocked()
    );

    preferLowLatencyAudio = lowLatencyAudio;

    // HW accelerated cores are only supported on opengles 3.
    if (Environment::getInstance().isUseHwAcceleration() && openglESVersion < 3) {
        throw LibretroDroidError("OpenGL ES 3 is required for this Core", ERROR_GL_NOT_COMPATIBLE);
    }

    fragmentShaderConfig = shaderConfig;

    rumble = std::make_unique<Rumble>();
}

bool LibretroDroid::reloadCurrentGameIntoCoreLocked() {
    if (!core || loadedGameMode_ == LoadedGameMode::NONE) {
        return false;
    }

    struct retro_system_info system_info {};
    core->retro_get_system_info(&system_info);

    struct retro_game_info game_info {};
    game_info.meta = nullptr;

    switch (loadedGameMode_) {
        case LoadedGameMode::PATH: {
            activeGamePathCString_ = loadedGamePath_;
            game_info.path = activeGamePathCString_.c_str();
            if (system_info.need_fullpath) {
                activeRomBytes_.clear();
                game_info.data = nullptr;
                game_info.size = 0;
            } else {
                if (pristineRomBytes_.empty()) {
                    struct Utils::ReadResult file = Utils::readFileAsBytes(loadedGamePath_);
                    pristineRomBytes_.assign(
                        reinterpret_cast<const int8_t*>(file.data),
                        reinterpret_cast<const int8_t*>(file.data) + file.size
                    );
                    delete[] file.data;
                }
                activeRomBytes_ = pristineRomBytes_;
                game_info.data = activeRomBytes_.empty() ? nullptr : activeRomBytes_.data();
                game_info.size = activeRomBytes_.size();
            }
            break;
        }

        case LoadedGameMode::BYTES: {
            activeGamePathCString_.clear();
            game_info.path = nullptr;
            if (system_info.need_fullpath) {
                activeRomBytes_.clear();
                game_info.data = nullptr;
                game_info.size = 0;
            } else {
                activeRomBytes_ = pristineRomBytes_;
                game_info.data = activeRomBytes_.empty() ? nullptr : activeRomBytes_.data();
                game_info.size = activeRomBytes_.size();
            }
            break;
        }

        case LoadedGameMode::VIRTUAL_FILES: {
            if (VFS::getInstance().getVirtualFileCount() == 0) {
                return false;
            }
            VFS::getInstance().resetAllOffsets();
            activeGamePathCString_ = loadedGamePath_;
            game_info.path = activeGamePathCString_.c_str();
            bool loadUsingVFS = system_info.need_fullpath || VFS::getInstance().getVirtualFileCount() > 1;
            if (loadUsingVFS) {
                activeRomBytes_.clear();
                game_info.data = nullptr;
                game_info.size = 0;
            } else {
                if (pristineRomBytes_.empty()) {
                    int dupFd = dup(VFS::getInstance().getVirtualFileFD(0));
                    if (dupFd < 0) {
                        return false;
                    }
                    lseek64(dupFd, 0, SEEK_SET);
                    struct Utils::ReadResult file = Utils::readFileAsBytes(dupFd);
                    pristineRomBytes_.assign(
                        reinterpret_cast<const int8_t*>(file.data),
                        reinterpret_cast<const int8_t*>(file.data) + file.size
                    );
                    delete[] file.data;
                }
                activeRomBytes_ = pristineRomBytes_;
                game_info.data = activeRomBytes_.empty() ? nullptr : activeRomBytes_.data();
                game_info.size = activeRomBytes_.size();
            }
            break;
        }

        case LoadedGameMode::NONE:
            return false;
    }

    return core->retro_load_game(&game_info);
}

void LibretroDroid::loadGameFromPath(const std::string& gamePath) {
    LOGD("Performing libretrodroid loadGameFromPath");
    loadedGameMode_ = LoadedGameMode::PATH;
    loadedGamePath_ = gamePath;
    pristineRomBytes_.clear();

    struct retro_system_info system_info {};
    core->retro_get_system_info(&system_info);

    if (!system_info.need_fullpath) {
        struct Utils::ReadResult file = Utils::readFileAsBytes(gamePath);
        pristineRomBytes_.assign(
            reinterpret_cast<const int8_t*>(file.data),
            reinterpret_cast<const int8_t*>(file.data) + file.size
        );
        delete[] file.data;
    }

    Achievements::getInstance().prepareGameHash(
        gamePath,
        pristineRomBytes_.empty() ? nullptr : reinterpret_cast<const uint8_t*>(pristineRomBytes_.data()),
        pristineRomBytes_.size()
    );
    Environment::getInstance().setHardcoreContext(
        Achievements::getInstance().isHardcoreEnforcedLocked(),
        Achievements::getInstance().getCoreLibraryNameLocked(),
        Achievements::getInstance().getEffectiveConsoleIdLocked()
    );

    if (!reloadCurrentGameIntoCoreLocked()) {
        LOGE("Cannot load game. Leaving.");
        throw std::runtime_error("Cannot load game");
    }

    gameLoaded = true;
    afterGameLoad();
}

void LibretroDroid::loadGameFromBytes(const int8_t *data, size_t size) {
    LOGD("Performing libretrodroid loadGameFromBytes");
    loadedGameMode_ = LoadedGameMode::BYTES;
    loadedGamePath_.clear();
    if (data != nullptr && size > 0) {
        pristineRomBytes_.assign(data, data + size);
    } else {
        pristineRomBytes_.clear();
    }

    Achievements::getInstance().prepareGameHash(
        "",
        pristineRomBytes_.empty() ? nullptr : reinterpret_cast<const uint8_t*>(pristineRomBytes_.data()),
        pristineRomBytes_.size()
    );
    Environment::getInstance().setHardcoreContext(
        Achievements::getInstance().isHardcoreEnforcedLocked(),
        Achievements::getInstance().getCoreLibraryNameLocked(),
        Achievements::getInstance().getEffectiveConsoleIdLocked()
    );

    if (!reloadCurrentGameIntoCoreLocked()) {
        LOGE("Cannot load game. Leaving.");
        throw std::runtime_error("Cannot load game");
    }

    gameLoaded = true;
    afterGameLoad();
}

void LibretroDroid::loadGameFromVirtualFiles(std::vector<VFSFile> virtualFiles) {
    LOGD("Performing libretrodroid loadGameFromVirtualFiles");
    struct retro_system_info system_info {};
    core->retro_get_system_info(&system_info);

    if (virtualFiles.empty()) {
        LOGE("Calling loadGameFromVirtualFiles without any file.");
        throw std::runtime_error("Calling loadGameFromVirtualFiles without any file.");
    }

    std::string firstFilePath = virtualFiles[0].getFileName();
    int firstFileFD = virtualFiles[0].getFD();
    bool loadUsingVFS = system_info.need_fullpath || virtualFiles.size() > 1;

    loadedGameMode_ = LoadedGameMode::VIRTUAL_FILES;
    loadedGamePath_ = firstFilePath;
    pristineRomBytes_.clear();

    if (!loadUsingVFS) {
        int dupFd = dup(firstFileFD);
        if (dupFd < 0) {
            throw std::runtime_error("Cannot duplicate virtual file descriptor.");
        }
        lseek64(dupFd, 0, SEEK_SET);
        struct Utils::ReadResult file = Utils::readFileAsBytes(dupFd);
        pristineRomBytes_.assign(
            reinterpret_cast<const int8_t*>(file.data),
            reinterpret_cast<const int8_t*>(file.data) + file.size
        );
        delete[] file.data;
    }

    VFS::getInstance().initialize(std::move(virtualFiles));

    Achievements::getInstance().prepareGameHash(
        firstFilePath,
        pristineRomBytes_.empty() ? nullptr : reinterpret_cast<const uint8_t*>(pristineRomBytes_.data()),
        pristineRomBytes_.size()
    );
    Environment::getInstance().setHardcoreContext(
        Achievements::getInstance().isHardcoreEnforcedLocked(),
        Achievements::getInstance().getCoreLibraryNameLocked(),
        Achievements::getInstance().getEffectiveConsoleIdLocked()
    );

    if (!reloadCurrentGameIntoCoreLocked()) {
        LOGE("Cannot load game. Leaving.");
        throw std::runtime_error("Cannot load game");
    }

    gameLoaded = true;
    afterGameLoad();
}

void LibretroDroid::destroy() {
    std::lock_guard<std::mutex> lock(coreLock);

    if (!core) {
        return;
    }

    LOGD("Performing libretrodroid destroy");

    Achievements::getInstance().shutdown();

    if (gameLoaded &&
        Environment::getInstance().getHwContextDestroy() != nullptr &&
        eglGetCurrentContext() != EGL_NO_CONTEXT) {
        Environment::getInstance().getHwContextDestroy()();
    }

    if (gameLoaded) {
        core->retro_unload_game();
        gameLoaded = false;
    }
    core->retro_deinit();

    video = nullptr;
    core = nullptr;
    rumble = nullptr;
    fpsSync = nullptr;
    audio = nullptr;

    Environment::getInstance().deinitialize();
    VFS::getInstance().deinitialize();
}

void LibretroDroid::resume() {
    LOGD("Performing libretrodroid resume");

    input = std::make_unique<Input>();

    fpsSync->reset();
    audio->start();
    refreshAspectRatio();
}

void LibretroDroid::pause() {
    LOGD("Performing libretrodroid pause");
    audio->stop();

    input = nullptr;
}

void LibretroDroid::step() {
    std::lock_guard<std::mutex> lock(coreLock);

    if (!core || !gameLoaded) {
        return;
    }

    if (Achievements::getInstance().consumeExternalSdkResetRequestLocked()) {
        performCleanHardcoreRestartLocked();
    }

    LOGD("Stepping into retro_run()");

    unsigned frames = 1;
    if (fpsSync) {
        unsigned requestedFrames = fpsSync->advanceFrames();

        // If the application runs too slow it's better to just skip those frames.
        frames = std::min(requestedFrames, 2u);
    }

    const size_t totalRuns = frames * frameSpeed;
    for (size_t i = 0; i < totalRuns; i++) {
        // Fast-forward: only the last frame of the batch is drawn, the others are run and thrown away.
        skipVideoFrame = (i + 1 < totalRuns);
        core->retro_run();
        Achievements::getInstance().stepFrame(Environment::getInstance().getMemoryMap());
    }
    skipVideoFrame = false;

    if (video && !video->rendersInVideoCallback()) {
        video->renderFrame();
    }

    if (fpsSync) {
        fpsSync->wait();
    }

    if (rumble && rumbleEnabled) {
        rumble->fetchFromEnvironment();
    }

    // Some games override the core geometry at runtime. These fields get updated in retro_run().
    if (video && Environment::getInstance().isGameGeometryUpdated()) {
        Environment::getInstance().clearGameGeometryUpdated();

        video->updateRendererSize(
            Environment::getInstance().getGameGeometryWidth(),
            Environment::getInstance().getGameGeometryHeight()
        );

        dirtyVideo = true;
    }

    if (video && Environment::getInstance().isScreenRotationUpdated()) {
        Environment::getInstance().clearScreenRotationUpdated();

        video->updateRotation(Environment::getInstance().getScreenRotation());
    }
}

float LibretroDroid::getAspectRatio() {
    float gameAspectRatio = Environment::getInstance().retrieveGameSpecificAspectRatio();
    return gameAspectRatio > 0 ? gameAspectRatio : defaultAspectRatio;
}

void LibretroDroid::refreshAspectRatio() {
    video->updateAspectRatio(getAspectRatio());
}

void LibretroDroid::setRumbleEnabled(bool enabled) {
    rumbleEnabled = enabled;
}

bool LibretroDroid::isRumbleEnabled() const {
    return rumbleEnabled;
}

void LibretroDroid::setFrameSpeed(unsigned int speed) {
    std::lock_guard<std::mutex> lock(coreLock);
    frameSpeed = std::max(1u, speed);
    updateAudioSampleRateMultiplier();
}

void LibretroDroid::setFastForwardAudioMode(int mode) {
    fastForwardAudioMode = mode;
    if (audio) {
        audio->setFastForwardAudioMode(mode);
    }
}

void LibretroDroid::setAudioEnabled(bool enabled) {
    audioEnabled = enabled;
}

void LibretroDroid::setShaderConfig(ShaderManager::Config shaderConfig) {
    fragmentShaderConfig = std::move(shaderConfig);
    if (video) {
        video->updateShaderType(fragmentShaderConfig);
    }
}

void LibretroDroid::handleVideoRefresh(
    const void *data,
    unsigned int width,
    unsigned int height,
    size_t pitch
) {
    if (video && !skipVideoFrame) {
        video->onNewFrame(data, width, height, pitch);

        if (video->rendersInVideoCallback()) {
            video->renderFrame();
        }
    }
}

size_t LibretroDroid::handleAudioCallback(const int16_t *data, size_t frames) {
    if (audio && audioEnabled) {
        audio->write(data, frames);
    }
    return frames;
}

int16_t LibretroDroid::handleSetInputState(
    unsigned int port,
    unsigned int device,
    unsigned int index,
    unsigned int id
) {
    if (input) {
        return input->getInputState(port, device, index, id);
    }
    return 0;
}

uintptr_t LibretroDroid::handleGetCurrentFrameBuffer() {
    if (video) {
        return video->getCurrentFramebuffer();
    }
    return 0;
}

void LibretroDroid::reset() {
    std::lock_guard<std::mutex> lock(coreLock);

    if (!core) {
        return;
    }
    core->retro_reset();
    Achievements::getInstance().refreshMemoryRegionsLocked(
        core.get(),
        Environment::getInstance().getMemoryMap()
    );
    Achievements::getInstance().reset();
}

std::pair<int8_t*, size_t> LibretroDroid::serializeState() {
    std::lock_guard<std::mutex> lock(coreLock);

    if (!core) {
        return std::pair(nullptr, 0);
    }

    size_t coreSize = core->retro_serialize_size();
    if (coreSize == 0) {
        return std::pair(nullptr, 0);
    }
    std::vector<uint8_t> progress = Achievements::getInstance().serializeProgress();

    if (progress.empty()) {
        auto* data = new int8_t[coreSize];
        core->retro_serialize(data, coreSize);
        return std::pair(data, coreSize);
    }

    uint32_t progressLen = static_cast<uint32_t>(progress.size());
    size_t totalSize = coreSize + progress.size() + 4 + 8;
    auto* data = new int8_t[totalSize];

    core->retro_serialize(data, coreSize);
    memcpy(data + coreSize, progress.data(), progress.size());

    uint8_t lenBytes[4] = {
        static_cast<uint8_t>(progressLen & 0xFF),
        static_cast<uint8_t>((progressLen >> 8) & 0xFF),
        static_cast<uint8_t>((progressLen >> 16) & 0xFF),
        static_cast<uint8_t>((progressLen >> 24) & 0xFF),
    };
    memcpy(data + coreSize + progress.size(), lenBytes, 4);
    memcpy(data + coreSize + progress.size() + 4, kAchievementsProgressMagic, 8);

    return std::pair(data, totalSize);
}

void LibretroDroid::resetCheat() {
    std::lock_guard<std::mutex> lock(coreLock);

    if (core) {
        core->retro_cheat_reset();
    }
}

void LibretroDroid::setCheat(unsigned index, bool enabled, const std::string& code) {
    std::lock_guard<std::mutex> lock(coreLock);

    if (!core) {
        return;
    }
    if (Achievements::getInstance().isHardcoreEnforcedLocked()) {
        core->retro_cheat_reset();
        LOGW("Blocked setCheat while RetroAchievements Hardcore mode is active.");
        return;
    }

    core->retro_cheat_set(index, enabled, code.c_str());
}

bool LibretroDroid::requiresVideoRefresh() const {
    return dirtyVideo;
}

void LibretroDroid::clearRequiresVideoRefresh() {
    dirtyVideo = false;
}

void LibretroDroid::refreshAvAndAudioLocked() {
    struct retro_system_av_info system_av_info {};
    core->retro_get_system_av_info(&system_av_info);

    fpsSync = std::make_unique<FPSSync>(system_av_info.timing.fps, screenRefreshRate);

    double inputSampleRate = system_av_info.timing.sample_rate * fpsSync->getTimeStretchFactor();

    audio = std::make_unique<Audio>(
        (int32_t) std::lround(inputSampleRate),
        system_av_info.timing.fps,
        preferLowLatencyAudio
    );

    updateAudioSampleRateMultiplier();

    defaultAspectRatio = findDefaultAspectRatio(system_av_info);
}

void LibretroDroid::afterGameLoad() {
    refreshAvAndAudioLocked();

    Achievements::getInstance().startSession(
        core.get(),
        Environment::getInstance().getMemoryMap()
    );
    Environment::getInstance().setHardcoreContext(
        Achievements::getInstance().isHardcoreEnforcedLocked(),
        Achievements::getInstance().getCoreLibraryNameLocked(),
        Achievements::getInstance().getEffectiveConsoleIdLocked()
    );
    Achievements::getInstance().notifyJavaEventsIfPending();
}

std::vector<int8_t> LibretroDroid::captureSramVectorLocked() {
    if (!core) {
        return {};
    }
    size_t size = core->retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    const auto* ptr = static_cast<const int8_t*>(core->retro_get_memory_data(RETRO_MEMORY_SAVE_RAM));
    if (size == 0 || ptr == nullptr) {
        return {};
    }
    return std::vector<int8_t>(ptr, ptr + size);
}

void LibretroDroid::restoreSramVectorLocked(const std::vector<int8_t>& sram) {
    if (!core || sram.empty()) {
        return;
    }
    size_t sramSize = core->retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    void* sramState = core->retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    if (sramState != nullptr && sram.size() <= sramSize) {
        memcpy(sramState, sram.data(), sram.size());
    }
}

bool LibretroDroid::performCleanHardcoreRestartLocked() {
    if (!core || !gameLoaded || coreSoFilePath_.empty() || loadedGameMode_ == LoadedGameMode::NONE) {
        Achievements::getInstance().abortHardcoreEnableLocked("Game is not currently loaded");
        return false;
    }

    Achievements::getInstance().updateCoreMetadataLocked(core.get());
    std::string failureReason;
    if (!Achievements::getInstance().beginHardcoreEnableLocked(&failureReason)) {
        Achievements::getInstance().abortHardcoreEnableLocked(failureReason);
        Environment::getInstance().setHardcoreContext(
            false,
            Achievements::getInstance().getCoreLibraryNameLocked(),
            Achievements::getInstance().getEffectiveConsoleIdLocked()
        );
        return false;
    }

    try {
        core->retro_cheat_reset();
        frameSpeed = std::max(1u, frameSpeed);
        updateAudioSampleRateMultiplier();
        if (input) {
            input->clearAll();
        }

        std::vector<int8_t> savedSram = captureSramVectorLocked();
        const bool wasResumed = (input != nullptr);
        const bool hadVideo = (video != nullptr);

        if (audio) {
            audio->stop();
        }

        if (Environment::getInstance().getHwContextDestroy() != nullptr &&
            eglGetCurrentContext() != EGL_NO_CONTEXT) {
            Environment::getInstance().getHwContextDestroy()();
        }

        core->retro_unload_game();
        gameLoaded = false;
        core->retro_deinit();
        core = nullptr;

        Environment::getInstance().prepareForCoreReload();
        Environment::getInstance().setHardcoreContext(
            true,
            Achievements::getInstance().getCoreLibraryNameLocked(),
            Achievements::getInstance().getEffectiveConsoleIdLocked()
        );
        VFS::getInstance().resetAllOffsets();

        core = std::make_unique<Core>(coreSoFilePath_);
        core->retro_set_video_refresh(&callback_hw_video_refresh);
        core->retro_set_environment(&Environment::callback_environment);
        core->retro_set_audio_sample(&callback_audio_sample);
        core->retro_set_audio_sample_batch(&callback_set_audio_sample_batch);
        core->retro_set_input_poll(&callback_retro_set_input_poll);
        core->retro_set_input_state(&callback_set_input_state);
        core->retro_init();

        Achievements::getInstance().updateCoreMetadataLocked(core.get());
        Environment::getInstance().setHardcoreContext(
            true,
            Achievements::getInstance().getCoreLibraryNameLocked(),
            Achievements::getInstance().getEffectiveConsoleIdLocked()
        );

        if (!reloadCurrentGameIntoCoreLocked()) {
            throw std::runtime_error("Clean game reload failed during Hardcore enable");
        }
        gameLoaded = true;

        for (const auto& kv : controllerTypes_) {
            core->retro_set_controller_port_device(kv.first, kv.second);
        }

        restoreSramVectorLocked(savedSram);
        refreshAvAndAudioLocked();

        if (hadVideo && eglGetCurrentContext() != EGL_NO_CONTEXT) {
            recreateVideoSurfaceLocked();
            dirtyVideo = true;
        }
        if (wasResumed) {
            if (fpsSync) {
                fpsSync->reset();
            }
            if (audio) {
                audio->start();
            }
        }

        if (!Achievements::getInstance().completeHardcoreCleanResetLocked(
                core.get(),
                Environment::getInstance().getMemoryMap(),
                &failureReason)) {
            Environment::getInstance().setHardcoreContext(
                false,
                Achievements::getInstance().getCoreLibraryNameLocked(),
                Achievements::getInstance().getEffectiveConsoleIdLocked()
            );
            return false;
        }

        return true;
    } catch (const std::exception& e) {
        LOGE("Clean Hardcore restart failed: %s", e.what());
        Achievements::getInstance().abortHardcoreEnableLocked(
            std::string("Hardcore clean restart failed: ") + e.what()
        );
        Environment::getInstance().setHardcoreContext(
            false,
            Achievements::getInstance().getCoreLibraryNameLocked(),
            Achievements::getInstance().getEffectiveConsoleIdLocked()
        );
        return false;
    }
}

float LibretroDroid::findDefaultAspectRatio(const retro_system_av_info& system_av_info) {
    float result = system_av_info.geometry.aspect_ratio;
    if (result < 0) {
        result =
            (float) system_av_info.geometry.base_width / (float) system_av_info.geometry.base_height;
    }
    return result;
}

void LibretroDroid::handleRumbleUpdates(const std::function<void(int, float, float)> &handler) {
    if (rumble && rumbleEnabled) {
        rumble->handleRumbleUpdates(handler);
    }
}

void LibretroDroid::setViewport(Rect viewportRect) {
    this->viewportRect = viewportRect;

    if (video != nullptr) {
        video->updateViewportSize(viewportRect);
    }
}

void LibretroDroid::setViewportAlignment(unsigned int viewportAlignment) {
    this-> viewportAlignment = viewportAlignment;

    if (video) {
        this->video->updateViewportAlignment(viewportAlignment);
    }
}

void LibretroDroid::setScreenRegions(const std::vector<VideoLayout::ScreenRegion>& regions) {
    this->screenRegions = regions;

    if (video) {
        this->video->updateScreenRegions(regions);
    }
}

void LibretroDroid::configureAchievements(const AchievementsConfig& config) {
    std::lock_guard<std::mutex> lock(coreLock);
    Achievements::getInstance().configure(config);
    if (core) {
        Achievements::getInstance().updateCoreMetadataLocked(core.get());
    }
    Environment::getInstance().setHardcoreContext(
        Achievements::getInstance().isHardcoreEnforcedLocked(),
        Achievements::getInstance().getCoreLibraryNameLocked(),
        Achievements::getInstance().getEffectiveConsoleIdLocked()
    );
}

bool LibretroDroid::setHardcoreMode(bool enabled) {
    bool ok = false;
    {
        std::lock_guard<std::mutex> lock(coreLock);
        if (!enabled) {
            Achievements::getInstance().disableHardcoreImmediateLocked();
            Environment::getInstance().setHardcoreContext(
                false,
                Achievements::getInstance().getCoreLibraryNameLocked(),
                Achievements::getInstance().getEffectiveConsoleIdLocked()
            );
            ok = true;
        } else {
            ok = performCleanHardcoreRestartLocked();
        }
    }
    Achievements::getInstance().notifyJavaEventsIfPending();
    return ok;
}

bool LibretroDroid::canPauseAchievements(int* outFramesRemaining) {
    std::lock_guard<std::mutex> lock(coreLock);
    uint32_t remaining = 0;
    bool allowed = Achievements::getInstance().canPauseLocked(&remaining);
    if (outFramesRemaining != nullptr) {
        *outFramesRemaining = static_cast<int>(remaining);
    }
    return allowed;
}

void LibretroDroid::idleAchievements() {
    {
        std::lock_guard<std::mutex> lock(coreLock);
        Achievements::getInstance().idleLocked();
    }
    Achievements::getInstance().notifyJavaEventsIfPending();
}

void LibretroDroid::completeAchievementHttpRequest(
    uint64_t requestId,
    int httpStatusCode,
    const char* responseBody,
    size_t responseLength
) {
    {
        std::lock_guard<std::mutex> lock(coreLock);
        Achievements::getInstance().completeHttpRequestLocked(
            requestId,
            httpStatusCode,
            responseBody,
            responseLength
        );
    }
    Achievements::getInstance().notifyJavaEventsIfPending();
}

NativeAchievementsSnapshot LibretroDroid::captureAchievementsSnapshot() {
    std::lock_guard<std::mutex> lock(coreLock);
    return Achievements::getInstance().captureSnapshotLocked();
}

} //namespace libretrodroid
