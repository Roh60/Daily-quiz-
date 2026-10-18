package com.example.ui.library

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class CurrentAffairsSource(val name: String, val description: String, val url: String)
private data class CurrentAffairsItem(val title: String, val link: String, val published: String, val summary: String)

private suspend fun loadOfficialPibFeed(): List<CurrentAffairsItem> = withContext(Dispatchers.IO) {
    val connection = (URL("https://pib.gov.in/RssMain.aspx?ModId=6&Lang=1&Regid=1").openConnection() as HttpURLConnection)
    try {
        connection.connectTimeout = 8000
        connection.readTimeout = 10000
        connection.setRequestProperty("User-Agent", "GKSwalAndroid/1.0 (official RSS reader)")
        connection.connect()
        if (connection.responseCode !in 200..299) throw IllegalStateException("RSS server returned ${connection.responseCode}")
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(connection.inputStream, "UTF-8")
        val items = mutableListOf<CurrentAffairsItem>()
        var event = parser.eventType
        var inItem = false
        var tag = ""
        var title = ""
        var link = ""
        var published = ""
        var summary = ""
        while (event != XmlPullParser.END_DOCUMENT && items.size < 30) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name.lowercase()
                    if (tag == "item") { inItem = true; title = ""; link = ""; published = ""; summary = "" }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inItem) {
                    val value = parser.text?.trim().orEmpty()
                    when (tag) {
                        "title" -> if (title.isBlank()) title = value
                        "link" -> if (link.isBlank()) link = value
                        "pubdate", "published", "dc:date" -> if (published.isBlank()) published = value
                        "description" -> if (summary.isBlank()) summary = value
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name.equals("item", true)) {
                    if (title.isNotBlank() && link.startsWith("https://")) {
                        items += CurrentAffairsItem(title, link, published, android.text.Html.fromHtml(summary, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim())
                    }
                    inItem = false
                }
            }
            event = parser.next()
        }
        items
    } finally { connection.disconnect() }
}

private val currentAffairsSources = listOf(
    CurrentAffairsSource("Press Information Bureau (PIB)", "Government of India announcements and press releases", "https://pib.gov.in/"),
    CurrentAffairsSource("Reserve Bank of India", "Monetary policy, banking and financial updates", "https://www.rbi.org.in/"),
    CurrentAffairsSource("ISRO", "Space missions and science announcements", "https://www.isro.gov.in/"),
    CurrentAffairsSource("PRS Legislative Research", "Bills, Parliament and policy explainers", "https://prsindia.org/"),
    CurrentAffairsSource("Election Commission of India", "Election-related official announcements", "https://www.eci.gov.in/"),
    CurrentAffairsSource("Ministry of External Affairs", "Foreign affairs and official briefings", "https://www.mea.gov.in/"),
    CurrentAffairsSource("Ministry of Finance", "Budget, economic and finance announcements", "https://www.finmin.nic.in/"),
    CurrentAffairsSource("Supreme Court of India", "Judgments, notices and official updates", "https://www.sci.gov.in/")
)

@Composable
fun CurrentAffairsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var feedItems by remember { mutableStateOf<List<CurrentAffairsItem>>(emptyList()) }
    var feedLoading by remember { mutableStateOf(true) }
    var feedError by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    LaunchedEffect(refreshKey) {
        feedLoading = true
        feedError = false
        try {
            feedItems = loadOfficialPibFeed()
            feedError = feedItems.isEmpty()
        } catch (_: Exception) {
            feedError = true
        } finally {
            feedLoading = false
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Current Affairs & Official Sources", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Live headlines are loaded from the official Press Information Bureau RSS feed when internet is available. Headlines and publication dates come from the source; open the original release to verify details.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Text("Latest official updates", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Source: Press Information Bureau, Government of India", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (feedLoading) Text("Loading latest headlines…", style = MaterialTheme.typography.bodyMedium)
            if (feedError) Text("Live feed is temporarily unavailable. Check your internet connection or open the official source below.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            Button(onClick = { refreshKey += 1 }, enabled = !feedLoading) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Text(if (feedLoading) "Refreshing…" else "Refresh headlines", modifier = Modifier.padding(start = 8.dp))
            }
        }
        items(feedItems) { item ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (item.published.isNotBlank()) Text("Published: ${item.published}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    if (item.summary.isNotBlank()) Text(item.summary.take(360), style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Button(onClick = {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link))) }
                        catch (_: Exception) { Toast.makeText(context, "Could not open this article", Toast.LENGTH_SHORT).show() }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Text("Read original release", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        items(currentAffairsSources) { source ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(source.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url)))
                        } catch (_: Exception) {
                            Toast.makeText(context, "Could not open this source", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Text("Open official source", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Text("For exam preparation", fontWeight = FontWeight.Bold)
                    Text("This feed shows official press releases, not independent fact-checking or a curated exam digest. Dates/headlines depend on the source feed. Verify important claims in the original release before using them for exam preparation.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
