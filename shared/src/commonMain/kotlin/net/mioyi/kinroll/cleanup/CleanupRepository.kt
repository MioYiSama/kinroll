package net.mioyi.kinroll.cleanup

import kotlinx.coroutines.flow.Flow
import net.mioyi.kinroll.data.PhotoDao
import net.mioyi.kinroll.data.PhotoSummary
import net.mioyi.kinroll.platform.PhotoKind
import net.mioyi.kinroll.search.EmbeddingIndex

data class DuplicateGroup(val photos: List<PhotoSummary>, val keepId: String)

class CleanupRepository(private val photoDao: PhotoDao, private val embeddingIndex: EmbeddingIndex) {
    fun screenshots(): Flow<List<PhotoSummary>> = photoDao.observeByKind(PhotoKind.SCREENSHOT.name)
    fun savedImages(): Flow<List<PhotoSummary>> = photoDao.observeByKind(PhotoKind.SAVED.name)
    fun badShots(): Flow<List<PhotoSummary>> = photoDao.observeBadShots()

    suspend fun duplicates(): List<DuplicateGroup> {
        val groups = DuplicateFinder.find(photoDao.duplicateCandidates())
        if (groups.isEmpty()) return emptyList()
        val photos = mutableMapOf<String, PhotoSummary>()
        for (batch in groups.flatMap { it.first }.chunked(500)) {
            for (photo in photoDao.summariesByIds(batch)) photos[photo.id] = photo
        }
        return groups.mapNotNull { (ids, keepId) ->
            val ordered = ids.mapNotNull(photos::get)
            if (ordered.size < 2) null else DuplicateGroup(ordered, if (keepId in photos) keepId else ordered.first().id)
        }
    }

    suspend fun onTrashed(ids: List<String>) {
        for (batch in ids.chunked(500)) {
            photoDao.deleteByIds(batch)
            embeddingIndex.remove(batch)
        }
    }
}
