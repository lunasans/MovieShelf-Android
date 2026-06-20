package at.neuhaus.movieshelf.data.local.db

import androidx.room.*

@Dao
interface MovieDao {

    @Query("SELECT * FROM movies WHERE boxsetParentId IS NULL AND (inCollection = 1 OR inCollection IS NULL)")
    suspend fun getAllMovies(): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE id = :id LIMIT 1")
    suspend fun getMovieById(id: Int): MovieEntity?

    @Query("""
        SELECT * FROM movies
        WHERE boxsetParentId IS NULL
          AND (inCollection = 1 OR inCollection IS NULL)
          AND (title LIKE '%' || :query || '%'
               OR director LIKE '%' || :query || '%'
               OR genre LIKE '%' || :query || '%')
    """)
    suspend fun searchMovies(query: String): List<MovieEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovies(movies: List<MovieEntity>)

    /** Ersetzt den gesamten Cache atomar (verhindert leeren Cache bei abgebrochenem Insert). */
    @Transaction
    suspend fun replaceAll(movies: List<MovieEntity>) {
        deleteAll()
        insertMovies(movies)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovie(movie: MovieEntity)

    @Query("UPDATE movies SET isWatched = :isWatched WHERE id = :id")
    suspend fun updateWatched(id: Int, isWatched: Boolean)

    @Query("DELETE FROM movies WHERE cachedAt < :cutoff")
    suspend fun deleteOldCache(cutoff: Long)

    @Query("DELETE FROM movies")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM movies WHERE boxsetParentId IS NULL AND (inCollection = 1 OR inCollection IS NULL)")
    suspend fun getMovieCount(): Int

    @Query("SELECT MAX(cachedAt) FROM movies")
    suspend fun getLastCacheTime(): Long?

    @Query("""
        SELECT DISTINCT genre FROM movies
        WHERE genre IS NOT NULL AND genre != '' AND boxsetParentId IS NULL AND (inCollection = 1 OR inCollection IS NULL)
        ORDER BY genre
    """)
    suspend fun getDistinctGenres(): List<String>

    @Query("""
        SELECT DISTINCT director FROM movies
        WHERE director IS NOT NULL AND director != '' AND boxsetParentId IS NULL AND (inCollection = 1 OR inCollection IS NULL)
        ORDER BY director
    """)
    suspend fun getDistinctDirectors(): List<String>

    @Query("SELECT MIN(year) FROM movies WHERE year IS NOT NULL AND boxsetParentId IS NULL AND (inCollection = 1 OR inCollection IS NULL)")
    suspend fun getMinYear(): Int?

    @Query("SELECT MAX(year) FROM movies WHERE year IS NOT NULL AND boxsetParentId IS NULL AND (inCollection = 1 OR inCollection IS NULL)")
    suspend fun getMaxYear(): Int?
}
