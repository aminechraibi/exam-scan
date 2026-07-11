package com.example.examscan.data

import androidx.room.*

@Entity(tableName = "exams")
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val examDate: String,
    val pagesPerPaper: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "papers", foreignKeys = [ForeignKey(entity = ExamEntity::class, parentColumns = ["id"], childColumns = ["examId"], onDelete = ForeignKey.CASCADE)], indices=[Index("examId")])
data class PaperEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val examId: Long,
    val paperNumber: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "pages", foreignKeys = [ForeignKey(entity = PaperEntity::class, parentColumns = ["id"], childColumns = ["paperId"], onDelete = ForeignKey.CASCADE)], indices=[Index("paperId")])
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paperId: Long,
    val pageNumber: Int,
    val originalPath: String,
    val labeledPath: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class PaperWithPages(
    @Embedded val paper: PaperEntity,
    @Relation(parentColumn = "id", entityColumn = "paperId") val pages: List<PageEntity>
)
