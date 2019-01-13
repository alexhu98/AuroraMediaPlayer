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
//                PopulateDatabaseAsyncTask(instance!!).execute()
            }
        }

        private class PopulateDatabaseAsyncTask(db: AppDatabase): AsyncTask<Void?, Void?, Void?>() {
            private val fileItemDao = db.fileItemDao()

            override fun doInBackground(vararg params: Void?): Void? {
                fileItemDao.insert(FileItem(name = "a.mp4", folder = "/x", fileSize = 1231L, lastModified = 1234561L))
                fileItemDao.insert(FileItem(name = "b.mp4", folder = "/x", fileSize = 1232L, lastModified = 1234562L))
                fileItemDao.insert(FileItem(name = "c.mp4", folder = "/x", fileSize = 1233L, lastModified = 1234563L))
                fileItemDao.insert(FileItem(name = "d.mp4", folder = "/x", fileSize = 1234L, lastModified = 1234564L))
                fileItemDao.insert(FileItem(name = "e.mp4", folder = "/t", fileSize = 1235L, lastModified = 1234565L))
                fileItemDao.insert(FileItem(name = "f.mp4", folder = "/x", fileSize = 1231L, lastModified = 1234561L))
                fileItemDao.insert(FileItem(name = "g.mp4", folder = "/x", fileSize = 1232L, lastModified = 1234562L))
                fileItemDao.insert(FileItem(name = "h.mp4", folder = "/x", fileSize = 1233L, lastModified = 1234563L))
                fileItemDao.insert(FileItem(name = "i.mp4", folder = "/x", fileSize = 1234L, lastModified = 1234564L))
                fileItemDao.insert(FileItem(name = "j.mp4", folder = "/t", fileSize = 1235L, lastModified = 1234565L))
                fileItemDao.insert(FileItem(name = "k.mp4", folder = "/x", fileSize = 1231L, lastModified = 1234561L))
                fileItemDao.insert(FileItem(name = "l.mp4", folder = "/x", fileSize = 1232L, lastModified = 1234562L))
                fileItemDao.insert(FileItem(name = "m.mp4", folder = "/x", fileSize = 1233L, lastModified = 1234563L))
                fileItemDao.insert(FileItem(name = "n.mp4", folder = "/x", fileSize = 1234L, lastModified = 1234564L))
                fileItemDao.insert(FileItem(name = "o.mp4", folder = "/t", fileSize = 1235L, lastModified = 1234565L))
                fileItemDao.insert(FileItem(name = "p.mp4", folder = "/x", fileSize = 1231L, lastModified = 1234561L))
                fileItemDao.insert(FileItem(name = "q.mp4", folder = "/x", fileSize = 1232L, lastModified = 1234562L))
                fileItemDao.insert(FileItem(name = "r.mp4", folder = "/x", fileSize = 1233L, lastModified = 1234563L))
                fileItemDao.insert(FileItem(name = "s.mp4", folder = "/x", fileSize = 1234L, lastModified = 1234564L))
                fileItemDao.insert(FileItem(name = "t.mp4", folder = "/t", fileSize = 1235L, lastModified = 1234565L))
                fileItemDao.insert(FileItem(name = "u.mp4", folder = "/x", fileSize = 1231L, lastModified = 1234561L))
                fileItemDao.insert(FileItem(name = "v.mp4", folder = "/x", fileSize = 1232L, lastModified = 1234562L))
                fileItemDao.insert(FileItem(name = "w.mp4", folder = "/x", fileSize = 1233L, lastModified = 1234563L))
                fileItemDao.insert(FileItem(name = "x.mp4", folder = "/x", fileSize = 1234L, lastModified = 1234564L))
                fileItemDao.insert(FileItem(name = "y.mp4", folder = "/t", fileSize = 1235L, lastModified = 1234565L))
                fileItemDao.insert(FileItem(name = "z.mp4", folder = "/x", fileSize = 1231L, lastModified = 1234561L))
                return null
            }
        }
    }
}
