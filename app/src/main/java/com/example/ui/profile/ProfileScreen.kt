package com.example.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.BuildConfig
import com.example.data.local.GkSwalDatabase
import com.example.data.repository.GkBookRepository
import com.example.data.repository.SocialFeedRepository
import com.example.data.repository.UserEngagementStats
import com.example.ui.auth.AuthManager
import coil.compose.AsyncImage
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.EmeraldCorrect
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RedIncorrect
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.functions
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(
    bookRepository: GkBookRepository,
    socialRepository: SocialFeedRepository,
    onSignOut: () -> Unit,
    onNavigateToBookmarks: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val user = Firebase.auth.currentUser

    val displayName = user?.displayName?.ifBlank { null }
        ?: user?.email?.substringBefore("@")
        ?: "GK Aspirant"
    var visibleDisplayName by remember { mutableStateOf(displayName) }
    val email = user?.email ?: "user@gkswal.app"
    val uid = user?.uid ?: ""

    // Room Study Stats
    val quizHistory by bookRepository.getQuizHistory().collectAsState(initial = emptyList())
    val bookmarkedQuestions by bookRepository.getBookmarkedQuestions().collectAsState(initial = emptyList())

    // Firestore Community Engagement Stats
    val socialStats by socialRepository.observeUserEngagementStats(uid)
        .collectAsState(initial = UserEngagementStats())

    val targetExams = listOf("UPSC CSE", "SSC CGL", "State PSC", "Banking / IBPS", "Railways RRB", "Defense / CDS")
    val selectedExams = remember { mutableStateListOf("UPSC CSE", "SSC CGL") }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var deletionError by remember { mutableStateOf<String?>(null) }
    var showEditProfile by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(displayName) }
    var editBio by remember { mutableStateOf("") }
    var visiblePhotoUrl by remember { mutableStateOf(user?.photoUrl?.toString().orEmpty()) }
    var editPhotoUrl by remember { mutableStateOf(visiblePhotoUrl) }
    var profileSaveError by remember { mutableStateOf<String?>(null) }
    var savingProfile by remember { mutableStateOf(false) }
    var examPreferenceMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            try {
                val profile = FirebaseFirestore.getInstance(context.getString(com.example.R.string.firestore_database_id))
                    .collection("users").document(uid).get().await()
                val saved = profile.get("targetExams") as? List<*>
                if (!saved.isNullOrEmpty()) {
                    selectedExams.clear()
                    selectedExams.addAll(saved.filterIsInstance<String>().filter { it in targetExams })
                }
                visiblePhotoUrl = profile.getString("photoUrl") ?: user?.photoUrl?.toString().orEmpty()
            } catch (_: Exception) { /* Keep local defaults if the profile cannot be read. */ }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("user_profile_screen")
    ) {
        // --- 1. USER IDENTITY & BADGE HEADER ---
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_profile_header_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (visiblePhotoUrl.startsWith("https://")) {
                            AsyncImage(
                                model = visiblePhotoUrl,
                                contentDescription = "Profile photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Text(
                                text = visibleDisplayName.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = visibleDisplayName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )

                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    TextButton(onClick = {
                        editName = visibleDisplayName
                        editPhotoUrl = visiblePhotoUrl
                        profileSaveError = null
                        coroutineScope.launch {
                            try {
                                val profile = FirebaseFirestore.getInstance(context.getString(com.example.R.string.firestore_database_id))
                                    .collection("users").document(uid).get().await()
                                editBio = profile.getString("bio") ?: ""
                                editPhotoUrl = profile.getString("photoUrl") ?: visiblePhotoUrl
                            } catch (_: Exception) { editBio = "" }
                            showEditProfile = true
                        }
                    }) { Text("Edit profile") }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AmberAccent.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Aspirant Scholar",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = AmberAccent)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = EmeraldCorrect.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CloudDone, contentDescription = null, tint = EmeraldCorrect, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Cloud Synced",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = EmeraldCorrect)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 2. PRACTICE & STUDY PERFORMANCE STATS (ROOM) ---
        item {
            val totalQuizzes = quizHistory.size
            val totalQuestionsAttempted = quizHistory.sumOf { it.totalQuestions }
            val totalCorrect = quizHistory.sumOf { it.correctCount }
            val avgAccuracy = if (totalQuizzes > 0) {
                quizHistory.map { it.scorePercentage }.average().toInt()
            } else 0

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("practice_stats_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Quiz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Study & Practice Stats",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "Local Room DB",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4 Key Metrics Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatTile(
                            icon = Icons.Default.Quiz,
                            label = "Quizzes Taken",
                            value = "$totalQuizzes",
                            color = MaterialTheme.colorScheme.primary
                        )
                        StatTile(
                            icon = Icons.Default.TrendingUp,
                            label = "Questions Done",
                            value = "$totalQuestionsAttempted",
                            color = MaterialTheme.colorScheme.primary
                        )
                        StatTile(
                            icon = Icons.Default.EmojiEvents,
                            label = "Avg Accuracy",
                            value = "$avgAccuracy%",
                            color = if (avgAccuracy >= 60) EmeraldCorrect else AmberAccent
                        )
                        StatTile(
                            icon = Icons.Default.Bookmark,
                            label = "Bookmarks",
                            value = "${bookmarkedQuestions.size}",
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Knowledge Mastery Progress
                    val masteryPercent = (avgAccuracy / 100f).coerceIn(0f, 1f)
                    Text(
                        text = "GK Mastery Index: $avgAccuracy% (${if (avgAccuracy >= 75) "Advanced" else if (avgAccuracy >= 50) "Intermediate" else "Novice"})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { masteryPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (avgAccuracy >= 60) EmeraldCorrect else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        // Server-authoritative wallet: users cannot credit themselves from the client.
        item { WalletSection() }

        // --- 3. COMMUNITY & SOCIAL ENGAGEMENT SUMMARY (FIRESTORE) ---
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("social_engagement_stats_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Poll, contentDescription = null, tint = AmberAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Social Engagement Summary",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AmberAccent.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Cloud Firestore",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AmberAccent),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatTile(
                            icon = Icons.Default.EditNote,
                            label = "Posts Published",
                            value = "${socialStats.postsCount}",
                            color = MaterialTheme.colorScheme.primary
                        )
                        StatTile(
                            icon = Icons.Default.Poll,
                            label = "Polls Created",
                            value = "${socialStats.pollsCount}",
                            color = AmberAccent
                        )
                        StatTile(
                            icon = Icons.Default.Favorite,
                            label = "Likes Received",
                            value = "${socialStats.totalLikesReceived}",
                            color = RedIncorrect
                        )
                        StatTile(
                            icon = Icons.Default.People,
                            label = "Following",
                            value = "${socialStats.followingCount}",
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    if (socialStats.totalCommentsReceived > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Your contributions have sparked ${socialStats.totalCommentsReceived} discussion replies from fellow aspirants.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 4. TARGET COMPETITIVE EXAMS CUSTOMIZATION ---
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Target Examination Focus",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Text(
                        text = "Customize your syllabus priorities and practice recommendations:",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(targetExams) { exam ->
                            val isSelected = selectedExams.contains(exam)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) selectedExams.remove(exam) else selectedExams.add(exam)
                                    examPreferenceMessage = null
                                    coroutineScope.launch {
                                        try {
                                            FirebaseFirestore.getInstance(context.getString(com.example.R.string.firestore_database_id))
                                                .collection("users").document(uid).update(
                                                    mapOf("targetExams" to selectedExams.toList(), "updatedAt" to FieldValue.serverTimestamp())
                                                ).await()
                                        } catch (_: Exception) {
                                            examPreferenceMessage = "Preferences could not sync. Check connection and try again."
                                        }
                                    }
                                },
                                label = { Text(exam, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                    examPreferenceMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
            }
        }

        // --- DAILY STREAK + FRIEND INVITE (organic user acquisition) ---
        item {
            val streakDays = calculateStudyStreak(quizHistory.map { it.timestamp })
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Stars, contentDescription = null, tint = AmberAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Daily Streak & Friends Challenge", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    Text(
                        "🔥 $streakDays-day study streak",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "Practice every day to build consistency. Invite friends to join GK Swal and compare your learning progress.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = {
                            val shareText = "I'm learning with GK Swal — daily challenges, GK practice and a learning community. Join me: https://play.google.com/store/apps/details?id=com.aistudio.gkswal.vqxrmp"
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Join my GK Swal challenge")
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Invite friends to GK Swal"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.People, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Invite friends")
                    }
                    Text(
                        "Invite sharing opens your phone's share menu. Referral tracking/rewards are not enabled yet.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // --- 6. PUBLISHER ATTRIBUTION DISCLOSURE ---
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Policy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Publisher Transparency & Legal Attribution", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Lucent GK: Publisher name is shown for resource discovery; questions must be independently written or licensed.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Arihant Publications: No full book text is included unless permission or a suitable licence is confirmed.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Drishti IAS: Official resources should be opened through authorized links; GK Swal is not affiliated unless formally agreed.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "All user posts, poll votes, and bookmarks remain secure under your Google authenticated account.",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    )
                }
            }
        }

        // --- 7. PRIVACY AND ACCOUNT CONTROLS ---
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Policy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Privacy & Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text("Review how account, community and learning data are used. The public privacy-policy URL must be published before Play Store release.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { showPrivacyDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Policy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Privacy information")
                    }
                    Button(
                        onClick = { deletionError = null; showDeleteDialog = true },
                        enabled = !isDeletingAccount,
                        colors = ButtonDefaults.buttonColors(containerColor = RedIncorrect),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isDeletingAccount) "Deleting account…" else "Delete my account and data")
                    }
                    deletionError?.let { Text(it, color = RedIncorrect, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        // --- 8. SIGN OUT BUTTON ---
        item {
            Button(
                onClick = {
                    AuthManager.signOut(
                        context = context,
                        credentialManager = credentialManager,
                        onSignOutComplete = onSignOut,
                        scope = coroutineScope
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RedIncorrect.copy(alpha = 0.12f),
                    contentColor = RedIncorrect
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("profile_sign_out_button")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out of Account", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showEditProfile) {
        AlertDialog(
            onDismissRequest = { if (!savingProfile) showEditProfile = false },
            title = { Text("Edit profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = editName, onValueChange = { editName = it.take(60) }, label = { Text("Display name") }, singleLine = true)
                    OutlinedTextField(value = editPhotoUrl, onValueChange = { editPhotoUrl = it.take(500) }, label = { Text("Profile photo HTTPS URL (optional)") }, singleLine = true)
                    OutlinedTextField(value = editBio, onValueChange = { editBio = it.take(300) }, label = { Text("Bio (optional)") }, minLines = 2)
                    if (!profileSaveError.isNullOrBlank()) Text(profileSaveError!!, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = !savingProfile, onClick = {
                    val cleanName = editName.trim()
                    if (cleanName.isEmpty()) { profileSaveError = "Name cannot be empty."; return@TextButton }
                    val cleanPhotoUrl = editPhotoUrl.trim()
                    if (cleanPhotoUrl.isNotEmpty() && !cleanPhotoUrl.startsWith("https://")) { profileSaveError = "Profile photo URL must start with https://"; return@TextButton }
                    savingProfile = true
                    coroutineScope.launch {
                        try {
                            val firestore = FirebaseFirestore.getInstance(context.getString(com.example.R.string.firestore_database_id))
                            firestore.collection("users").document(uid).update(
                                mapOf("displayName" to cleanName, "bio" to editBio.trim(), "photoUrl" to cleanPhotoUrl, "updatedAt" to FieldValue.serverTimestamp())
                            ).await()
                            user?.updateProfile(
                                UserProfileChangeRequest.Builder().setDisplayName(cleanName)
                                    .setPhotoUri(cleanPhotoUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }).build()
                            )?.await()
                            visibleDisplayName = cleanName
                            visiblePhotoUrl = cleanPhotoUrl
                            showEditProfile = false
                        } catch (e: Exception) {
                            profileSaveError = e.localizedMessage ?: "Profile could not be saved. Check Firebase rules and connection."
                        } finally { savingProfile = false }
                    }
                }) { Text(if (savingProfile) "Saving…" else "Save") }
            },
            dismissButton = { TextButton(enabled = !savingProfile, onClick = { showEditProfile = false }) { Text("Cancel") } }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("GK Swal Privacy Information") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "GK Swal processes sign-in identity, profile details, community posts, comments, poll votes, reports and learning activity to provide app features. Community data may be stored in Firebase; bookmarks and quiz history are stored locally on this device. Current Affairs displays headlines from the official PIB RSS feed and links to original releases when internet access is available."
                    )
                    Text(
                        "Google UMP consent handling is included. Ad requests stay disabled unless UMP permits them and a banner ad-unit ID is configured. Actual data practices depend on the final production SDK configuration."
                    )
                    if (BuildConfig.PUBLIC_PRIVACY_POLICY_URL.startsWith("https://")) {
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PUBLIC_PRIVACY_POLICY_URL)))
                            }
                        }) { Text("Open public Privacy Policy") }
                    } else {
                        Text("Public Privacy Policy is not configured yet. The developer must publish the policy before public release.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (BuildConfig.PUBLIC_ACCOUNT_DELETION_URL.startsWith("https://")) {
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PUBLIC_ACCOUNT_DELETION_URL)))
                            }
                        }) { Text("Open account-deletion webpage") }
                    } else {
                        Text("External account-deletion webpage is not configured yet. In-app account deletion is available only after its Firebase callable function is deployed and tested.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPrivacyDialog = false }) { Text("Close") } }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingAccount) showDeleteDialog = false },
            title = { Text("Delete your GK Swal account?") },
            text = {
                Text("This permanently deletes your Firebase account, your profile, your posts and related cloud data using the account-deletion service, and removes local quiz/bookmark data from this device. This cannot be undone. If deletion fails, the app will show an error and you can retry.")
            },
            confirmButton = {
                TextButton(
                    enabled = !isDeletingAccount,
                    onClick = {
                        coroutineScope.launch {
                            isDeletingAccount = true
                            deletionError = null
                            try {
                                Firebase.functions.getHttpsCallable("deleteMyAccount").call().await()
                                GkSwalDatabase.clearLocalData(context)
                                showDeleteDialog = false
                                AuthManager.signOut(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onSignOutComplete = onSignOut,
                                    scope = coroutineScope
                                )
                                android.widget.Toast.makeText(context, "Account deletion completed", android.widget.Toast.LENGTH_LONG).show()
                            } catch (error: Exception) {
                                deletionError = "Deletion service unavailable or failed. Deploy Firebase Function 'deleteMyAccount' and retry."
                                android.util.Log.e("GKSwal", "Account deletion failed", error)
                            } finally {
                                isDeletingAccount = false
                            }
                        }
                    }
                ) { Text(if (isDeletingAccount) "Deleting…" else "Delete permanently") }
            },
            dismissButton = {
                TextButton(enabled = !isDeletingAccount, onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun calculateStudyStreak(timestamps: List<Long>): Int {
    if (timestamps.isEmpty()) return 0
    val formatter = SimpleDateFormat("yyyyMMdd", Locale.US)
    val activeDays = timestamps.map { formatter.format(Date(it)) }.toSet()
    val calendar = Calendar.getInstance()
    val today = formatter.format(calendar.time)
    val yesterdayCalendar = calendar.clone() as Calendar
    yesterdayCalendar.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = formatter.format(yesterdayCalendar.time)

    // A streak remains active if the last practice was today or yesterday.
    if (today !in activeDays && yesterday !in activeDays) return 0
    if (today !in activeDays) calendar.add(Calendar.DAY_OF_YEAR, -1)

    var streak = 0
    while (formatter.format(calendar.time) in activeDays) {
        streak++
        calendar.add(Calendar.DAY_OF_YEAR, -1)
    }
    return streak
}

@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.08f),
        modifier = Modifier.width(78.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = color,
                    fontSize = 16.sp
                )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 1
            )
        }
    }
}
