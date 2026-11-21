/*
 *     Copyright (C) 2026  Arcadune Project
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

#include "achievements.h"

#include <algorithm>
#include <cctype>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <strings.h>
#include <unistd.h>

#include "environment.h"
#include "log.h"
#include "vfs/vfs.h"

namespace libretrodroid {

namespace {

// Synthetic server warning IDs injected by RetroAchievements (e.g. 101000001 "Unknown Emulator").
constexpr uint32_t kWarningAchievementMinId = 101000001u;
constexpr const char* kHardcoreDowngradedStatus = "HARDCORE_UNREGISTERED_EMULATOR";
constexpr const char* kHardcoreCoreUnsupportedPrefix = "HARDCORE_CORE_UNSUPPORTED:";

bool hasServerWarningAchievement(rc_client_t* client) {
    if (client == nullptr) {
        return false;
    }
    if (rc_client_get_achievement_info(client, kWarningAchievementMinId) != nullptr ||
        rc_client_get_achievement_info(client, kWarningAchievementMinId + 1u) != nullptr) {
        return true;
    }
    rc_client_achievement_list_t* list = rc_client_create_achievement_list(
        client,
        RC_CLIENT_ACHIEVEMENT_CATEGORY_CORE_AND_UNOFFICIAL,
        RC_CLIENT_ACHIEVEMENT_LIST_GROUPING_LOCK_STATE
    );
    if (list == nullptr) {
        return false;
    }
    bool found = false;
    for (uint32_t b = 0; b < list->num_buckets && !found; b++) {
        const rc_client_achievement_bucket_t& bucket = list->buckets[b];
        for (uint32_t a = 0; a < bucket.num_achievements; a++) {
            const rc_client_achievement_t* ach = bucket.achievements[a];
            if (ach != nullptr && ach->id >= kWarningAchievementMinId) {
                found = true;
                break;
            }
        }
    }
    rc_client_destroy_achievement_list(list);
    return found;
}

struct HashFileHandle {
    int fd = -1;
};

void* RC_CCONV hashFileOpen(const char* pathUtf8) {
    if (pathUtf8 == nullptr || pathUtf8[0] == '\0') {
        return nullptr;
    }

    int fd = VFS::getInstance().openDuplicateFD(pathUtf8);
    if (fd < 0) {
        fd = ::open(pathUtf8, O_RDONLY | O_CLOEXEC);
    }
    if (fd < 0) {
        return nullptr;
    }

    auto* handle = new HashFileHandle();
    handle->fd = fd;
    return handle;
}

void RC_CCONV hashFileSeek(void* fileHandle, int64_t offset, int origin) {
    if (fileHandle == nullptr) {
        return;
    }
    auto* handle = static_cast<HashFileHandle*>(fileHandle);
    lseek64(handle->fd, static_cast<off64_t>(offset), origin);
}

int64_t RC_CCONV hashFileTell(void* fileHandle) {
    if (fileHandle == nullptr) {
        return 0;
    }
    auto* handle = static_cast<HashFileHandle*>(fileHandle);
    off64_t pos = lseek64(handle->fd, 0, SEEK_CUR);
    return pos >= 0 ? static_cast<int64_t>(pos) : 0;
}

size_t RC_CCONV hashFileRead(void* fileHandle, void* buffer, size_t requestedBytes) {
    if (fileHandle == nullptr || buffer == nullptr || requestedBytes == 0) {
        return 0;
    }
    auto* handle = static_cast<HashFileHandle*>(fileHandle);
    auto* out = static_cast<uint8_t*>(buffer);
    size_t totalRead = 0;

    while (totalRead < requestedBytes) {
        ssize_t n = ::read(handle->fd, out + totalRead, requestedBytes - totalRead);
        if (n > 0) {
            totalRead += static_cast<size_t>(n);
        } else if (n == 0) {
            break;
        } else if (errno != EINTR) {
            break;
        }
    }
    return totalRead;
}

void RC_CCONV hashFileClose(void* fileHandle) {
    if (fileHandle == nullptr) {
        return;
    }
    auto* handle = static_cast<HashFileHandle*>(fileHandle);
    if (handle->fd >= 0) {
        ::close(handle->fd);
    }
    delete handle;
}

bool hasExtensionIgnoreCase(const std::string& path, const char* extWithoutDot) {
    auto dot = path.find_last_of('.');
    if (dot == std::string::npos || dot + 1 >= path.size()) {
        return false;
    }
    return strcasecmp(path.c_str() + dot + 1, extWithoutDot) == 0;
}

struct DiscTrackWrapper {
    bool isChd = false;
    int64_t chdHandle = 0;
    void* defaultHandle = nullptr;
};

rc_hash_cdreader_t getBaseDefaultCdReader() {
    rc_hash_cdreader_t base {};
    rc_hash_get_default_cdreader(&base);
    return base;
}

void* RC_CCONV customCdOpenTrackIterator(
    const char* path,
    uint32_t track,
    const struct rc_hash_iterator* iterator
) {
    if (path == nullptr || path[0] == '\0') {
        return nullptr;
    }
    if (hasExtensionIgnoreCase(std::string(path), "chd")) {
        int64_t chdHandle = Achievements::getInstance().openChdTrackFromNative(path, track);
        if (chdHandle <= 0) {
            return nullptr;
        }
        auto* wrapper = new DiscTrackWrapper();
        wrapper->isChd = true;
        wrapper->chdHandle = chdHandle;
        return wrapper;
    }
    rc_hash_cdreader_t base = getBaseDefaultCdReader();
    void* inner = base.open_track_iterator != nullptr
        ? base.open_track_iterator(path, track, iterator)
        : (base.open_track != nullptr ? base.open_track(path, track) : nullptr);
    if (inner == nullptr) {
        return nullptr;
    }
    auto* wrapper = new DiscTrackWrapper();
    wrapper->isChd = false;
    wrapper->defaultHandle = inner;
    return wrapper;
}

void* RC_CCONV customCdOpenTrack(const char* path, uint32_t track) {
    return customCdOpenTrackIterator(path, track, nullptr);
}

size_t RC_CCONV customCdReadSector(
    void* trackHandle,
    uint32_t sector,
    void* buffer,
    size_t requestedBytes
) {
    if (trackHandle == nullptr || buffer == nullptr || requestedBytes == 0) {
        return 0;
    }
    auto* wrapper = static_cast<DiscTrackWrapper*>(trackHandle);
    if (wrapper->isChd) {
        return Achievements::getInstance().readChdSectorFromNative(
            wrapper->chdHandle,
            sector,
            buffer,
            requestedBytes
        );
    }
    rc_hash_cdreader_t base = getBaseDefaultCdReader();
    return base.read_sector != nullptr
        ? base.read_sector(wrapper->defaultHandle, sector, buffer, requestedBytes)
        : 0;
}

uint32_t RC_CCONV customCdFirstTrackSector(void* trackHandle) {
    if (trackHandle == nullptr) {
        return 0;
    }
    auto* wrapper = static_cast<DiscTrackWrapper*>(trackHandle);
    if (wrapper->isChd) {
        return Achievements::getInstance().firstChdTrackSectorFromNative(wrapper->chdHandle);
    }
    rc_hash_cdreader_t base = getBaseDefaultCdReader();
    return base.first_track_sector != nullptr
        ? base.first_track_sector(wrapper->defaultHandle)
        : 0;
}

void RC_CCONV customCdCloseTrack(void* trackHandle) {
    if (trackHandle == nullptr) {
        return;
    }
    auto* wrapper = static_cast<DiscTrackWrapper*>(trackHandle);
    if (wrapper->isChd) {
        Achievements::getInstance().closeChdTrackFromNative(wrapper->chdHandle);
    } else {
        rc_hash_cdreader_t base = getBaseDefaultCdReader();
        if (base.close_track != nullptr && wrapper->defaultHandle != nullptr) {
            base.close_track(wrapper->defaultHandle);
        }
    }
    delete wrapper;
}

void populateHashCallbacks(rc_hash_callbacks_t* callbacks) {
    if (callbacks == nullptr) {
        return;
    }
    memset(callbacks, 0, sizeof(*callbacks));
    callbacks->filereader.open = &hashFileOpen;
    callbacks->filereader.seek = &hashFileSeek;
    callbacks->filereader.tell = &hashFileTell;
    callbacks->filereader.read = &hashFileRead;
    callbacks->filereader.close = &hashFileClose;
    callbacks->cdreader.open_track = &customCdOpenTrack;
    callbacks->cdreader.open_track_iterator = &customCdOpenTrackIterator;
    callbacks->cdreader.read_sector = &customCdReadSector;
    callbacks->cdreader.first_track_sector = &customCdFirstTrackSector;
    callbacks->cdreader.close_track = &customCdCloseTrack;
}

std::string safeString(const char* s) {
    return s != nullptr ? std::string(s) : std::string();
}

std::string sanitizeUaSegment(const std::string& raw, bool replaceSpacesWithUnderscore) {
    std::string out;
    out.reserve(raw.size());
    for (unsigned char ch : raw) {
        if (ch < 0x20 || ch == 0x7F) {
            continue;
        }
        if (ch == ' ') {
            out.push_back(replaceSpacesWithUnderscore ? '_' : ' ');
        } else if (ch == '/' && replaceSpacesWithUnderscore) {
            out.push_back('-');
        } else {
            out.push_back(static_cast<char>(ch));
        }
    }
    while (!out.empty() && (out.back() == ' ' || out.back() == '_')) {
        out.pop_back();
    }
    return out;
}

void redactQueryParam(std::string& text, const char* keyWithEquals) {
    const size_t keyLen = strlen(keyWithEquals);
    size_t searchPos = 0;
    while (searchPos < text.size()) {
        size_t pos = text.find(keyWithEquals, searchPos);
        if (pos == std::string::npos) {
            break;
        }
        bool boundaryOk = (pos == 0) ||
                          text[pos - 1] == '?' ||
                          text[pos - 1] == '&' ||
                          text[pos - 1] == ' ';
        if (!boundaryOk) {
            searchPos = pos + keyLen;
            continue;
        }
        size_t valueStart = pos + keyLen;
        size_t valueEnd = valueStart;
        while (valueEnd < text.size() &&
               text[valueEnd] != '&' &&
               text[valueEnd] != ' ' &&
               text[valueEnd] != '"' &&
               text[valueEnd] != '\r' &&
               text[valueEnd] != '\n') {
            valueEnd++;
        }
        text.replace(valueStart, valueEnd - valueStart, "[REDACTED]");
        searchPos = valueStart + 10;
    }
}

std::string redactSensitiveMessage(const char* rawMessage) {
    if (rawMessage == nullptr) {
        return {};
    }
    std::string out(rawMessage);
    redactQueryParam(out, "t=");
    redactQueryParam(out, "p=");
    size_t tokenPos = out.find("\"Token\":\"");
    while (tokenPos != std::string::npos) {
        size_t valStart = tokenPos + 9;
        size_t valEnd = out.find('"', valStart);
        if (valEnd == std::string::npos) {
            break;
        }
        out.replace(valStart, valEnd - valStart, "[REDACTED]");
        tokenPos = out.find("\"Token\":\"", valStart + 10);
    }
    return out;
}

std::string resolveGameBadgeUrl(const rc_client_game_t* game) {
    if (game == nullptr) {
        return {};
    }
    if (game->badge_url != nullptr && game->badge_url[0] != '\0') {
        return std::string(game->badge_url);
    }
    char urlBuf[256] = {};
    if (rc_client_game_get_image_url(game, urlBuf, sizeof(urlBuf)) == RC_OK) {
        return std::string(urlBuf);
    }
    return {};
}

std::string resolveAchievementBadgeUrl(const rc_client_achievement_t* ach, bool locked) {
    if (ach == nullptr) {
        return {};
    }
    const char* direct = locked ? ach->badge_locked_url : ach->badge_url;
    if (direct != nullptr && direct[0] != '\0') {
        return std::string(direct);
    }
    char urlBuf[256] = {};
    int state = locked ? RC_CLIENT_ACHIEVEMENT_STATE_INACTIVE : RC_CLIENT_ACHIEVEMENT_STATE_UNLOCKED;
    if (rc_client_achievement_get_image_url(ach, state, urlBuf, sizeof(urlBuf)) == RC_OK) {
        return std::string(urlBuf);
    }
    return {};
}

jstring utf8ToJString(JNIEnv* env, const char* utf8) {
    if (utf8 == nullptr || utf8[0] == '\0') {
        return env->NewString(nullptr, 0);
    }
    std::u16string utf16;
    const auto* bytes = reinterpret_cast<const uint8_t*>(utf8);
    size_t i = 0;
    while (bytes[i] != 0) {
        uint32_t cp = 0;
        uint8_t b0 = bytes[i];
        if (b0 < 0x80) {
            cp = b0;
            i += 1;
        } else if ((b0 & 0xE0) == 0xC0 && (bytes[i + 1] & 0xC0) == 0x80) {
            cp = ((b0 & 0x1F) << 6) | (bytes[i + 1] & 0x3F);
            i += 2;
        } else if ((b0 & 0xF0) == 0xE0 &&
                   (bytes[i + 1] & 0xC0) == 0x80 &&
                   (bytes[i + 2] & 0xC0) == 0x80) {
            cp = ((b0 & 0x0F) << 12) | ((bytes[i + 1] & 0x3F) << 6) | (bytes[i + 2] & 0x3F);
            i += 3;
        } else if ((b0 & 0xF8) == 0xF0 &&
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

void Achievements::initJni(JavaVM* vm, JNIEnv* env) {
    javaVm_ = vm;
    jclass localBridge = env->FindClass("com/swordfish/libretrodroid/AchievementsBridge");
    if (localBridge != nullptr) {
        bridgeClass_ = static_cast<jclass>(env->NewGlobalRef(localBridge));
        dispatchHttpMethod_ = env->GetStaticMethodID(
            bridgeClass_,
            "dispatchHttpRequest",
            "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"
        );
        notifyEventsMethod_ = env->GetStaticMethodID(
            bridgeClass_,
            "onNativeEventsAvailable",
            "()V"
        );
        cancelAllHttpMethod_ = env->GetStaticMethodID(
            bridgeClass_,
            "cancelAllRequests",
            "()V"
        );
        openChdTrackMethod_ = env->GetStaticMethodID(
            bridgeClass_,
            "openChdTrack",
            "(Ljava/lang/String;II)J"
        );
        readChdSectorMethod_ = env->GetStaticMethodID(
            bridgeClass_,
            "readChdSector",
            "(JII)[B"
        );
        firstChdTrackSectorMethod_ = env->GetStaticMethodID(
            bridgeClass_,
            "firstChdTrackSector",
            "(J)I"
        );
        closeChdTrackMethod_ = env->GetStaticMethodID(
            bridgeClass_,
            "closeChdTrack",
            "(J)V"
        );
        env->DeleteLocalRef(localBridge);
    }
}

int64_t Achievements::openChdTrackFromNative(const char* pathUtf8, uint32_t track) {
    if (pathUtf8 == nullptr || javaVm_ == nullptr || bridgeClass_ == nullptr || openChdTrackMethod_ == nullptr) {
        return 0;
    }
    JNIEnv* env = nullptr;
    bool attachedHere = false;
    jint envStatus = javaVm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (envStatus == JNI_EDETACHED) {
        if (javaVm_->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            attachedHere = true;
        }
    }
    int64_t result = 0;
    if (env != nullptr) {
        int dupFd = VFS::getInstance().openDuplicateFD(pathUtf8);
        jstring jPath = utf8ToJString(env, pathUtf8);
        if (jPath == nullptr) {
            if (dupFd >= 0) {
                ::close(dupFd);
            }
        } else {
            result = static_cast<int64_t>(
                env->CallStaticLongMethod(
                    bridgeClass_,
                    openChdTrackMethod_,
                    jPath,
                    static_cast<jint>(dupFd),
                    static_cast<jint>(track)
                )
            );
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                result = 0;
            }
            env->DeleteLocalRef(jPath);
        }
    }
    if (attachedHere) {
        javaVm_->DetachCurrentThread();
    }
    return result;
}

size_t Achievements::readChdSectorFromNative(
    int64_t handle,
    uint32_t sector,
    void* buffer,
    size_t requestedBytes
) {
    if (handle <= 0 || buffer == nullptr || requestedBytes == 0 ||
        javaVm_ == nullptr || bridgeClass_ == nullptr || readChdSectorMethod_ == nullptr) {
        return 0;
    }
    JNIEnv* env = nullptr;
    bool attachedHere = false;
    jint envStatus = javaVm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (envStatus == JNI_EDETACHED) {
        if (javaVm_->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            attachedHere = true;
        }
    }
    size_t copied = 0;
    if (env != nullptr) {
        const size_t clampedReq = std::min<size_t>(requestedBytes, 16u * 1024u * 1024u);
        auto jArr = static_cast<jbyteArray>(
            env->CallStaticObjectMethod(
                bridgeClass_,
                readChdSectorMethod_,
                static_cast<jlong>(handle),
                static_cast<jint>(sector),
                static_cast<jint>(clampedReq)
            )
        );
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        } else if (jArr != nullptr) {
            jsize arrLen = env->GetArrayLength(jArr);
            if (arrLen > 0) {
                copied = std::min<size_t>(requestedBytes, static_cast<size_t>(arrLen));
                env->GetByteArrayRegion(jArr, 0, static_cast<jsize>(copied), reinterpret_cast<jbyte*>(buffer));
            }
            env->DeleteLocalRef(jArr);
        }
    }
    if (attachedHere) {
        javaVm_->DetachCurrentThread();
    }
    return copied;
}

uint32_t Achievements::firstChdTrackSectorFromNative(int64_t handle) {
    if (handle <= 0 || javaVm_ == nullptr || bridgeClass_ == nullptr || firstChdTrackSectorMethod_ == nullptr) {
        return 0;
    }
    JNIEnv* env = nullptr;
    bool attachedHere = false;
    jint envStatus = javaVm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (envStatus == JNI_EDETACHED) {
        if (javaVm_->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            attachedHere = true;
        }
    }
    uint32_t firstSector = 0;
    if (env != nullptr) {
        jint val = env->CallStaticIntMethod(
            bridgeClass_,
            firstChdTrackSectorMethod_,
            static_cast<jlong>(handle)
        );
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        } else if (val >= 0) {
            firstSector = static_cast<uint32_t>(val);
        }
    }
    if (attachedHere) {
        javaVm_->DetachCurrentThread();
    }
    return firstSector;
}

void Achievements::closeChdTrackFromNative(int64_t handle) {
    if (handle <= 0 || javaVm_ == nullptr || bridgeClass_ == nullptr || closeChdTrackMethod_ == nullptr) {
        return;
    }
    JNIEnv* env = nullptr;
    bool attachedHere = false;
    jint envStatus = javaVm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (envStatus == JNI_EDETACHED) {
        if (javaVm_->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            attachedHere = true;
        }
    }
    if (env != nullptr) {
        env->CallStaticVoidMethod(
            bridgeClass_,
            closeChdTrackMethod_,
            static_cast<jlong>(handle)
        );
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
    }
    if (attachedHere) {
        javaVm_->DetachCurrentThread();
    }
}

void Achievements::configure(const AchievementsConfig& config) {
    shutdown();
    config_ = config;
    effectiveConsoleId_ = config.consoleId;
    hardcoreEnforced_ = config.hardcoreEnabled && config.coreHardcoreAllowed;
    if (config.hardcoreEnabled && !config.coreHardcoreAllowed) {
        config_.hardcoreEnabled = false;
        statusMessage_ = std::string(kHardcoreCoreUnsupportedPrefix) +
            (config.coreUnsupportedReason.empty()
                ? "Core cannot enforce Hardcore compliance"
                : config.coreUnsupportedReason);
    } else {
        statusMessage_.clear();
    }

    if (config_.enabled && !config_.username.empty() && !config_.token.empty() && config_.consoleId != 0) {
        sessionStatus_ = NativeAchievementsSnapshot::STATUS_SIGNING_IN;
    } else {
        sessionStatus_ = NativeAchievementsSnapshot::STATUS_DISABLED;
    }
    rebuildUserAgentLocked();
}

void Achievements::updateCoreMetadataLocked(Core* core) {
    activeCore_ = core;
    if (core != nullptr) {
        struct retro_system_info sysInfo {};
        core->retro_get_system_info(&sysInfo);
        coreLibraryName_ = safeString(sysInfo.library_name);
        coreLibraryVersion_ = safeString(sysInfo.library_version);
    } else {
        coreLibraryName_.clear();
        coreLibraryVersion_.clear();
    }
    rebuildUserAgentLocked();
}

void Achievements::rebuildUserAgentLocked() {
    std::string appVer = sanitizeUaSegment(config_.appVersion, true);
    if (appVer.empty()) {
        appVer = "1.0.9";
    }
    std::string osRel = sanitizeUaSegment(config_.osRelease, false);
    if (osRel.empty()) {
        osRel = "Android";
    } else {
        osRel = "Android " + osRel;
    }

    std::string ua = "Arcadune/" + appVer + " (" + osRel + ")";
    std::string coreName = sanitizeUaSegment(coreLibraryName_, true);
    std::string coreVer = sanitizeUaSegment(coreLibraryVersion_, true);
    if (!coreName.empty()) {
        ua += " " + coreName;
        if (!coreVer.empty()) {
            ua += "/" + coreVer;
        }
    }
    if (!userAgentClause_.empty()) {
        ua += " " + userAgentClause_;
    }
    fullUserAgent_ = std::move(ua);
}

void Achievements::prepareGameHash(
    const std::string& gamePath,
    const uint8_t* gameData,
    size_t gameSize
) {
    gameHash_.clear();
    hashFailureReason_.clear();
    effectiveConsoleId_ = config_.consoleId;
    if (effectiveConsoleId_ == RC_CONSOLE_NINTENDO &&
        hasExtensionIgnoreCase(gamePath, "fds")) {
        effectiveConsoleId_ = RC_CONSOLE_FAMICOM_DISK_SYSTEM;
    } else if (effectiveConsoleId_ == RC_CONSOLE_PC_ENGINE &&
               (hasExtensionIgnoreCase(gamePath, "cue") ||
                hasExtensionIgnoreCase(gamePath, "chd") ||
                hasExtensionIgnoreCase(gamePath, "ccd") ||
                hasExtensionIgnoreCase(gamePath, "m3u"))) {
        effectiveConsoleId_ = RC_CONSOLE_PC_ENGINE_CD;
    }

    if (!config_.enabled || config_.username.empty() || config_.token.empty() || effectiveConsoleId_ == 0) {
        return;
    }

    if (hasExtensionIgnoreCase(gamePath, "cso") ||
        hasExtensionIgnoreCase(gamePath, "cdi") ||
        hasExtensionIgnoreCase(gamePath, "mds") ||
        (effectiveConsoleId_ == RC_CONSOLE_PLAYSTATION && hasExtensionIgnoreCase(gamePath, "pbp"))) {
        hashFailureReason_ =
            "Compressed disc containers (.cso, .pbp, .cdi, .mds) cannot be hashed for RetroAchievements; use .chd, .cue/.bin, .gdi, or .iso.";
        LOGW("RetroAchievements unsupported compressed disc container: %s", gamePath.c_str());
        return;
    }

    const uint8_t* hashBuffer = gameData;
    size_t hashBufferSize = gameSize;
    if (effectiveConsoleId_ == RC_CONSOLE_ARCADE && !hasExtensionIgnoreCase(gamePath, "neo")) {
        // Arcade .zip/.7z sets hash by archive stem name in rcheevos.
        hashBuffer = nullptr;
        hashBufferSize = 0;
    }

    const char* effectivePath = gamePath.empty() ? "?" : gamePath.c_str();

    rc_hash_iterator_t iterator {};
    rc_hash_initialize_iterator(&iterator, effectivePath, hashBuffer, hashBufferSize);
    populateHashCallbacks(&iterator.callbacks);

    char hash[33] = {};
    int generated = rc_hash_generate(hash, effectiveConsoleId_, &iterator);
    rc_hash_destroy_iterator(&iterator);

    VFS::getInstance().resetAllOffsets();

    if (generated && hash[0] != '\0') {
        gameHash_ = std::string(hash);
        LOGI("RetroAchievements hashed ROM (%u): %s", effectiveConsoleId_, gameHash_.c_str());
    } else {
        LOGW("RetroAchievements could not generate hash for path: %s", effectivePath);
    }
}

void Achievements::refreshMemoryRegionsLocked(Core* core, const struct retro_memory_map* mmap) {
    activeCore_ = core;
    const uint32_t consoleId = getEffectiveConsoleIdLocked();
    rc_libretro_memory_destroy(&memoryRegions_);
    memset(&memoryRegions_, 0, sizeof(memoryRegions_));
    hasValidMemoryRegion_ = false;
    mappedMemoryGeneration_ = Environment::getInstance().getMemoryMapGeneration();
    if (core != nullptr && consoleId != 0) {
        hasValidMemoryRegion_ =
            (rc_libretro_memory_init(&memoryRegions_, mmap, &getCoreMemoryInfoCallback, consoleId) != 0);
    }
}

void Achievements::startSession(Core* core, const struct retro_memory_map* mmap) {
    updateCoreMetadataLocked(core);

    const uint32_t consoleId = effectiveConsoleId_ != 0 ? effectiveConsoleId_ : config_.consoleId;
    if (hardcoreEnforced_) {
        bool systemAllowed = coreLibraryName_.empty() ||
            (consoleId != 0 && rc_libretro_is_system_allowed(coreLibraryName_.c_str(), consoleId) != 0);
        if (!config_.coreHardcoreAllowed || !systemAllowed) {
            hardcoreEnforced_ = false;
            config_.hardcoreEnabled = false;
            statusMessage_ = std::string(kHardcoreCoreUnsupportedPrefix) +
                (!config_.coreUnsupportedReason.empty()
                    ? config_.coreUnsupportedReason
                    : ("Core " + coreLibraryName_ + " is not eligible for Hardcore on this system"));
            NativeAchievementEvent ev {};
            ev.type = NativeAchievementEvent::TYPE_HARDCORE_MODE_CHANGED;
            ev.hardcore = false;
            ev.description = statusMessage_;
            pushEvent(std::move(ev));
        }
    }

    if (!config_.enabled || config_.username.empty() || config_.token.empty() || consoleId == 0) {
        sessionStatus_ = NativeAchievementsSnapshot::STATUS_DISABLED;
        return;
    }

    retriedLateMemoryInit_ = false;
    activeTrackers_.clear();
    refreshMemoryRegionsLocked(core, mmap);

    client_ = rc_client_create(&readMemoryCallback, &serverCallCallback);
    if (client_ == nullptr) {
        hardcoreEnforced_ = false;
        sessionStatus_ = NativeAchievementsSnapshot::STATUS_LOAD_ERROR;
        statusMessage_ = "Failed to initialize RetroAchievements runtime";
        return;
    }

    rc_client_set_userdata(client_, this);
    rc_client_enable_logging(client_, RC_CLIENT_LOG_LEVEL_INFO, &logMessageCallback);
    rc_client_set_event_handler(client_, &eventHandlerCallback);

    rc_hash_callbacks_t hashCallbacks {};
    populateHashCallbacks(&hashCallbacks);
    rc_client_set_hash_callbacks(client_, &hashCallbacks);

    if (!config_.serverHost.empty()) {
        rc_client_set_host(client_, config_.serverHost.c_str());
    }

    rc_client_set_hardcore_enabled(client_, hardcoreEnforced_ ? 1 : 0);
    rc_client_set_encore_mode_enabled(client_, config_.encoreEnabled ? 1 : 0);
    rc_client_set_unofficial_enabled(client_, config_.unofficialEnabled ? 1 : 0);

    char uaBuffer[64] = {};
    rc_client_get_user_agent_clause(client_, uaBuffer, sizeof(uaBuffer));
    userAgentClause_ = std::string(uaBuffer);
    rebuildUserAgentLocked();

    sessionStatus_ = NativeAchievementsSnapshot::STATUS_SIGNING_IN;

    rc_client_begin_login_with_token(
        client_,
        config_.username.c_str(),
        config_.token.c_str(),
        &loginCallback,
        this
    );
}

void Achievements::stepFrame(const struct retro_memory_map* mmap) {
    const uint32_t consoleId = effectiveConsoleId_ != 0 ? effectiveConsoleId_ : config_.consoleId;
    if (activeCore_ != nullptr && consoleId != 0) {
        const uint64_t currentMapGen = Environment::getInstance().getMemoryMapGeneration();
        if (currentMapGen != mappedMemoryGeneration_) {
            refreshMemoryRegionsLocked(activeCore_, mmap);
        } else if (!hasValidMemoryRegion_ && !retriedLateMemoryInit_) {
            retriedLateMemoryInit_ = true;
            refreshMemoryRegionsLocked(activeCore_, mmap);
            if (!hasValidMemoryRegion_ && hardcoreEnforced_) {
                abortHardcoreEnableLocked(
                    std::string(kHardcoreCoreUnsupportedPrefix) +
                    "Emulation core exposed zero RetroAchievements memory"
                );
            }
        }
    }

    if (client_ == nullptr) {
        return;
    }

    rc_client_do_frame(client_);
}

void Achievements::reset() {
    activeTrackers_.clear();
    if (client_ != nullptr) {
        rc_client_reset(client_);
    }
}

bool Achievements::isHardcoreEnforcedLocked() const {
    return hardcoreEnforced_;
}

std::string Achievements::getCoreLibraryNameLocked() const {
    return coreLibraryName_;
}

uint32_t Achievements::getEffectiveConsoleIdLocked() const {
    return effectiveConsoleId_ != 0 ? effectiveConsoleId_ : config_.consoleId;
}

bool Achievements::beginHardcoreEnableLocked(std::string* outFailureReason) {
    const uint32_t consoleId = getEffectiveConsoleIdLocked();
    if (!config_.coreHardcoreAllowed) {
        if (outFailureReason != nullptr) {
            *outFailureReason = !config_.coreUnsupportedReason.empty()
                ? config_.coreUnsupportedReason
                : "Core cannot enforce Hardcore compliance";
        }
        return false;
    }
    if (!coreLibraryName_.empty() &&
        consoleId != 0 &&
        rc_libretro_is_system_allowed(coreLibraryName_.c_str(), consoleId) == 0) {
        if (outFailureReason != nullptr) {
            *outFailureReason = "Core " + coreLibraryName_ + " is disallowed for Hardcore on this system";
        }
        return false;
    }

    transitionResetInProgress_ = true;
    externalSdkResetRequested_ = false;
    hardcoreEnforced_ = true;
    activeTrackers_.clear();

    if (client_ != nullptr) {
        rc_client_set_hardcore_enabled(client_, 1);
    }
    return true;
}

bool Achievements::completeHardcoreCleanResetLocked(
    Core* core,
    const struct retro_memory_map* mmap,
    std::string* outFailureReason
) {
    updateCoreMetadataLocked(core);
    const uint32_t consoleId = getEffectiveConsoleIdLocked();

    retriedLateMemoryInit_ = false;
    refreshMemoryRegionsLocked(core, mmap);

    if (!coreLibraryName_.empty() &&
        consoleId != 0 &&
        rc_libretro_is_system_allowed(coreLibraryName_.c_str(), consoleId) == 0) {
        std::string reason = "Core " + coreLibraryName_ + " is disallowed for Hardcore on this system";
        if (outFailureReason != nullptr) {
            *outFailureReason = reason;
        }
        abortHardcoreEnableLocked(reason);
        return false;
    }

    if (client_ != nullptr) {
        rc_client_reset(client_);
        if (hasServerWarningAchievement(client_)) {
            rc_client_set_hardcore_enabled(client_, 0);
            hardcoreEnforced_ = false;
            config_.hardcoreEnabled = false;
            hardcoreServerBlocked_ = true;
            transitionResetInProgress_ = false;
            externalSdkResetRequested_ = false;
            statusMessage_ = kHardcoreDowngradedStatus;
            if (outFailureReason != nullptr) {
                *outFailureReason = kHardcoreDowngradedStatus;
            }
            NativeAchievementEvent ev {};
            ev.type = NativeAchievementEvent::TYPE_HARDCORE_MODE_CHANGED;
            ev.hardcore = false;
            ev.description = statusMessage_;
            pushEvent(std::move(ev));
            return false;
        }
    }

    transitionResetInProgress_ = false;
    externalSdkResetRequested_ = false;
    hardcoreEnforced_ = true;
    config_.hardcoreEnabled = true;
    if (statusMessage_ == kHardcoreDowngradedStatus ||
        statusMessage_.rfind(kHardcoreCoreUnsupportedPrefix, 0) == 0) {
        statusMessage_.clear();
    }

    NativeAchievementEvent ev {};
    ev.type = NativeAchievementEvent::TYPE_HARDCORE_MODE_CHANGED;
    ev.hardcore = true;
    pushEvent(std::move(ev));
    return true;
}

void Achievements::abortHardcoreEnableLocked(const std::string& reason) {
    transitionResetInProgress_ = false;
    externalSdkResetRequested_ = false;
    hardcoreEnforced_ = false;
    config_.hardcoreEnabled = false;
    activeTrackers_.clear();

    if (client_ != nullptr) {
        rc_client_set_hardcore_enabled(client_, 0);
    }
    statusMessage_ = reason;

    NativeAchievementEvent ev {};
    ev.type = NativeAchievementEvent::TYPE_HARDCORE_MODE_CHANGED;
    ev.hardcore = false;
    ev.description = reason;
    pushEvent(std::move(ev));
}

void Achievements::disableHardcoreImmediateLocked() {
    transitionResetInProgress_ = false;
    externalSdkResetRequested_ = false;
    hardcoreEnforced_ = false;
    config_.hardcoreEnabled = false;
    activeTrackers_.clear();

    if (client_ != nullptr) {
        rc_client_set_hardcore_enabled(client_, 0);
    }

    NativeAchievementEvent ev {};
    ev.type = NativeAchievementEvent::TYPE_HARDCORE_MODE_CHANGED;
    ev.hardcore = false;
    pushEvent(std::move(ev));
}

bool Achievements::consumeExternalSdkResetRequestLocked() {
    if (!externalSdkResetRequested_) {
        return false;
    }
    externalSdkResetRequested_ = false;
    return true;
}

bool Achievements::canPauseLocked(uint32_t* outFramesRemaining) {
    if (outFramesRemaining != nullptr) {
        *outFramesRemaining = 0;
    }
    if (!hardcoreEnforced_ || client_ == nullptr) {
        return true;
    }
    uint32_t remaining = 0;
    int allowed = rc_client_can_pause(client_, &remaining);
    if (outFramesRemaining != nullptr) {
        *outFramesRemaining = remaining;
    }
    return allowed != 0;
}

void Achievements::idleLocked() {
    if (client_ != nullptr) {
        rc_client_idle(client_);
    }
}

void Achievements::changeMediaLocked(
    const std::string& filePath,
    const uint8_t* data,
    size_t dataSize
) {
    if (client_ == nullptr || (filePath.empty() && (data == nullptr || dataSize == 0))) {
        return;
    }
    VFS::getInstance().resetAllOffsets();
    rc_client_begin_identify_and_change_media(
        client_,
        filePath.empty() ? nullptr : filePath.c_str(),
        data,
        dataSize,
        &changeMediaCallback,
        this
    );
}

void Achievements::shutdown() {
    {
        std::lock_guard<std::mutex> lock(httpMapMutex_);
        currentGeneration_++;
        pendingRequests_.clear();
    }

    if (client_ != nullptr) {
        rc_client_destroy(client_);
        client_ = nullptr;
    }

    rc_libretro_memory_destroy(&memoryRegions_);
    memset(&memoryRegions_, 0, sizeof(memoryRegions_));
    hasValidMemoryRegion_ = false;
    mappedMemoryGeneration_ = 0;
    activeCore_ = nullptr;
    retriedLateMemoryInit_ = false;
    effectiveConsoleId_ = 0;
    coreLibraryName_.clear();
    coreLibraryVersion_.clear();
    gameHash_.clear();
    hashFailureReason_.clear();
    userAgentClause_.clear();
    fullUserAgent_.clear();
    sessionStatus_ = NativeAchievementsSnapshot::STATUS_DISABLED;
    statusMessage_.clear();
    hardcoreEnforced_ = false;
    hardcoreServerBlocked_ = false;
    transitionResetInProgress_ = false;
    externalSdkResetRequested_ = false;
    activeTrackers_.clear();

    {
        std::lock_guard<std::mutex> lock(eventMutex_);
        eventQueue_.clear();
        hasUnnotifiedEvents_ = false;
    }

    if (javaVm_ != nullptr && bridgeClass_ != nullptr && cancelAllHttpMethod_ != nullptr) {
        JNIEnv* env = nullptr;
        if (javaVm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK && env != nullptr) {
            env->CallStaticVoidMethod(bridgeClass_, cancelAllHttpMethod_);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
            }
        }
    }
}

std::vector<uint8_t> Achievements::serializeProgress() {
    if (client_ == nullptr || !rc_client_is_game_loaded(client_)) {
        return {};
    }
    size_t needed = rc_client_progress_size(client_);
    if (needed == 0) {
        return {};
    }
    std::vector<uint8_t> buffer(needed);
    if (rc_client_serialize_progress_sized(client_, buffer.data(), buffer.size()) == RC_OK) {
        return buffer;
    }
    return {};
}

void Achievements::onStateUnserialized(const uint8_t* progressData, size_t progressSize) {
    if (client_ == nullptr) {
        return;
    }
    if (progressData != nullptr && progressSize > 0) {
        if (rc_client_deserialize_progress_sized(client_, progressData, progressSize) != RC_OK) {
            rc_client_reset(client_);
        }
    } else {
        rc_client_reset(client_);
    }
}

void Achievements::completeHttpRequestLocked(
    uint64_t requestId,
    int httpStatusCode,
    const char* responseBody,
    size_t responseLength
) {
    PendingHttpCallback pending {};
    {
        std::lock_guard<std::mutex> lock(httpMapMutex_);
        auto it = pendingRequests_.find(requestId);
        if (it == pendingRequests_.end()) {
            return;
        }
        pending = it->second;
        pendingRequests_.erase(it);
        if (pending.generation != currentGeneration_ || client_ == nullptr || pending.callback == nullptr) {
            return;
        }
    }

    rc_api_server_response_t serverResponse {};
    serverResponse.body = responseBody != nullptr ? responseBody : "";
    serverResponse.body_length = responseBody != nullptr ? responseLength : 0;
    serverResponse.http_status_code = httpStatusCode;

    pending.callback(&serverResponse, pending.callbackData);
}

std::vector<NativeAchievementEvent> Achievements::pollEvents() {
    std::lock_guard<std::mutex> lock(eventMutex_);
    hasUnnotifiedEvents_ = false;
    std::vector<NativeAchievementEvent> drained;
    drained.swap(eventQueue_);
    return drained;
}

void Achievements::notifyJavaEventsIfPending() {
    bool shouldNotify = false;
    {
        std::lock_guard<std::mutex> lock(eventMutex_);
        if (hasUnnotifiedEvents_) {
            hasUnnotifiedEvents_ = false;
            shouldNotify = true;
        }
    }

    if (!shouldNotify || javaVm_ == nullptr || bridgeClass_ == nullptr || notifyEventsMethod_ == nullptr) {
        return;
    }

    JNIEnv* env = nullptr;
    bool attachedHere = false;
    jint envStatus = javaVm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (envStatus == JNI_EDETACHED) {
        if (javaVm_->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            attachedHere = true;
        }
    }
    if (env != nullptr) {
        env->CallStaticVoidMethod(bridgeClass_, notifyEventsMethod_);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
    }
    if (attachedHere) {
        javaVm_->DetachCurrentThread();
    }
}

NativeAchievementsSnapshot Achievements::captureSnapshotLocked() {
    NativeAchievementsSnapshot snap {};
    snap.sessionStatus = sessionStatus_;
    snap.statusMessage = statusMessage_;
    snap.gameHash = gameHash_;
    snap.userAgent = fullUserAgent_;
    snap.hardcoreActive = hardcoreEnforced_ &&
        (client_ == nullptr || rc_client_get_hardcore_enabled(client_) != 0);

    for (const auto& kv : activeTrackers_) {
        NativeLeaderboardTracker tr {};
        tr.id = static_cast<int32_t>(kv.first);
        tr.display = kv.second;
        snap.leaderboardTrackers.push_back(std::move(tr));
    }
    std::sort(
        snap.leaderboardTrackers.begin(),
        snap.leaderboardTrackers.end(),
        [](const NativeLeaderboardTracker& a, const NativeLeaderboardTracker& b) {
            return a.id < b.id;
        }
    );

    if (client_ == nullptr) {
        return snap;
    }

    const rc_client_game_t* game = rc_client_get_game_info(client_);
    if (game != nullptr && game->id != 0) {
        snap.gameId = static_cast<int32_t>(game->id);
        snap.gameTitle = safeString(game->title);
        snap.gameBadgeUrl = resolveGameBadgeUrl(game);
        if (game->hash != nullptr && game->hash[0] != '\0') {
            snap.gameHash = std::string(game->hash);
        }

        rc_client_user_game_summary_t summary {};
        rc_client_get_user_game_summary(client_, &summary);
        snap.totalCount = static_cast<int32_t>(
            summary.num_core_achievements +
            (config_.unofficialEnabled ? summary.num_unofficial_achievements : 0)
        );
        snap.unlockedCount = static_cast<int32_t>(summary.num_unlocked_achievements);
        snap.totalPoints = static_cast<int32_t>(summary.points_core);
        snap.unlockedPoints = static_cast<int32_t>(summary.points_unlocked);

        if (rc_client_has_rich_presence(client_)) {
            char rpBuf[256] = {};
            size_t written = rc_client_get_rich_presence_message(client_, rpBuf, sizeof(rpBuf));
            if (written > 0) {
                snap.richPresence = std::string(rpBuf);
            }
        }

        int category = config_.unofficialEnabled
            ? RC_CLIENT_ACHIEVEMENT_CATEGORY_CORE_AND_UNOFFICIAL
            : RC_CLIENT_ACHIEVEMENT_CATEGORY_CORE;
        rc_client_achievement_list_t* list = rc_client_create_achievement_list(
            client_,
            category,
            RC_CLIENT_ACHIEVEMENT_LIST_GROUPING_PROGRESS
        );
        if (list != nullptr) {
            for (uint32_t b = 0; b < list->num_buckets; b++) {
                const rc_client_achievement_bucket_t& bucket = list->buckets[b];
                for (uint32_t a = 0; a < bucket.num_achievements; a++) {
                    const rc_client_achievement_t* ach = bucket.achievements[a];
                    if (ach == nullptr || ach->id >= kWarningAchievementMinId) {
                        continue;
                    }
                    NativeAchievementDef def {};
                    def.id = static_cast<int32_t>(ach->id);
                    def.title = safeString(ach->title);
                    def.description = safeString(ach->description);
                    def.badgeUrl = resolveAchievementBadgeUrl(ach, false);
                    def.badgeLockedUrl = resolveAchievementBadgeUrl(ach, true);
                    def.points = static_cast<int32_t>(ach->points);
                    def.state = static_cast<int32_t>(ach->state);
                    def.category = static_cast<int32_t>(ach->category);
                    def.bucket = static_cast<int32_t>(ach->bucket);
                    def.unlockedMask = static_cast<int32_t>(ach->unlocked);
                    def.unlockTimeSeconds = static_cast<int64_t>(ach->unlock_time);
                    def.measuredProgress = safeString(ach->measured_progress);
                    def.measuredPercent = ach->measured_percent;
                    def.rarity = ach->rarity;
                    def.rarityHardcore = ach->rarity_hardcore;
                    def.type = static_cast<int32_t>(ach->type);
                    snap.achievements.push_back(std::move(def));
                }
            }
            rc_client_destroy_achievement_list(list);
        }

        if (rc_client_has_leaderboards(client_)) {
            rc_client_leaderboard_list_t* lbList = rc_client_create_leaderboard_list(
                client_,
                RC_CLIENT_LEADERBOARD_LIST_GROUPING_TRACKING
            );
            if (lbList != nullptr) {
                for (uint32_t b = 0; b < lbList->num_buckets; b++) {
                    const rc_client_leaderboard_bucket_t& bucket = lbList->buckets[b];
                    for (uint32_t l = 0; l < bucket.num_leaderboards; l++) {
                        const rc_client_leaderboard_t* lb = bucket.leaderboards[l];
                        if (lb == nullptr) {
                            continue;
                        }
                        NativeLeaderboardDef lbDef {};
                        lbDef.id = static_cast<int32_t>(lb->id);
                        lbDef.title = safeString(lb->title);
                        lbDef.description = safeString(lb->description);
                        lbDef.trackerValue = safeString(lb->tracker_value);
                        lbDef.state = static_cast<int32_t>(lb->state);
                        lbDef.format = static_cast<int32_t>(lb->format);
                        lbDef.lowerIsBetter = lb->lower_is_better != 0;
                        snap.leaderboards.push_back(std::move(lbDef));
                    }
                }
                rc_client_destroy_leaderboard_list(lbList);
            }
        }
    }

    return snap;
}

void Achievements::pushEvent(NativeAchievementEvent event) {
    std::lock_guard<std::mutex> lock(eventMutex_);
    eventQueue_.push_back(std::move(event));
    hasUnnotifiedEvents_ = true;
}

void Achievements::dispatchHttpToJava(
    uint64_t requestId,
    const char* url,
    const char* postData,
    const char* contentType
) {
    if (javaVm_ == nullptr || bridgeClass_ == nullptr || dispatchHttpMethod_ == nullptr) {
        return;
    }

    JNIEnv* env = nullptr;
    bool attachedHere = false;
    jint envStatus = javaVm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (envStatus == JNI_EDETACHED) {
        if (javaVm_->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            attachedHere = true;
        }
    }
    if (env != nullptr) {
        jstring jUrl = utf8ToJString(env, url);
        jstring jPostData = postData != nullptr ? utf8ToJString(env, postData) : nullptr;
        jstring jContentType = contentType != nullptr ? utf8ToJString(env, contentType) : nullptr;
        jstring jFullUa = utf8ToJString(env, fullUserAgent_.c_str());
        jstring jGameHash = utf8ToJString(env, gameHash_.c_str());

        env->CallStaticVoidMethod(
            bridgeClass_,
            dispatchHttpMethod_,
            static_cast<jlong>(requestId),
            jUrl,
            jPostData,
            jContentType,
            jFullUa,
            jGameHash
        );
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }

        env->DeleteLocalRef(jUrl);
        if (jPostData != nullptr) env->DeleteLocalRef(jPostData);
        if (jContentType != nullptr) env->DeleteLocalRef(jContentType);
        env->DeleteLocalRef(jFullUa);
        env->DeleteLocalRef(jGameHash);
    }
    if (attachedHere) {
        javaVm_->DetachCurrentThread();
    }
}

uint32_t RC_CCONV Achievements::readMemoryCallback(
    uint32_t address,
    uint8_t* buffer,
    uint32_t numBytes,
    rc_client_t* client
) {
    auto* self = static_cast<Achievements*>(rc_client_get_userdata(client));
    if (self == nullptr || !self->hasValidMemoryRegion_ || self->memoryRegions_.total_size == 0) {
        return 0;
    }
    return rc_libretro_memory_read(&self->memoryRegions_, address, buffer, numBytes);
}

void RC_CCONV Achievements::serverCallCallback(
    const rc_api_request_t* request,
    rc_client_server_callback_t callback,
    void* callbackData,
    rc_client_t* client
) {
    auto* self = static_cast<Achievements*>(rc_client_get_userdata(client));
    if (self == nullptr || request == nullptr || request->url == nullptr) {
        return;
    }

    uint64_t reqId = 0;
    {
        std::lock_guard<std::mutex> lock(self->httpMapMutex_);
        reqId = ++self->nextRequestId_;
        self->pendingRequests_[reqId] = PendingHttpCallback {
            callback,
            callbackData,
            self->currentGeneration_
        };
    }

    self->dispatchHttpToJava(reqId, request->url, request->post_data, request->content_type);
}

void RC_CCONV Achievements::eventHandlerCallback(
    const rc_client_event_t* event,
    rc_client_t* client
) {
    auto* self = static_cast<Achievements*>(rc_client_get_userdata(client));
    if (self == nullptr || event == nullptr) {
        return;
    }

    const bool hardcore = rc_client_get_hardcore_enabled(client) != 0;

    switch (event->type) {
        case RC_CLIENT_EVENT_RESET: {
            if (!self->transitionResetInProgress_) {
                self->externalSdkResetRequested_ = true;
            }
            NativeAchievementEvent ev {};
            ev.type = NativeAchievementEvent::TYPE_RESET_REQUIRED;
            ev.hardcore = hardcore;
            self->pushEvent(std::move(ev));
            break;
        }
        case RC_CLIENT_EVENT_ACHIEVEMENT_TRIGGERED: {
            if (event->achievement == nullptr || event->achievement->id >= kWarningAchievementMinId) break;
            const auto* ach = event->achievement;
            NativeAchievementEvent ev {};
            ev.type = NativeAchievementEvent::TYPE_ACHIEVEMENT_TRIGGERED;
            ev.id = static_cast<int32_t>(ach->id);
            ev.title = safeString(ach->title);
            ev.description = safeString(ach->description);
            ev.badgeUrl = resolveAchievementBadgeUrl(ach, false);
            ev.points = static_cast<int32_t>(ach->points);
            ev.hardcore = hardcore;
            ev.rarity = hardcore ? ach->rarity_hardcore : ach->rarity;
            ev.achievementType = static_cast<int32_t>(ach->type);
            self->pushEvent(std::move(ev));
            break;
        }
        case RC_CLIENT_EVENT_ACHIEVEMENT_PROGRESS_INDICATOR_SHOW:
        case RC_CLIENT_EVENT_ACHIEVEMENT_PROGRESS_INDICATOR_UPDATE: {
            if (event->achievement == nullptr || event->achievement->id >= kWarningAchievementMinId) break;
            const auto* ach = event->achievement;
            NativeAchievementEvent ev {};
            ev.type = NativeAchievementEvent::TYPE_PROGRESS_SHOW;
            ev.id = static_cast<int32_t>(ach->id);
            ev.title = safeString(ach->title);
            ev.description = safeString(ach->description);
            ev.badgeUrl = resolveAchievementBadgeUrl(ach, false);
            ev.measuredProgress = safeString(ach->measured_progress);
            ev.measuredPercent = ach->measured_percent;
            ev.points = static_cast<int32_t>(ach->points);
            ev.hardcore = hardcore;
            self->pushEvent(std::move(ev));
            break;
        }
        case RC_CLIENT_EVENT_ACHIEVEMENT_PROGRESS_INDICATOR_HIDE: {
            NativeAchievementEvent ev {};
            ev.type = NativeAchievementEvent::TYPE_PROGRESS_HIDE;
            self->pushEvent(std::move(ev));
            break;
        }
        case RC_CLIENT_EVENT_ACHIEVEMENT_CHALLENGE_INDICATOR_SHOW: {
            if (event->achievement == nullptr || event->achievement->id >= kWarningAchievementMinId) break;
            const auto* ach = event->achievement;
            NativeAchievementEvent ev {};
            ev.type = NativeAchievementEvent::TYPE_CHALLENGE_SHOW;
            ev.id = static_cast<int32_t>(ach->id);
            ev.title = safeString(ach->title);
            ev.badgeUrl = resolveAchievementBadgeUrl(ach, false);
            self->pushEvent(std::move(ev));
            break;
        }
        case RC_CLIENT_EVENT_ACHIEVEMENT_CHALLENGE_INDICATOR_HIDE: {
            if (event->achievement == nullptr || event->achievement->id >= kWarningAchievementMinId) break;
            NativeAchievementEvent ev {};
            ev.type = NativeAchievementEvent::TYPE_CHALLENGE_HIDE;
            ev.id = static_cast<int32_t>(event->achievement->id);
            self->pushEvent(std::move(ev));
            break;
        }
        case RC_CLIENT_EVENT_LEADERBOARD_STARTED: {
            if (event->leaderboard == nullptr) break;
            const auto* lb = event->leaderboard;
            NativeAchievementEvent ev {};
            ev.type = NativeAchievementEvent::TYPE_LEADERBOARD_STARTED;
            ev.id = static_cast<int32_t>(lb->id);
            ev.title = safeString(lb->title);
            ev.description = safeString(lb->description);
            ev.measuredProgress = safeString(lb->tracker_value);
            ev.hardcore = hardcore;
            self->pushEvent(std::move(ev));
            break;
        }
        case RC_CLIENT_EVENT_LEADERBOARD_FAILED: {
            if (event->leaderboard == nullptr) break;
            const auto* lb = event->leaderboard;
            NativeAchievementEvent ev {};
            ev.type = NativeAchievementEvent::TYPE_LEADERBOARD_FAILED;
            ev.id = static_cast<int32_t>(lb->id);
            ev.title = safeString(lb->title);
            ev.description = safeString(lb->description);
            ev.hardcore = hardcore;
            self->pushEvent(std::move(ev));
            break;
        }
        case RC_CLIENT_EVENT_LEADERBOARD_SUBMITTED: {
            if (event->leaderboard == nullptr) break;
            const auto* lb = event->leaderboard;
            NativeAchievementEvent ev {};
            ev.type = NativeAchievementEvent::TYPE_LEADERBOARD_SUBMITTED;
            ev.id = static_cast<int32_t>(lb->id);
            ev.title = safeString(lb->title);
            ev.description = safeString(lb->description);
            ev.measuredProgress = safeString(lb->tracker_value);
            ev.hardcore = hardcore;
            self->pushEvent(std::move(ev));
            break;
        }
        case RC_CLIENT_EVENT_LEADERBOARD_SCOREBOARD: {
            if (event->leaderboard_scoreboard == nullptr) break;
            const auto* sb = event->leaderboard_scoreboard;
            const auto* lb = event->leaderboard;
            NativeAchievementEvent ev {};
            ev.type = NativeAchievementEvent::TYPE_LEADERBOARD_SCOREBOARD;
            ev.id = static_cast<int32_t>(sb->leaderboard_id);
            ev.title = lb != nullptr ? safeString(lb->title) : std::string("Leaderboard");
            ev.measuredProgress = safeString(sb->submitted_score);
            ev.description = safeString(sb->best_score);
            ev.points = static_cast<int32_t>(sb->new_rank);
            ev.totalCount = static_cast<int32_t>(sb->num_entries);
            ev.hardcore = hardcore;
            self->pushEvent(std::move(ev));
            break;
        }
        case RC_CLIENT_EVENT_LEADERBOARD_TRACKER_SHOW:
        case RC_CLIENT_EVENT_LEADERBOARD_TRACKER_UPDATE: {
            if (event->leaderboard_tracker == nullptr) break;
            const auto* tr = event->leaderboard_tracker;
            std::string display = safeString(tr->display);
            self->activeTrackers_[tr->id] = display;
            NativeAchievementEvent ev {};
            ev.type = NativeAchievementEvent::TYPE_LEADERBOARD_TRACKER_SHOW;
            ev.id = static_cast<int32_t>(tr->id);
            ev.measuredProgress = std::move(display);
            ev.hardcore = hardcore;
            self->pushEvent(std::move(ev));
            break;
        }
        case RC_CLIENT_EVENT_LEADERBOARD_TRACKER_HIDE: {
            if (event->leaderboard_tracker == nullptr) break;
            self->activeTrackers_.erase(event->leaderboard_tracker->id);
            NativeAchievementEvent ev {};
            ev.type = NativeAchievementEvent::TYPE_LEADERBOARD_TRACKER_HIDE;
            ev.id = static_cast<int32_t>(event->leaderboard_tracker->id);
            ev.hardcore = hardcore;
            self->pushEvent(std::move(ev));
            break;
        }
        case RC_CLIENT_EVENT_GAME_COMPLETED: {
            const rc_client_game_t* game = rc_client_get_game_info(client);
            NativeAchievementEvent ev {};
            ev.type = NativeAchievementEvent::TYPE_GAME_COMPLETED;
            if (game != nullptr) {
                ev.id = static_cast<int32_t>(game->id);
                ev.title = safeString(game->title);
                ev.badgeUrl = resolveGameBadgeUrl(game);
            }
            ev.hardcore = hardcore;
            self->pushEvent(std::move(ev));
            break;
        }
        case RC_CLIENT_EVENT_SERVER_ERROR: {
            if (event->server_error == nullptr) break;
            NativeAchievementEvent ev {};
            ev.type = NativeAchievementEvent::TYPE_SERVER_ERROR;
            ev.title = safeString(event->server_error->api);
            ev.description = redactSensitiveMessage(event->server_error->error_message);
            self->pushEvent(std::move(ev));
            break;
        }
        case RC_CLIENT_EVENT_DISCONNECTED: {
            NativeAchievementEvent ev {};
            ev.type = NativeAchievementEvent::TYPE_DISCONNECTED;
            self->pushEvent(std::move(ev));
            break;
        }
        case RC_CLIENT_EVENT_RECONNECTED: {
            NativeAchievementEvent ev {};
            ev.type = NativeAchievementEvent::TYPE_RECONNECTED;
            self->pushEvent(std::move(ev));
            break;
        }
        default:
            break;
    }
}

void RC_CCONV Achievements::logMessageCallback(const char* message, const rc_client_t* /*client*/) {
    if (message != nullptr) {
        std::string safeMsg = redactSensitiveMessage(message);
        LOGI("[rcheevos] %s", safeMsg.c_str());
    }
}

