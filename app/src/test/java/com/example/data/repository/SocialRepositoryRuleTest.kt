package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SocialRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createPost_authenticatedUser_success() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = SocialFeedRepository(firestore)

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createPost(
                type = "text",
                content = "Test post on Harappan Civilization",
                category = "History",
                tags = listOf("#IndusValley", "#UPSC"),
                pollOptions = null
            )
        }
        assertTrue(result.isSuccess)
        val postId = result.getOrNull()
        assertNotNull(postId)
    }

    @Test
    fun createPoll_authenticatedUser_successAndVote() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = SocialFeedRepository(firestore)

        val pollResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createPost(
                type = "poll",
                content = "Which river valley had Lothal dockyard?",
                category = "History",
                tags = listOf("#Harappa"),
                pollOptions = listOf("Bhogava", "Ravi", "Indus", "Ghaggar")
            )
        }
        assertTrue(pollResult.isSuccess)
        val postId = pollResult.getOrThrow()

        // Vote on option 0
        val voteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.voteOnPoll(postId, 0)
        }
        assertTrue(voteResult.isSuccess)
    }

    @Test
    fun unauthenticated_createPost_fails() = runBlocking {
        auth.signOut()
        val repository = SocialFeedRepository(firestore)

        val result = try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                repository.createPost(
                    type = "text",
                    content = "Unauthenticated content",
                    category = "Polity",
                    tags = emptyList(),
                    pollOptions = null
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
        assertTrue(result.isFailure)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@gkswal.test"
        const val DEFAULT_TIMEOUT_MS = 8000L
    }
}
