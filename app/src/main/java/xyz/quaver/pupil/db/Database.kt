package xyz.quaver.pupil.db

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.kodein.di.*

@Database(entities = [History::class, Bookmark::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
}

val databaseModule = DI.Module("database") {
    bind<AppDatabase>() with singleton { Room.databaseBuilder(instance<Application>(), AppDatabase::class.java, "pupil").build() }
}