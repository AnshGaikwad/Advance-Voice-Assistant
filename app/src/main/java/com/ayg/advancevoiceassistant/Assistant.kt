package com.ayg.advancevoiceassistant

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assistant_messages_table")
data class Assistant(

    @PrimaryKey(autoGenerate = true)
    var assistantId: Long = 0L,

    @ColumnInfo(name = "assistant_messages")
    var message: String = "DEFAULT_MESSAGE",

    @ColumnInfo(name = "type_of_message")
    var type: Int = -1,
) {

}