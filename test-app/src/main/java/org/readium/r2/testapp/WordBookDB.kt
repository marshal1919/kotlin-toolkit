package org.readium.r2.testapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase


@Database(
    entities = [WordBook::class],
    version = 1,
    exportSchema = false
)
//@TypeConverters(HighlightConverters::class)
abstract class WordBookDB : RoomDatabase() {

    abstract fun wBookDao(): WordBookDao

    companion object {
        @Volatile
        private var INSTANCE: WordBookDB? = null

        fun getDatabase(context: Context): WordBookDB {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WordBookDB::class.java,
                    "wordbook_db"
                ).build()
                INSTANCE = instance
                return instance
            }
        }
    }
}
