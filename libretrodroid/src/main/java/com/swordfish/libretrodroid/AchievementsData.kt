package com.swordfish.libretrodroid

data class AchievementsConfig(
    val enabled: Boolean,
    val username: String,
    val token: String,
    val consoleId: Int,
    val hardcoreEnabled: Boolean,
    val encoreEnabled: Boolean,
    val unofficialEnabled: Boolean,
)

class AchievementEvent(
    val type: Int,
    val id: Int,
    val title: String,
    val description: String,
    val badgeUrl: String,
    val measuredProgress: String,
    val measuredPercent: Float,
    val points: Int,
    val totalPoints: Int,
    val unlockedCount: Int,
    val totalCount: Int,
    val hardcore: Boolean,
    val rarity: Float,
    val achievementType: Int,
) {
    companion object {
        const val TYPE_LOGIN_SUCCEEDED = 1
        const val TYPE_LOGIN_FAILED = 2
        const val TYPE_GAME_IDENTIFYING = 3
        const val TYPE_GAME_LOADED = 4
        const val TYPE_GAME_UNSUPPORTED = 5
        const val TYPE_ACHIEVEMENT_TRIGGERED = 6
        const val TYPE_PROGRESS_SHOW = 7
        const val TYPE_PROGRESS_HIDE = 8
        const val TYPE_CHALLENGE_SHOW = 9
        const val TYPE_CHALLENGE_HIDE = 10
        const val TYPE_GAME_COMPLETED = 11
        const val TYPE_SERVER_ERROR = 12
        const val TYPE_DISCONNECTED = 13
        const val TYPE_RECONNECTED = 14
    }
}

class AchievementDef(
    val id: Int,
    val title: String,
    val description: String,
    val badgeUrl: String,
    val badgeLockedUrl: String,
    val points: Int,
    val state: Int,
    val category: Int,
    val bucket: Int,
    val unlockedMask: Int,
    val unlockTimeSeconds: Long,
    val measuredProgress: String,
    val measuredPercent: Float,
    val rarity: Float,
    val rarityHardcore: Float,
    val type: Int,
) {
    val isUnlockedSoftcore: Boolean
        get() = (unlockedMask and UNLOCKED_SOFTCORE) != 0

    val isUnlockedHardcore: Boolean
        get() = (unlockedMask and UNLOCKED_HARDCORE) != 0

    val isUnlocked: Boolean
        get() = unlockedMask != 0

    companion object {
        const val UNLOCKED_SOFTCORE = 1
        const val UNLOCKED_HARDCORE = 2

        const val TYPE_STANDARD = 0
        const val TYPE_MISSABLE = 1
        const val TYPE_PROGRESSION = 2
        const val TYPE_WIN = 3

        const val BUCKET_ACTIVE_CHALLENGE = 6
        const val BUCKET_ALMOST_THERE = 7

        const val CATEGORY_CORE = 1
        const val CATEGORY_UNOFFICIAL = 2
    }
}

class AchievementsSnapshot(
    val sessionStatus: Int,
    val statusMessage: String,
    val gameId: Int,
    val gameTitle: String,
    val gameBadgeUrl: String,
    val richPresence: String,
    val totalCount: Int,
    val unlockedCount: Int,
    val totalPoints: Int,
    val unlockedPoints: Int,
    val hardcoreActive: Boolean,
    val achievements: Array<AchievementDef>,
) {
    companion object {
        const val STATUS_DISABLED = 0
        const val STATUS_SIGNING_IN = 1
        const val STATUS_IDENTIFYING = 2
        const val STATUS_LOADED = 3
        const val STATUS_UNSUPPORTED = 4
        const val STATUS_LOGIN_FAILED = 5
        const val STATUS_LOAD_ERROR = 6
    }
}
