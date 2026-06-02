package com.example.spinetrack.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.spinetrack.R

/**
 * Helper for posture alert notifications with three distinct sounds.
 */
object PostureNotificationHelper {
    private const val CHANNEL_VERSION = "v2"
    const val CHANNEL_LOW = "posture_alert_low_" + CHANNEL_VERSION
    const val CHANNEL_MEDIUM = "posture_alert_medium_" + CHANNEL_VERSION
    const val CHANNEL_HIGH = "posture_alert_high_" + CHANNEL_VERSION

    private val legacyChannelIds = listOf(
        "posture_alert_low",
        "posture_alert_medium",
        "posture_alert_high"
    )

    private val notificationCounter = java.util.concurrent.atomic.AtomicInteger(2000)

    /**
     * Ensure notification channels exist (Android O+).
     */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        legacyChannelIds.forEach { legacyId ->
            manager.deleteNotificationChannel(legacyId)
        }

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val lowSound = soundUri(context, R.raw.posture_alert_low)
        val mediumSound = soundUri(context, R.raw.posture_alert_medium)
        val highSound = soundUri(context, R.raw.posture_alert_high)

        val low = NotificationChannel(
            CHANNEL_LOW,
            context.getString(R.string.notif_channel_low),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_low_desc)
            setSound(lowSound, attributes)
        }

        val medium = NotificationChannel(
            CHANNEL_MEDIUM,
            context.getString(R.string.notif_channel_medium),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_medium_desc)
            setSound(mediumSound, attributes)
        }

        val high = NotificationChannel(
            CHANNEL_HIGH,
            context.getString(R.string.notif_channel_high),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_high_desc)
            setSound(highSound, attributes)
        }

        manager.createNotificationChannel(low)
        manager.createNotificationChannel(medium)
        manager.createNotificationChannel(high)
    }

    /**
     * Post a posture alert notification with a severity-specific sound.
     */
    fun notifyPostureAlert(
        context: Context,
        severity: PostureAlertSeverity,
        title: String,
        message: String
    ) {
        val channelId = when (severity) {
            PostureAlertSeverity.LOW -> CHANNEL_LOW
            PostureAlertSeverity.MEDIUM -> CHANNEL_MEDIUM
            PostureAlertSeverity.HIGH -> CHANNEL_HIGH
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setColor(ContextCompat.getColor(context, R.color.peach_primary))
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .build()

        val notificationId = notificationCounter.incrementAndGet()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    /**
     * Map ICP score to a severity level.
     */
    fun severityFromIcp(icp: Double): PostureAlertSeverity {
        return when {
            icp < 40.0 -> PostureAlertSeverity.HIGH
            icp < 60.0 -> PostureAlertSeverity.MEDIUM
            else -> PostureAlertSeverity.LOW
        }
    }

    private fun soundUri(context: Context, rawRes: Int): Uri {
        return Uri.parse("android.resource://${context.packageName}/$rawRes")
    }
}

/**
 * Severity levels for posture alerts.
 */
enum class PostureAlertSeverity {
    LOW,
    MEDIUM,
    HIGH
}
