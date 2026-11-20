package com.kponseel.vocaux

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class Status { PENDING, DECODING, TRANSCRIBING, DONE, ERROR }

data class Vocal(
    val id: String,
    val name: String,
    val createdAt: Long,
    val audioFile: String,          // fichier audio copié dans l'app
    val status: Status = Status.PENDING,
    val duration: Double = 0.0,
    val text: String = "",
    val corrected: String = "",
    val summary: String = "",
    val model: String = "",
    val error: String = "",
) {
    val best get() = corrected.ifEmpty { text }
    val busy get() = status == Status.PENDING || status == Status.DECODING || status == Status.TRANSCRIBING

    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("createdAt", createdAt); put("audioFile", audioFile)
        put("status", status.name); put("duration", duration); put("text", text); put("corrected", corrected)
        put("summary", summary); put("model", model); put("error", error)
    }

    companion object {
        fun fromJson(o: JSONObject) = Vocal(
            id = o.getString("id"), name = o.optString("name"), createdAt = o.optLong("createdAt"),
            audioFile = o.optString("audioFile"), status = runCatching { Status.valueOf(o.optString("status")) }.getOrDefault(Status.DONE),
            duration = o.optDouble("duration", 0.0), text = o.optString("text"), corrected = o.optString("corrected"),
            summary = o.optString("summary"), model = o.optString("model"), error = o.optString("error"),
        )
    }
}

/** Historique enregistré dans la mémoire interne de l'app (JSON + copies des fichiers audio). */
class Repo(ctx: Context) {
    val audioDir = File(ctx.filesDir, "audio").apply { mkdirs() }
    private val file = File(ctx.filesDir, "history.json")

    fun load(): List<Vocal> = try {
        val a = JSONArray(file.readText())
        (0 until a.length()).map { Vocal.fromJson(a.getJSONObject(it)) }
    } catch (_: Exception) { emptyList() }

    @Synchronized fun save(list: List<Vocal>) {
        val tmp = File(file.parentFile, "history.json.tmp")
        tmp.writeText(JSONArray(list.map { it.toJson() }).toString())
        tmp.renameTo(file)
    }
}

class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var model: String get() = p.getString("model", AsrModels.DEFAULT)!!; set(v) = p.edit().putString("model", v).apply()
    var language: String get() = p.getString("lang", "fr")!!; set(v) = p.edit().putString("lang", v).apply()
    var apiKey: String get() = p.getString("apiKey", "")!!; set(v) = p.edit().putString("apiKey", v.trim()).apply()
    var autoSummary: Boolean get() = p.getBoolean("autoSummary", false); set(v) = p.edit().putBoolean("autoSummary", v).apply()
}
