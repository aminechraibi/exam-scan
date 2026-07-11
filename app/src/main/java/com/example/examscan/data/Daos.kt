package com.example.examscan.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface ExamDao {
    @Query("SELECT * FROM exams ORDER BY createdAt DESC") fun observeAll(): Flow<List<ExamEntity>>
    @Query("SELECT * FROM exams WHERE id=:id") fun observe(id: Long): Flow<ExamEntity?>
    @Query("SELECT * FROM exams WHERE id=:id") suspend fun get(id: Long): ExamEntity?
    @Insert suspend fun insert(exam: ExamEntity): Long
    @Update suspend fun update(exam: ExamEntity)
    @Delete suspend fun delete(exam: ExamEntity)
}
@Dao interface PaperDao {
    @Transaction @Query("SELECT * FROM papers WHERE examId=:examId ORDER BY createdAt DESC") fun observeForExam(examId: Long): Flow<List<PaperWithPages>>
    @Transaction @Query("SELECT * FROM papers WHERE examId=:examId ORDER BY paperNumber ASC") suspend fun getForExam(examId: Long): List<PaperWithPages>
    @Query("SELECT COALESCE(MAX(paperNumber),0)+1 FROM papers WHERE examId=:examId") suspend fun nextNumber(examId: Long): Int
    @Query("SELECT * FROM papers WHERE id=:id") suspend fun get(id: Long): PaperEntity?
    @Insert suspend fun insert(paper: PaperEntity): Long
    @Update suspend fun update(paper: PaperEntity)
    @Delete suspend fun delete(paper: PaperEntity)
}
@Dao interface PageDao {
    @Query("SELECT * FROM pages WHERE paperId=:paperId ORDER BY pageNumber") fun observeForPaper(paperId: Long): Flow<List<PageEntity>>
    @Query("SELECT * FROM pages WHERE paperId=:paperId ORDER BY pageNumber") suspend fun getForPaper(paperId: Long): List<PageEntity>
    @Query("SELECT * FROM pages WHERE id=:id") suspend fun get(id: Long): PageEntity?
    @Insert suspend fun insert(page: PageEntity): Long
    @Update suspend fun update(page: PageEntity)
    @Delete suspend fun delete(page: PageEntity)
}
