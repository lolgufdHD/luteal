package fr.luteal.core.model

data class DuoSharingPreferences(
    val shareCycleDay: Boolean = false,
    val sharePeriodEstimate: Boolean = false,
    val shareMood: Boolean = false,
    val shareEnergy: Boolean = false,
    val shareSupportRequests: Boolean = false,
    val sharePillStatus: Boolean = false,
    /** Device-local like pill status: the Today countdown ring. */
    val shareCountdown: Boolean = false,
    // Shared journal (history). Like pill status these are device-local
    // grants: the tracker's device applies them when sealing the projection,
    // so the server needs no new grant fields and never sees the data.
    val shareHistoryFlow: Boolean = false,
    val shareHistoryEstimate: Boolean = false,
    val shareHistoryLevels: Boolean = false,
    val shareHistoryPill: Boolean = false,
    val historyMonths: Int = DEFAULT_HISTORY_MONTHS
) {
    val sharesAnyHistory: Boolean
        get() = shareHistoryFlow || shareHistoryEstimate || shareHistoryLevels || shareHistoryPill

    companion object {
        const val DEFAULT_HISTORY_MONTHS = 6
        val HISTORY_MONTH_OPTIONS = listOf(3, 6, 12)

        fun normalizeHistoryMonths(months: Int): Int =
            if (months in HISTORY_MONTH_OPTIONS) months else DEFAULT_HISTORY_MONTHS
    }
}

enum class DuoSharingField {
    CYCLE_DAY,
    PERIOD_ESTIMATE,
    MOOD,
    ENERGY,
    SUPPORT_REQUESTS,
    PILL_STATUS,
    COUNTDOWN,
    HISTORY_FLOW,
    HISTORY_ESTIMATE,
    HISTORY_LEVELS,
    HISTORY_PILL
}
