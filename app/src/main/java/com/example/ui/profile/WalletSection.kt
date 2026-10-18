package com.example.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

private data class WithdrawalItem(val id: String, val amountPaise: Long, val status: String, val createdLabel: String)
private data class LedgerItem(val id: String, val amountPaise: Long, val type: String, val status: String, val createdLabel: String)
private data class RewardClaimItem(val id: String, val title: String, val status: String, val createdLabel: String)

@Composable
fun WalletSection(modifier: Modifier = Modifier) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id)) }
    val functions = remember { FirebaseFunctions.getInstance() }
    val scope = rememberCoroutineScope()
    var balancePaise by remember { mutableStateOf(0L) }
    var earnedPaise by remember { mutableStateOf(0L) }
    var withdrawals by remember { mutableStateOf(emptyList<WithdrawalItem>()) }
    var ledgerItems by remember { mutableStateOf(emptyList<LedgerItem>()) }
    var rewardClaims by remember { mutableStateOf(emptyList<RewardClaimItem>()) }
    var showClaimDialog by remember { mutableStateOf(false) }
    var claimTitle by remember { mutableStateOf("") }
    var claimTotal by remember { mutableStateOf("10") }
    var claimCorrect by remember { mutableStateOf("8") }
    var claimDuration by remember { mutableStateOf("120") }
    var showDialog by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var upiText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    DisposableEffect(uid) {
        val walletListener = db.collection("wallets").document(uid).addSnapshotListener { snapshot, _ ->
            balancePaise = snapshot?.getLong("balancePaise") ?: 0L
            earnedPaise = snapshot?.getLong("lifetimeEarnedPaise") ?: 0L
        }
        val ledgerListener = db.collection("wallets").document(uid).collection("transactions").orderBy("createdAt", Query.Direction.DESCENDING).limit(10)
            .addSnapshotListener { snapshot, _ ->
                ledgerItems = snapshot?.documents?.map { doc ->
                    LedgerItem(
                        id = doc.id,
                        amountPaise = doc.getLong("amountPaise") ?: 0L,
                        type = doc.getString("type") ?: "transaction",
                        status = doc.getString("status") ?: "completed",
                        createdLabel = doc.getTimestamp("createdAt")?.toDate()?.let { java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(it) } ?: "Just now"
                    )
                } ?: emptyList()
            }
        val withdrawalListener = db.collection("withdrawal_requests").whereEqualTo("uid", uid).limit(10)
            .addSnapshotListener { snapshot, _ ->
                withdrawals = snapshot?.documents?.map { doc ->
                    WithdrawalItem(
                        id = doc.id,
                        amountPaise = doc.getLong("amountPaise") ?: 0L,
                        status = doc.getString("status") ?: "pending",
                        createdLabel = doc.getTimestamp("createdAt")?.toDate()?.let { java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(it) } ?: "Just now"
                    )
                } ?: emptyList()
            }
        val claimsListener = db.collection("reward_claims").whereEqualTo("uid", uid).limit(10)
            .addSnapshotListener { snapshot, _ ->
                rewardClaims = snapshot?.documents?.map { doc ->
                    RewardClaimItem(
                        id = doc.id,
                        title = doc.getString("quizTitle") ?: "Practice reward",
                        status = doc.getString("status") ?: "pending_review",
                        createdLabel = doc.getTimestamp("createdAt")?.toDate()?.let { java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(it) } ?: "Just now"
                    )
                } ?: emptyList()
            }
        onDispose { walletListener.remove(); ledgerListener.remove(); withdrawalListener.remove(); claimsListener.remove() }
    }

    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Earning Wallet", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Available balance", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatRupees(balancePaise), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Lifetime credited", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatRupees(earnedPaise), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                }
            }
            Text("Rewards are reviewed before credit. Quiz results are currently stored locally, so submitting a claim does not guarantee a reward. UPI transfers are processed manually by the operator.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { claimTitle = ""; claimTotal = "10"; claimCorrect = "8"; claimDuration = "120"; message = null; showClaimDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Request practice reward review")
            }
            if (rewardClaims.isNotEmpty()) {
                Text("Practice reward claims", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                rewardClaims.forEach { claim ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(claim.title, style = MaterialTheme.typography.bodyMedium)
                            Text(claim.createdLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(claim.status.replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Button(onClick = { amountText = ""; upiText = ""; message = null; showDialog = true }, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.Icon(Icons.Default.Payments, contentDescription = null)
                Text(" Request UPI withdrawal")
            }
            if (!message.isNullOrBlank()) Text(message!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            if (ledgerItems.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.Icon(Icons.Default.History, contentDescription = null)
                    Text("Recent wallet transactions", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
                ledgerItems.forEach { item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.type.replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                            Text("${item.createdLabel} · ${item.status}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text((if (item.amountPaise > 0) "+" else "") + formatRupees(item.amountPaise), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = if (item.amountPaise > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            if (withdrawals.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.Icon(Icons.Default.History, contentDescription = null)
                    Text("Withdrawal requests", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
                withdrawals.forEach { item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(formatRupees(item.amountPaise), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(item.createdLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(item.status.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = when (item.status) {
                            "paid", "completed" -> MaterialTheme.colorScheme.primary
                            "rejected" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        })
                    }
                }
            }
        }
    }

    if (showClaimDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) showClaimDialog = false },
            title = { Text("Practice reward review") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Claims are manually reviewed. Quiz data currently runs locally; do not submit false details. No reward is credited automatically.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = claimTitle, onValueChange = { claimTitle = it.take(100) }, label = { Text("Quiz / practice title") }, singleLine = true)
                    OutlinedTextField(value = claimTotal, onValueChange = { claimTotal = it.filter { ch -> ch.isDigit() }.take(3) }, label = { Text("Total questions (5–100)") }, singleLine = true)
                    OutlinedTextField(value = claimCorrect, onValueChange = { claimCorrect = it.filter { ch -> ch.isDigit() }.take(3) }, label = { Text("Correct answers") }, singleLine = true)
                    OutlinedTextField(value = claimDuration, onValueChange = { claimDuration = it.filter { ch -> ch.isDigit() }.take(5) }, label = { Text("Duration in seconds") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    val total = claimTotal.toIntOrNull() ?: 0
                    val correct = claimCorrect.toIntOrNull() ?: -1
                    val duration = claimDuration.toIntOrNull() ?: 0
                    if (claimTitle.isBlank() || total !in 5..100 || correct !in 0..total || duration !in 10..7200) {
                        message = "Quiz title, questions, score aur duration sahi bharein."; return@TextButton
                    }
                    busy = true; message = null
                    scope.launch {
                        try {
                            functions.getHttpsCallable("submitPracticeRewardClaim").call(mapOf(
                                "quizTitle" to claimTitle.trim(), "totalQuestions" to total,
                                "correctAnswers" to correct, "durationSeconds" to duration
                            )).await()
                            message = "Claim review ke liye submit ho gayi. Reward automatic credit nahi hota."
                            showClaimDialog = false
                        } catch (e: Exception) {
                            message = e.localizedMessage ?: "Claim submit nahi hui. Firebase Functions deployment check karein."
                        } finally { busy = false }
                    }
                }) { if (busy) CircularProgressIndicator() else Text("Submit for review") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { showClaimDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) showDialog = false },
            title = { Text("UPI withdrawal request") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Available: ${formatRupees(balancePaise)} · Minimum ₹100")
                    OutlinedTextField(value = amountText, onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' }.take(9) }, label = { Text("Amount in ₹") }, singleLine = true)
                    OutlinedTextField(value = upiText, onValueChange = { upiText = it.trim().take(255) }, label = { Text("UPI ID (name@bank)") }, singleLine = true)
                    Text("Requesting withdrawal reserves the amount. Rejected requests are refunded. A request is not an instant payment.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!message.isNullOrBlank()) Text(message!!, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    val paise = try { BigDecimal(amountText).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() } catch (_: Exception) { -1L }
                    if (paise < 10000L || paise > 1000000L) { message = "Amount ₹100 से ₹10,000 के बीच डालें।"; return@TextButton }
                    if (!Regex("^[A-Za-z0-9][A-Za-z0-9._-]{1,127}@[A-Za-z][A-Za-z0-9.-]{1,63}$").matches(upiText)) { message = "Valid UPI ID डालें, जैसे name@bank."; return@TextButton }
                    busy = true; message = null
                    scope.launch {
                        try {
                            functions.getHttpsCallable("requestWalletWithdrawal").call(mapOf("amountPaise" to paise, "upiId" to upiText)).await()
                            message = "Request submit हो गई। भुगतान होने पर status update होगा।"
                            showDialog = false
                        } catch (e: Exception) {
                            message = e.localizedMessage ?: "Request नहीं हो सकी। Firebase Functions deployment जाँचें।"
                        } finally { busy = false }
                    }
                }) { if (busy) CircularProgressIndicator() else Text("Submit request") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { showDialog = false }) { Text("Cancel") } }
        )
    }
}

private fun formatRupees(paise: Long): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(paise / 100.0)
