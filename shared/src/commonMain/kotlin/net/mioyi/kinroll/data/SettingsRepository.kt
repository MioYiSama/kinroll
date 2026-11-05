package net.mioyi.kinroll.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import net.mioyi.kinroll.model.DownloadSource

@Entity(tableName = "app_setting")
data class SettingEntity(@PrimaryKey val key: String, val value: String)

@Dao
interface SettingDao {
    @Query("SELECT value FROM app_setting WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)
}

class SettingsRepository(private val dao: SettingDao) {
    suspend fun downloadSource(): DownloadSource {
        val stored = dao.get(DOWNLOAD_SOURCE_KEY)
        return DownloadSource.entries.firstOrNull { it.name == stored }
            ?: DownloadSource.systemDefault()
    }

    suspend fun setDownloadSource(source: DownloadSource) {
        dao.put(SettingEntity(DOWNLOAD_SOURCE_KEY, source.name))
    }

    private companion object {
        const val DOWNLOAD_SOURCE_KEY = "download_source"
    }
}
