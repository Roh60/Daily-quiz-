package com.example.ui.feed

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PostComment
import com.example.data.model.SocialPost
import com.example.data.repository.SocialFeedRepository
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.EmeraldCorrect
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RedIncorrect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun SocialFeedScreen(
    repository: SocialFeedRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUserId = repository.currentUserId()

    // Primary Sorting Tabs: 0: Latest, 1: Following
    var selectedSortTab by remember { mutableIntStateOf(0) }
    val sortTabs = listOf("Latest Feed", "Following")

    // Filter by post type or topic
    var filterPollsOnly by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Indian Polity", "History", "Geography", "General Science", "Indian Economy", "Static GK", "Exam Strategy")

    // Real-time Firestore streams
    val allPosts by repository.observePosts().collectAsState(initial = emptyList())
    val followedAuthorIds by repository.observeFollowingAuthors().collectAsState(initial = emptySet())

    // --- PAGINATION STATE ---
    val pageSize = 8
    var visiblePageLimit by remember { mutableIntStateOf(pageSize) }
    var isLoadingMore by remember { mutableStateOf(false) }

    // Filter and Sort posts
    val filteredPosts = remember(allPosts, selectedSortTab, followedAuthorIds, filterPollsOnly, selectedCategory) {
        allPosts
            .filter { post ->
                // Sort by Latest or Following
                if (selectedSortTab == 1) {
                    followedAuthorIds.contains(post.authorId) || post.authorId == currentUserId
                } else {
                    true
                }
            }
            .filter { post ->
                if (filterPollsOnly) post.type == "poll" else true
            }
            .filter { post ->
                if (selectedCategory != "All") post.category.equals(selectedCategory, ignoreCase = true) else true
            }
            .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
    }

    // Paginated sublist to display in LazyColumn
    val paginatedPosts = remember(filteredPosts, visiblePageLimit) {
        filteredPosts.take(visiblePageLimit)
    }

    val hasMorePages = paginatedPosts.size < filteredPosts.size

    val listState = rememberLazyListState()

    // Infinite scroll pagination trigger
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = paginatedPosts.size
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMorePages && lastVisibleIndex >= (totalItems - 2).coerceAtLeast(0) && !isLoadingMore
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && hasMorePages) {
            isLoadingMore = true
            delay(400) // Smooth micro-delay for pagination indication
            visiblePageLimit += pageSize
            isLoadingMore = false
        }
    }

    // Dialog states
    var showCreatePostDialog by remember { mutableStateOf(false) }
    var showCreatePollDialog by remember { mutableStateOf(false) }
    var activePostForComments by remember { mutableStateOf<SocialPost?>(null) }
    var postToEdit by remember { mutableStateOf<SocialPost?>(null) }
    var postToReport by remember { mutableStateOf<SocialPost?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: Sort Tabs ("Latest" vs "Following")
            TabRow(
                selectedTabIndex = selectedSortTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedSortTab]),
                        height = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                sortTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedSortTab == index,
                        onClick = {
                            selectedSortTab = index
                            visiblePageLimit = pageSize
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedSortTab == index) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                if (index == 1 && followedAuthorIds.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${followedAuthorIds.size}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.testTag(if (index == 0) "tab_sort_latest" else "tab_sort_following")
                    )
                }
            }

            // Quick Filters Bar: Category chips + "Polls Only" toggle
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 4.dp)
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FilterChip(
                            selected = filterPollsOnly,
                            onClick = {
                                filterPollsOnly = !filterPollsOnly
                                visiblePageLimit = pageSize
                            },
                            label = { Text("📊 Polls Only", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmberAccent.copy(alpha = 0.2f),
                                selectedLabelColor = AmberAccent
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = filterPollsOnly,
                                borderColor = if (filterPollsOnly) AmberAccent else Color.Transparent
                            )
                        )
                    }

                    items(categories) { cat ->
                        val isSelected = selectedCategory == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedCategory = cat
                                visiblePageLimit = pageSize
                            },
                            label = { Text(cat, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // Feed Content with LazyColumn and Pagination
            if (paginatedPosts.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (selectedSortTab == 1) Icons.Default.PersonAdd else Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (selectedSortTab == 1) "No Posts From Followed Authors" else "No Posts Found",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Text(
                            text = if (selectedSortTab == 1) {
                                "You haven't followed any educators or aspirants yet, or they haven't posted. Switch to 'Latest Feed' to discover community contributors!"
                            } else {
                                "No community posts or polls match the selected category filter."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (selectedSortTab == 1) {
                            Button(
                                onClick = { selectedSortTab = 0 },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Browse Latest Feed")
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { showCreatePostDialog = true },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Post First Question")
                                }
                                OutlinedButton(
                                    onClick = { showCreatePollDialog = true },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Create Poll")
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("social_feed_lazy_column")
                ) {
                    // Posts items
                    items(
                        items = paginatedPosts,
                        key = { it.postId }
                    ) { post ->
                        SocialPostCard(
                            post = post,
                            repository = repository,
                            isOwnPost = post.authorId == currentUserId,
                            onCommentsClicked = { activePostForComments = post },
                            onEditClicked = { postToEdit = post },
                            onDeleteClicked = {
                                coroutineScope.launch {
                                    val result = repository.deletePost(post.postId)
                                    if (result.isSuccess) {
                                        Toast.makeText(context, "Post deleted", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Could not delete post", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onReportClicked = { postToReport = post }
                        )
                    }

                    // --- PAGINATION FOOTER ITEMS ---
                    if (isLoadingMore) {
                        item(key = "pagination_loading_spinner") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Loading more discussions...",
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                }
                            }
                        }
                    } else if (hasMorePages) {
                        item(key = "load_more_manual_button") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        visiblePageLimit += pageSize
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("load_more_posts_button")
                                ) {
                                    Text("Load More Posts (${filteredPosts.size - paginatedPosts.size} remaining)")
                                }
                            }
                        }
                    } else if (paginatedPosts.size >= 5) {
                        item(key = "pagination_end_footer") {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldCorrect,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "You're all caught up! Total ${filteredPosts.size} posts viewed.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Buttons for Post & Poll creation
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FloatingActionButton(
                onClick = { showCreatePollDialog = true },
                containerColor = AmberAccent,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("create_poll_fab")
            ) {
                Row(modifier = Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Poll, contentDescription = "Create Poll")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Poll", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            FloatingActionButton(
                onClick = { showCreatePostDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("create_post_fab")
            ) {
                Row(modifier = Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = "Create Post")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Post", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }

    // Dialogs
    if (showCreatePostDialog) {
        CreateTextPostDialog(
            onDismiss = { showCreatePostDialog = false },
            onSubmit = { content, category, tags ->
                coroutineScope.launch {
                    val result = repository.createPost(
                        type = "text",
                        content = content,
                        category = category,
                        tags = tags,
                        pollOptions = null
                    )
                    if (result.isSuccess) {
                        Toast.makeText(context, "Post published!", Toast.LENGTH_SHORT).show()
                        showCreatePostDialog = false
                    } else {
                        Toast.makeText(context, "Failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    if (showCreatePollDialog) {
        CreatePollDialog(
            onDismiss = { showCreatePollDialog = false },
            onSubmit = { question, options, category, tags, allowChange, expireHours ->
                coroutineScope.launch {
                    val result = repository.createPost(
                        type = "poll",
                        content = question,
                        category = category,
                        tags = tags,
                        pollOptions = options,
                        allowVoteChange = allowChange,
                        expiresInHours = expireHours
                    )
                    if (result.isSuccess) {
                        Toast.makeText(context, "Poll published!", Toast.LENGTH_SHORT).show()
                        showCreatePollDialog = false
                    } else {
                        Toast.makeText(context, "Failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    if (postToEdit != null) {
        val post = postToEdit!!
        EditPostDialog(
            initialContent = post.content,
            initialCategory = post.category,
            initialTags = post.tags,
            onDismiss = { postToEdit = null },
            onSubmit = { newContent, newCat, newTags ->
                coroutineScope.launch {
                    val res = repository.editPost(post.postId, newContent, newCat, newTags)
                    if (res.isSuccess) {
                        Toast.makeText(context, "Post updated", Toast.LENGTH_SHORT).show()
                        postToEdit = null
                    } else {
                        Toast.makeText(context, "Failed to update", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (activePostForComments != null) {
        CommentsDialog(
            post = activePostForComments!!,
            repository = repository,
            onDismiss = { activePostForComments = null }
        )
    }

    if (postToReport != null) {
        ReportPostDialog(
            post = postToReport!!,
            onDismiss = { postToReport = null },
            onSubmitReport = { reason, notes ->
                coroutineScope.launch {
                    val res = repository.reportContent("post", postToReport!!.postId, reason, notes)
                    if (res.isSuccess) {
                        Toast.makeText(context, "Report submitted for moderation review.", Toast.LENGTH_LONG).show()
                        postToReport = null
                    }
                }
            }
        )
    }
}

@Composable
fun SocialPostCard(
    post: SocialPost,
    repository: SocialFeedRepository,
    isOwnPost: Boolean,
    onCommentsClicked: () -> Unit,
    onEditClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onReportClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isLiked by repository.observeUserLiked(post.postId).collectAsState(initial = false)
    val userVoteIndex by repository.observeUserVote(post.postId).collectAsState(initial = null)
    val isFollowing by repository.observeIsFollowing(post.authorId).collectAsState(initial = false)

    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("post_card_${post.postId}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Author Bar & Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = post.authorName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = post.authorName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = post.authorRole.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        val dateStr = post.createdAt?.toDate()?.let {
                            SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(it)
                        } ?: "Just now"

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                            Text(" • ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = post.category,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Follow / Unfollow inline button
                    if (!isOwnPost) {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    repository.toggleFollowAuthor(post.authorId, isFollowing)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("follow_button_${post.postId}")
                        ) {
                            Text(
                                text = if (isFollowing) "Following" else "+ Follow",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFollowing) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (isOwnPost) {
                                DropdownMenuItem(
                                    text = { Text("Edit Post") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onEditClicked()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Post") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = RedIncorrect) },
                                    onClick = {
                                        showMenu = false
                                        onDeleteClicked()
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Report Content") },
                                    leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onReportClicked()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Post Content Text
            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Tags row
            if (post.tags.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    items(post.tags) { tag ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = if (tag.startsWith("#")) tag else "#$tag",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Interactive Poll UI (if poll type)
            if (post.type == "poll" && post.pollOptions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                PollOptionsSection(
                    post = post,
                    userVotedIndex = userVoteIndex,
                    onVote = { selectedIdx ->
                        coroutineScope.launch {
                            val res = repository.voteOnPoll(post.postId, selectedIdx)
                            if (res.isFailure) {
                                Toast.makeText(context, res.exceptionOrNull()?.message ?: "Vote failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Integrated Action Bar: Like, Comment, Share
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Integrated Like Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                coroutineScope.launch {
                                    repository.toggleLike(post.postId, isLiked)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("like_button_${post.postId}")
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) RedIncorrect else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${post.likesCount}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isLiked) RedIncorrect else MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    // Integrated Comment Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onCommentsClicked() }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("comments_button_${post.postId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "Comments",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${post.commentsCount}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    // Integrated Share Button
                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "GK Swal: ${post.category}")
                                val shareBody = if (post.type == "poll") {
                                    "📊 GK Swal Poll:\n${post.content}\n\nVote now on GK Swal!"
                                } else {
                                    "💡 GK Swal Study Note:\n${post.content}\n\n#GKSwal #${post.category}"
                                }
                                putExtra(Intent.EXTRA_TEXT, shareBody)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share GK Discussion"))
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PollOptionsSection(
    post: SocialPost,
    userVotedIndex: Int?,
    onVote: (Int) -> Unit
) {
    val totalVotes = post.totalVotes
    val hasVoted = userVotedIndex != null
    val isClosed = post.isClosed

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        post.pollOptions.forEachIndexed { index, optionMap ->
            val optText = optionMap["text"] as? String ?: ""
            val optVotes = (optionMap["votes"] as? Number)?.toLong() ?: 0L
            val percentage = if (totalVotes > 0) ((optVotes.toFloat() / totalVotes) * 100f).toInt() else 0
            val isUserChoice = userVotedIndex == index

            val canClick = !isClosed && (userVotedIndex == null || post.allowVoteChange)

            val animatedProgress by animateFloatAsState(
                targetValue = if (hasVoted || isClosed) percentage / 100f else 0f,
                label = "pollProgressBar"
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isUserChoice) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(
                    1.5.dp,
                    if (isUserChoice) MaterialTheme.colorScheme.primary else Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = canClick) { onVote(index) }
                    .testTag("poll_option_${post.postId}_$index")
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (hasVoted || isClosed) {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            color = if (isUserChoice) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else MaterialTheme.colorScheme.surfaceVariant,
                            trackColor = Color.Transparent
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            if (isUserChoice) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Your vote",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = optText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isUserChoice) FontWeight.Bold else FontWeight.Medium
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (hasVoted || isClosed) {
                            Text(
                                text = "$percentage% ($optVotes)",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUserChoice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }

        // Poll Footer Info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$totalVotes votes total • ${if (isClosed) "Closed" else "Active poll"}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            if (post.allowVoteChange && !isClosed) {
                Text(
                    text = "Vote change allowed",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                )
            }
        }
    }
}

@Composable
fun CreateTextPostDialog(
    onDismiss: () -> Unit,
    onSubmit: (content: String, category: String, tags: List<String>) -> Unit
) {
    var content by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("History") }
    var tagInput by remember { mutableStateOf("") }
    val maxChars = 500

    val categories = listOf("History", "Indian Polity", "Geography", "General Science", "Indian Economy", "Static GK", "Exam Strategy", "General")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Publish Text Post", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = content,
                    onValueChange = { if (it.length <= maxChars) content = it },
                    placeholder = { Text("Share GK tricks, conceptual questions, or exam insights...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .testTag("create_post_content_input")
                )
                Text(
                    text = "${content.length}/$maxChars characters",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (content.length >= maxChars) RedIncorrect else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp, bottom = 8.dp)
                )

                Text("Category:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    placeholder = { Text("Topic tags e.g. #UPSC #Polity") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val tags = tagInput.split(" ", ",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    onSubmit(content, selectedCategory, tags)
                },
                enabled = content.isNotBlank(),
                modifier = Modifier.testTag("publish_post_button")
            ) {
                Text("Publish Post")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CreatePollDialog(
    onDismiss: () -> Unit,
    onSubmit: (question: String, options: List<String>, category: String, tags: List<String>, allowChange: Boolean, expireHours: Int?) -> Unit
) {
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    var selectedCategory by remember { mutableStateOf("Indian Polity") }
    var tagInput by remember { mutableStateOf("#GKSwalPoll") }
    var allowVoteChange by remember { mutableStateOf(true) }

    val categories = listOf("Indian Polity", "History", "Geography", "General Science", "Indian Economy", "Static GK")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Quiz Poll", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = question,
                    onValueChange = { if (it.length <= 300) question = it },
                    placeholder = { Text("Ask a GK question or community poll...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .testTag("create_poll_question_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("Options (2 to 4 choices):", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))

                options.forEachIndexed { index, opt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = opt,
                            onValueChange = { options[index] = it },
                            placeholder = { Text("Choice ${index + 1}") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("poll_option_input_$index")
                        )
                        if (options.size > 2) {
                            IconButton(onClick = { options.removeAt(index) }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove option", tint = RedIncorrect)
                            }
                        }
                    }
                }

                if (options.size < 4) {
                    TextButton(onClick = { options.add("") }) {
                        Text("+ Add Choice")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Allow users to change vote", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = allowVoteChange, onCheckedChange = { allowVoteChange = it })
                }
            }
        },
        confirmButton = {
            val validOptions = options.map { it.trim() }.filter { it.isNotEmpty() }
            Button(
                onClick = {
                    val tags = tagInput.split(" ", ",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    onSubmit(question, validOptions, selectedCategory, tags, allowVoteChange, 72)
                },
                enabled = question.isNotBlank() && validOptions.size >= 2,
                modifier = Modifier.testTag("publish_poll_button")
            ) {
                Text("Publish Poll")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EditPostDialog(
    initialContent: String,
    initialCategory: String,
    initialTags: List<String>,
    onDismiss: () -> Unit,
    onSubmit: (content: String, category: String, tags: List<String>) -> Unit
) {
    var content by remember { mutableStateOf(initialContent) }
    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var tagInput by remember { mutableStateOf(initialTags.joinToString(" ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Post", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    placeholder = { Text("Tags") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val tags = tagInput.split(" ", ",").map { it.trim() }.filter { it.isNotEmpty() }
                    onSubmit(content, selectedCategory, tags)
                },
                enabled = content.isNotBlank()
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CommentsDialog(
    post: SocialPost,
    repository: SocialFeedRepository,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val comments by repository.observeComments(post.postId).collectAsState(initial = emptyList())
    var newCommentText by remember { mutableStateOf("") }
    var replyToComment by remember { mutableStateOf<PostComment?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Comments (${comments.size})", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.height(350.dp)) {
                if (replyToComment != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Replying to ${replyToComment!!.authorName}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            IconButton(onClick = { replyToComment = null }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                if (comments.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No comments yet. Start the conversation!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(comments) { comment ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = comment.authorName,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        TextButton(
                                            onClick = { replyToComment = comment },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("Reply", fontSize = 11.sp)
                                        }
                                    }
                                    Text(
                                        text = comment.content,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newCommentText,
                        onValueChange = { newCommentText = it },
                        placeholder = { Text(if (replyToComment != null) "Write a reply..." else "Add a comment...") },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (newCommentText.isNotBlank()) {
                                coroutineScope.launch {
                                    repository.addComment(post.postId, newCommentText, replyToComment?.commentId)
                                    newCommentText = ""
                                    replyToComment = null
                                }
                            }
                        },
                        enabled = newCommentText.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun ReportPostDialog(
    post: SocialPost,
    onDismiss: () -> Unit,
    onSubmitReport: (reason: String, notes: String?) -> Unit
) {
    val reasons = listOf("Spam or Bot", "Misleading/Incorrect GK Fact", "Inappropriate or Offensive", "Harassment")
    var selectedReason by remember { mutableStateOf(reasons[0]) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report Content", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Select a violation reason for moderation review:", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                reasons.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedReason == reason, onClick = { selectedReason = reason })
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("Optional details...") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmitReport(selectedReason, notes) },
                colors = ButtonDefaults.buttonColors(containerColor = RedIncorrect)
            ) {
                Text("Submit Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
