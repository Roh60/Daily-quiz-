package com.example.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.ui.unit.dp
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import org.json.JSONObject

private data class ServerQuizQuestion(val id: String, val question: String, val options: List<String>, val order: Int)
private data class CurrencyGKQuestion(val question: String, val options: List<String>, val correctIndex: Int, val explanation: String)

private val currencyGKFallbackQuestions = listOf(
    CurrencyGKQuestion(
        question = "भारत की मुद्रा का नाम क्या है?",
        options = listOf("USD — अमेरिकी डॉलर", "INR — भारतीय रुपया", "EURO — यूरो", "Yuan — चीनी युआन"),
        correctIndex = 1,
        explanation = "भारत की आधिकारिक मुद्रा भारतीय रुपया (INR) है।"
    ),
    CurrencyGKQuestion(
        question = "इस अभ्यास प्रश्न के अनुसार \$1 की कीमत भारत में कितने रुपये है?",
        options = listOf("₹95", "₹96", "₹97", "₹99"),
        correctIndex = 1,
        explanation = "इस अभ्यास सेट में सही उत्तर ₹96 दिया गया है। यह लाइव exchange rate नहीं है।"
    ),
    CurrencyGKQuestion(
        question = "इस अभ्यास प्रश्न के अनुसार €1 की कीमत भारत में कितने रुपये है?",
        options = listOf("₹108", "₹109", "₹112", "₹116"),
        correctIndex = 0,
        explanation = "इस अभ्यास सेट में सही उत्तर ₹108 दिया गया है। यह लाइव exchange rate नहीं है।"
    ),
    CurrencyGKQuestion(
        question = "चीन की मुद्रा की मूल इकाई क्या कहलाती है?",
        options = listOf("Yen", "Won", "Yuan", "Rupee"),
        correctIndex = 2,
        explanation = "चीन की मुद्रा renminbi है और उसकी मूल इकाई yuan कहलाती है।"
    ),
    CurrencyGKQuestion(
        question = "जापान की मुद्रा क्या है?",
        options = listOf("Yen (JPY)", "Euro (EUR)", "Dollar (USD)", "Yuan (CNY)"),
        correctIndex = 0,
        explanation = "जापान की मुद्रा yen (JPY) है।"
    )
)

