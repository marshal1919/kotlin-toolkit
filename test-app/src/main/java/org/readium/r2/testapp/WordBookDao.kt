package org.readium.r2.testapp

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WordBookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend  fun insertWord(word: WordBook)

    @Delete
    suspend fun deleteWord(word: WordBook)

    @Query("SELECT * FROM wordbook")
    suspend fun getAllWords(): List<WordBook>

    @Query("SELECT word FROM wordbook")
    suspend fun getAllWordsEn(): List<String>
}