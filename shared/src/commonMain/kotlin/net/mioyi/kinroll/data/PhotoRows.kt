package net.mioyi.kinroll.data

data class PhotoSummary(
    val id: String,
    val uri: String,
    val takenAt: Long?,
    val addedAt: Long?,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val kind: String,
    val category: String?,
    val badShotReasons: Int,
)

data class SyncInfo(val id: String, val modifiedAt: Long?, val sizeBytes: Long)

data class CategoryCount(val category: String, val count: Int)

class IdEmbedding(val id: String, val clipEmbedding: ByteArray)

class DupRow(
    val id: String,
    val uri: String,
    val dHash: Long,
    val clipEmbedding: ByteArray,
    val blurScore: Float?,
    val width: Int,
    val height: Int,
    val takenAt: Long?,
    val sizeBytes: Long,
)