@Composable
fun EarnToolsScreen() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val functions = remember { FirebaseFunctions.getInstance() }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Rewards are available only for published server-verified quizzes. One cash reward per UTC day; eligibility is controlled by the server.") }
    var quizId by remember { mutableStateOf("daily-general") }
    var sessionId by remember { mutableStateOf("") }
    var questions by remember { mutableStateOf(emptyList<ServerQuizQuestion>()) }
    val answers = remember { mutableStateMapOf<String, Int>() }
    var currencyGKQuestions by remember { mutableStateOf(currencyGKFallbackQuestions) }
    var currencyBankLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(context) {
        try {
            val root = JSONObject(context.assets.open("global_currency_gk_questions.json").bufferedReader().use { it.readText() })
            val rows = root.getJSONArray("questions")
            val loaded = (0 until rows.length()).mapNotNull { i ->
                val item = rows.optJSONObject(i) ?: return@mapNotNull null
                val optionsJson = item.optJSONArray("options") ?: return@mapNotNull null
                val options = (0 until optionsJson.length()).map { j -> optionsJson.optString(j) }
                if (options.size != 4) return@mapNotNull null
                CurrencyGKQuestion(
                    question = item.optString("question"),
                    options = options,
                    correctIndex = item.optInt("correctIndex", -1),
                    explanation = item.optString("explanation")
                ).takeIf { it.question.isNotBlank() && it.correctIndex in 0..3 }
            }
            if (loaded.isNotEmpty()) currencyGKQuestions = loaded
        } catch (_: Exception) {
            // Keep the small built-in fallback bank if the bundled JSON cannot be read.
        } finally {
            currencyBankLoaded = true
        }
    }
    var currencyQuestionIndex by remember { mutableStateOf(0) }
    var currencySelectedOption by remember { mutableStateOf(-1) }
    var currencyAnswerSubmitted by remember { mutableStateOf(false) }
    var currencyQuizScore by remember { mutableStateOf(0) }
    var referralCode by remember { mutableStateOf("") }
    var referralInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Earn & Tools", style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Server-verified quiz", style = MaterialTheme.typography.titleLarge)
                Text("Only quizzes published in the secure server question bank can award wallet rewards. Your answer key is checked on the server, not trusted from the phone.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = quizId, onValueChange = { quizId = it }, label = { Text("Published quiz ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val data = functions.getHttpsCallable("startVerifiedQuizSession").call(mapOf("quizId" to quizId.trim())).await().data as Map<*, *>
                            sessionId = data["sessionId"].toString()
                            val list = data["questions"] as? List<*> ?: emptyList<Any>()
                            questions = list.mapNotNull { item ->
                                val q = item as? Map<*, *> ?: return@mapNotNull null
                                val opts = (q["options"] as? List<*>)?.map { it.toString() } ?: emptyList()
                                val id = q["id"]?.toString() ?: return@mapNotNull null
                                ServerQuizQuestion(id, q["question"].toString(), opts, (q["order"] as? Number)?.toInt() ?: 0)
                            }.sortedBy { it.order }
                            answers.clear()
                            message = if (questions.isEmpty()) "No questions returned. Check the published quiz configuration." else "Quiz started. Submit before the 30-minute session expires."
                        } catch (e: Exception) { message = "Could not start quiz: ${e.message ?: "Please try again."}" }
                        finally { busy = false }
                    }
                }, enabled = !busy && quizId.isNotBlank()) { Text("Start secure quiz") }
                if (busy) CircularProgressIndicator()
                questions.forEachIndexed { index, q ->
                    Spacer(Modifier.height(4.dp))
                    Text("${index + 1}. ${q.question}", style = MaterialTheme.typography.titleMedium)
                    q.options.forEachIndexed { optionIndex, option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = answers[q.id] == optionIndex, onClick = { answers[q.id] = optionIndex })
                            Text(option, modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (questions.isNotEmpty() && sessionId.isNotBlank()) {
                    Button(onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val result = functions.getHttpsCallable("submitVerifiedQuizSession").call(mapOf("sessionId" to sessionId, "answers" to answers.toMap())).await().data as Map<*, *>
                                message = "Result: ${result["correct"]}/${result["total"]} correct (${result["percent"]}%). Reward credited: ₹${((result["rewardPaise"] as? Number)?.toDouble() ?: 0.0) / 100.0}. A reward of ₹0 means the daily limit or quiz threshold was not met."
                                questions = emptyList(); sessionId = ""; answers.clear()
                            } catch (e: Exception) { message = "Could not submit quiz: ${e.message ?: "Please try again."}" }
                            finally { busy = false }
                        }
                    }, enabled = !busy) { Text("Submit answers") }
                }
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Referral rewards", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = {
                    scope.launch { busy = true; try { val d = functions.getHttpsCallable("getMyReferralCode").call().await().data as Map<*, *>; referralCode = d["code"].toString() } catch (e: Exception) { message = "Referral code error: ${e.message}" } finally { busy = false } }
                }, enabled = !busy) { Text("Get my referral code") }
                if (referralCode.isNotBlank()) {
                    Text("Your code: $referralCode", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Join GK Swal for GK practice. Use my referral code: $referralCode")
                        }
                        context.startActivity(Intent.createChooser(share, "Share referral code"))
                    }) { Text("Share invite") }
                }
                OutlinedTextField(value = referralInput, onValueChange = { referralInput = it.uppercase(Locale.ROOT) }, label = { Text("Friend's referral code") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = {
                    scope.launch { busy = true; try { val d = functions.getHttpsCallable("redeemReferralCode").call(mapOf("code" to referralInput.trim())).await().data as Map<*, *>; message = d["message"].toString(); referralInput = "" } catch (e: Exception) { message = "Referral request failed: ${e.message}" } finally { busy = false } }
                }, enabled = !busy && referralInput.isNotBlank()) { Text("Submit referral") }
                Text("Referral rewards are pending review and may be rejected for abuse or ineligible accounts.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Currency GK Quiz", style = MaterialTheme.typography.titleLarge)
                Text("दुनिया के देशों, मुद्रा के नाम और ISO codes पर ${currencyGKQuestions.size}+ MCQ सवाल।", style = MaterialTheme.typography.bodyMedium)
                Text(if (currencyBankLoaded) "Global Currency question bank loaded." else "Question bank लोड हो रहा है…", style = MaterialTheme.typography.bodySmall)
                Text("ध्यान दें: रुपये की कीमत वाले सवाल अभ्यास के उदाहरण हैं, लाइव बाजार दर नहीं।", style = MaterialTheme.typography.bodySmall)
                val currencyQuestion = currencyGKQuestions[currencyQuestionIndex]
                Text("प्रश्न ${currencyQuestionIndex + 1}/${currencyGKQuestions.size}  •  Score: $currencyQuizScore", style = MaterialTheme.typography.labelLarge)
                Text(currencyQuestion.question, style = MaterialTheme.typography.titleMedium)
                currencyQuestion.options.forEachIndexed { optionIndex, option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = currencySelectedOption == optionIndex,
                            onClick = { if (!currencyAnswerSubmitted) currencySelectedOption = optionIndex },
                            enabled = !currencyAnswerSubmitted
                        )
                        Text(option, modifier = Modifier.weight(1f))
                    }
                }
                if (currencyAnswerSubmitted) {
                    Text(
                        if (currencySelectedOption == currencyQuestion.correctIndex) "सही उत्तर!" else "गलत उत्तर। सही उत्तर: ${currencyQuestion.options[currencyQuestion.correctIndex]}",
                        color = if (currencySelectedOption == currencyQuestion.correctIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(currencyQuestion.explanation, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {
                        if (currencyQuestionIndex < currencyGKQuestions.lastIndex) {
                            currencyQuestionIndex += 1
                            currencySelectedOption = -1
                            currencyAnswerSubmitted = false
                        } else {
                            currencyQuestionIndex = 0
                            currencySelectedOption = -1
                            currencyAnswerSubmitted = false
                            currencyQuizScore = 0
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (currencyQuestionIndex < currencyGKQuestions.lastIndex) "अगला प्रश्न" else "फिर से शुरू करें")
                    }
                } else {
                    Button(
                        onClick = {
                            if (currencySelectedOption >= 0) {
                                if (currencySelectedOption == currencyQuestion.correctIndex) currencyQuizScore += 1
                                currencyAnswerSubmitted = true
                            }
                        },
                        enabled = currencySelectedOption >= 0,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("उत्तर जांचें") }
                }
            }
        }
    }
}
