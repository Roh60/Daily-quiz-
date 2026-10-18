package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val publisher: String, // "Lucent GK", "Arihant Publications", "Drishti IAS"
    val edition: String,
    val description: String,
    val licensingStatus: String,
    val officialUrl: String,
    val totalChapters: Int,
    val totalQuestions: Int,
    val subjectsCovered: String
)

@Entity(tableName = "questions")
data class QuestionEntity(
    @PrimaryKey val id: String,
    val bookSource: String, // "Lucent GK", "Arihant Publications", "Drishti IAS"
    val subject: String,    // "History", "Indian Polity", "Geography", "General Science", "Indian Economy", "Static GK"
    val chapter: String,
    val examCategory: String, // "UPSC", "SSC", "State PSC", "Banking", "Railways", "Defense", "All Exams"
    val question: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctOptionIndex: Int, // 0..3
    val explanation: String,
    val difficulty: String, // "Easy", "Medium", "Hard"
    val licensingStatus: String,
    val isBookmarked: Boolean = false,
    val timesAttempted: Int = 0,
    val timesCorrect: Int = 0
)

@Entity(tableName = "quiz_history")
data class QuizHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val bookSource: String,
    val subject: String,
    val examCategory: String,
    val totalQuestions: Int,
    val correctCount: Int,
    val scorePercentage: Float,
    val durationSeconds: Long
)

@Entity(tableName = "saved_bookmarks")
data class SavedBookmarkEntity(
    @PrimaryKey val questionId: String,
    val savedAt: Long = System.currentTimeMillis()
)
