package info.movieshelf.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Lokale Sammlung der App.
 *
 * Version 7 wechselt vom reinen Lese-Cache zur führenden Datenhaltung: jede
 * Zeile hat eine eigene lokale ID neben der Server-ID und trägt ihren
 * Abgleich-Zustand mit sich (siehe [MovieEntity]).
 *
 * Der Sprung von 6 auf 7 wird bewusst nicht migriert. Der alte Bestand war
 * reiner Cache und enthielt nichts, was nicht auch auf dem Server liegt — eine
 * Migration könnte nur halbgare Zwischenzustände erzeugen. Stattdessen wird die
 * Datei verworfen und der erste Start danach holt einen Vollstand.
 *
 * Version 11 wird dagegen migriert: dort kommt nur eine Spalte hinzu, und der
 * Bestand ist inzwischen führend — ihn zu verwerfen hiesse, lokal angelegte
 * Filme und alle heruntergeladenen Bilder wegzuwerfen.
 */
@Database(
    entities = [
        MovieEntity::class,
        ActorEntity::class,
        FilmActorCrossRef::class,
        ListEntity::class,
        ListItemEntity::class,
        ListItemTombstoneEntity::class,
        SeasonEntity::class,
        EpisodeEntity::class,
        ExternalMovieEntity::class,
        SettingEntity::class,
        PendingUploadEntity::class
    ],
    version = 13,
    exportSchema = false
)
abstract class MovieShelfDatabase : RoomDatabase() {

    abstract fun movieDao(): MovieDao
    abstract fun actorDao(): ActorDao
    abstract fun listDao(): ListDao
    abstract fun seriesDao(): SeriesDao
    abstract fun externalMovieDao(): ExternalMovieDao
    abstract fun settingDao(): SettingDao
    abstract fun pendingUploadDao(): PendingUploadDao

    companion object {
        @Volatile
        private var INSTANCE: MovieShelfDatabase? = null

        /**
         * Neue Spalte fuer den zuletzt vom Server bestaetigten "gesehen"-Stand.
         *
         * Vorhandene Zeilen bekommen ihren aktuellen Wert eingetragen: was vor
         * dieser Fassung in der Datenbank stand, kam vom Server oder wurde
         * direkt dorthin gemeldet. Ohne diesen Schritt galte die ganze
         * Sammlung als offene Markierung und der naechste Abgleich schaltete
         * sie reihenweise um.
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE movies ADD COLUMN syncedWatched INTEGER")
                db.execSQL("UPDATE movies SET syncedWatched = isWatched")
            }
        }

        /**
         * Eigene Sternbewertung und ihr zuletzt bestaetigter Stand.
         *
         * Beide bleiben leer: vor dieser Fassung kannte die App keine
         * Bewertungen, es gibt also nichts zu uebernehmen. Da beide Spalten
         * gleich (naemlich `NULL`) sind, gilt keine Zeile als offene
         * Bewertung und der naechste Abgleich schickt nichts Ungewolltes los.
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE movies ADD COLUMN userRating INTEGER")
                db.execSQL("ALTER TABLE movies ADD COLUMN syncedUserRating INTEGER")
            }
        }

        /**
         * Gesehen-Stand je Folge.
         *
         * Beide Spalten starten auf 0 und damit gleich: keine Folge gilt als
         * offene Markierung, der naechste Abgleich schickt also nichts los,
         * was der Nutzer nie angetippt hat. Der naechste Pull traegt den
         * Serverstand ein.
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE episodes ADD COLUMN isWatched INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE episodes ADD COLUMN syncedWatched INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): MovieShelfDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    MovieShelfDatabase::class.java,
                    "movieshelf.db"
                )
                    .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                    .fallbackToDestructiveMigration(true)
                    // Ohne diesen Schalter bleiben die Fremdschlüssel von
                    // Besetzung, Staffeln und Listeninhalten wirkungslos und
                    // gelöschte Filme hinterlassen verwaiste Zeilen.
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