void RC_CCONV Achievements::loginCallback(
    int result,
    const char* errorMessage,
    rc_client_t* client,
    void* userdata
) {
    auto* self = static_cast<Achievements*>(userdata);
    if (self == nullptr || self->client_ != client) {
        return;
    }

    if (result != RC_OK) {
        self->sessionStatus_ = NativeAchievementsSnapshot::STATUS_LOGIN_FAILED;
        self->statusMessage_ = errorMessage != nullptr
            ? redactSensitiveMessage(errorMessage)
            : std::string(rc_error_str(result));

        NativeAchievementEvent ev {};
        ev.type = NativeAchievementEvent::TYPE_LOGIN_FAILED;
        ev.id = result;
        ev.description = self->statusMessage_;
        self->pushEvent(std::move(ev));
        return;
    }

    const rc_client_user_t* user = rc_client_get_user_info(client);
    if (user != nullptr) {
        NativeAchievementEvent ev {};
        ev.type = NativeAchievementEvent::TYPE_LOGIN_SUCCEEDED;
        ev.title = safeString(user->display_name != nullptr ? user->display_name : user->username);
        if (user->avatar_url != nullptr && user->avatar_url[0] != '\0') {
            ev.badgeUrl = std::string(user->avatar_url);
        } else {
            char avatarBuf[256] = {};
            if (rc_client_user_get_image_url(user, avatarBuf, sizeof(avatarBuf)) == RC_OK) {
                ev.badgeUrl = std::string(avatarBuf);
            }
        }
        ev.points = static_cast<int32_t>(user->score);
        ev.totalPoints = static_cast<int32_t>(user->score_softcore);
        ev.hardcore = self->hardcoreEnforced_;
        self->pushEvent(std::move(ev));
    }

    if (self->gameHash_.empty()) {
        self->sessionStatus_ = NativeAchievementsSnapshot::STATUS_UNSUPPORTED;
        self->statusMessage_ = !self->hashFailureReason_.empty()
            ? self->hashFailureReason_
            : "Unrecognized ROM format";
        NativeAchievementEvent ev {};
        ev.type = NativeAchievementEvent::TYPE_GAME_UNSUPPORTED;
        ev.description = self->statusMessage_;
        ev.hardcore = self->hardcoreEnforced_;
        self->pushEvent(std::move(ev));
        return;
    }

    self->sessionStatus_ = NativeAchievementsSnapshot::STATUS_IDENTIFYING;
    if (self->statusMessage_.rfind(kHardcoreCoreUnsupportedPrefix, 0) != 0) {
        self->statusMessage_.clear();
    }
    {
        NativeAchievementEvent ev {};
        ev.type = NativeAchievementEvent::TYPE_GAME_IDENTIFYING;
        ev.hardcore = self->hardcoreEnforced_;
        self->pushEvent(std::move(ev));
    }

    rc_client_begin_load_game(client, self->gameHash_.c_str(), &loadGameCallback, self);
}

