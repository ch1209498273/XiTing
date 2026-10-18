package com.lujinyu.xiting

internal enum class SupportRepair { OVERLAY, MEDIA_ACCESS, NOTIFICATIONS, PLAYER, START_ASSISTANT, BATTERY, APP_DETAILS }
internal data class SupportSnapshot(
    val overlay: Boolean, val mediaAccess: Boolean, val notifications: Boolean,
    val batteryExempt: Boolean, val session: Boolean, val playing: Boolean,
    val pause: Boolean, val seek: Boolean, val assistant: Boolean, val black: Boolean
)
internal fun SupportSnapshot.suggestedRepair(): SupportRepair = when {
    !overlay -> SupportRepair.OVERLAY
    !mediaAccess -> SupportRepair.MEDIA_ACCESS
    !session || !playing -> SupportRepair.PLAYER
    !assistant -> SupportRepair.START_ASSISTANT
    !notifications -> SupportRepair.NOTIFICATIONS
    !batteryExempt -> SupportRepair.BATTERY
    else -> SupportRepair.APP_DETAILS
}
internal fun firstSuccessConfirmed(sound: Boolean?, returned: Boolean?): Boolean = sound == true && returned == true
