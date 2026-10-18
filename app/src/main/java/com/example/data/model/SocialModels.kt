package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val role: String = "aspirant", // aspirant, contributor, admin
    val bio: String? = null,
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PollOption(
    val id: Int = 0,
    val text: String = "",
    val votes: Long = 0L
)

@IgnoreExtraProperties
data class SocialPost(
    @DocumentId val id: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorRole: String = "aspirant",
    val type: String = "text", // "text" or "poll"
    val content: String = "",
    val category: String = "General",
    val tags: List<String> = emptyList(),
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    val pollOptions: List<Map<String, Any>> = emptyList(),
    val totalVotes: Long = 0L,
    val allowVoteChange: Boolean = true,
    val isClosed: Boolean = false,
    @ServerTimestamp val expiresAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PollVote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val optionIndex: Long = 0L,
    @ServerTimestamp val votedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PostComment(
    @DocumentId val id: String = "",
    val commentId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val content: String = "",
    val parentId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ContentReport(
    @DocumentId val id: String = "",
    val reportId: String = "",
    val reporterId: String = "",
    val targetType: String = "post", // "post", "comment", "user"
    val targetId: String = "",
    val reason: String = "spam",
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)
