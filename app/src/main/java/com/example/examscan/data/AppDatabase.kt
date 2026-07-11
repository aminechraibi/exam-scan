package com.example.examscan.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities=[ExamEntity::class, PaperEntity::class, PageEntity::class], version=1, exportSchema=true)
abstract class AppDatabase: RoomDatabase() {
    abstract fun examDao(): ExamDao
    abstract fun paperDao(): PaperDao
    abstract fun pageDao(): PageDao
    companion object { fun create(context: Context) = Room.databaseBuilder(context, AppDatabase::class.java, "exam_scan.db").build() }
}