void RC_CCONV Achievements::loadGameCallback(
    int result,
    const char* errorMessage,
    rc_client_t* client,
    void* userdata
) {
    auto* self = static_cast<Achievements*>(userdata);
    if (self == nullptr || self->client_ != client) {
        return;
    }

    const rc_client_game_t* game = rc_client_get_game_info(client);
    if (result == RC_NO_GAME_LOADED || (result == RC_OK && (game == nullptr || game->id == 0))) {
        self->sessionStatus_ = NativeAchievementsSnapshot::STATUS_UNSUPPORTED;
        self->statusMessage_ = "No RetroAchievements set for this ROM";
        NativeAchievementEvent ev {};
        ev.type = NativeAchievementEvent::TYPE_GAME_UNSUPPORTED;
        ev.description = self->gameHash_;
        ev.hardcore = self->hardcoreEnforced_;
        self->pushEvent(std::move(ev));
        return;
    }

    if (result != RC_OK) {
        self->sessionStatus_ = NativeAchievementsSnapshot::STATUS_LOAD_ERROR;
        self->statusMessage_ = errorMessage != nullptr
            ? redactSensitiveMessage(errorMessage)
            : std::string(rc_error_str(result));
        NativeAchievementEvent ev {};
        ev.type = NativeAchievementEvent::TYPE_SERVER_ERROR;
        ev.title = "load_game";
        ev.description = self->statusMessage_;
        self->pushEvent(std::move(ev));
        return;
    }

    self->sessionStatus_ = NativeAchievementsSnapshot::STATUS_LOADED;
    if (self->statusMessage_.rfind(kHardcoreCoreUnsupportedPrefix, 0) != 0) {
        self->statusMessage_.clear();
    }
    self->hardcoreServerBlocked_ = false;

    if (hasServerWarningAchievement(client) &&
        self->hardcoreEnforced_ &&
        rc_client_get_hardcore_enabled(client) != 0) {
        LOGI("[rcheevos] Server flagged emulator as unregistered for Hardcore; switching session to Softcore.");
        rc_client_set_hardcore_enabled(client, 0);
        self->hardcoreEnforced_ = false;
        self->config_.hardcoreEnabled = false;
        self->hardcoreServerBlocked_ = true;
        self->statusMessage_ = kHardcoreDowngradedStatus;
    }

    rc_client_user_game_summary_t summary {};
    rc_client_get_user_game_summary(client, &summary);

    NativeAchievementEvent ev {};
    ev.type = NativeAchievementEvent::TYPE_GAME_LOADED;
    ev.id = static_cast<int32_t>(game->id);
    ev.title = safeString(game->title);
    ev.description = self->statusMessage_;
    ev.badgeUrl = resolveGameBadgeUrl(game);
    ev.totalCount = static_cast<int32_t>(
        summary.num_core_achievements +
        (self->config_.unofficialEnabled ? summary.num_unofficial_achievements : 0)
    );
    ev.unlockedCount = static_cast<int32_t>(summary.num_unlocked_achievements);
    ev.totalPoints = static_cast<int32_t>(summary.points_core);
    ev.points = static_cast<int32_t>(summary.points_unlocked);
    ev.hardcore = self->hardcoreEnforced_ && (rc_client_get_hardcore_enabled(client) != 0);
    self->pushEvent(std::move(ev));
}

