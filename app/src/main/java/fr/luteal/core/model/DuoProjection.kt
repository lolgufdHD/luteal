package fr.luteal.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The projection the tracker shares with their partner.
 *
 * This is the plaintext sealed inside `DuoView.payload`. It is composed on the
 * tracker's device, which applies the sharing grants before encrypting: a field
 * the tracker has not granted is absent here because it was never encrypted,
 * not because the server filtered it afterwards. The server therefore cannot
 * leak what it never received.
 *
 * Private notes, symptoms, pain and biomarkers have no representation here at
 * all, by construction rather than by policy. Day-level flow, mood/energy and
 * pill days appear only inside [history], and only for the categories the
 * tracker explicitly granted on their own device.
 */
@Serializable
data class DuoProjection(
    /** Schema version of the sealed payload, so clients can migrate it. */
    @SerialName("v") val version: Int = CURRENT_VERSION,
    @SerialName("cycle_day") val cycleDay: Int? = null,
    @SerialName("period_estimate") val periodEstimate: SharedEstimate? = null,
    @SerialName("mood") val mood: SharedLevel? = null,
    @SerialName("energy") val energy: SharedLevel? = null,
    @SerialName("pill_status") val pillStatus: SharedPillStatus? = null,
    /**
     * Shared journal. Optional and additive: older clients ignore it
     * (ContractJson has ignoreUnknownKeys), so the payload version stays 1.
     */
    @SerialName("history") val history: SharedHistory? = null,
    /** The Today countdown ring. Optional and additive like [history]. */
    @SerialName("countdown") val countdown: SharedCountdown? = null
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class SharedEstimate(
    @SerialName("window_start") val windowStart: String,
    @SerialName("window_end") val windowEnd: String,
    @SerialName("central_date") val centralDate: String? = null,
    @SerialName("cycle_count") val cycleCount: Int? = null,
    @SerialName("variability_days") val variabilityDays: Int? = null
)

@Serializable
data class SharedLevel(
    @SerialName("date") val date: String,
    @SerialName("level") val level: Int
)

/**
 * A window of the tracker's journal, reduced to the categories they granted.
 * Each list is null when its category is not granted. Notes, symptoms, pain
 * and biomarkers have no representation here.
 */
@Serializable
data class SharedHistory(
    /** First shared date (inclusive), ISO-8601. Nothing before it is sent. */
    @SerialName("from") val from: String,
    /** Last shared date (inclusive), normally the tracker's "today". */
    @SerialName("to") val to: String,
    @SerialName("months") val months: Int,
    @SerialName("cycle_starts") val cycleStarts: List<String>? = null,
    @SerialName("flow") val flow: List<SharedFlowDay>? = null,
    @SerialName("estimate") val estimate: SharedEstimate? = null,
    @SerialName("levels") val levels: List<SharedDayLevels>? = null,
    @SerialName("pill") val pill: SharedPillHistory? = null
)

@Serializable
data class SharedFlowDay(
    @SerialName("date") val date: String,
    /** One of spotting, light, medium, heavy. */
    @SerialName("intensity") val intensity: String
)

@Serializable
data class SharedDayLevels(
    @SerialName("date") val date: String,
    @SerialName("mood") val mood: Int? = null,
    @SerialName("energy") val energy: Int? = null
)

@Serializable
data class SharedPillHistory(
    /** Start of the pack that contains [SharedHistory.from], not the original regimen start. */
    @SerialName("anchor") val anchor: String,
    @SerialName("active_days") val activeDays: Int,
    @SerialName("pause_days") val pauseDays: Int,
    @SerialName("taken") val takenDates: List<String> = emptyList()
)

/**
 * The tracker's countdown ring: from the last period (or pill pause) to the
 * next. Day numbers are 1-based from [anchor]. Only flow intensities and the
 * estimate are included, never notes or symptoms.
 */
@Serializable
data class SharedCountdown(
    /** The tracker's "today" when this was composed. */
    @SerialName("date") val date: String,
    @SerialName("anchor") val anchor: String,
    @SerialName("length") val length: Int,
    /** One of pill_pack, personal, typical. */
    @SerialName("basis") val basis: String,
    @SerialName("segments") val segments: List<SharedCountdownSegment>,
    @SerialName("flow") val flow: List<SharedRingFlow> = emptyList(),
    @SerialName("estimated_days") val estimatedDays: List<Int> = emptyList(),
    @SerialName("window_start") val windowStart: String? = null,
    @SerialName("window_end") val windowEnd: String? = null
)

@Serializable
data class SharedCountdownSegment(
    /** One of bleed, follicular, ovulation, luteal, pill_active. */
    @SerialName("phase") val phase: String,
    @SerialName("first") val firstDay: Int,
    @SerialName("last") val lastDay: Int
)

@Serializable
data class SharedRingFlow(
    @SerialName("day") val day: Int,
    /** One of spotting, light, medium, heavy. */
    @SerialName("intensity") val intensity: String
)

@Serializable
data class SharedPillStatus(
    @SerialName("date") val date: String,
    @SerialName("is_active_day") val isActiveDay: Boolean,
    @SerialName("day_in_phase") val dayInPhase: Int,
    @SerialName("total_phase_days") val totalPhaseDays: Int,
    @SerialName("active_days") val activeDays: Int,
    @SerialName("pause_days") val pauseDays: Int,
    @SerialName("is_taken") val isTaken: Boolean = false,
    @SerialName("reminder_time") val reminderTime: String? = null
)
