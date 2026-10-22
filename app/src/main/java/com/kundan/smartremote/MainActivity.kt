package com.kundan.smartremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Tv.init(this)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF4C8DFF), secondary = Color(0xFF7FD3A8),
                background = Color(0xFF0E1116), surface = Color(0xFF171B22),
                surfaceVariant = Color(0xFF222833)
            )) { App() }
        }
        // Reconnect automatically to the last TV.
        if (Tv.hasSaved && Tv.status.value != Status.CONNECTED) Tv.connect()
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    REMOTE("Remote", Icons.Filled.SettingsRemote),
    APPS("Apps", Icons.Filled.Apps),
    MACROS("Macros", Icons.Filled.AutoAwesome),
    CONNECT("Connect", Icons.Filled.Wifi)
}

@Composable
private fun App() {
    var tab by remember { mutableStateOf(Tab.REMOTE) }
    val status by Tv.status.collectAsState()
    LaunchedEffect(Unit) { if (!Tv.hasSaved) tab = Tab.CONNECT }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { StatusBar() },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t, onClick = { tab = t },
                        icon = { Icon(t.icon, null) }, label = { Text(t.label) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.REMOTE -> RemoteScreen()
                Tab.APPS -> AppsScreen()
                Tab.MACROS -> MacrosScreen()
                Tab.CONNECT -> ConnectScreen(onConnected = { tab = Tab.REMOTE })
            }
        }
    }
    // Jump to the remote as soon as a connection succeeds from the Connect tab.
    LaunchedEffect(status) { if (status == Status.CONNECTED && tab == Tab.CONNECT) tab = Tab.REMOTE }
}

@Composable
private fun StatusBar() {
    val status by Tv.status.collectAsState()
    val msg by Tv.message.collectAsState()
    val color = when (status) {
        Status.CONNECTED -> Color(0xFF3DDC84)
        Status.CONNECTING -> Color(0xFFFFC145)
        Status.ERROR -> Color(0xFFFF5C5C)
        Status.DISCONNECTED -> Color(0xFF8A93A3)
    }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(msg, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        if (status != Status.CONNECTED && status != Status.CONNECTING) {
            TextButton(onClick = { Tv.connect() }) { Text("Retry") }
        }
    }
}

// ---------------------------------------------------------------- Remote

