package com.kponseel.vocaux

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Green = Color(0xFF25D366)
private val Teal = Color(0xFF128C7E)
private val colors = darkColorScheme(
    primary = Green, onPrimary = Color(0xFF06261A), secondary = Teal,
    background = Color(0xFF0B1412), surface = Color(0xFF121F1C), surfaceVariant = Color(0xFF1A2B27),
    onBackground = Color(0xFFE9F3EF), onSurface = Color(0xFFE9F3EF), onSurfaceVariant = Color(0xFF93A9A1),
    error = Color(0xFFFF6B6B),
)

private fun fmtDur(s: Double) = "%d:%02d".format((s / 60).toInt(), (s % 60).toInt())
private val dateFmt = SimpleDateFormat("d MMM, HH:mm", Locale.FRANCE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: VocauxViewModel) {
    MaterialTheme(colorScheme = colors) {
        val items by vm.items.collectAsStateWithLifecycle()
        val status by vm.status.collectAsStateWithLifecycle()
        val progress by vm.progress.collectAsStateWithLifecycle()
        val dl by vm.download.collectAsStateWithLifecycle()
        val busyIds by vm.busyIds.collectAsStateWithLifecycle()
        val msg by vm.messages.collectAsStateWithLifecycle()
        var showSettings by remember { mutableStateOf(false) }
        var selecting by remember { mutableStateOf(false) }
        var selected by remember { mutableStateOf(setOf<String>()) }
        var trimTarget by remember { mutableStateOf<Vocal?>(null) }
        var confirmDelete by remember { mutableStateOf<Set<String>?>(null) }
        val snack = remember { SnackbarHostState() }
        val ctx = LocalContext.current

        LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.messages.value = null } }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) vm.addUris(uris)
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                TopAppBar(
                    title = { Text(if (selecting) "${selected.size} sélectionné(s)" else "Vocaux", fontWeight = FontWeight.Bold) },
                    navigationIcon = { if (selecting) IconButton({ selecting = false; selected = emptySet() }) { Icon(Icons.Default.Close, "Annuler") } },
                    actions = {
                        if (selecting) {
                            TextButton({ selected = if (selected.size == items.count { !it.busy }) emptySet() else items.filter { !it.busy }.map { it.id }.toSet() }) { Text("Tout") }
                            IconButton({ if (selected.isNotEmpty()) confirmDelete = selected }, enabled = selected.isNotEmpty()) { Icon(Icons.Default.Delete, "Supprimer") }
                        } else {
                            if (items.isNotEmpty()) IconButton({ selecting = true }) { Icon(Icons.Default.Checklist, "Sélectionner") }
                            IconButton({ showSettings = true }) { Icon(Icons.Default.Settings, "Réglages") }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            floatingActionButton = {
                if (!selecting) ExtendedFloatingActionButton(
                    onClick = { picker.launch(arrayOf("audio/*", "video/*", "application/ogg")) },
                    icon = { Icon(Icons.Default.Add, null) }, text = { Text("Ajouter") },
                    containerColor = Green, contentColor = Color(0xFF06261A),
                )
            },
        ) { pad ->
            LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Column {
                        val d = dl
                        if (d != null) {
                            Text("Téléchargement du modèle (une seule fois) : ${d.done / 1_048_576} / ${d.total / 1_048_576} Mo",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            LinearProgressIndicator({ if (d.total > 0) d.done.toFloat() / d.total else 0f }, Modifier.fillMaxWidth().padding(top = 6.dp))
                        } else {
                            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            progress?.let { LinearProgressIndicator({ it }, Modifier.fillMaxWidth().padding(top = 6.dp)) }
                        }
                    }
                }
                if (items.isEmpty()) item {
                    Text("Aucun vocal pour l'instant.\n\nDans WhatsApp : appui long sur un vocal → Partager → Vocaux.\nOu touche « Ajouter ».",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 40.dp).fillMaxWidth())
                }
                items(items, key = { it.id }) { v ->
                    VocalCard(
                        v = v, vm = vm, selecting = selecting, selected = v.id in selected, claudeBusy = v.id in busyIds,
                        onToggle = { if (!v.busy) selected = if (v.id in selected) selected - v.id else selected + v.id },
                        onLongPress = { if (!v.busy) { selecting = true; selected = selected + v.id } },
                        onTrim = { trimTarget = v }, onDelete = { confirmDelete = setOf(v.id) },
                        onCopy = { copy(ctx, export(v)) }, onShare = { share(ctx, export(v)) },
                    )
                }
                item {
                    Text("Vocaux ${BuildConfig.VERSION_NAME} · compilé le ${buildDate()}", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                }
            }
        }

        confirmDelete?.let { ids ->
            AlertDialog(onDismissRequest = { confirmDelete = null },
                title = { Text(if (ids.size > 1) "Supprimer ${ids.size} vocaux ?" else "Supprimer ce vocal ?") },
                text = { Text("C'est définitif.") },
                confirmButton = { TextButton({ vm.delete(ids); confirmDelete = null; selecting = false; selected = emptySet() }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton({ confirmDelete = null }) { Text("Annuler") } })
        }
        trimTarget?.let { v -> TrimDialog(v, onDismiss = { trimTarget = null }) { a, b -> vm.trim(v.id, a, b); trimTarget = null } }
        if (showSettings) SettingsSheet(vm) { showSettings = false; vm.refreshStatus() }
    }
}

