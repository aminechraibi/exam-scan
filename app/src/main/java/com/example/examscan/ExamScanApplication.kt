package com.example.examscan

import android.app.Application
import com.example.examscan.data.AppDatabase
import com.example.examscan.data.ExamRepository
import com.example.examscan.diagnostics.Diagnostics

class ExamScanApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { ExamRepository(this, database.examDao(), database.paperDao(), database.pageDao()) }
    override fun onCreate() { super.onCreate(); Diagnostics.initialize(this) }
}
