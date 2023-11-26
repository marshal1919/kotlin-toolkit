package org.readium.r2.testapp

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wordbook")
data class WordBook (

    @ColumnInfo val word: String,
    @ColumnInfo val explain: String?
){
    @PrimaryKey(autoGenerate = true) var id: Int = 0
}