@Composable
private fun RemoteButton(
    icon: ImageVector, label: String, modifier: Modifier = Modifier,
    size: Int = 64, tint: Color = MaterialTheme.colorScheme.onSurface,
    bg: Color = MaterialTheme.colorScheme.surfaceVariant, onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size.dp).clip(CircleShape).background(bg)
                .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
            contentAlignment = Alignment.Center
        ) { Icon(icon, label, tint = tint, modifier = Modifier.size((size * 0.45).dp)) }
        Text(label, fontSize = 11.sp, color = Color(0xFF9AA3B2), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun RemoteScreen() {
    var typed by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RemoteButton(Icons.Filled.PowerSettingsNew, "Power", tint = Color(0xFFFF6B6B)) { Tv.key(Key.POWER) }
            RemoteButton(Icons.Filled.Input, "Input") { Tv.key(Key.INPUT) }
            RemoteButton(Icons.Filled.Settings, "Settings") { Tv.key(Key.SETTINGS) }
            RemoteButton(Icons.Filled.Mic, "Assistant") { Tv.key(Key.ASSISTANT) }
        }
        Spacer(Modifier.height(20.dp))
        DPad()
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            RemoteButton(Icons.AutoMirrored.Filled.ArrowBack, "Back") { Tv.key(Key.BACK) }
            RemoteButton(Icons.Filled.Home, "Home") { Tv.key(Key.HOME) }
            RemoteButton(Icons.Filled.Menu, "Menu") { Tv.key(Key.MENU) }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RemoteButton(Icons.Filled.VolumeDown, "Vol −") { Tv.key(Key.VOL_DOWN) }
            RemoteButton(Icons.AutoMirrored.Filled.VolumeOff, "Mute") { Tv.key(Key.MUTE) }
            RemoteButton(Icons.Filled.VolumeUp, "Vol +") { Tv.key(Key.VOL_UP) }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RemoteButton(Icons.Filled.FastRewind, "Rewind", size = 52) { Tv.key(Key.REWIND) }
            RemoteButton(Icons.Filled.PlayArrow, "Play/Pause", size = 52) { Tv.key(Key.PLAY_PAUSE) }
            RemoteButton(Icons.Filled.FastForward, "Forward", size = 52) { Tv.key(Key.FAST_FORWARD) }
        }
        Spacer(Modifier.height(20.dp))

        // Type on the TV using the phone keyboard
        OutlinedTextField(
            value = typed, onValueChange = { typed = it },
            label = { Text("Type on TV (search boxes, passwords)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (typed.isNotEmpty()) { Tv.text(typed); typed = "" } }),
            trailingIcon = {
                Row {
                    IconButton(onClick = { Tv.key(Key.DEL) }) { Icon(Icons.AutoMirrored.Filled.Backspace, "Delete on TV") }
                    IconButton(onClick = { if (typed.isNotEmpty()) { Tv.text(typed); typed = "" } }) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send")
                    }
                }
            }
        )
        Spacer(Modifier.height(20.dp))

        Text("Quick tools", fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolChip("Close app", Icons.Filled.Close, Modifier.weight(1f)) { Tv.closeCurrentApp() }
            ToolChip("Clean RAM", Icons.Filled.CleaningServices, Modifier.weight(1f)) { Tv.cleanRam() }
            ToolChip("Screen off", Icons.Filled.DarkMode, Modifier.weight(1f)) { Tv.key(Key.SLEEP) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ToolChip(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier
    ) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.secondary)
            Text(label, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun DPad() {
    val haptic = LocalHapticFeedback.current
    val ring = MaterialTheme.colorScheme.surfaceVariant
    fun press(code: Int) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); Tv.key(code) }
    Box(Modifier.size(240.dp).clip(CircleShape).background(ring)) {
        @Composable
        fun Arrow(icon: ImageVector, desc: String, align: Alignment, code: Int) =
            Box(Modifier.align(align).size(80.dp).clip(CircleShape).clickable { press(code) },
                contentAlignment = Alignment.Center) { Icon(icon, desc, modifier = Modifier.size(40.dp)) }
        Arrow(Icons.Filled.KeyboardArrowUp, "Up", Alignment.TopCenter, Key.UP)
        Arrow(Icons.Filled.KeyboardArrowDown, "Down", Alignment.BottomCenter, Key.DOWN)
        Arrow(Icons.Filled.KeyboardArrowLeft, "Left", Alignment.CenterStart, Key.LEFT)
        Arrow(Icons.Filled.KeyboardArrowRight, "Right", Alignment.CenterEnd, Key.RIGHT)
        Box(
            Modifier.align(Alignment.Center).size(96.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary).clickable { press(Key.OK) },
            contentAlignment = Alignment.Center
        ) { Text("OK", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White) }
    }
}

// ---------------------------------------------------------------- Apps

@Composable
private fun AppsScreen() {
    val apps by Tv.apps.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Apps on your TV", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { Tv.refreshApps() }) { Icon(Icons.Filled.Refresh, "Refresh") }
        }
        if (apps.isEmpty()) {
            Text("Connect to the TV, then tap refresh to load its apps.", color = Color(0xFF9AA3B2),
                modifier = Modifier.padding(top = 24.dp))
        }
        LazyVerticalGrid(columns = GridCells.Fixed(3), verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
            items(apps, key = { it.pkg }) { app ->
                Surface(onClick = { Tv.launch(app) }, shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(12.dp).fillMaxWidth().height(76.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center) {
                            Text(app.label.take(1), fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Text(app.label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Macros

@Composable
private fun MacrosScreen() {
    val context = LocalContext.current
    var macros by remember { mutableStateOf(MacroStore.load(context)) }
    var editing by remember { mutableStateOf<Macro?>(null) }
    val apps by Tv.apps.collectAsState()

    fun persist(list: List<Macro>) { macros = list; MacroStore.save(context, list) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Macros", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = { editing = Macro(name = "", steps = emptyList()) }) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("New")
            }
        }
        Text("One tap runs several steps, e.g. open JioHotstar and turn volume down.",
            color = Color(0xFF9AA3B2), fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(macros, key = { it.id }) { m ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.name, fontWeight = FontWeight.SemiBold)
                            Text(m.steps.joinToString(" → ") { it.describe(apps) }, fontSize = 12.sp,
                                color = Color(0xFF9AA3B2), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { editing = m }) { Icon(Icons.Filled.Edit, "Edit") }
                        FilledIconButton(onClick = { Tv.runMacro(m) }) { Icon(Icons.Filled.PlayArrow, "Run") }
                    }
                }
            }
        }
    }

    editing?.let { m ->
        MacroEditor(
            initial = m, apps = apps,
            onDismiss = { editing = null },
            onDelete = { persist(macros.filter { it.id != m.id }); editing = null },
            onSave = { saved ->
                persist(if (macros.any { it.id == saved.id }) macros.map { if (it.id == saved.id) saved else it }
                        else macros + saved)
                editing = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MacroEditor(
    initial: Macro, apps: List<TvApp>,
    onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (Macro) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    val steps = remember { mutableStateListOf<Step>().apply { addAll(initial.steps) } }
    var picker by remember { mutableStateOf<String?>(null) } // "key", "app", "text", "delay"
    var input by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.name.isEmpty()) "New macro" else "Edit macro") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Name, e.g. Cricket mode") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                steps.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}. ${s.describe(apps)}", modifier = Modifier.weight(1f), fontSize = 14.sp)
                        IconButton(onClick = { steps.removeAt(i) }) { Icon(Icons.Filled.Delete, "Remove step") }
                    }
                }
                if (steps.isEmpty()) Text("No steps yet — add some below.", color = Color(0xFF9AA3B2), fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { picker = "key" }, label = { Text("+ Button") })
                    AssistChip(onClick = { picker = "app" }, label = { Text("+ Open app") })
                    AssistChip(onClick = { picker = "text"; input = "" }, label = { Text("+ Type text") })
                    AssistChip(onClick = { picker = "delay"; input = "1" }, label = { Text("+ Wait") })
                    AssistChip(onClick = { steps.add(Step(StepType.CLEAN, "")) }, label = { Text("+ Clean RAM") })
                }
                when (picker) {
                    "key" -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Key.named.forEach { (label, code) ->
                            SuggestionChip(onClick = { steps.add(Step(StepType.KEY, code.toString())); picker = null },
                                label = { Text(label) })
                        }
                    }
                    "app" -> if (apps.isEmpty()) Text("Connect to the TV first to list its apps.", fontSize = 13.sp)
                        else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            apps.forEach { a ->
                                SuggestionChip(onClick = {
                                    steps.add(Step(StepType.APP, a.component ?: a.pkg)); picker = null
                                }, label = { Text(a.label) })
                            }
                        }
                    "text", "delay" -> Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(input, { input = it }, singleLine = true, modifier = Modifier.weight(1f),
                            label = { Text(if (picker == "text") "Text to type" else "Seconds") },
                            keyboardOptions = if (picker == "delay") KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                else KeyboardOptions.Default)
                        TextButton(onClick = {
                            if (picker == "text" && input.isNotEmpty()) steps.add(Step(StepType.TEXT, input))
                            if (picker == "delay") {
                                val ms = ((input.toDoubleOrNull() ?: 1.0) * 1000).toLong().coerceIn(100, 60_000)
                                steps.add(Step(StepType.DELAY, ms.toString()))
                            }
                            picker = null
                        }) { Text("Add") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && steps.isNotEmpty(),
                onClick = { onSave(initial.copy(name = name.trim(), steps = steps.toList())) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (initial.name.isNotEmpty()) TextButton(onClick = onDelete) { Text("Delete", color = Color(0xFFFF6B6B)) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

// ---------------------------------------------------------------- Connect

@Composable
private fun ConnectScreen(onConnected: () -> Unit) {
    var host by remember { mutableStateOf(Tv.host) }
    var port by remember { mutableStateOf(Tv.port.toString()) }
    var pairPort by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val status by Tv.status.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Connect to your TV", style = MaterialTheme.typography.titleLarge)
        Text("Phone and TV must be on the same Wi-Fi. On the TV: Settings → System → Developer options → Wireless debugging.",
            color = Color(0xFF9AA3B2), fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))

        OutlinedTextField(host, { host = it }, label = { Text("TV IP address") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(14.dp)) {
                Text("Step 1 — Pair (first time only)", fontWeight = FontWeight.SemiBold)
                Text("On the TV choose \"Pair device with pairing code\". Enter the port and 6-digit code it shows.",
                    fontSize = 13.sp, color = Color(0xFF9AA3B2), modifier = Modifier.padding(vertical = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(pairPort, { pairPort = it.filter(Char::isDigit) }, label = { Text("Pairing port") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f))
                    OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("Code") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Button(enabled = status != Status.CONNECTING && pairPort.isNotEmpty() && code.length == 6,
                    onClick = { Tv.pair(host, pairPort.toInt(), code) { ok -> if (ok) { code = ""; pairPort = "" } } },
                    modifier = Modifier.fillMaxWidth()) { Text("Pair") }
            }
        }

        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(14.dp)) {
                Text("Step 2 — Connect", fontWeight = FontWeight.SemiBold)
                Text("Use the port shown on the main Wireless debugging screen (it is not the pairing port).",
                    fontSize = 13.sp, color = Color(0xFF9AA3B2), modifier = Modifier.padding(vertical = 6.dp))
                OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(enabled = status != Status.CONNECTING && port.isNotEmpty(),
                    onClick = { Tv.connect(host, port.toInt()) }, modifier = Modifier.fillMaxWidth()) { Text("Connect") }
            }
        }

        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(14.dp)) {
                Text("Stop the port from changing (optional)", fontWeight = FontWeight.SemiBold)
                Text("Switches the TV to port 5555 so you don't need to re-enter the port each time. " +
                    "Lasts until the TV is unplugged or fully restarted. The TV may ask you to allow the connection.",
                    fontSize = 13.sp, color = Color(0xFF9AA3B2), modifier = Modifier.padding(vertical = 6.dp))
                OutlinedButton(enabled = status == Status.CONNECTED,
                    onClick = { Tv.fixPort5555(); port = "5555" }, modifier = Modifier.fillMaxWidth()) { Text("Fix port to 5555") }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (status == Status.CONNECTED) {
            TextButton(onClick = { Tv.disconnect() }) { Text("Disconnect") }
        }
    }
}
