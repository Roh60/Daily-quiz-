package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        BookEntity::class,
        QuestionEntity::class,
        QuizHistoryEntity::class,
        SavedBookmarkEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class GkSwalDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun questionDao(): QuestionDao
    abstract fun quizHistoryDao(): QuizHistoryDao

    companion object {
        // Update inaccurate starter-count metadata without deleting users' quiz history/bookmarks.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE books SET edition = 'Check publisher catalogue for current edition', description = 'Resource directory entry for GK exam preparation. GK Swal does not include the full book text; open the publisher link to check current editions and availability.', licensingStatus = 'Reference link only — no publisher endorsement claimed', totalChapters = 0, totalQuestions = 9 WHERE id = 'book_lucent_gk'")
                db.execSQL("UPDATE books SET title = 'Arihant Publications — Study Resources', edition = 'Check publisher catalogue for current edition', description = 'Resource directory entry for competitive-exam study materials. GK Swal does not include full copyrighted book text; open the publisher link to check current editions and availability.', licensingStatus = 'Reference link only — no publisher endorsement claimed', totalChapters = 0, totalQuestions = 4 WHERE id = 'book_arihant_gk'")
                db.execSQL("UPDATE books SET title = 'Drishti IAS — Official Study Resources', edition = 'Check official website for current resources', description = 'Resource directory entry for civil-services preparation. GK Swal does not include full copyrighted book or course text; open the official site to check current resources.', licensingStatus = 'Reference link only — no affiliation claimed', officialUrl = 'https://www.drishtiias.com', totalChapters = 0, totalQuestions = 4 WHERE id = 'book_drishti_ias'")
                db.execSQL("UPDATE questions SET licensingStatus = 'Original starter practice set — review pending'")
            }
        }

        @Volatile
        private var INSTANCE: GkSwalDatabase? = null

        fun clearLocalData(context: Context) {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
                context.applicationContext.deleteDatabase("gk_swal_local.db")
            }
        }

        fun getInstance(context: Context): GkSwalDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GkSwalDatabase::class.java,
                    "gk_swal_local.db"
                )
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Prepopulate database with verified educational resources
                        CoroutineScope(Dispatchers.IO).launch {
                            val database = getInstance(context)
                            database.bookDao().insertBooks(DatabasePrepopulator.getInitialBooks())
                            database.questionDao().insertQuestions(DatabasePrepopulator.getInitialQuestions())
                        }
                    }
                })
                // Intentionally avoid destructive fallback: missing migrations must fail loudly
                // instead of silently deleting quiz history, bookmarks, and local progress.
                .build()
                INSTANCE = instance

                // Double check prepopulation if database already existed empty
                CoroutineScope(Dispatchers.IO).launch {
                    if (instance.questionDao().getQuestionCount() == 0) {
                        instance.bookDao().insertBooks(DatabasePrepopulator.getInitialBooks())
                        instance.questionDao().insertQuestions(DatabasePrepopulator.getInitialQuestions())
                    }
                }

                // Add new topic content to existing installs without clearing history or bookmarks.
                CoroutineScope(Dispatchers.IO).launch {
                    instance.bookDao().insertBooks(listOf(DatabasePrepopulator.getAdditionalTopicBook()))
                    val missingQuestions = DatabasePrepopulator.getAdditionalTopicQuestions().filter { question ->
                        instance.questionDao().getQuestionById(question.id) == null
                    }
                    if (missingQuestions.isNotEmpty()) {
                        instance.questionDao().insertQuestions(missingQuestions)
                    }
                }

                instance
            }
        }
    }
}
