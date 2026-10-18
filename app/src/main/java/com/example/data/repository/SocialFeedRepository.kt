package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ContentReport
import com.example.data.model.PollVote
import com.example.data.model.PostComment
import com.example.data.model.SocialPost
import com.example.data.model.UserProfile
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

class SocialFeedRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth
    private val functions = FirebaseFunctions.getInstance()

    fun currentUserId(): String? = auth.currentUser?.uid

    fun currentUserDisplayName(): String =
        auth.currentUser?.displayName?.ifBlank { null }
            ?: auth.currentUser?.email?.substringBefore("@")
            ?: "GK Aspirant"

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before performing social operations.")
    }

    // Observe posts in real-time
    fun observePosts(): Flow<List<SocialPost>> = flow {
        val path = "posts"
        val query = db.collection(path).orderBy("createdAt", Query.Direction.DESCENDING).limit(50)
        query.snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(SocialPost::class.java)?.copy(id = doc.id, postId = doc.id)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
            .collect { emit(it) }
    }

    // Create a new text post or poll post
    suspend fun createPost(
        type: String, // "text" or "poll"
        content: String,
        category: String,
        tags: List<String>,
        pollOptions: List<String>?,
        allowVoteChange: Boolean = true,
        expiresInHours: Int? = null
    ): Result<String> {
        val uid = requireUserId()
        val postId = UUID.randomUUID().toString()
        val postRef = db.collection("posts").document(postId)

        val optionsList = if (type == "poll" && pollOptions != null) {
            pollOptions.mapIndexed { idx, optText ->
                mapOf(
                    "id" to idx,
                    "text" to optText.trim(),
                    "votes" to 0L
                )
            }
        } else {
            emptyList()
        }

        val expiresTimestamp = if (expiresInHours != null && expiresInHours > 0) {
            Timestamp(Date(System.currentTimeMillis() + expiresInHours * 3600 * 1000L))
        } else null

        val payload = mutableMapOf<String, Any>(
            "postId" to postId,
            "authorId" to uid,
            "authorName" to currentUserDisplayName(),
            "authorRole" to "aspirant",
            "type" to type,
            "content" to content.trim(),
            "category" to category,
            "tags" to tags,
            "likesCount" to 0L,
            "commentsCount" to 0L,
            "totalVotes" to 0L,
            "allowVoteChange" to allowVoteChange,
            "isClosed" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        if (type == "poll") {
            payload["pollOptions"] = optionsList
        }
        if (expiresTimestamp != null) {
            payload["expiresAt"] = expiresTimestamp
        }

        return try {
            postRef.set(payload).await()
            Result.success(postId)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, postRef.path)
            Result.failure(e)
        }
    }

    // Vote writes and poll totals are updated atomically by a trusted callable function.
    suspend fun voteOnPoll(postId: String, optionIndex: Int): Result<Unit> {
        val uid = requireUserId()
        if (optionIndex !in 0..3) return Result.failure(IllegalArgumentException("Invalid poll option."))
        return try {
            functions.getHttpsCallable("voteOnPoll")
                .call(mapOf("postId" to postId, "optionIndex" to optionIndex))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "posts/$postId/votes/$uid")
            Result.failure(e)
        }
    }

    // Observe user's selected vote on a specific poll
    fun observeUserVote(postId: String): Flow<Int?> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val voteRef = db.collection("posts").document(postId).collection("votes").document(uid)
        val registration = voteRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(null)
                return@addSnapshotListener
            }
            val index = snapshot?.getLong("optionIndex")?.toInt()
            trySend(index)
        }
        awaitClose { registration.remove() }
    }

    // Like document and aggregate count are updated atomically on the server.
    suspend fun toggleLike(postId: String, currentLiked: Boolean): Result<Unit> {
        val uid = requireUserId()
        return try {
            functions.getHttpsCallable("togglePostLike")
                .call(mapOf("postId" to postId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "posts/$postId/likes/$uid")
            Result.failure(e)
        }
    }

    // Observe whether current user liked a post
    fun observeUserLiked(postId: String): Flow<Boolean> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(false)
            close()
            return@callbackFlow
        }
        val likeRef = db.collection("posts").document(postId).collection("likes").document(uid)
        val registration = likeRef.addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.exists() == true)
        }
        awaitClose { registration.remove() }
    }

    // Observe comments
    fun observeComments(postId: String): Flow<List<PostComment>> = callbackFlow {
        val commentsCollection = db.collection("posts").document(postId).collection("comments")
        val commentsQuery = commentsCollection.orderBy("createdAt", Query.Direction.ASCENDING)
        val registration = commentsQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, commentsCollection.path)
                trySend(emptyList())
                return@addSnapshotListener
            }
            val comments = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(PostComment::class.java)?.copy(id = doc.id, commentId = doc.id)
            } ?: emptyList()
            trySend(comments)
        }
        awaitClose { registration.remove() }
    }

    // Comment creation and count increment are atomic on the trusted server.
    suspend fun addComment(postId: String, content: String, parentId: String? = null): Result<Unit> {
        val uid = requireUserId()
        val cleanContent = content.trim()
        if (cleanContent.isEmpty() || cleanContent.length > 500) {
            return Result.failure(IllegalArgumentException("Comment must contain 1–500 characters."))
        }
        val payload = mutableMapOf<String, Any>("postId" to postId, "content" to cleanContent)
        if (!parentId.isNullOrBlank()) payload["parentId"] = parentId
        return try {
            functions.getHttpsCallable("addPostComment").call(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "posts/$postId/comments")
            Result.failure(e)
        }
    }

    // Delete own post through a trusted server function so nested likes/comments/votes are removed too.
    suspend fun deletePost(postId: String): Result<Unit> {
        requireUserId()
        return try {
            functions.getHttpsCallable("deleteMyPost").call(mapOf("postId" to postId)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "posts/$postId")
            Result.failure(e)
        }
    }

    // Edit own post
    suspend fun editPost(postId: String, newContent: String, newCategory: String, newTags: List<String>): Result<Unit> {
        requireUserId()
        val postRef = db.collection("posts").document(postId)
        return try {
            postRef.update(
                mapOf(
                    "content" to newContent.trim(),
                    "category" to newCategory,
                    "tags" to newTags,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, postRef.path)
            Result.failure(e)
        }
    }

    // Follow/unfollow through a single server-side transaction to avoid one-sided edges.
    suspend fun toggleFollowAuthor(targetAuthorId: String, currentFollowed: Boolean): Result<Unit> {
        val uid = requireUserId()
        if (uid == targetAuthorId) return Result.failure(IllegalArgumentException("You cannot follow yourself."))
        return try {
            functions.getHttpsCallable("toggleFollowAuthor")
                .call(mapOf("targetAuthorId" to targetAuthorId)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$targetAuthorId/followers/$uid")
            Result.failure(e)
        }
    }

    // Observe all author IDs followed by the current user
    fun observeFollowingAuthors(): Flow<Set<String>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptySet())
            close()
            return@callbackFlow
        }
        val followingRef = db.collection("users").document(uid).collection("following")
        val registration = followingRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptySet())
                return@addSnapshotListener
            }
            val followedIds = snapshot?.documents?.mapNotNull { it.id }?.toSet() ?: emptySet()
            trySend(followedIds)
        }
        awaitClose { registration.remove() }
    }

    // Observe if current user follows target author
    fun observeIsFollowing(targetAuthorId: String): Flow<Boolean> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null || uid == targetAuthorId) {
            trySend(false)
            close()
            return@callbackFlow
        }
        val followingRef = db.collection("users").document(uid).collection("following").document(targetAuthorId)
        val registration = followingRef.addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.exists() == true)
        }
        awaitClose { registration.remove() }
    }

    // Submit report for moderation
    suspend fun reportContent(
        targetType: String,
        targetId: String,
        reason: String,
        notes: String?
    ): Result<Unit> {
        requireUserId()
        val reportId = UUID.randomUUID().toString()
        val payload = mutableMapOf<String, Any>(
            "reportId" to reportId,
            "targetType" to targetType,
            "targetId" to targetId,
            "reason" to reason
        )
        if (!notes.isNullOrBlank()) payload["notes"] = notes.trim()

        return try {
            functions.getHttpsCallable("submitContentReport").call(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "reports/$reportId")
            Result.failure(e)
        }
    }

    // Observe or synchronize user profile
    suspend fun syncUserProfile(role: String = "aspirant", bio: String? = null) {
        val user = auth.currentUser ?: return
        val userRef = db.collection("users").document(user.uid)
        try {
            val snapshot = userRef.get().await()
            if (!snapshot.exists()) {
                val profile = mutableMapOf<String, Any>(
                    "userId" to user.uid,
                    "displayName" to currentUserDisplayName(),
                    "role" to role,
                    "followersCount" to 0L,
                    "followingCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (user.photoUrl != null) {
                    profile["photoUrl"] = user.photoUrl.toString()
                }
                if (!bio.isNullOrBlank()) {
                    profile["bio"] = bio
                }
                userRef.set(profile).await()
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, userRef.path)
        }
    }

    // Observe user's social engagement summary (posts created, polls created, likes & comments)
    fun observeUserEngagementStats(userId: String): Flow<UserEngagementStats> = callbackFlow {
        val postsQuery = db.collection("posts")
        val followingRef = db.collection("users").document(userId).collection("following")

        var allPosts = emptyList<SocialPost>()
        var followingCount = 0

        fun dispatch() {
            val userPosts = allPosts.filter { it.authorId == userId }
            val polls = userPosts.count { it.type == "poll" }
            val totalLikes = userPosts.sumOf { it.likesCount }
            val totalComments = userPosts.sumOf { it.commentsCount }

            trySend(
                UserEngagementStats(
                    postsCount = userPosts.size,
                    pollsCount = polls,
                    totalLikesReceived = totalLikes,
                    totalCommentsReceived = totalComments,
                    followingCount = followingCount
                )
            )
        }

        val postsReg = postsQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, postsQuery.path)
                return@addSnapshotListener
            }
            allPosts = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(SocialPost::class.java)
            } ?: emptyList()
            dispatch()
        }

        val followingReg = followingRef.addSnapshotListener { snapshot, _ ->
            followingCount = snapshot?.size() ?: 0
            dispatch()
        }

        awaitClose {
            postsReg.remove()
            followingReg.remove()
        }
    }
}

data class UserEngagementStats(
    val postsCount: Int = 0,
    val pollsCount: Int = 0,
    val totalLikesReceived: Long = 0L,
    val totalCommentsReceived: Long = 0L,
    val followingCount: Int = 0
)
