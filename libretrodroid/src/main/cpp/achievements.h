/*
 *     Copyright (C) 2026  Arcadune Project
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

#ifndef LIBRETRODROID_ACHIEVEMENTS_H
#define LIBRETRODROID_ACHIEVEMENTS_H

#include <jni.h>
#include <cstdint>
#include <cstddef>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

#include "core.h"
#include "libretro.h"

extern "C" {
#include "rc_client.h"
#include "rc_consoles.h"
#include "rc_error.h"
#include "rc_hash.h"
#include "rc_libretro.h"
}

namespace libretrodroid {

struct AchievementsConfig {
    bool enabled = false;
    std::string username;
    std::string token;
    uint32_t consoleId = 0;
    bool hardcoreEnabled = false;
    bool encoreEnabled = false;
    bool unofficialEnabled = false;
    std::string serverHost;
    std::string appVersion;
    std::string osRelease;
    bool coreHardcoreAllowed = true;
    std::string coreUnsupportedReason;
};

struct NativeAchievementEvent {
    int32_t type = 0;
    int32_t id = 0;
    std::string title;
    std::string description;
    std::string badgeUrl;
    std::string measuredProgress;
    float measuredPercent = 0.0f;
    int32_t points = 0;
    int32_t totalPoints = 0;
    int32_t unlockedCount = 0;
    int32_t totalCount = 0;
    bool hardcore = false;
    float rarity = 0.0f;
    int32_t achievementType = 0;

    static constexpr int32_t TYPE_LOGIN_SUCCEEDED = 1;
    static constexpr int32_t TYPE_LOGIN_FAILED = 2;
    static constexpr int32_t TYPE_GAME_IDENTIFYING = 3;
    static constexpr int32_t TYPE_GAME_LOADED = 4;
    static constexpr int32_t TYPE_GAME_UNSUPPORTED = 5;
    static constexpr int32_t TYPE_ACHIEVEMENT_TRIGGERED = 6;
    static constexpr int32_t TYPE_PROGRESS_SHOW = 7;
    static constexpr int32_t TYPE_PROGRESS_HIDE = 8;
    static constexpr int32_t TYPE_CHALLENGE_SHOW = 9;
    static constexpr int32_t TYPE_CHALLENGE_HIDE = 10;
    static constexpr int32_t TYPE_GAME_COMPLETED = 11;
    static constexpr int32_t TYPE_SERVER_ERROR = 12;
    static constexpr int32_t TYPE_DISCONNECTED = 13;
    static constexpr int32_t TYPE_RECONNECTED = 14;
    static constexpr int32_t TYPE_LEADERBOARD_STARTED = 15;
    static constexpr int32_t TYPE_LEADERBOARD_FAILED = 16;
    static constexpr int32_t TYPE_LEADERBOARD_SUBMITTED = 17;
    static constexpr int32_t TYPE_LEADERBOARD_SCOREBOARD = 18;
    static constexpr int32_t TYPE_LEADERBOARD_TRACKER_SHOW = 19;
    static constexpr int32_t TYPE_LEADERBOARD_TRACKER_HIDE = 20;
    static constexpr int32_t TYPE_HARDCORE_MODE_CHANGED = 21;
    static constexpr int32_t TYPE_RESET_REQUIRED = 22;
};

struct NativeAchievementDef {
    int32_t id = 0;
    std::string title;
    std::string description;
    std::string badgeUrl;
    std::string badgeLockedUrl;
    int32_t points = 0;
    int32_t state = 0;
    int32_t category = 0;
    int32_t bucket = 0;
    int32_t unlockedMask = 0;
    int64_t unlockTimeSeconds = 0;
    std::string measuredProgress;
    float measuredPercent = 0.0f;
    float rarity = 0.0f;
    float rarityHardcore = 0.0f;
    int32_t type = 0;
};

struct NativeLeaderboardDef {
    int32_t id = 0;
    std::string title;
    std::string description;
    std::string trackerValue;
    int32_t state = 0;
    int32_t format = 0;
    bool lowerIsBetter = false;
};

struct NativeLeaderboardTracker {
    int32_t id = 0;
    std::string display;
};

struct NativeAchievementsSnapshot {
    int32_t sessionStatus = 0;
    std::string statusMessage;
    int32_t gameId = 0;
    std::string gameTitle;
    std::string gameBadgeUrl;
    std::string gameHash;
    std::string richPresence;
    std::string userAgent;
    int32_t totalCount = 0;
    int32_t unlockedCount = 0;
    int32_t totalPoints = 0;
    int32_t unlockedPoints = 0;
    bool hardcoreActive = false;
    std::vector<NativeAchievementDef> achievements;
    std::vector<NativeLeaderboardDef> leaderboards;
    std::vector<NativeLeaderboardTracker> leaderboardTrackers;

    static constexpr int32_t STATUS_DISABLED = 0;
    static constexpr int32_t STATUS_SIGNING_IN = 1;
    static constexpr int32_t STATUS_IDENTIFYING = 2;
    static constexpr int32_t STATUS_LOADED = 3;
    static constexpr int32_t STATUS_UNSUPPORTED = 4;
    static constexpr int32_t STATUS_LOGIN_FAILED = 5;
    static constexpr int32_t STATUS_LOAD_ERROR = 6;
};

class Achievements {
public:
    static Achievements& getInstance() {
        static Achievements instance;
        return instance;
    }

    Achievements(const Achievements&) = delete;
    void operator=(const Achievements&) = delete;

    void initJni(JavaVM* vm, JNIEnv* env);

    // Must be called under coreLock (or before core creation).
    void configure(const AchievementsConfig& config);
    void updateCoreMetadataLocked(Core* core);
    void prepareGameHash(const std::string& gamePath, const uint8_t* gameData, size_t gameSize);
    void startSession(Core* core, const struct retro_memory_map* mmap);
    void refreshMemoryRegionsLocked(Core* core, const struct retro_memory_map* mmap);
    void stepFrame(const struct retro_memory_map* mmap);
    void reset();
    void shutdown();

    bool isHardcoreEnforcedLocked() const;
    std::string getCoreLibraryNameLocked() const;
    uint32_t getEffectiveConsoleIdLocked() const;

    bool beginHardcoreEnableLocked(std::string* outFailureReason);
    bool completeHardcoreCleanResetLocked(
        Core* core,
        const struct retro_memory_map* mmap,
        std::string* outFailureReason
    );
    void abortHardcoreEnableLocked(const std::string& reason);
    void disableHardcoreImmediateLocked();
    bool consumeExternalSdkResetRequestLocked();

    bool canPauseLocked(uint32_t* outFramesRemaining = nullptr);
    void idleLocked();
    void changeMediaLocked(const std::string& filePath, const uint8_t* data, size_t dataSize);

    std::vector<uint8_t> serializeProgress();
    void onStateUnserialized(const uint8_t* progressData, size_t progressSize);

    // Called under coreLock.
    void completeHttpRequestLocked(
        uint64_t requestId,
        int httpStatusCode,
        const char* responseBody,
        size_t responseLength
    );

    // Safe to call without coreLock (uses leaf eventMutex_).
    std::vector<NativeAchievementEvent> pollEvents();
    void notifyJavaEventsIfPending();

    int64_t openChdTrackFromNative(const char* pathUtf8, uint32_t track);
    size_t readChdSectorFromNative(int64_t handle, uint32_t sector, void* buffer, size_t requestedBytes);
    uint32_t firstChdTrackSectorFromNative(int64_t handle);
    void closeChdTrackFromNative(int64_t handle);

    // Must be called under coreLock.
    NativeAchievementsSnapshot captureSnapshotLocked();

private:
    Achievements() = default;

    struct PendingHttpCallback {
        rc_client_server_callback_t callback = nullptr;
        void* callbackData = nullptr;
        uint64_t generation = 0;
    };

    void rebuildUserAgentLocked();
    void pushEvent(NativeAchievementEvent event);
    void dispatchHttpToJava(
        uint64_t requestId,
        const char* url,
        const char* postData,
        const char* contentType
    );

    static uint32_t RC_CCONV readMemoryCallback(
        uint32_t address,
        uint8_t* buffer,
        uint32_t numBytes,
        rc_client_t* client
    );
    static void RC_CCONV serverCallCallback(
        const rc_api_request_t* request,
        rc_client_server_callback_t callback,
        void* callbackData,
        rc_client_t* client
    );
    static void RC_CCONV eventHandlerCallback(
        const rc_client_event_t* event,
        rc_client_t* client
    );
    static void RC_CCONV logMessageCallback(
        const char* message,
        const rc_client_t* client
    );
    static void RC_CCONV loginCallback(
        int result,
        const char* errorMessage,
        rc_client_t* client,
        void* userdata
    );
    static void RC_CCONV loadGameCallback(
        int result,
        const char* errorMessage,
        rc_client_t* client,
        void* userdata
    );
    static void RC_CCONV changeMediaCallback(
        int result,
        const char* errorMessage,
        rc_client_t* client,
        void* userdata
    );
    static void RC_CCONV getCoreMemoryInfoCallback(
        uint32_t id,
        rc_libretro_core_memory_info_t* info
    );

private:
    JavaVM* javaVm_ = nullptr;
    jclass bridgeClass_ = nullptr;
    jmethodID dispatchHttpMethod_ = nullptr;
    jmethodID notifyEventsMethod_ = nullptr;
    jmethodID cancelAllHttpMethod_ = nullptr;
    jmethodID openChdTrackMethod_ = nullptr;
    jmethodID readChdSectorMethod_ = nullptr;
    jmethodID firstChdTrackSectorMethod_ = nullptr;
    jmethodID closeChdTrackMethod_ = nullptr;

    AchievementsConfig config_ {};
    Core* activeCore_ = nullptr;
    rc_client_t* client_ = nullptr;
    rc_libretro_memory_regions_t memoryRegions_ {};
    bool hasValidMemoryRegion_ = false;
    uint64_t mappedMemoryGeneration_ = 0;
    bool retriedLateMemoryInit_ = false;
    uint32_t effectiveConsoleId_ = 0;
    std::string coreLibraryName_;
    std::string coreLibraryVersion_;
    std::string gameHash_;
    std::string hashFailureReason_;
    std::string userAgentClause_;
    std::string fullUserAgent_;

    int32_t sessionStatus_ = NativeAchievementsSnapshot::STATUS_DISABLED;
    std::string statusMessage_;
    bool hardcoreEnforced_ = false;
    bool hardcoreServerBlocked_ = false;
    bool transitionResetInProgress_ = false;
    bool externalSdkResetRequested_ = false;

    std::unordered_map<uint32_t, std::string> activeTrackers_;

    std::mutex httpMapMutex_;
    uint64_t nextRequestId_ = 0;
    uint64_t currentGeneration_ = 1;
    std::unordered_map<uint64_t, PendingHttpCallback> pendingRequests_;

    std::mutex eventMutex_;
    std::vector<NativeAchievementEvent> eventQueue_;
    bool hasUnnotifiedEvents_ = false;
};

} // namespace libretrodroid

#endif // LIBRETRODROID_ACHIEVEMENTS_H