private fun buildDate(): String = runCatching {
    SimpleDateFormat("d MMMM yyyy à HH:mm", Locale.FRANCE).format(Date.from(java.time.Instant.parse(BuildConfig.BUILD_TIME)))
}.getOrDefault(BuildConfig.BUILD_TIME)

private fun export(v: Vocal) = if (v.summary.isNotEmpty()) "${v.best}\n\nRésumé :\n${v.summary}" else v.best
private fun copy(ctx: Context, t: String) {
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Vocal", t))
}
private fun share(ctx: Context, t: String) {
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), null))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VocalCard(
    v: Vocal, vm: VocauxViewModel, selecting: Boolean, selected: Boolean, claudeBusy: Boolean,
    onToggle: () -> Unit, onLongPress: () -> Unit, onTrim: () -> Unit, onDelete: () -> Unit,
    onCopy: () -> Unit, onShare: () -> Unit,
) {
    var showOriginal by remember(v.id) { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
            .then(if (selected) Modifier.border(2.dp, Green, RoundedCornerShape(16.dp)) else Modifier)
            .combinedClickable(onClick = { if (selecting) onToggle() }, onLongClick = onLongPress),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) Checkbox(selected, { onToggle() }, enabled = !v.busy)
                Column(Modifier.weight(1f)) {
                    Text(v.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(dateFmt.format(Date(v.createdAt)), v.duration.takeIf { it > 0 }?.let(::fmtDur), v.model.ifEmpty { null })
                        .joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!selecting) PlayButton(v.audioFile)
            }
            when (v.status) {
                Status.PENDING -> Chip("En attente")
                Status.DECODING -> Chip("Lecture du fichier…")
                Status.TRANSCRIBING -> Chip("Transcription…")
                Status.ERROR -> Text(v.error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Status.DONE -> {
                    if (v.corrected.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text(if (showOriginal) "Transcription d'origine" else "✓ Corrigé par Claude", color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        TextButton({ showOriginal = !showOriginal }) { Text(if (showOriginal) "Voir la version corrigée" else "Voir l'original", fontSize = 12.sp) }
                    }
                    val shown = if (v.corrected.isNotEmpty() && !showOriginal) v.corrected else v.text
                    SelectionContainer {
                        Text(shown.ifEmpty { "(aucune parole détectée)" }, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    if (v.summary.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(top = 10.dp).fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text("RÉSUMÉ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            Text(v.summary, fontSize = 14.sp)
                        }
                    }
                    if (claudeBusy) Text("Claude travaille…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (!selecting && !v.busy) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (v.status == Status.DONE && v.text.isNotEmpty()) {
                    FilledTonalButton(onCopy) { Text("Copier") }
                    OutlinedButton(onShare) { Text("Partager") }
                    if (v.corrected.isEmpty()) OutlinedButton({ vm.correct(v.id) }, enabled = !claudeBusy) { Text("Corriger") }
                    if (v.summary.isEmpty()) OutlinedButton({ vm.summarize(v.id) }, enabled = !claudeBusy) { Text("Résumer") }
                }
                if (v.status == Status.ERROR) FilledTonalButton({ vm.retry(v.id) }) { Text("Réessayer") }
                OutlinedButton(onTrim) { Text("Couper") }
                OutlinedButton(onDelete) { Text("Supprimer") }
            }
        }
    }
}

@Composable private fun Chip(t: String) =
    Text(t, color = Color(0xFFFFC83D), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun PlayButton(path: String) {
    var player by remember(path) { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(path) { onDispose { player?.release() } }
    IconButton({
        val p = player
        if (p != null) { p.release(); player = null }
        else runCatching {
            player = MediaPlayer().apply { setDataSource(path); prepare(); start(); setOnCompletionListener { it.release(); player = null } }
        }
    }) { Icon(if (player != null) Icons.Default.Stop else Icons.Default.PlayArrow, "Écouter", tint = Green) }
}

@Composable
private fun TrimDialog(v: Vocal, onDismiss: () -> Unit, onConfirm: (Double, Double) -> Unit) {
    val total = remember(v.audioFile) {
        if (v.duration > 0) v.duration else runCatching {
            MediaPlayer().run { setDataSource(v.audioFile); prepare(); (duration / 1000.0).also { release() } }
        }.getOrDefault(0.0)
    }
    var range by remember { mutableStateOf(0f..total.toFloat().coerceAtLeast(1f)) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { player?.release() } }
    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        while (p.isPlaying) {
            if (p.currentPosition / 1000f >= range.endInclusive) { p.release(); player = null; break }
            kotlinx.coroutines.delay(100)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Couper le vocal") },
        text = {
            Column {
                Text("Début ${fmtDur(range.start.toDouble())} · Fin ${fmtDur(range.endInclusive.toDouble())}")
                RangeSlider(range, { range = it }, valueRange = 0f..total.toFloat().coerceAtLeast(1f))
                TextButton({
                    player?.release()
                    player = runCatching { MediaPlayer().apply { setDataSource(v.audioFile); prepare(); seekTo((range.start * 1000).toInt()); start() } }.getOrNull()
                }) { Icon(Icons.Default.PlayArrow, null); Text("Écouter la sélection") }
                Text("Durée gardée : ${fmtDur((range.endInclusive - range.start).toDouble())}. L'audio sera remplacé par ce passage puis retranscrit.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton({ onConfirm(range.start.toDouble(), range.endInclusive.toDouble()) }, enabled = range.endInclusive - range.start >= 0.5f) { Text("Couper et retranscrire") } },
        dismissButton = { TextButton(onDismiss) { Text("Annuler") } },
    )
}

private val languages = listOf("fr" to "Français", "auto" to "Détection auto (Whisper)", "en" to "Anglais", "es" to "Espagnol",
    "pt" to "Portugais", "it" to "Italien", "de" to "Allemand", "ar" to "Arabe")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(vm: VocauxViewModel, onClose: () -> Unit) {
    val s = vm.settings
    var model by remember { mutableStateOf(s.model) }
    var lang by remember { mutableStateOf(s.language) }
    var key by remember { mutableStateOf(s.apiKey) }
    var auto by remember { mutableStateOf(s.autoSummary) }
    val ver by vm.modelVersion.collectAsStateWithLifecycle()
    val dl by vm.download.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = { s.model = model; s.language = lang; s.apiKey = key; s.autoSummary = auto; onClose() },
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Réglages", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Modèle de transcription (sur le téléphone, gratuit)", fontWeight = FontWeight.SemiBold)
            AsrModels.all.forEach { m ->
                key(ver) {
                    val installed = vm.models.isInstalled(m)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(model == m.id, { model = m.id })
                        Column(Modifier.weight(1f)) {
                            Text(m.label, fontWeight = FontWeight.Medium)
                            Text("${m.description} · ≈ ${m.sizeMb} Mo" + if (installed) " · installé ✓" else "",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (installed) TextButton({ vm.deleteModel(m) }) { Text("Supprimer") }
                        else TextButton({ vm.downloadNow(m) }, enabled = dl == null) { Text("Télécharger") }
                    }
                }
            }
            Text("Langue des vocaux", fontWeight = FontWeight.SemiBold)
            languages.chunked(2).forEach { row ->
                Row { row.forEach { (code, label) ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { RadioButton(lang == code, { lang = code }); Text(label, fontSize = 14.sp) }
                } }
            }
            HorizontalDivider()
            Text("Claude (optionnel) : corriger et résumer", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(key, { key = it }, label = { Text("Clé API (sk-ant-…)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            Text("Modèle Claude Haiku 5.5 : moins d'un dixième de centime par vocal. Seul le texte est envoyé, jamais l'audio. La clé reste sur ce téléphone.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(auto, { auto = it }); Text("Résumer automatiquement chaque vocal") }
            HorizontalDivider()
            Text("Vocaux ${BuildConfig.VERSION_NAME} · compilé le ${buildDate()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