void RC_CCONV Achievements::changeMediaCallback(
    int result,
    const char* errorMessage,
    rc_client_t* client,
    void* userdata
) {
    auto* self = static_cast<Achievements*>(userdata);
    if (self == nullptr || self->client_ != client) {
        return;
    }

    const rc_client_game_t* game = rc_client_get_game_info(client);
    if (game != nullptr && game->hash != nullptr && game->hash[0] != '\0') {
        self->gameHash_ = std::string(game->hash);
    }

    if (result != RC_OK) {
        NativeAchievementEvent ev {};
        ev.type = NativeAchievementEvent::TYPE_SERVER_ERROR;
        ev.title = "change_media";
        ev.description = errorMessage != nullptr
            ? redactSensitiveMessage(errorMessage)
            : std::string(rc_error_str(result));
        ev.hardcore = self->hardcoreEnforced_ && (rc_client_get_hardcore_enabled(client) != 0);
        self->pushEvent(std::move(ev));
    }
}

void RC_CCONV Achievements::getCoreMemoryInfoCallback(
    uint32_t id,
    rc_libretro_core_memory_info_t* info
) {
    Core* core = Achievements::getInstance().activeCore_;
    if (core != nullptr) {
        info->data = static_cast<uint8_t*>(core->retro_get_memory_data(id));
        info->size = core->retro_get_memory_size(id);
    } else {
        info->data = nullptr;
        info->size = 0;
    }
}

} // namespace libretrodroid
