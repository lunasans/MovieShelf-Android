package at.neuhaus.movieshelf.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import at.neuhaus.movieshelf.MovieShelfApplication
import at.neuhaus.movieshelf.data.local.db.AppMode
import at.neuhaus.movieshelf.data.local.db.SettingKeys
import at.neuhaus.movieshelf.data.local.db.SyncClock
import java.util.concurrent.TimeUnit

/**
 * Abgleich im Hintergrund.
 *
 * Anders als der Abgleich von Hand läuft dieser **ohne Vorschau** — es ist
 * niemand da, der etwas bestätigen könnte. Das ist vertretbar, weil keine der
 * beteiligten Operationen überraschend löscht: entfernt wird nur, was der
 * Nutzer selbst als gelöscht markiert hat oder was der Server als gelöscht
 * meldet. Ein Spiegeln, das Unterschiede durch Löschen auflöst, gibt es nicht.
 *
 * Was der Hintergrundlauf **nicht** kann, ist Rückfragen. Deshalb hinterlässt
 * er sein Ergebnis in den Einstellungen, damit die Abgleich-Seite es beim
 * nächsten Öffnen zeigen kann — stille Konflikte wären sonst unsichtbar.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MovieShelfApplication
        val settings = app.database.settingDao()

        // Ohne Shelf gibt es nichts abzugleichen.
        val mode = AppMode.from(settings.get(SettingKeys.MODE))
        if (mode != AppMode.SHELF) return Result.success()

        return try {
            val result = app.syncEngine.runFullSync()
            val lists = app.listSyncEngine.sync()

            settings.put(SettingKeys.LAST_BACKGROUND_SYNC, SyncClock.now())
            settings.put(
                SettingKeys.LAST_BACKGROUND_RESULT,
                summarize(result, lists)
            )

            // Fehler einzelner Zeilen sind kein Grund für einen neuen Anlauf:
            // sie bleiben vorgemerkt und gehen beim nächsten Lauf erneut raus.
            Result.success()
        } catch (e: Exception) {
            settings.put(SettingKeys.LAST_BACKGROUND_SYNC, SyncClock.now())
            settings.put(SettingKeys.LAST_BACKGROUND_RESULT, "Fehlgeschlagen: ${e.message}")
            // Erneut versuchen — meist ist nur das Netz weg.
            Result.retry()
        }
    }

    private fun summarize(result: SyncResult, lists: ListSyncResult): String {
        val parts = buildList {
            if (result.push.total > 0) add("${result.push.total} hochgeladen")
            if (result.pull.applied > 0) add("${result.pull.applied} übernommen")
            if (result.pull.deleted > 0) add("${result.pull.deleted} entfernt")
            if (result.pull.skipped > 0) add("${result.pull.skipped} lokal behalten")
            if (result.artworkStored > 0) add("${result.artworkStored} Bilder geholt")
            if (lists.itemsAdded > 0) add("${lists.itemsAdded} Listen-Einträge ergänzt")
            val errors = result.errors.size + lists.errors.size
            if (errors > 0) add("$errors Fehler")
        }
        return if (parts.isEmpty()) "Nichts zu tun" else parts.joinToString(", ")
    }

    companion object {
        private const val WORK_NAME = "movieshelf_sync"

        /**
         * Regelmäßigen Abgleich einrichten.
         *
         * Zwölf Stunden statt stündlich: eine Filmsammlung ändert sich selten,
         * und häufigere Läufe kosten Akku und Datenvolumen ohne Gegenwert.
         * [ExistingPeriodicWorkPolicy.KEEP] lässt einen bereits geplanten Lauf
         * unangetastet, damit ein App-Start den Rhythmus nicht zurücksetzt.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Nach dem Wechsel in den eigenständigen Betrieb abbestellen. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
