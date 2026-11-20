package com.clipforge.autoeditor.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipforge.autoeditor.data.ScriptParser
import com.clipforge.autoeditor.ffmpeg.FfmpegCommandBuilder
import com.clipforge.autoeditor.ffmpeg.FfmpegRenderManager
import com.clipforge.autoeditor.model.ClipAFramingMode
import com.clipforge.autoeditor.model.GlobalSettings
import com.clipforge.autoeditor.model.OutcomeBadge
import com.clipforge.autoeditor.model.RealClipAngle
import com.clipforge.autoeditor.model.RenderState
import com.clipforge.autoeditor.model.RenderStats
import com.clipforge.autoeditor.model.StoryPair
import com.clipforge.autoeditor.util.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipForgeViewModel(application: Application) : AndroidViewModel(application) {

    private val renderManager = FfmpegRenderManager()

    private val _pairs = MutableStateFlow<List<StoryPair>>(emptyList())
    val pairs: StateFlow<List<StoryPair>> = _pairs.asStateFlow()

    private val _globalSettings = MutableStateFlow(GlobalSettings())
    val globalSettings: StateFlow<GlobalSettings> = _globalSettings.asStateFlow()

    private val _scriptInput = MutableStateFlow("")
    val scriptInput: StateFlow<String> = _scriptInput.asStateFlow()

    private val _parseMessage = MutableStateFlow<String?>(null)
    val parseMessage: StateFlow<String?> = _parseMessage.asStateFlow()

    private val _renderState = MutableStateFlow<RenderState>(RenderState.Idle)
    val renderState: StateFlow<RenderState> = _renderState.asStateFlow()

    private val _renderStats = MutableStateFlow(RenderStats())
    val renderStats: StateFlow<RenderStats> = _renderStats.asStateFlow()

    private val _showCommandDebug = MutableStateFlow(false)
    val showCommandDebug: StateFlow<Boolean> = _showCommandDebug.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    init {
        loadSampleTemplate()
    }

    fun updateScriptInput(text: String) {
        _scriptInput.value = text
        _parseMessage.value = null
    }

    fun loadSampleTemplate() {
        _scriptInput.value = ScriptParser.SAMPLE_TEMPLATE_JSON
        parseScriptIntoPairs()
    }

    fun parseScriptIntoPairs() {
        ScriptParser.parse(_scriptInput.value)
            .onSuccess { parsed ->
                _pairs.value = parsed
                _parseMessage.value = "Loaded ${parsed.size} Story Pair(s). Tap missing video slots to attach clips."
                refreshCommandPreview()
            }
            .onFailure { err ->
                _parseMessage.value = "JSON Error: ${err.message}"
            }
    }

    fun addEmptyPair() {
        val nextNum = _pairs.value.size + 1
        _pairs.update { current ->
            current + StoryPair(
                storyTitle = "Story #$nextNum",
                locationYear = "CITY, 2024",
                shockFact = "Key moment from real footage"
            )
        }
        refreshCommandPreview()
    }

    fun deletePair(pairId: String) {
        _pairs.update { list -> list.filterNot { it.id == pairId } }
        refreshCommandPreview()
    }

    fun movePair(fromIndex: Int, toIndex: Int) {
        _pairs.update { list ->
            if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) {
                return@update list
            }
            val mutable = list.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            mutable
        }
        refreshCommandPreview()
    }

    fun updatePairMetadata(
        pairId: String,
        storyTitle: String? = null,
        locationYear: String? = null,
        shockFact: String? = null,
        outcomeBadge: OutcomeBadge? = null,
        creditTag: String? = null,
        framingMode: ClipAFramingMode? = null
    ) {
        _pairs.update { list ->
            list.map { pair ->
                if (pair.id != pairId) pair else pair.copy(
                    storyTitle = storyTitle ?: pair.storyTitle,
                    locationYear = locationYear ?: pair.locationYear,
                    shockFact = shockFact ?: pair.shockFact,
                    outcomeBadge = outcomeBadge ?: pair.outcomeBadge,
                    creditTag = creditTag ?: pair.creditTag,
                    framingMode = framingMode ?: pair.framingMode
                )
            }
        }
        refreshCommandPreview()
    }

    fun updateClipATrim(pairId: String, startSec: Float, endSec: Float) {
        _pairs.update { list ->
            list.map { pair ->
                if (pair.id != pairId) pair else pair.copy(
                    clipAStartSec = startSec.coerceAtLeast(0f),
                    clipAEndSec = endSec.coerceAtLeast(startSec + 0.5f)
                )
            }
        }
        refreshCommandPreview()
    }

    fun attachClipAVideo(pairId: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val probed = StorageHelper.probeMedia(getApplication(), uri)
            _pairs.update { list ->
                list.map { pair ->
                    if (pair.id != pairId) pair else {
                        val clampedEnd = pair.clipAEndSec.coerceIn(0.5f, probed.durationSec)
                        val clampedStart = pair.clipAStartSec.coerceIn(0f, (clampedEnd - 0.5f).coerceAtLeast(0f))
                        pair.copy(
                            clipAUri = uri,
                            clipADisplayName = probed.displayName,
                            clipAPlaceholderLabel = "",
                            clipAMaxDurationSec = probed.durationSec,
                            clipAStartSec = clampedStart,
                            clipAEndSec = clampedEnd,
                            clipAHasAudio = probed.hasAudio,
                            clipAPortrait = probed.portrait
                        )
                    }
                }
            }
            refreshCommandPreview()
        }
    }


    /**
     * Attach many videos at once. Files are sorted by file name and dropped into the slots in script order:
     * Pair1 Clip A, Pair1 Real #1, Pair1 Real #2, Pair2 Clip A, Pair2 Real #1, ...
     */
    fun attachVideosInOrder(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val named = uris
                .map { it to StorageHelper.queryDisplayName(getApplication(), it) }
                .sortedBy { it.second.lowercase(Locale.ROOT) }
            val slots = mutableListOf<Pair<String, String?>>()
            _pairs.value.forEach { p ->
                slots.add(Pair(p.id, null))
                p.realClips.forEach { slots.add(Pair(p.id, it.id)) }
            }
            val count = minOf(slots.size, named.size)
            val probes = (0 until count).map { StorageHelper.probeMedia(getApplication(), named[it].first) }
            _pairs.update { list ->
                list.map { pair ->
                    var updated = pair
                    for (i in 0 until count) {
                        val slot = slots[i]
                        if (slot.first != pair.id) continue
                        val uri = named[i].first
                        val pr = probes[i]
                        val angleId = slot.second
                        updated = if (angleId == null) {
                            val end = updated.clipAEndSec.coerceIn(0.5f, pr.durationSec)
                            val start = updated.clipAStartSec.coerceIn(0f, (end - 0.5f).coerceAtLeast(0f))
                            updated.copy(
                                clipAUri = uri,
                                clipADisplayName = pr.displayName,
                                clipAPlaceholderLabel = "",
                                clipAMaxDurationSec = pr.durationSec,
                                clipAStartSec = start,
                                clipAEndSec = end,
                                clipAHasAudio = pr.hasAudio,
                                clipAPortrait = pr.portrait
                            )
                        } else {
                            updated.copy(
                                realClips = updated.realClips.map { angle ->
                                    if (angle.id != angleId) angle else {
                                        val end = angle.endSec.coerceIn(0.5f, pr.durationSec)
                                        val start = angle.startSec.coerceIn(0f, (end - 0.5f).coerceAtLeast(0f))
                                        angle.copy(
                                            uri = uri,
                                            displayName = pr.displayName,
                                            placeholderLabel = "",
                                            maxDurationSec = pr.durationSec,
                                            startSec = start,
                                            endSec = end,
                                            hasAudio = pr.hasAudio,
                                            portrait = pr.portrait
                                        )
                                    }
                                }
                            )
                        }
                    }
                    updated
                }
            }
            _parseMessage.value = "Attached $count of ${slots.size} slots (sorted by file name). " +
                (if (named.size != slots.size) "You picked ${named.size} files but the script needs ${slots.size}." else "All slots filled.")
            refreshCommandPreview()
        }
    }

    fun addRealAngle(pairId: String) {
        _pairs.update { list ->
            list.map { pair ->
                if (pair.id != pairId) pair else pair.copy(
                    realClips = pair.realClips + RealClipAngle()
                )
            }
        }
        refreshCommandPreview()
    }

    fun removeRealAngle(pairId: String, angleId: String) {
        _pairs.update { list ->
            list.map { pair ->
                if (pair.id != pairId || pair.realClips.size <= 1) pair else pair.copy(
                    realClips = pair.realClips.filterNot { it.id == angleId }
                )
            }
        }
        refreshCommandPreview()
    }

    fun attachRealAngleVideo(pairId: String, angleId: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val probed = StorageHelper.probeMedia(getApplication(), uri)
            _pairs.update { list ->
                list.map { pair ->
                    if (pair.id != pairId) pair else pair.copy(
                        realClips = pair.realClips.map { angle ->
                            if (angle.id != angleId) angle else {
                                val clampedEnd = angle.endSec.coerceIn(0.5f, probed.durationSec)
                                val clampedStart = angle.startSec.coerceIn(0f, (clampedEnd - 0.5f).coerceAtLeast(0f))
                                angle.copy(
                                    uri = uri,
                                    displayName = probed.displayName,
                                    placeholderLabel = "",
                                    maxDurationSec = probed.durationSec,
                                    startSec = clampedStart,
                                    endSec = clampedEnd,
                                    hasAudio = probed.hasAudio,
                                    portrait = probed.portrait
                                )
                            }
                        }
                    )
                }
            }
            refreshCommandPreview()
        }
    }

    fun updateRealAngleTrim(pairId: String, angleId: String, startSec: Float, endSec: Float) {
        _pairs.update { list ->
            list.map { pair ->
                if (pair.id != pairId) pair else pair.copy(
                    realClips = pair.realClips.map { angle ->
                        if (angle.id != angleId) angle else angle.copy(
                            startSec = startSec.coerceAtLeast(0f),
                            endSec = endSec.coerceAtLeast(startSec + 0.5f)
                        )
                    }
                )
            }
        }
        refreshCommandPreview()
    }

    fun updateGlobalSettings(transform: (GlobalSettings) -> GlobalSettings) {
        _globalSettings.update(transform)
        refreshCommandPreview()
    }

    fun attachBackgroundMusic(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val name = StorageHelper.queryDisplayName(getApplication(), uri)
            _globalSettings.update {
                it.copy(bgMusicUri = uri, bgMusicDisplayName = name)
            }
            refreshCommandPreview()
        }
    }

    fun clearBackgroundMusic() {
        _globalSettings.update {
            it.copy(bgMusicUri = null, bgMusicDisplayName = "None selected")
        }
        refreshCommandPreview()
    }

    fun toggleCommandDebug(enabled: Boolean) {
        _showCommandDebug.value = enabled
        if (enabled) refreshCommandPreview()
    }

    fun refreshCommandPreview() {
        val currentPairs = _pairs.value
        if (currentPairs.isEmpty() || currentPairs.any { !it.isReadyToRender }) {
            _renderStats.update {
                it.copy(commandPreview = "Attach all Clip A and Real Footage videos to preview the exact FFmpeg command.")
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val previewOut = renderManager.createOutputFilePath(getApplication())
                FfmpegCommandBuilder.build(
                    context = getApplication(),
                    pairs = currentPairs,
                    settings = _globalSettings.value,
                    outputPath = previewOut,
                    useSoftwareFallback = !_globalSettings.value.preferHardwareEncoder,
                    forPreview = true
                )
            }.onSuccess { built ->
                _renderStats.update {
                    it.copy(
                        commandPreview = built.printableCommand,
                        totalDurationMs = built.totalDurationMs,
                        totalFrames = built.totalFrames,
                        encoderLabel = built.encoderName
                    )
                }
            }
        }
    }

    fun startRender(useSoftwareFallback: Boolean = false) {
        val currentPairs = _pairs.value
        if (currentPairs.isEmpty()) {
            _renderState.value = RenderState.Failed("Add at least one Story Pair before rendering.")
            return
        }
        if (currentPairs.any { !it.isReadyToRender }) {
            _renderState.value = RenderState.Failed("One or more Story Pairs still have missing video slots.")
            return
        }

        _renderState.value = RenderState.Preparing
        _statusBanner.value = null
        _renderStats.update {
            it.copy(
                progress = 0f,
                currentTimeMs = 0L,
                currentFrame = 0L,
                fps = 0f,
                speed = 0f,
                etaSeconds = 0L,
                logs = listOf(if (useSoftwareFallback) "Retrying with libx264 software encoder..." else "Initializing FFmpegKit pipeline...")
            )
        }

        viewModelScope.launch {
            renderManager.startRender(
                context = getApplication(),
                pairs = currentPairs,
                settings = _globalSettings.value,
                useSoftwareFallback = useSoftwareFallback,
                onSessionStarted = { sessionId, built ->
                    // Only move Preparing -> Rendering. If FFmpeg already failed/finished
                    // (bad filter graph fails in milliseconds) do not overwrite that state.
                    _renderState.update { current ->
                        if (current is RenderState.Preparing) RenderState.Rendering(sessionId) else current
                    }
                    _renderStats.update {
                        it.copy(
                            commandPreview = built.printableCommand,
                            totalDurationMs = built.totalDurationMs,
                            totalFrames = built.totalFrames,
                            encoderLabel = built.encoderName
                        )
                    }
                },
                onProgress = { timeMs, frame, fps, speed, _ ->
                    _renderStats.update { stats ->
                        val totalMs = stats.totalDurationMs.coerceAtLeast(1L)
                        val progress = (timeMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 0.99f)
                        val remainingMs = (totalMs - timeMs).coerceAtLeast(0L)
                        val etaSec = if (speed > 0.05f) {
                            ((remainingMs / 1000f) / speed).toLong()
                        } else 0L
                        stats.copy(
                            progress = progress,
                            currentTimeMs = timeMs,
                            currentFrame = frame,
                            fps = fps,
                            speed = speed,
                            etaSeconds = etaSec
                        )
                    }
                },
                onLog = { line ->
                    _renderStats.update { stats ->
                        stats.copy(logs = (stats.logs + line).takeLast(120))
                    }
                },
                onCompleted = { outputPath ->
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val galleryResult = StorageHelper.saveVideoToGallery(
                        getApplication(),
                        outputPath,
                        "ClipForge_$stamp.mp4"
                    )
                    val galleryUri = galleryResult.getOrNull()
                    _renderStats.update { it.copy(progress = 1f, etaSeconds = 0L) }
                    _renderState.value = RenderState.Completed(outputPath, galleryUri)
                    _statusBanner.value = if (galleryUri != null) {
                        "Saved to Movies/ClipForge/"
                    } else {
                        "Rendered to cache (tap Save to Gallery to export)."
                    }
                },
                onCancelled = {
                    _renderState.value = RenderState.Cancelled
                },
                onFailed = { message, canRetrySoftware ->
                    if (canRetrySoftware) {
                        startRender(useSoftwareFallback = true)
                    } else {
                        _renderState.value = RenderState.Failed(message)
                    }
                }
            )
        }
    }

    fun cancelRender() {
        val state = _renderState.value
        if (state is RenderState.Rendering) {
            renderManager.cancel(state.sessionId)
        }
    }

    fun saveCompletedToGallery() {
        val state = _renderState.value
        if (state !is RenderState.Completed) return
        viewModelScope.launch(Dispatchers.IO) {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            StorageHelper.saveVideoToGallery(getApplication(), state.outputPath, "ClipForge_$stamp.mp4")
                .onSuccess { uri ->
                    _renderState.value = state.copy(savedGalleryUri = uri)
                    _statusBanner.value = "Exported to Movies/ClipForge/"
                }
                .onFailure { err ->
                    _statusBanner.value = "Save failed: ${err.message}"
                }
        }
    }
}
