package net.mioyi.kinroll.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(entities = [PhotoEntity::class, SettingEntity::class], version = 1)
@ConstructedBy(KinrollDatabaseConstructor::class)
abstract class KinrollDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun settingDao(): SettingDao
}

@Suppress("KotlinNoActualForExpect")
expect object KinrollDatabaseConstructor : RoomDatabaseConstructor<KinrollDatabase> {
    override fun initialize(): KinrollDatabase
}
