package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY publisher ASC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE publisher = :publisher")
    fun getBooksByPublisher(publisher: String): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<BookEntity>)
}

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions")
    fun getAllQuestions(): Flow<List<QuestionEntity>>

    @Query("""
        SELECT * FROM questions 
        WHERE (:bookSource IS NULL OR bookSource = :bookSource)
        AND (:subject IS NULL OR subject = :subject)
        AND (:examCategory IS NULL OR examCategory = :examCategory OR examCategory = 'All Exams')
        AND (:difficulty IS NULL OR difficulty = :difficulty)
        ORDER BY id ASC
    """)
    fun filterQuestions(
        bookSource: String?,
        subject: String?,
        examCategory: String?,
        difficulty: String?
    ): Flow<List<QuestionEntity>>

    @Query("""
        SELECT * FROM questions 
        WHERE (question LIKE '%' || :query || '%' OR chapter LIKE '%' || :query || '%' OR explanation LIKE '%' || :query || '%')
        ORDER BY id ASC
    """)
    fun searchQuestions(query: String): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE isBookmarked = 1")
    fun getBookmarkedQuestions(): Flow<List<QuestionEntity>>

    @Query("UPDATE questions SET isBookmarked = :isBookmarked WHERE id = :questionId")
    suspend fun setBookmark(questionId: String, isBookmarked: Boolean)

    @Query("SELECT * FROM questions WHERE id = :id LIMIT 1")
    suspend fun getQuestionById(id: String): QuestionEntity?

    @Query("UPDATE questions SET timesAttempted = timesAttempted + 1, timesCorrect = timesCorrect + :wasCorrect WHERE id = :questionId")
    suspend fun recordAttempt(questionId: String, wasCorrect: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Query("SELECT COUNT(*) FROM questions")
    suspend fun getQuestionCount(): Int
}

@Dao
interface QuizHistoryDao {
    @Query("SELECT * FROM quiz_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<QuizHistoryEntity>>

    @Insert
    suspend fun insertHistory(history: QuizHistoryEntity)

    @Query("SELECT COUNT(*) FROM quiz_history")
    suspend fun getTotalSessions(): Int

    @Query("SELECT AVG(scorePercentage) FROM quiz_history")
    suspend fun getAverageScore(): Float?
}
