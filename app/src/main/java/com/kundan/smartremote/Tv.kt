package com.kundan.smartremote

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/** Android key codes used by the remote. */
object Key {
    const val HOME = 3; const val BACK = 4
    const val UP = 19; const val DOWN = 20; const val LEFT = 21; const val RIGHT = 22; const val OK = 23
    const val VOL_UP = 24; const val VOL_DOWN = 25; const val POWER = 26
    const val DEL = 67; const val MENU = 82; const val SEARCH = 84
    const val PLAY_PAUSE = 85; const val REWIND = 89; const val FAST_FORWARD = 90
    const val MUTE = 164; const val SETTINGS = 176; const val INPUT = 178
    const val ASSISTANT = 219; const val SLEEP = 223; const val WAKEUP = 224

    val named: List<Pair<String, Int>> = listOf(
        "Home" to HOME, "Back" to BACK, "Up" to UP, "Down" to DOWN, "Left" to LEFT,
        "Right" to RIGHT, "OK" to OK, "Volume +" to VOL_UP, "Volume −" to VOL_DOWN,
        "Mute" to MUTE, "Power" to POWER, "Play/Pause" to PLAY_PAUSE, "Rewind" to REWIND,
        "Fast forward" to FAST_FORWARD, "Settings" to SETTINGS, "Input" to INPUT,
        "Menu" to MENU, "Assistant" to ASSISTANT, "Screen off" to SLEEP, "Screen on" to WAKEUP
    )
    fun nameOf(code: Int) = named.firstOrNull { it.second == code }?.first ?: "Key $code"
}

data class TvApp(val pkg: String, val component: String?) {
    val label: String get() = KNOWN[pkg] ?: pkg.split('.').filter {
        it !in setOf("com", "in", "tv", "android", "google", "app", "apps", "atv", "leanback")
    }.lastOrNull()?.replaceFirstChar { it.uppercase() } ?: pkg

    companion object {
        val KNOWN = mapOf(
            "com.google.android.youtube.tv" to "YouTube",
            "com.google.android.youtube.tvkids" to "YouTube Kids",
            "com.google.android.youtube.tvmusic" to "YouTube Music",
            "com.netflix.ninja" to "Netflix",
            "com.amazon.amazonvideo.livingroom" to "Prime Video",
            "in.startv.hotstar" to "JioHotstar",
            "in.startv.hotstaronly" to "JioHotstar",
            "com.jio.media.stb.ondemand" to "JioCinema",
            "com.graymatrix.did" to "ZEE5",
            "com.sonyliv" to "SonyLIV",
            "com.spotify.tv.android" to "Spotify",
            "com.google.android.apps.tv.launcherx" to "Google TV Home",
            "com.android.tv.settings" to "Settings",
            "com.google.android.play.games" to "Play Games",
            "com.android.vending" to "Play Store",
            "com.google.android.tvrecommendations" to "Recommendations",
        )
    }
}

enum class Status { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/**
 * Everything that talks to the TV. All ADB work runs on one background thread
 * so button presses are sent in the order you press them.
 */
object Tv {
    private val adbThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val scope = CoroutineScope(SupervisorJob() + adbThread)

    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private val manager by lazy { AdbManager.get(appContext) }

    val status = MutableStateFlow(Status.DISCONNECTED)
    val message = MutableStateFlow("Not connected")
    val apps = MutableStateFlow<List<TvApp>>(emptyList())

    val host: String get() = prefs.getString("host", "192.168.1.8") ?: ""
    val port: Int get() = prefs.getInt("port", 33711)
    val hasSaved: Boolean get() = prefs.getBoolean("connectedOnce", false)

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("tv", Context.MODE_PRIVATE)
    }

    fun save(host: String, port: Int) =
        prefs.edit().putString("host", host.trim()).putInt("port", port).apply()

    /** One-time pairing using the 6-digit code shown on the TV. */
    fun pair(host: String, pairPort: Int, code: String, onDone: (Boolean) -> Unit) = scope.launch {
        status.value = Status.CONNECTING
        message.value = "Pairing…"
        val ok = runCatching { manager.pair(host.trim(), pairPort, code.trim()) }
            .onFailure { message.value = "Pairing failed: ${it.message ?: it.javaClass.simpleName}" }
            .getOrDefault(false)
        if (ok) message.value = "Paired! Now press Connect."
        status.value = if (ok) Status.DISCONNECTED else Status.ERROR
        withMain { onDone(ok) }
    }

    fun connect(host: String = this.host, port: Int = this.port) = scope.launch {
        save(host, port)
        status.value = Status.CONNECTING
        message.value = "Connecting to $host:$port…"
        val ok = runCatching {
            if (manager.isConnected) manager.disconnect()
            manager.connect(host.trim(), port)
        }.onFailure {
            message.value = "Couldn't connect: ${it.message ?: it.javaClass.simpleName}. " +
                "Check the TV is on, same Wi-Fi, and the port in Wireless debugging."
        }.getOrDefault(false)
        if (ok) {
            status.value = Status.CONNECTED
            message.value = "Connected to $host:$port"
            prefs.edit().putBoolean("connectedOnce", true).apply()
            refreshAppsInternal()
        } else {
            status.value = Status.ERROR
            if (!message.value.startsWith("Couldn't")) message.value = "Connection refused"
        }
    }

