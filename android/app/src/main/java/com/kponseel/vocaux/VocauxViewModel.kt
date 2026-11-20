package com.kponseel.vocaux

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class ModelDownload(val modelId: String, val done: Long, val total: Long)

class VocauxViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    val settings = Settings(app)
    val models = ModelStore(app)
    private val repo = Repo(app)
    private val transcriber = Transcriber(models)
    private val queueLock = Mutex()

    val items = MutableStateFlow(repo.load().map { if (it.busy) it.copy(status = Status.PENDING) else it })
    val status = MutableStateFlow("")
    val progress = MutableStateFlow<Float?>(null)
    val download = MutableStateFlow<ModelDownload?>(null)
    val busyIds = MutableStateFlow(setOf<String>())   // vocaux en cours de correction / résumé
    val messages = MutableStateFlow<String?>(null)
    val modelVersion = MutableStateFlow(0)              // incrémenté quand un modèle est téléchargé / supprimé

    init { refreshStatus(); processQueue() }

    fun toast(m: String) { messages.value = m }

    private fun persist() = repo.save(items.value)
    private fun update(id: String, f: (Vocal) -> Vocal) {
        items.update { list -> list.map { if (it.id == id) f(it) else it } }
        persist()
    }

    fun refreshStatus() {
        val m = AsrModels.byId(settings.model)
        status.value = if (models.isInstalled(m)) "${m.label} · sur le téléphone ✓" else "${m.label} : téléchargé au premier vocal (≈ ${m.sizeMb} Mo)"
    }

    /** Ajoute des fichiers (partage, sélecteur…) : copie dans l'app puis file d'attente. */
    fun addUris(uris: List<Uri>) = viewModelScope.launch {
        val added = withContext(Dispatchers.IO) {
            uris.mapNotNull { uri ->
                runCatching {
                    val id = UUID.randomUUID().toString()
                    val name = displayName(uri) ?: "vocal"
                    val dest = File(repo.audioDir, id + "_" + name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
                    ctx.contentResolver.openInputStream(uri)!!.use { i -> dest.outputStream().use { i.copyTo(it) } }
                    Vocal(id = id, name = name, createdAt = System.currentTimeMillis(), audioFile = dest.absolutePath)
                }.getOrNull()
            }
        }
        if (added.isEmpty()) { toast("Fichier illisible"); return@launch }
        items.update { added.reversed() + it }
        persist()
        toast(if (added.size > 1) "${added.size} vocaux ajoutés" else "Vocal ajouté")
        processQueue()
    }

    private fun displayName(uri: Uri): String? = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: uri.lastPathSegment

    fun processQueue() = viewModelScope.launch {
        if (queueLock.isLocked) return@launch
        queueLock.withLock {
            if (items.value.none { it.status == Status.PENDING }) return@withLock
            WorkService.start(ctx)
            try {
                while (true) {
                    val next = items.value.lastOrNull { it.status == Status.PENDING } ?: break
                    processOne(next)
                }
            } finally {
                WorkService.stop(ctx)
                progress.value = null
                refreshStatus()
            }
        }
    }

    private suspend fun processOne(v: Vocal) {
        val m = AsrModels.byId(settings.model)
        try {
            if (!models.isInstalled(m)) downloadModel(m)
            update(v.id) { it.copy(status = Status.DECODING, error = "") }
            status.value = "Lecture du fichier…"
            val samples = withContext(Dispatchers.IO) { AudioDecoder.decode(File(v.audioFile)) }
            update(v.id) { it.copy(status = Status.TRANSCRIBING, duration = samples.size / 16000.0) }
            status.value = "Transcription en cours…"
            progress.value = 0f
            val lang = settings.language.takeIf { it != "auto" } ?: ""
            val text = withContext(Dispatchers.Default) {
                transcriber.transcribe(m, lang, samples) { p -> progress.value = p }
            }
            update(v.id) { it.copy(status = Status.DONE, text = text, corrected = "", summary = "", model = m.label) }
            if (settings.autoSummary && settings.apiKey.isNotBlank() && text.isNotBlank()) summarize(v.id)
        } catch (e: Throwable) {
            update(v.id) { it.copy(status = Status.ERROR, error = e.message ?: "Erreur inconnue") }
        } finally { progress.value = null }
    }

    suspend fun downloadModel(m: AsrModel) = withContext(Dispatchers.IO) {
        try {
            download.value = ModelDownload(m.id, 0, m.sizeMb * 1_048_576L)
            models.download(m, { d, t -> download.value = ModelDownload(m.id, d, t) })
        } finally {
            download.value = null
            modelVersion.update { it + 1 }
        }
    }

    fun downloadNow(m: AsrModel) = viewModelScope.launch {
        runCatching { downloadModel(m) }.onFailure { toast(it.message ?: "Téléchargement impossible") }
        refreshStatus()
    }

    fun deleteModel(m: AsrModel) { transcriber.release(); models.delete(m); modelVersion.update { it + 1 }; refreshStatus() }

    fun retry(id: String) { update(id) { it.copy(status = Status.PENDING, error = "") }; processQueue() }

    fun delete(ids: Set<String>) {
        val gone = items.value.filter { it.id in ids && !it.busy }
        gone.forEach { File(it.audioFile).delete() }
        items.update { list -> list.filterNot { v -> gone.any { it.id == v.id } } }
        persist()
        toast(if (gone.size > 1) "${gone.size} vocaux supprimés" else "Vocal supprimé")
    }

    /** Garde le passage [start, end] (secondes), remplace l'audio et retranscrit. */
    fun trim(id: String, start: Double, end: Double) = viewModelScope.launch {
        val v = items.value.firstOrNull { it.id == id } ?: return@launch
        try {
            val out = withContext(Dispatchers.IO) {
                val s = AudioDecoder.decode(File(v.audioFile))
                val a = (start * 16000).toInt().coerceIn(0, s.size); val b = (end * 16000).toInt().coerceIn(a, s.size)
                require(b - a >= 8000) { "Passage trop court" }
                val f = File(repo.audioDir, UUID.randomUUID().toString() + "_coupe.wav")
                AudioDecoder.writeWav(s.copyOfRange(a, b), f)
                File(v.audioFile).delete()
                f
            }
            val base = v.name.substringBeforeLast('.').removeSuffix(" (coupé)")
            update(id) { it.copy(audioFile = out.absolutePath, name = "$base (coupé).wav", status = Status.PENDING,
                text = "", corrected = "", summary = "", error = "", duration = end - start) }
            processQueue()
        } catch (e: Exception) { toast("Découpe impossible : ${e.message}") }
    }

    fun summarize(id: String) = run(id, true)
    fun correct(id: String) = run(id, false)

    private fun run(id: String, summary: Boolean) = viewModelScope.launch {
        val v = items.value.firstOrNull { it.id == id } ?: return@launch
        if (settings.apiKey.isBlank()) { toast("Ajoute ta clé API Claude dans les réglages"); return@launch }
        busyIds.update { it + id }
        try {
            val r = withContext(Dispatchers.IO) {
                if (summary) Claude.summarize(settings.apiKey, v.best) else Claude.correct(settings.apiKey, v.text)
            }
            update(id) { if (summary) it.copy(summary = r) else it.copy(corrected = r) }
        } catch (e: Exception) {
            toast((if (summary) "Résumé : " else "Correction : ") + (e.message ?: "erreur"))
        } finally { busyIds.update { it - id } }
    }

    override fun onCleared() { transcriber.release() }

    fun handleIntent(intent: Intent?) {
        intent ?: return
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.parcelableUri())
            Intent.ACTION_SEND_MULTIPLE -> intent.parcelableUris()
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            else -> emptyList()
        }
        if (uris.isNotEmpty()) addUris(uris)
    }
}

@Suppress("DEPRECATION")
private fun Intent.parcelableUri(): Uri? =
    if (android.os.Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else getParcelableExtra(Intent.EXTRA_STREAM)

@Suppress("DEPRECATION")
private fun Intent.parcelableUris(): List<Uri> =
    (if (android.os.Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
     else getParcelableArrayListExtra(Intent.EXTRA_STREAM)) ?: emptyList()
