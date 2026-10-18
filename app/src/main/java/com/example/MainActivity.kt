package com.example

import android.app.Activity
import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.credentials.CredentialManager
import com.example.ads.AdBanner
import com.example.ads.AdsConsentManager
import com.example.data.repository.GkBookRepository
import com.example.data.repository.SocialFeedRepository
import com.example.ui.auth.AuthManager
import com.example.ui.auth.AuthScreen
import com.example.ui.bookmarks.BookmarksAndStatsScreen
import com.example.ui.feed.SocialFeedScreen
import com.example.ui.library.LibraryScreen
import com.example.ui.library.CurrentAffairsScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.profile.EarnToolsScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Install App Check before any Auth/Firestore/Functions SDK is used.
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            if (BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance()
            else PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        setContent {
            MyApplicationTheme {
                AppRootContent()
            }
        }
        // Consent is requested before any ad request. Ads stay hidden until UMP allows them.
        AdsConsentManager.requestConsent(this)
    }
}

@Composable
fun AppRootContent() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        // Attempt silent auto-sign-in on app launch if account was already linked
        AuthManager.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = { currentUser = Firebase.auth.currentUser },
            onUnauthenticated = {},
            scope = coroutineScope
        )
    }

    if (currentUser == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        MainAppScaffold(
            currentUserId = currentUser!!.uid
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    currentUserId: String
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val bookRepository = remember { GkBookRepository(context) }
    val socialRepository = remember { SocialFeedRepository(context) }

    LaunchedEffect(currentUserId) {
        // Synchronize initial user profile in Firestore
        coroutineScope.launch {
            socialRepository.syncUserProfile()
        }
    }

    var selectedNavIndex by remember { mutableIntStateOf(0) }

    val navItems = listOf(
        NavigationItem("Learn", Icons.AutoMirrored.Filled.MenuBook, "nav_library"),
        NavigationItem("Community", Icons.Default.Poll, "nav_feed"),
        NavigationItem("Saved", Icons.Default.Bookmark, "nav_bookmarks"),
        NavigationItem("Profile", Icons.Default.Person, "nav_profile"),
        NavigationItem("Earn", Icons.Default.EmojiEvents, "nav_earn"),
        NavigationItem("News", Icons.Default.Newspaper, "nav_current_affairs")
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text(
                            text = "GK Swal",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = when (selectedNavIndex) {
                                0 -> "Learn something new every day"
                                1 -> "Learn together"
                                2 -> "Your saved questions & progress"
                                3 -> "Your learning profile"
                                4 -> "Secure rewards and useful tools"
                                else -> "Explore trusted updates"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (AdsConsentManager.privacyOptionsRequired.value) {
                        IconButton(onClick = { context.findActivity()?.let(AdsConsentManager::showPrivacyOptions) }) {
                            Icon(Icons.Default.PrivacyTip, contentDescription = "Privacy choices")
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column {
                AdBanner()
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    navItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selectedNavIndex == index,
                            onClick = { selectedNavIndex = index },
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title) },
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedNavIndex) {
                0 -> LibraryScreen(repository = bookRepository)
                1 -> SocialFeedScreen(repository = socialRepository)
                2 -> BookmarksAndStatsScreen(repository = bookRepository)
                3 -> ProfileScreen(
                    bookRepository = bookRepository,
                    socialRepository = socialRepository,
                    onSignOut = { /* Auth state listener triggers login screen */ },
                    onNavigateToBookmarks = { selectedNavIndex = 2 }
                )
                4 -> EarnToolsScreen()
                else -> CurrentAffairsScreen()
            }
        }
    }
}

data class NavigationItem(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val testTag: String
)


private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
