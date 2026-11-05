package net.mioyi.kinroll.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val SUMMARY_COLUMNS =
    "id, uri, takenAt, addedAt, sizeBytes, width, height, kind, category, badShotReasons"

@Dao
interface PhotoDao {
    @Query("SELECT COUNT(*) FROM photo")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM photo WHERE indexVersion >= :version")
    fun observeIndexedCount(version: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM photo WHERE kind = :kind")
    fun observeKindCount(kind: String): Flow<Int>

    @Query("SELECT id, modifiedAt, sizeBytes FROM photo ORDER BY COALESCE(takenAt, addedAt) DESC")
    suspend fun syncInfo(): List<SyncInfo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PhotoEntity>)

    @Query("DELETE FROM photo WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT id FROM photo WHERE indexVersion < :version ORDER BY COALESCE(takenAt, addedAt) DESC LIMIT :limit")
    suspend fun pendingIds(version: Int, limit: Int): List<String>

    @Query("SELECT * FROM photo WHERE id = :id")
    suspend fun getById(id: String): PhotoEntity?

    @Update
    suspend fun update(entity: PhotoEntity)

    @Query("SELECT $SUMMARY_COLUMNS FROM photo ORDER BY COALESCE(takenAt, addedAt) DESC")
    fun observeSummaries(): Flow<List<PhotoSummary>>

    @Query("SELECT $SUMMARY_COLUMNS FROM photo WHERE kind = :kind ORDER BY COALESCE(takenAt, addedAt) DESC")
    fun observeByKind(kind: String): Flow<List<PhotoSummary>>

    @Query("SELECT $SUMMARY_COLUMNS FROM photo WHERE isBadShot = 1 ORDER BY COALESCE(takenAt, addedAt) DESC")
    fun observeBadShots(): Flow<List<PhotoSummary>>

    @Query("SELECT $SUMMARY_COLUMNS FROM photo WHERE category = :category ORDER BY COALESCE(takenAt, addedAt) DESC")
    fun observeByCategory(category: String): Flow<List<PhotoSummary>>

    @Query(
        "SELECT category, COUNT(*) AS count FROM photo WHERE category IS NOT NULL " +
            "GROUP BY category ORDER BY MAX(COALESCE(takenAt, addedAt)) DESC",
    )
    fun observeCategoryCounts(): Flow<List<CategoryCount>>

    @Query("SELECT id FROM photo WHERE ocrText LIKE :pattern ORDER BY COALESCE(takenAt, addedAt) DESC LIMIT :limit")
    suspend fun searchOcr(pattern: String, limit: Int): List<String>

    @Query("SELECT id, clipEmbedding FROM photo WHERE clipEmbedding IS NOT NULL ORDER BY COALESCE(takenAt, addedAt) DESC")
    suspend fun allEmbeddings(): List<IdEmbedding>

    @Query(
        "SELECT id, uri, dHash, clipEmbedding, blurScore, width, height, takenAt, sizeBytes FROM photo " +
            "WHERE dHash IS NOT NULL AND clipEmbedding IS NOT NULL ORDER BY COALESCE(takenAt, addedAt) DESC",
    )
    suspend fun duplicateCandidates(): List<DupRow>

    @Query("SELECT * FROM photo WHERE id IN (:ids) ORDER BY COALESCE(takenAt, addedAt) DESC")
    suspend fun getByIds(ids: List<String>): List<PhotoEntity>

    @Query("SELECT $SUMMARY_COLUMNS FROM photo WHERE id IN (:ids)")
    suspend fun summariesByIds(ids: List<String>): List<PhotoSummary>
}
