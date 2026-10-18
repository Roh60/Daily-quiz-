package com.example.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Structural checks for the bundled starter question bank; these do not replace editorial fact-checking. */
class QuestionBankIntegrityTest {

    @Test
    fun `all bundled questions have unique ids and valid answer indexes`() {
        val questions = DatabasePrepopulator.getInitialQuestions() +
            DatabasePrepopulator.getAdditionalTopicQuestions()

        assertFalse("Question bank must not be empty", questions.isEmpty())
        assertEquals("Question IDs must be unique", questions.size, questions.map { it.id }.toSet().size)

        questions.forEach { question ->
            assertTrue("Blank question ID", question.id.isNotBlank())
            assertTrue("Blank question text: ${question.id}", question.question.isNotBlank())
            assertTrue("Blank explanation: ${question.id}", question.explanation.isNotBlank())
            assertTrue("Invalid answer index for ${question.id}", question.correctOptionIndex in 0..3)
            val options = listOf(question.optionA, question.optionB, question.optionC, question.optionD)
            assertTrue("Empty option for ${question.id}", options.all { it.isNotBlank() })
            assertEquals("Options must be distinct for ${question.id}", 4, options.map { it.trim().lowercase() }.toSet().size)
        }
    }
    @Test
    fun `fresh install has exactly 400 questions in each of twelve categories`() {
        val questions = DatabasePrepopulator.getInitialQuestions() +
            DatabasePrepopulator.getAdditionalTopicQuestions()
        val categories = listOf(
            "History", "Indian Polity", "Geography", "General Science",
            "Indian Economy", "Static GK", "Sports", "Entertainment",
            "Kids", "Currency", "Current Affairs", "Facts"
        )

        assertEquals("Expected 4,800 total questions", 4800, questions.size)
        categories.forEach { category ->
            assertEquals("Wrong count for $category", 400, questions.count { it.subject == category })
        }
    }

}
