/*
 *     Copyright (C) 2026  Arcadune Project
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

#include "achievements.h"

#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <strings.h>
#include <unistd.h>

#include "log.h"
#include "vfs/vfs.h"

namespace libretrodroid {

namespace {

// Synthetic server warning IDs injected by RetroAchievements (e.g. 101000001 "Unknown Emulator").
constexpr uint32_t kWarningAchievementMinId = 101000001u;
constexpr const char* kHardcoreDowngradedStatus = "HARDCORE_UNREGISTERED_EMULATOR";

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

std::string safeString(const char* s) {
    return s != nullptr ? std::string(s) : std::string();
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
            "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"
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
        env->DeleteLocalRef(localBridge);
    }
}

void Achievements::configure(const AchievementsConfig& config) {
    shutdown();
    config_ = config;
    if (config_.enabled && !config_.username.empty() && !config_.token.empty() && config_.consoleId != 0) {
        sessionStatus_ = NativeAchievementsSnapshot::STATUS_SIGNING_IN;
        statusMessage_.clear();
    } else {
        sessionStatus_ = NativeAchievementsSnapshot::STATUS_DISABLED;
        statusMessage_.clear();
    }
}

void Achievements::prepareGameHash(
    const std::string& gamePath,
    const uint8_t* gameData,
    size_t gameSize
) {
    gameHash_.clear();
    if (!config_.enabled || config_.username.empty() || config_.token.empty() || config_.consoleId == 0) {
        return;
    }

    uint32_t effectiveConsoleId = config_.consoleId;
    if (effectiveConsoleId == RC_CONSOLE_PC_ENGINE &&
        (hasExtensionIgnoreCase(gamePath, "cue") || hasExtensionIgnoreCase(gamePath, "chd"))) {
        effectiveConsoleId = RC_CONSOLE_PC_ENGINE_CD;
    }

    const uint8_t* hashBuffer = gameData;
    size_t hashBufferSize = gameSize;
    if (effectiveConsoleId == RC_CONSOLE_ARCADE && !hasExtensionIgnoreCase(gamePath, "neo")) {
        // Arcade .zip/.7z sets hash by archive stem name in rcheevos.
        hashBuffer = nullptr;
        hashBufferSize = 0;
    }

    const char* effectivePath = gamePath.empty() ? "?" : gamePath.c_str();

    rc_hash_iterator_t iterator {};
    rc_hash_initialize_iterator(&iterator, effectivePath, hashBuffer, hashBufferSize);
    iterator.callbacks.filereader.open = &hashFileOpen;
    iterator.callbacks.filereader.seek = &hashFileSeek;
    iterator.callbacks.filereader.tell = &hashFileTell;
    iterator.callbacks.filereader.read = &hashFileRead;
    iterator.callbacks.filereader.close = &hashFileClose;
    rc_hash_get_default_cdreader(&iterator.callbacks.cdreader);

    char hash[33] = {};
    int generated = rc_hash_generate(hash, effectiveConsoleId, &iterator);
    rc_hash_destroy_iterator(&iterator);

    VFS::getInstance().resetAllOffsets();

    if (generated && hash[0] != '\0') {
        gameHash_ = std::string(hash);
        LOGI("RetroAchievements hashed ROM (%u): %s", effectiveConsoleId, gameHash_.c_str());
    } else {
        LOGW("RetroAchievements could not generate hash for path: %s", effectivePath);
    }
}

void Achievements::startSession(Core* core, const struct retro_memory_map* mmap) {
    if (!config_.enabled || config_.username.empty() || config_.token.empty() || config_.consoleId == 0) {
        sessionStatus_ = NativeAchievementsSnapshot::STATUS_DISABLED;
        return;
    }

    activeCore_ = core;
    retriedLateMemoryInit_ = false;
    memset(&memoryRegions_, 0, sizeof(memoryRegions_));
    rc_libretro_memory_init(&memoryRegions_, mmap, &getCoreMemoryInfoCallback, config_.consoleId);

    client_ = rc_client_create(&readMemoryCallback, &serverCallCallback);
    if (client_ == nullptr) {
        sessionStatus_ = NativeAchievementsSnapshot::STATUS_LOAD_ERROR;
        statusMessage_ = "Failed to initialize RetroAchievements runtime";
        return;
    }

    rc_client_set_userdata(client_, this);
    rc_client_enable_logging(client_, RC_CLIENT_LOG_LEVEL_INFO, &logMessageCallback);
    rc_client_set_event_handler(client_, &eventHandlerCallback);
    rc_client_set_hardcore_enabled(client_, config_.hardcoreEnabled ? 1 : 0);
    rc_client_set_encore_mode_enabled(client_, config_.encoreEnabled ? 1 : 0);
    rc_client_set_unofficial_enabled(client_, config_.unofficialEnabled ? 1 : 0);

    char uaBuffer[64] = {};
    rc_client_get_user_agent_clause(client_, uaBuffer, sizeof(uaBuffer));
    userAgentClause_ = std::string(uaBuffer);

    sessionStatus_ = NativeAchievementsSnapshot::STATUS_SIGNING_IN;
    statusMessage_.clear();

    rc_client_begin_login_with_token(
        client_,
        config_.username.c_str(),
        config_.token.c_str(),
        &loginCallback,
        this
    );
}

void Achievements::stepFrame(const struct retro_memory_map* mmap) {
    if (client_ == nullptr) {
        return;
    }

    if (memoryRegions_.total_size == 0 && !retriedLateMemoryInit_ && activeCore_ != nullptr) {
        retriedLateMemoryInit_ = true;
        rc_libretro_memory_init(&memoryRegions_, mmap, &getCoreMemoryInfoCallback, config_.consoleId);
    }

    rc_client_do_frame(client_);
}

void Achievements::reset() {
    if (client_ != nullptr) {
        rc_client_reset(client_);
    }
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
    activeCore_ = nullptr;
    retriedLateMemoryInit_ = false;
    gameHash_.clear();
    sessionStatus_ = NativeAchievementsSnapshot::STATUS_DISABLED;
    statusMessage_.clear();
    hardcoreServerBlocked_ = false;

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
    if (rc_client_get_hardcore_enabled(client_) != 0) {
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
    snap.hardcoreActive = client_ != nullptr
        ? (rc_client_get_hardcore_enabled(client_) != 0)
        : config_.hardcoreEnabled;

    if (client_ == nullptr) {
        return snap;
    }

    const rc_client_game_t* game = rc_client_get_game_info(client_);
    if (game != nullptr && game->id != 0) {
        snap.gameId = static_cast<int32_t>(game->id);
        snap.gameTitle = safeString(game->title);
        snap.gameBadgeUrl = resolveGameBadgeUrl(game);

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
        jstring jUaClause = utf8ToJString(env, userAgentClause_.c_str());

        env->CallStaticVoidMethod(
            bridgeClass_,
            dispatchHttpMethod_,
            static_cast<jlong>(requestId),
            jUrl,
            jPostData,
            jContentType,
            jUaClause
        );
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }

        env->DeleteLocalRef(jUrl);
        if (jPostData != nullptr) env->DeleteLocalRef(jPostData);
        if (jContentType != nullptr) env->DeleteLocalRef(jContentType);
        env->DeleteLocalRef(jUaClause);
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
    if (self == nullptr || self->memoryRegions_.total_size == 0) {
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
            ev.description = safeString(event->server_error->error_message);
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
        LOGI("[rcheevos] %s", message);
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
            ? std::string(errorMessage)
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
        self->pushEvent(std::move(ev));
    }

    if (self->gameHash_.empty()) {
        self->sessionStatus_ = NativeAchievementsSnapshot::STATUS_UNSUPPORTED;
        self->statusMessage_ = "Unrecognized ROM format";
        NativeAchievementEvent ev {};
        ev.type = NativeAchievementEvent::TYPE_GAME_UNSUPPORTED;
        ev.description = self->statusMessage_;
        self->pushEvent(std::move(ev));
        return;
    }

    self->sessionStatus_ = NativeAchievementsSnapshot::STATUS_IDENTIFYING;
    self->statusMessage_.clear();
    {
        NativeAchievementEvent ev {};
        ev.type = NativeAchievementEvent::TYPE_GAME_IDENTIFYING;
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
        self->pushEvent(std::move(ev));
        return;
    }

    if (result != RC_OK) {
        self->sessionStatus_ = NativeAchievementsSnapshot::STATUS_LOAD_ERROR;
        self->statusMessage_ = errorMessage != nullptr
            ? std::string(errorMessage)
            : std::string(rc_error_str(result));
        NativeAchievementEvent ev {};
        ev.type = NativeAchievementEvent::TYPE_SERVER_ERROR;
        ev.title = "load_game";
        ev.description = self->statusMessage_;
        self->pushEvent(std::move(ev));
        return;
    }

    self->sessionStatus_ = NativeAchievementsSnapshot::STATUS_LOADED;
    self->statusMessage_.clear();
    self->hardcoreServerBlocked_ = false;

    if (hasServerWarningAchievement(client) &&
        self->config_.hardcoreEnabled &&
        rc_client_get_hardcore_enabled(client) != 0) {
        LOGI("[rcheevos] Server flagged emulator as unregistered for Hardcore; switching session to Softcore.");
        rc_client_set_hardcore_enabled(client, 0);
        self->hardcoreServerBlocked_ = true;
        self->statusMessage_ = kHardcoreDowngradedStatus;
    }

    rc_client_user_game_summary_t summary {};
    rc_client_get_user_game_summary(client, &summary);

    NativeAchievementEvent ev {};
    ev.type = NativeAchievementEvent::TYPE_GAME_LOADED;
    ev.id = static_cast<int32_t>(game->id);
    ev.title = safeString(game->title);
    ev.description = self->hardcoreServerBlocked_ ? std::string(kHardcoreDowngradedStatus) : std::string();
    ev.badgeUrl = resolveGameBadgeUrl(game);
    ev.totalCount = static_cast<int32_t>(
        summary.num_core_achievements +
        (self->config_.unofficialEnabled ? summary.num_unofficial_achievements : 0)
    );
    ev.unlockedCount = static_cast<int32_t>(summary.num_unlocked_achievements);
    ev.totalPoints = static_cast<int32_t>(summary.points_core);
    ev.points = static_cast<int32_t>(summary.points_unlocked);
    ev.hardcore = rc_client_get_hardcore_enabled(client) != 0;
    self->pushEvent(std::move(ev));
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
