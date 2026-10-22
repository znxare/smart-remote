package com.kundan.smartremote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class StepType { KEY, APP, TEXT, DELAY, CLEAN }

data class Step(val type: StepType, val value: String) {
    fun describe(apps: List<TvApp>): String = when (type) {
        StepType.KEY -> "Press ${Key.nameOf(value.toIntOrNull() ?: 0)}"
        StepType.APP -> "Open " + (apps.firstOrNull { it.pkg == value.substringBefore('/') }?.label
            ?: TvApp(value.substringBefore('/'), null).label)
        StepType.TEXT -> "Type \"$value\""
        StepType.DELAY -> "Wait ${(value.toLongOrNull() ?: 0) / 1000.0}s"
        StepType.CLEAN -> "Close background apps"
    }
}

data class Macro(val id: String = UUID.randomUUID().toString(), val name: String, val steps: List<Step>)

/** Saves macros on the phone as JSON. */
object MacroStore {
    private const val KEY = "macros"

    private fun defaults(): List<Macro> = listOf(
        Macro(name = "Quiet night", steps = List(5) { Step(StepType.KEY, Key.VOL_DOWN.toString()) }),
        Macro(name = "Fresh start", steps = listOf(
            Step(StepType.CLEAN, ""), Step(StepType.DELAY, "500"), Step(StepType.KEY, Key.HOME.toString())
        )),
        Macro(name = "YouTube search", steps = listOf(
            Step(StepType.APP, "com.google.android.youtube.tv"), Step(StepType.DELAY, "3000"),
            Step(StepType.KEY, Key.SEARCH.toString())
        )),
    )

    fun load(context: Context): List<Macro> {
        val prefs = context.getSharedPreferences("tv", Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null) ?: return defaults().also { save(context, it) }
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val steps = o.getJSONArray("steps")
                Macro(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    steps = (0 until steps.length()).map { j ->
                        val s = steps.getJSONObject(j)
                        Step(StepType.valueOf(s.getString("type")), s.getString("value"))
                    }
                )
            }
        }.getOrElse { defaults() }
    }

    fun save(context: Context, macros: List<Macro>) {
        val arr = JSONArray()
        macros.forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id); put("name", m.name)
                put("steps", JSONArray().apply {
                    m.steps.forEach { s -> put(JSONObject().put("type", s.type.name).put("value", s.value)) }
                })
            })
        }
        context.getSharedPreferences("tv", Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}
