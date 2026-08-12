package info.movieshelf.ui.sync

import androidx.annotation.StringRes
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import info.movieshelf.R
import info.movieshelf.data.sync.SyncPhase
import info.movieshelf.data.sync.SyncProgress

/**
 * Fortschritt des Abgleichs in der Statusleiste.
 *
 * Der Abgleich dauert bei einer grossen Sammlung Minuten. Ohne Meldung muesste
 * man dafuer im Bildschirm stehen bleiben und zusehen; mit ihr laesst sich die
 * App verlassen und der Stand von oben ablesen.
 *
 * Die Meldung ist still und ohne Rang: sie soll ablesbar sein, nicht stoeren.
 * Der Kanal heisst deshalb auch nicht "Benachrichtigungen" — wer ihn abschaltet,
 * verliert nur die Anzeige, nie einen Abgleich.
 */
class SyncNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Abgleich",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Fortschritt beim Abgleich mit der Shelf"
                    setShowBadge(false)
                }
            )
        }
    }

    /**
     * Stand anzeigen.
     *
     * Fehlt die Erlaubnis für Meldungen, passiert schlicht nichts — der
     * Abgleich selbst haengt nicht daran und laeuft unveraendert weiter.
     */
    fun show(progress: SyncProgress) {
        if (progress.phase == SyncPhase.DONE) {
            clear()
            return
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sync_notification)
            .setContentTitle("Synchronisation")
            .setContentText(describe(progress))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            // Laufender Vorgang: nicht wegwischbar, solange er laeuft.
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (progress.total > 0) {
            builder.setProgress(progress.total, progress.current, false)
        } else {
            // Solange nicht feststeht, wie viel zu tun ist, ein laufender
            // Balken statt einer erfundenen Zahl.
            builder.setProgress(0, 0, true)
        }

        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }

    fun clear() {
        runCatching { manager.cancel(NOTIFICATION_ID) }
    }

    private fun describe(progress: SyncProgress): String {
        val phase = context.getString(progress.phase.labelRes)
        val subject = progress.subject?.takeIf { it.isNotBlank() }
        val counted = if (progress.total > 0) "$phase ${progress.current}/${progress.total}" else phase
        return if (subject == null) counted else "$counted · $subject"
    }

    private companion object {
        const val CHANNEL_ID = "sync_progress"
        const val NOTIFICATION_ID = 4711
    }
}

/**
 * Beschriftung einer Abgleich-Phase.
 *
 * Steht hier und nicht im Bildschirm, damit Anzeige und Meldung nicht
 * auseinanderlaufen.
 */
@get:StringRes
val SyncPhase.labelRes: Int
    get() = when (this) {
        SyncPhase.PUSH -> R.string.phase_push
        SyncPhase.PULL -> R.string.phase_pull
        SyncPhase.MEDIA -> R.string.phase_media
        SyncPhase.DONE -> R.string.phase_done
    }
