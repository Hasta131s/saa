package com.example

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.CheckerState
import com.example.data.Hit
import com.example.data.HitRepository
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class MainActivity : ComponentActivity() {

    private lateinit var hitRepository: HitRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(this)
        hitRepository = HitRepository(database.hitDao())

        setContent {
            MyApplicationTheme {
                val context = LocalContext.current

                // Handle post notifications permission on target sdk 33+
                var hasNotificationPermission by remember {
                    mutableStateOf(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        } else {
                            true
                        }
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        hasNotificationPermission = isGranted
                    }
                )

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BlackBackground)
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(BlackBackground)
                    ) {
                        DashboardScreen(
                            hitRepository = hitRepository,
                            onStartChecking = {
                                val intent = Intent(context, com.example.service.CheckerService::class.java)
                                context.startService(intent)
                            },
                            onStopChecking = {
                                CheckerState.stop()
                                val intent = Intent(context, com.example.service.CheckerService::class.java)
                                context.stopService(intent)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    hitRepository: HitRepository,
    onStartChecking: () -> Unit,
    onStopChecking: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Observe central flow states
    val isChecking by CheckerState.isChecking.collectAsStateWithLifecycle()
    val totalCount by CheckerState.totalCount.collectAsStateWithLifecycle()
    val checkedCount by CheckerState.checkedCount.collectAsStateWithLifecycle()
    val successCount by CheckerState.successCount.collectAsStateWithLifecycle()
    val failureCount by CheckerState.failureCount.collectAsStateWithLifecycle()
    val currentEmail by CheckerState.currentEmail.collectAsStateWithLifecycle()
    val currentPassword by CheckerState.currentPassword.collectAsStateWithLifecycle()
    val soundEnabled by CheckerState.soundEnabled.collectAsStateWithLifecycle()
    val simulationMode by CheckerState.simulationMode.collectAsStateWithLifecycle()
    val currentUrl by CheckerState.currentCustomUrl.collectAsStateWithLifecycle()

    // Local inputs & view states
    var textInput by remember { mutableStateOf("") }
    var apiTargetUrlInput by remember { mutableStateOf(currentUrl) }
    var activeTab by remember { mutableStateOf(0) } // 0: Config/Status, 1: Live Logs (hits inside db)
    var showUrlConfigDialog by remember { mutableStateOf(false) }

    val dbHits by hitRepository.allHitsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // Progress calculations
    val progressFraction = remember(checkedCount, totalCount) {
        if (totalCount > 0) checkedCount.toFloat() / totalCount else 0f
    }
    val progressPercentString = remember(progressFraction) {
        "${(progressFraction * 100).toInt()}%"
    }

    // Result download callback
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    val success = exportSuccessfulHitsToUri(context, uri, dbHits)
                    if (success) {
                        Toast.makeText(context, "Hits başarıyla kaydedildi!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Dosya kaydetme hatası!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    // Handle initial custom test inputs load
    LaunchedEffect(Unit) {
        if (textInput.isEmpty()) {
            textInput = """ArianaGrande190@gmail.com:123456787
test@example.com:123456
user99@mail.ru:password123
admin@bosforlab.online:admin1234
valid_test_account@gmail.com:mypassword999"""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackBackground)
    ) {
        // App Header
        HeaderBar(
            onConfigUrlClick = { showUrlConfigDialog = true },
            simulationMode = simulationMode,
            onToggleSimulation = { CheckerState.simulationMode.value = !simulationMode }
        )

        // Global Progress Panel
        AnimatedVisibility(
            visible = isChecking || checkedCount > 0,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            ProgressPanel(
                progressPercentString = progressPercentString,
                progressFraction = progressFraction,
                isChecking = isChecking,
                currentEmail = currentEmail,
                currentPassword = currentPassword
            )
        }

        // Horizontal Segmented Switch (Combo Input vs Successful Hits)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(CharcoalSurface, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TabButton(
                text = "COMBO VE AYARLAR",
                isActive = activeTab == 0,
                modifier = Modifier.weight(1f),
                onClick = { activeTab = 0 }
            )
            TabButton(
                text = "BAŞARILI HİTLER (${dbHits.size})",
                isActive = activeTab == 1,
                modifier = Modifier.weight(1f),
                onClick = { activeTab = 1 }
            )
        }

        // Main working space
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            if (activeTab == 0) {
                // Settings & Combo editor
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Quick stats indicators
                    StatsOverviewGrid(
                        total = totalCount,
                        remaining = if (totalCount >= checkedCount) totalCount - checkedCount else 0,
                        success = successCount,
                        failed = failureCount
                    )

                    // Text editor for accounts
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .border(1.dp, AccentBorder, RoundedCornerShape(16.dp))
                            .background(CharcoalSurface)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        // Title bar of Combo card
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF141414))
                                .border(width = 0.dp, color = Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "COMBO LİSTESİ (MAIL:PASS VEYA USER:PASS)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedText,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TXT / UTF-8",
                                style = MaterialTheme.typography.labelSmall,
                                color = DarkMuted
                            )
                        }

                        // TextField Box
                        TextField(
                            value = textInput,
                            onValueChange = {
                                if (!isChecking) {
                                    textInput = it
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            placeholder = {
                                Text(
                                    "Buraya mail:şifre formatında comboları giriniz...",
                                    color = DarkMuted,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            readOnly = isChecking,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    }

                    // Toggles (Sound state, Load list quick indicators)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CharcoalSurface, RoundedCornerShape(12.dp))
                            .border(1.dp, AccentBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageOfSound(soundEnabled),
                                contentDescription = "Ses durumu",
                                tint = if (soundEnabled) WhiteAccent else DarkMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (soundEnabled) "İşlem Bittiğinde Sesli Uyarı: AÇIK" else "İşlem Bittiğinde Sesli Uyarı: KAPALI",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (soundEnabled) WhiteAccent else MutedText
                            )
                        }
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = { CheckerState.soundEnabled.value = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BlackBackground,
                                checkedTrackColor = WhiteAccent,
                                uncheckedThumbColor = DarkMuted,
                                uncheckedTrackColor = CharcoalSurface
                            )
                        )
                    }
                }
            } else {
                // Successful Hits log view
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "YAKALANAN BAŞARILI HESAPLAR",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText,
                            fontWeight = FontWeight.Bold
                        )
                        if (dbHits.isNotEmpty()) {
                            Text(
                                text = "TÜMÜNÜ SİL",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        coroutineScope.launch {
                                            hitRepository.deleteAll()
                                            Toast
                                                .makeText(context, "Hits temizlendi", Toast.LENGTH_SHORT)
                                                .show()
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                color = ErrorRed,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (dbHits.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .border(1.dp, AccentBorder, RoundedCornerShape(16.dp))
                                .background(CharcoalSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "No hits",
                                    tint = DarkMuted,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "Henüz başarılı hits yok.",
                                    color = MutedText,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Comboları girip Başlat butonuna basınız.",
                                    color = DarkMuted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .border(1.dp, AccentBorder, RoundedCornerShape(16.dp))
                                .background(CharcoalSurface)
                                .clip(RoundedCornerShape(16.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(dbHits) { hit ->
                                HitItemRow(hit) {
                                    // Quick copy to clipboard
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("KayraSQL Hit", "${hit.email}:${hit.password}")
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Kopyalandı: ${hit.email}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Control Bar
        FooterControlBar(
            isChecking = isChecking,
            dbHits = dbHits,
            onStart = {
                if (textInput.trim().isEmpty()) {
                    Toast.makeText(context, "Lütfen önce bir combo listesi giriniz!", Toast.LENGTH_SHORT).show()
                    return@FooterControlBar
                }

                // Analyze text input as email:password list
                val parsed = parseCombos(textInput)
                if (parsed.isEmpty()) {
                    Toast.makeText(context, "Geçersiz combo formatı! Satırları kontrol edin.", Toast.LENGTH_SHORT).show()
                    return@FooterControlBar
                }

                // Initialize State
                CheckerState.reset(parsed.size, parsed)
                onStartChecking()
            },
            onStop = {
                onStopChecking()
            },
            onDownloadClick = {
                // Let user select target or save directly to Downloads via MediaStore/SAF
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    coroutineScope.launch {
                        val fileUri = createTxtFileInDownloads(context, "KAYRASQL_HITS_${System.currentTimeMillis()}.txt")
                        if (fileUri != null) {
                            val success = exportSuccessfulHitsToUri(context, fileUri, dbHits)
                            if (success) {
                                Toast.makeText(context, "Hits Downloads klasörüne kaydedildi!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Dosya kayıt hatası!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            // Fallback to SAF picker
                            filePickerLauncher.launch("KAYRASQL_HITS.txt")
                        }
                    }
                } else {
                    filePickerLauncher.launch("KAYRASQL_HITS.txt")
                }
            }
        )
    }

    // Config Endpoint Dialog
    if (showUrlConfigDialog) {
        AlertDialog(
            onDismissRequest = { showUrlConfigDialog = false },
            title = {
                Text(
                    "API AYARI",
                    style = MaterialTheme.typography.titleLarge,
                    color = WhiteAccent
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Sorgunun gönderileceği vapi php endpoint adresini biçimlendirin:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedText
                    )
                    OutlinedTextField(
                        value = apiTargetUrlInput,
                        onValueChange = { apiTargetUrlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = AccentBorder
                        )
                    )
                    Text(
                        text = "Şablon sorgu parametri olarak &email=... ve &password=... otomatik eklenir.",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkMuted
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        CheckerState.currentCustomUrl.value = apiTargetUrlInput
                        showUrlConfigDialog = false
                        Toast.makeText(context, "API URL adresi güncellendi!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("KAYDET", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUrlConfigDialog = false }
                ) {
                    Text("VAZGEÇ", color = MutedText)
                }
            },
            containerColor = CharcoalSurface,
            tonalElevation = 6.dp
        )
    }
}

@Composable
fun HeaderBar(
    onConfigUrlClick: () -> Unit,
    simulationMode: Boolean,
    onToggleSimulation: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BlackBackground)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .border(width = 0.dp, color = Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(WhiteAccent, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "K",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = BlackBackground
                )
            }
            Column {
                Text(
                    text = "KAYRASQL SYSTEM",
                    style = MaterialTheme.typography.titleLarge,
                    color = WhiteAccent,
                    fontWeight = FontWeight.Black,
                    lineHeight = 22.sp
                )
                Text(
                    text = "Android Professional v2.4",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                    lineHeight = 12.sp
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onToggleSimulation,
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (simulationMode) Color(0xFF1E3A1E) else CharcoalSurface,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (simulationMode) Icons.Default.ThumbUp else Icons.Default.Info,
                    contentDescription = "Simülasyon Modu",
                    tint = if (simulationMode) SuccessGreen else MutedText,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = onConfigUrlClick,
                modifier = Modifier
                    .size(36.dp)
                    .background(CharcoalSurface, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "API Ayarı",
                    tint = WhiteAccent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ProgressPanel(
    progressPercentString: String,
    progressFraction: Float,
    isChecking: Boolean,
    currentEmail: String,
    currentPassword: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(CharcoalSurface, RoundedCornerShape(16.dp))
            .border(1.dp, AccentBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isChecking) SuccessGreen else MutedText, CircleShape)
                )
                Text(
                    text = if (isChecking) "SİSTEM DURUMU: ÇALIŞIYOR" else "SİSTEM DURUMU: DURAKLATILDI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = progressPercentString,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WhiteAccent
                )
            )
        }

        // Animated linear progress bar
        val animatedProgress by animateFloatAsState(
            targetValue = progressFraction,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "ProgressBar"
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(GreyAccent, RoundedCornerShape(3.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .background(WhiteAccent, RoundedCornerShape(3.dp))
            )
        }

        if (isChecking && currentEmail.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Test ediliyor",
                        tint = MutedText,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Sorgulanıyor: $currentEmail",
                        color = MutedText,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "şifre: $currentPassword",
                    color = DarkMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StatsOverviewGrid(
    total: Int,
    remaining: Int,
    success: Int,
    failed: Int
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                label = "TOPLAM",
                value = total.toString(),
                textColor = WhiteAccent,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "KALAN",
                value = remaining.toString(),
                textColor = MutedText,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                label = "BAŞARILI",
                value = success.toString(),
                textColor = SuccessGreen,
                backgroundColor = Color(0xFF1B2E1D),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "BAŞARISIZ",
                value = failed.toString(),
                textColor = DarkMuted,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    textColor: Color,
    backgroundColor: Color = CharcoalSurface,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(16.dp))
            .border(1.dp, AccentBorder, RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp, horizontal = 16.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MutedText,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = textColor
            )
        )
    }
}

@Composable
fun HitItemRow(
    hit: Hit,
    onCopyClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161616), RoundedCornerShape(10.dp))
            .border(1.dp, AccentBorder, RoundedCornerShape(10.dp))
            .clickable { onCopyClick() }
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(SuccessGreen, CircleShape)
                )
                Text(
                    text = hit.email,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "şifre: ${hit.password}",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MutedText
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onCopyClick,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Copy hit details",
                tint = MutedText,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
fun FooterControlBar(
    isChecking: Boolean,
    dbHits: List<Hit>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BlackBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onStart,
                enabled = !isChecking,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WhiteAccent,
                    contentColor = BlackBackground,
                    disabledContainerColor = CharcoalSurface,
                    disabledContentColor = DarkMuted
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Başlat")
                    Text(
                        text = "BAŞLAT",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Button(
                onClick = onStop,
                enabled = isChecking,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CharcoalSurface,
                    contentColor = WhiteAccent,
                    disabledContainerColor = CharcoalSurface,
                    disabledContentColor = DarkMuted
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    width = 1.dp
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Durdur")
                    Text(
                        text = "DURDUR",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Export hits
        OutlinedButton(
            onClick = onDownloadClick,
            enabled = dbHits.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White,
                disabledContentColor = DarkMuted
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                width = 1.dp
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "HİTLERİ İNDİR (.TXT)",
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "HİTLERİ İNDİR (.TXT)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TabButton(
    text: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) WhiteAccent else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isActive) BlackBackground else MutedText
        )
    }
}

// Combos Parser
fun parseCombos(text: String): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    val lines = text.split("\n")
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue
        
        // Find separators like : or ;
        val parts = if (trimmed.contains(":")) {
            trimmed.split(":", limit = 2)
        } else if (trimmed.contains(";")) {
            trimmed.split(";", limit = 2)
        } else {
            null
        }

        if (parts != null && parts.size == 2) {
            val u = parts[0].trim()
            val p = parts[1].trim()
            if (u.isNotEmpty() && p.isNotEmpty()) {
                result.add(Pair(u, p))
            }
        }
    }
    return result
}

// Sound alert icon resolver
fun imageOfSound(enabled: Boolean) = if (enabled) Icons.Default.Notifications else Icons.Default.PlayArrow

// Helper to write files to shared Downloads folder (API >= 29)
suspend fun createTxtFileInDownloads(context: Context, filename: String): Uri? {
    return withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            // Older versions can write via normal file API or SAF resolver fallback
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        try {
            resolver.insert(collection, contentValues)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

// Write the parsed Text payload into user chosen target URI stream
suspend fun exportSuccessfulHitsToUri(context: Context, uri: Uri, hits: List<Hit>): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
            if (outputStream != null) {
                val stringBuilder = StringBuilder()
                stringBuilder.append("=== KAYRASQL SYSTEM SUCCESSFUL HITS ===\n")
                stringBuilder.append("Tarih: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())}\n")
                stringBuilder.append("Toplam Hit Sayısı: ${hits.size}\n\n")
                
                for (hit in hits) {
                    stringBuilder.append("${hit.email}:${hit.password}\n")
                }
                
                outputStream.write(stringBuilder.toString().toByteArray())
                outputStream.flush()
                outputStream.close()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