    fun disconnect() = scope.launch {
        runCatching { manager.disconnect() }
        status.value = Status.DISCONNECTED
        message.value = "Disconnected"
    }

    /**
     * Switches the TV's ADB to a fixed port (5555) so the address stops
     * changing. Lasts until the TV fully restarts.
     */
    fun fixPort5555() = scope.launch {
        val h = host
        message.value = "Switching TV to port 5555…"
        runCatching { manager.openStream("tcpip:5555").use { s -> s.openInputStream().readBytes() } }
        runCatching { manager.disconnect() }
        delay(2500)
        connect(h, 5555)
    }

    /** Runs a shell command on the TV and returns its output. Call from [scope]. */
    private fun shellNow(cmd: String): String {
        if (!manager.isConnected) throw IllegalStateException("Not connected")
        manager.openStream("shell:$cmd").use { stream ->
            return stream.openInputStream().readBytes().toString(Charsets.UTF_8)
        }
    }

    private fun exec(block: suspend () -> Unit) = scope.launch {
        if (status.value != Status.CONNECTED) {
            message.value = "Not connected — open the Connect tab"
            return@launch
        }
        runCatching { block() }.onFailure {
            status.value = Status.ERROR
            message.value = "Lost connection: ${it.message ?: "error"}. Tap Connect again."
        }
    }

    fun key(code: Int) = exec { shellNow("input keyevent $code") }

    fun text(value: String) = exec { shellNow(textCommand(value)) }

    fun launch(app: TvApp) = exec { shellNow(launchCommand(app)) }

    /** Closes background apps to free memory. */
    fun cleanRam() = exec {
        shellNow("am kill-all")
        message.value = "Background apps closed"
    }

    /** Force-closes whatever app is on screen and goes Home. */
    fun closeCurrentApp() = exec {
        val pkg = foregroundPackage()
        if (pkg != null && pkg != "com.google.android.apps.tv.launcherx") {
            shellNow("am force-stop $pkg")
            message.value = "Closed ${TvApp(pkg, null).label}"
        }
        shellNow("input keyevent ${Key.HOME}")
    }

    fun refreshApps() = exec { refreshAppsInternal() }

    fun runMacro(macro: Macro) = exec {
        message.value = "Running \"${macro.name}\"…"
        for (step in macro.steps) {
            when (step.type) {
                StepType.KEY -> shellNow("input keyevent ${step.value}")
                StepType.APP -> shellNow(launchCommand(TvApp(step.value.substringBefore('/'),
                    step.value.takeIf { it.contains('/') })))
                StepType.TEXT -> shellNow(textCommand(step.value))
                StepType.DELAY -> delay(step.value.toLongOrNull() ?: 500)
                StepType.CLEAN -> shellNow("am kill-all")
            }
            if (step.type != StepType.DELAY) delay(150)
        }
        message.value = "Done: ${macro.name}"
    }

    private fun foregroundPackage(): String? {
        val out = shellNow("dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' | head -n 1")
        return Regex("""\s([a-zA-Z0-9_.]+)/""").find(out)?.groupValues?.get(1)
    }

    private fun refreshAppsInternal() {
        val out = runCatching {
            shellNow("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER")
        }.getOrDefault("")
        val found = Regex("""([a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z0-9_]+)+)/([a-zA-Z0-9_.$]+)""")
            .findAll(out)
            .map { TvApp(it.groupValues[1], "${it.groupValues[1]}/${it.groupValues[2]}") }
            .distinctBy { it.pkg }
            .toMutableList()
        if (found.isEmpty()) {
            runCatching { shellNow("pm list packages -3") }.getOrDefault("")
                .lineSequence().map { it.removePrefix("package:").trim() }
                .filter { it.isNotEmpty() }.forEach { found += TvApp(it, null) }
        }
        apps.value = found.sortedBy { it.label.lowercase() }
    }

    private fun launchCommand(app: TvApp): String =
        if (app.component != null) "am start -n ${app.component}"
        else "monkey -p ${app.pkg} -c android.intent.category.LEANBACK_LAUNCHER 1"

    private fun textCommand(value: String): String {
        // `input text` needs spaces as %s; wrap in single quotes for the shell.
        val escaped = value.replace("%", "\\%").replace(" ", "%s").replace("'", "'\\''")
        return "input text '$escaped'"
    }

    private suspend fun withMain(block: () -> Unit) =
        withContext(kotlinx.coroutines.Dispatchers.Main) { block() }

    val statusFlow: StateFlow<Status> get() = status
}
