package com.example.ui.library

import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.example.data.local.BookEntity
import com.example.data.local.QuestionEntity
import com.example.data.repository.GkBookRepository
import com.example.ui.theme.ArihantAccent
import com.example.ui.theme.DrishtiAccent
import com.example.ui.theme.EmeraldCorrect
import com.example.ui.theme.LucentAccent
import com.example.ui.theme.RedIncorrect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    repository: GkBookRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val publishers = listOf("Lucent GK", "Arihant Publications", "Drishti IAS")
    var selectedPublisherIndex by remember { mutableIntStateOf(0) }
    val currentPublisher = publishers[selectedPublisherIndex]

    var searchQuery by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("All") }
    var selectedExamCategory by remember { mutableStateOf("All Exams") }
    var selectedDifficulty by remember { mutableStateOf("All") }

    val subjects = listOf("All", "History", "Indian Polity", "Geography", "General Science", "Indian Economy", "Static GK", "Sports", "Entertainment", "Kids", "Currency", "Current Affairs", "Facts")
    val examCategories = listOf("All Exams", "UPSC", "SSC", "State PSC", "Banking", "Railways", "Defense")
    val difficulties = listOf("All", "Easy", "Medium", "Hard")

    val books by repository.getBooksByPublisher(currentPublisher).collectAsState(initial = emptyList())
    // A mixed daily challenge draws from every locally available subject and publisher.
    val allChallengeQuestions by repository.filterQuestions(
        bookSource = null,
        subject = null,
        examCategory = null,
        difficulty = null
    ).collectAsState(initial = emptyList())

    val questions by if (searchQuery.isNotBlank()) {
        repository.searchQuestions(searchQuery).collectAsState(initial = emptyList())
    } else {
        repository.filterQuestions(
            bookSource = if (selectedSubject != "All") null else currentPublisher,
            subject = selectedSubject,
            examCategory = selectedExamCategory,
            difficulty = selectedDifficulty
        ).collectAsState(initial = emptyList())
    }

    // Interactive Quiz Session state
    var isQuizModeActive by remember { mutableStateOf(false) }
    var quizQuestionsList by remember { mutableStateOf<List<QuestionEntity>>(emptyList()) }
    var activeQuizBookSource by remember { mutableStateOf(currentPublisher) }
    var activeQuizSubject by remember { mutableStateOf(selectedSubject) }
    var activeQuizExamCategory by remember { mutableStateOf(selectedExamCategory) }

    // Digital Book Authorized Link dialog
    var viewingBookDetails by remember { mutableStateOf<BookEntity?>(null) }

    if (isQuizModeActive && quizQuestionsList.isNotEmpty()) {
        PracticeQuizSessionDialog(
            questions = quizQuestionsList,
            bookSource = activeQuizBookSource,
            subject = activeQuizSubject,
            examCategory = activeQuizExamCategory,
            onDismiss = { isQuizModeActive = false },
            onSaveSession = { correct, total, duration ->
                coroutineScope.launch {
                    repository.recordQuizSession(
                        bookSource = activeQuizBookSource,
                        subject = activeQuizSubject,
                        examCategory = activeQuizExamCategory,
                        total = total,
                        correct = correct,
                        durationSeconds = duration
                    )
                    Toast.makeText(context, "Quiz session saved to practice history!", Toast.LENGTH_SHORT).show()
                    isQuizModeActive = false
                }
            }
        )
    }

    if (viewingBookDetails != null) {
        val book = viewingBookDetails!!
        AlertDialog(
            onDismissRequest = { viewingBookDetails = null },
            icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(book.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column {
                    Text("Publisher: ${book.publisher}", fontWeight = FontWeight.SemiBold)
                    Text("Edition: ${book.edition}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(book.description, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Licensing Status:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(book.licensingStatus, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            Text("This is a resource-directory listing. Starter practice questions are independently drafted and are not represented as publisher-authored; verify facts before exam use.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(book.officialUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Official Portal")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewingBookDetails = null }) {
                    Text("Close")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Publisher Switcher Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedPublisherIndex,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedPublisherIndex]),
                    height = 3.dp,
                    color = when (selectedPublisherIndex) {
                        0 -> LucentAccent
                        1 -> ArihantAccent
                        else -> DrishtiAccent
                    }
                )
            }
        ) {
            publishers.forEachIndexed { index, name ->
                val isSelected = selectedPublisherIndex == index
                Tab(
                    selected = isSelected,
                    onClick = { selectedPublisherIndex = index },
                    text = {
                        Text(
                            text = name,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                )
            }
        }

        // Active Publisher Header Card
        val activeBook = books.firstOrNull()
        if (activeBook != null) {
            Card(
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = when (selectedPublisherIndex) {
                                0 -> LucentAccent
                                1 -> ArihantAccent
                                else -> DrishtiAccent
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = activeBook.licensingStatus,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${activeBook.totalQuestions} starter practice questions currently stored locally",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row {
                        IconButton(
                            onClick = { viewingBookDetails = activeBook },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Publisher Info",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Button(
                            onClick = {
                                if (questions.isNotEmpty()) {
                                    quizQuestionsList = questions.shuffled().take(10)
                                    activeQuizBookSource = currentPublisher
                                    activeQuizSubject = selectedSubject
                                    activeQuizExamCategory = selectedExamCategory
                                    isQuizModeActive = true
                                } else {
                                    Toast.makeText(context, "No questions match filter", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Quiz Mode", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // GK Swal hero: prominent, action-led home section.
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)
                        ) {
                            Text(
                                "TODAY'S CHALLENGE",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            )
                        }
                        Text(
                            "Make today count.",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            "5 questions every day. One daily win.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.13f),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)) {
                        Text("12 topics", color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
                    }
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)) {
                        Text("Works offline", color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Button(
                    onClick = {
                        if (allChallengeQuestions.isNotEmpty()) {
                            quizQuestionsList = getDailyChallengeQuestions(context, allChallengeQuestions)
                            activeQuizBookSource = "GK Swal"
                            activeQuizSubject = "Mixed GK"
                            activeQuizExamCategory = "All Exams"
                            isQuizModeActive = true
                        } else {
                            Toast.makeText(context, "Questions are loading. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start daily quiz", fontWeight = FontWeight.Bold)
                }
                Text(
                    "${allChallengeQuestions.size} practice questions available on this device. Daily questions are selected from this bank; new factual content requires reviewed question-bank updates.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.76f)
                )
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search questions or topics") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("library_search_input")
        )

        // Filter Rows
        Column(modifier = Modifier.padding(bottom = 6.dp)) {
            // Subject chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(subjects) { subject ->
                    FilterChip(
                        selected = selectedSubject == subject,
                        onClick = { selectedSubject = subject },
                        label = { Text(subject, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            // Exam Category chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(examCategories) { category ->
                    FilterChip(
                        selected = selectedExamCategory == category,
                        onClick = { selectedExamCategory = category },
                        label = { Text(category, fontSize = 11.sp) }
                    )
                }
            }
        }

        // Questions List
        if (questions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No questions found matching criteria.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(questions, key = { it.id }) { question ->
                    QuestionCard(
                        question = question,
                        onToggleBookmark = {
                            coroutineScope.launch {
                                repository.toggleBookmark(question.id, question.isBookmarked)
                            }
                        },
                        onAttempt = { wasCorrect ->
                            coroutineScope.launch {
                                repository.recordAttempt(question.id, wasCorrect)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun QuestionCard(
    question: QuestionEntity,
    onToggleBookmark: () -> Unit,
    onAttempt: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedOptionIndex by remember { mutableStateOf<Int?>(null) }
    var isSubmitted by remember { mutableStateOf(false) }
    var showExplanation by remember { mutableStateOf(false) }

    val options = listOf(question.optionA, question.optionB, question.optionC, question.optionD)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("question_card_${question.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Subject, Chapter & Bookmark
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = question.subject,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (question.difficulty) {
                            "Easy" -> EmeraldCorrect.copy(alpha = 0.15f)
                            "Hard" -> RedIncorrect.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        }
                    ) {
                        Text(
                            text = question.difficulty,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = when (question.difficulty) {
                                "Easy" -> EmeraldCorrect
                                "Hard" -> RedIncorrect
                                else -> MaterialTheme.colorScheme.secondary
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (question.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Bookmark question",
                        tint = if (question.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = question.chapter,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                ),
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )

            // Question Text
            Text(
                text = question.question,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 22.sp
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // 4 Selectable Options
            options.forEachIndexed { index, optionText ->
                val optionLabel = when (index) {
                    0 -> "A"
                    1 -> "B"
                    2 -> "C"
                    else -> "D"
                }

                val isSelected = selectedOptionIndex == index
                val isCorrect = index == question.correctOptionIndex

                val containerColor by animateColorAsState(
                    targetValue = when {
                        !isSubmitted -> if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        isCorrect -> EmeraldCorrect.copy(alpha = 0.18f)
                        isSelected && !isCorrect -> RedIncorrect.copy(alpha = 0.18f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    },
                    label = "optionColor"
                )

                val borderColor = when {
                    !isSubmitted -> if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    isCorrect -> EmeraldCorrect
                    isSelected && !isCorrect -> RedIncorrect
                    else -> Color.Transparent
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = containerColor,
                    border = BorderStroke(1.5.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable(enabled = !isSubmitted) {
                            selectedOptionIndex = index
                            isSubmitted = true
                            showExplanation = true
                            onAttempt(isCorrect)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Option Badge Circle
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        !isSubmitted && isSelected -> MaterialTheme.colorScheme.primary
                                        isSubmitted && isCorrect -> EmeraldCorrect
                                        isSubmitted && isSelected && !isCorrect -> RedIncorrect
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSubmitted && isCorrect) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            } else if (isSubmitted && isSelected && !isCorrect) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            } else {
                                Text(
                                    text = optionLabel,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = optionText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected || (isSubmitted && isCorrect)) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        // Visible Checkmark Tick if selected or correct
                        if (isSelected && !isSubmitted) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Explanation Section
            AnimatedVisibility(visible = showExplanation) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Explanation:",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = question.explanation,
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PracticeQuizSessionDialog(
    questions: List<QuestionEntity>,
    bookSource: String,
    subject: String,
    examCategory: String,
    onDismiss: () -> Unit,
    onSaveSession: (correct: Int, total: Int, duration: Long) -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var selectedOptionIndex by remember { mutableStateOf<Int?>(null) }
    var isAnswered by remember { mutableStateOf(false) }
    var correctCount by remember { mutableIntStateOf(0) }
    var isQuizCompleted by remember { mutableStateOf(false) }

    var secondsElapsed by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isQuizCompleted) {
        while (!isQuizCompleted) {
            delay(1000L)
            secondsElapsed += 1
        }
    }

    val currentQuestion = questions.getOrNull(currentIndex)

    if (isQuizCompleted || currentQuestion == null) {
        val percentage = (correctCount.toFloat() / questions.size) * 100f
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Quiz Complete! 🎉", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Resource: $bookSource ($subject)")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Total Questions: ${questions.size}")
                    Text("Correct Answers: $correctCount / ${questions.size}", fontWeight = FontWeight.Bold)
                    Text("Score: ${percentage.toInt()}%", color = if (percentage >= 70) EmeraldCorrect else MaterialTheme.colorScheme.primary)
                    Text("Time Taken: ${secondsElapsed}s", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = { onSaveSession(correctCount, questions.size, secondsElapsed) }) {
                    Text("Save to History")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Exit")
                }
            }
        )
        return
    }

    val options = listOf(
        currentQuestion.optionA,
        currentQuestion.optionB,
        currentQuestion.optionC,
        currentQuestion.optionD
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Question ${currentIndex + 1} of ${questions.size}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text("${secondsElapsed}s", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            Column {
                LinearProgressIndicator(
                    progress = { (currentIndex + 1).toFloat() / questions.size },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = currentQuestion.question,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                options.forEachIndexed { index, opt ->
                    val isSelected = selectedOptionIndex == index
                    val isCorrect = index == currentQuestion.correctOptionIndex

                    val bg = when {
                        !isAnswered -> if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        isCorrect -> EmeraldCorrect.copy(alpha = 0.2f)
                        isSelected && !isCorrect -> RedIncorrect.copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = bg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(enabled = !isAnswered) {
                                selectedOptionIndex = index
                                isAnswered = true
                                if (isCorrect) correctCount += 1
                            }
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${'A' + index}. $opt",
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (isAnswered && isCorrect) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldCorrect, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (currentIndex + 1 < questions.size) {
                        currentIndex += 1
                        selectedOptionIndex = null
                        isAnswered = false
                    } else {
                        isQuizCompleted = true
                    }
                },
                enabled = isAnswered
            ) {
                Text(if (currentIndex + 1 < questions.size) "Next" else "Finish")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


/**
 * Keeps the daily challenge stable for the current local date and avoids the previous
 * day's question IDs on this device. This rotates existing content; it does not fetch
 * new facts from the internet.
 */
private fun getDailyChallengeQuestions(
    context: android.content.Context,
    pool: List<QuestionEntity>
): List<QuestionEntity> {
    if (pool.isEmpty()) return emptyList()

    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val today = dateFormat.format(Date())
    val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val yesterday = dateFormat.format(calendar.time)
    val prefs = context.getSharedPreferences("gk_swal_daily_challenge", android.content.Context.MODE_PRIVATE)
    val todayKey = "questions_$today"
    val storedTodayIds = prefs.getString(todayKey, null)
    if (!storedTodayIds.isNullOrBlank()) {
        val byId = pool.associateBy { it.id }
        val saved = storedTodayIds.split("|").mapNotNull { byId[it] }
        if (saved.isNotEmpty()) return saved.take(5)
    }

    val yesterdayIds = prefs.getString("questions_$yesterday", "")
        .orEmpty().split("|").filter { it.isNotBlank() }.toSet()
    val freshPool = pool.filterNot { it.id in yesterdayIds }.ifEmpty { pool }
    val seed = today.hashCode().toLong()
    val chosen = freshPool.shuffled(kotlin.random.Random(seed)).take(minOf(5, freshPool.size))
    prefs.edit().putString(todayKey, chosen.joinToString("|") { it.id }).apply()

    // Keep only a small recent window of daily ID lists to prevent preference growth.
    val oldCalendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -3) }
    val oldDate = dateFormat.format(oldCalendar.time)
    prefs.edit().remove("questions_$oldDate").apply()
    return chosen
}
