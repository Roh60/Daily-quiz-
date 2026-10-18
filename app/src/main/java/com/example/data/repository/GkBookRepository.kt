package com.example.data.repository

import android.content.Context
import com.example.data.local.BookEntity
import com.example.data.local.GkSwalDatabase
import com.example.data.local.QuestionEntity
import com.example.data.local.QuizHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class GkBookRepository(private val database: GkSwalDatabase) {

    constructor(context: Context) : this(GkSwalDatabase.getInstance(context))

    private val bookDao = database.bookDao()
    private val questionDao = database.questionDao()
    private val historyDao = database.quizHistoryDao()

    fun getAllBooks(): Flow<List<BookEntity>> = bookDao.getAllBooks()

    fun getBooksByPublisher(publisher: String): Flow<List<BookEntity>> =
        bookDao.getBooksByPublisher(publisher)

    fun filterQuestions(
        bookSource: String?,
        subject: String?,
        examCategory: String?,
        difficulty: String?
    ): Flow<List<QuestionEntity>> {
        val cleanBook = if (bookSource.isNullOrBlank() || bookSource == "All") null else bookSource
        val cleanSubject = if (subject.isNullOrBlank() || subject == "All") null else subject
        val cleanExam = if (examCategory.isNullOrBlank() || examCategory == "All Exams") null else examCategory
        val cleanDiff = if (difficulty.isNullOrBlank() || difficulty == "All") null else difficulty
        return questionDao.filterQuestions(cleanBook, cleanSubject, cleanExam, cleanDiff)
    }

    fun searchQuestions(query: String): Flow<List<QuestionEntity>> =
        questionDao.searchQuestions(query.trim())

    fun getBookmarkedQuestions(): Flow<List<QuestionEntity>> =
        questionDao.getBookmarkedQuestions()

    suspend fun toggleBookmark(questionId: String, currentStatus: Boolean) = withContext(Dispatchers.IO) {
        questionDao.setBookmark(questionId, !currentStatus)
    }

    suspend fun recordAttempt(questionId: String, isCorrect: Boolean) = withContext(Dispatchers.IO) {
        questionDao.recordAttempt(questionId, if (isCorrect) 1 else 0)
    }

    suspend fun recordQuizSession(
        bookSource: String,
        subject: String,
        examCategory: String,
        total: Int,
        correct: Int,
        durationSeconds: Long
    ) = withContext(Dispatchers.IO) {
        val percentage = if (total > 0) (correct.toFloat() / total) * 100f else 0f
        val history = QuizHistoryEntity(
            bookSource = bookSource,
            subject = subject,
            examCategory = examCategory,
            totalQuestions = total,
            correctCount = correct,
            scorePercentage = percentage,
            durationSeconds = durationSeconds
        )
        historyDao.insertHistory(history)
    }

    fun getQuizHistory(): Flow<List<QuizHistoryEntity>> = historyDao.getAllHistory()

    suspend fun getAnalytics(): QuizAnalytics = withContext(Dispatchers.IO) {
        val totalSessions = historyDao.getTotalSessions()
        val avgScore = historyDao.getAverageScore() ?: 0f
        QuizAnalytics(totalSessions, avgScore)
    }

    suspend fun importQuestionsBatch(questions: List<QuestionEntity>) = withContext(Dispatchers.IO) {
        questionDao.insertQuestions(questions)
    }
}

data class QuizAnalytics(
    val totalSessions: Int,
    val averageScorePercentage: Float
)
