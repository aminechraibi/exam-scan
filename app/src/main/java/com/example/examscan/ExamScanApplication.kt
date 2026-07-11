package com.example.examscan

import android.app.Application
import com.example.examscan.data.AppDatabase
import com.example.examscan.data.ExamRepository

class ExamScanApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { ExamRepository(this, database.examDao(), database.paperDao(), database.pageDao()) }
}
