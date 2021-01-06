package com.ayg.advancevoiceassistant

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Assistant::class], version = 1, exportSchema = false)
abstract class AssistantDatabase : RoomDatabase() {

    abstract val assistantDao : AssistantDao

    companion object
    {
        // a value of a volatile variable is never to be cached and all writes and reads will be done to the main memory
        @Volatile
        private var INSTANCE: AssistantDatabase? = null

        fun getInstance(context : Context) : AssistantDatabase {
            synchronized(this){
                var instance = INSTANCE

                if(instance == null)
                {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        AssistantDatabase::class.java,
                        "assistant_messages_database"
                    )
                        .fallbackToDestructiveMigration()
                        .build()
                    INSTANCE = instance
                }
                return instance
            }
        }
    }
}