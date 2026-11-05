package net.mioyi.kinroll.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.mioyi.kinroll.model.DownloadSource

class SettingsRepositoryTest {
    @Test
    fun missingSourceUsesSystemDefaultWithoutPersistingIt() = runTest {
        val dao = InMemorySettingDao()
        val repository = SettingsRepository(dao)

        assertEquals(DownloadSource.systemDefault(), repository.downloadSource())
        assertNull(dao.get("download_source"))
    }

    @Test
    fun invalidStoredSourcesUseSystemDefaultWithoutOverwritingThem() = runTest {
        val dao = InMemorySettingDao()
        val repository = SettingsRepository(dao)

        for (invalid in listOf("", "unknown", "hugging_face", " HUGGING_FACE ")) {
            dao.put(SettingEntity("download_source", invalid))

            assertEquals(DownloadSource.systemDefault(), repository.downloadSource())
            assertEquals(invalid, dao.get("download_source"))
        }
    }

    @Test
    fun everySourcePersistsItsEnumNameAndRoundTripsThroughNewRepository() = runTest {
        val dao = InMemorySettingDao()

        for (source in DownloadSource.entries) {
            SettingsRepository(dao).setDownloadSource(source)

            assertEquals(source.name, dao.get("download_source"))
            assertEquals(source, SettingsRepository(dao).downloadSource())
        }
    }

    private class InMemorySettingDao : SettingDao {
        private val values = mutableMapOf<String, String>()

        override suspend fun get(key: String): String? = values[key]

        override suspend fun put(setting: SettingEntity) {
            values[setting.key] = setting.value
        }
    }
}
