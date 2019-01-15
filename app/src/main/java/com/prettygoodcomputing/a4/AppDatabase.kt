package com.prettygoodcomputing.a4

import android.arch.persistence.db.SupportSQLiteDatabase
import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.content.Context
import android.os.AsyncTask

@Database(entities = [FileItem::class, FolderItem::class], version = 1, exportSchema  = false)
abstract class AppDatabase: RoomDatabase() {

    abstract fun fileItemDao(): FileItemDao
    abstract fun folderItemDao(): FolderItemDao

    companion object {
        private var instance: AppDatabase? = null

        @Synchronized
        @JvmStatic fun getInstance(context: Context): AppDatabase {
            if (instance == null) {
                instance = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "AppDatabase")
                    .fallbackToDestructiveMigration()
                    .addCallback(FILE_ITEM_CALLBACK)
                    .build()
            }
            return instance!!
        }

        private val FILE_ITEM_CALLBACK = object: RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                PopulateDatabaseAsyncTask(instance!!).execute()
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
            }
        }

        private class PopulateDatabaseAsyncTask(db: AppDatabase): AsyncTask<Void?, Void?, Void?>() {
            private val fileItemDao = db.fileItemDao()

            override fun doInBackground(vararg params: Void?): Void? {
                return null
            }
        }
    }
}
