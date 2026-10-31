package com.example.data.local.autosave

import com.example.core.packaging.RoomProjectPackager
import com.example.data.local.dao.KitchenProjectDao
import com.example.data.local.entity.AutoSaveSnapshotEntity
import com.example.data.local.relation.ProjectWithAssets
import com.example.data.local.relation.SavedCameraState
import com.example.domain.repository.KitchenProject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32

/**
 * Current execution status of the periodic & debounced Auto-Save Engine.
 */
enum class AutoSaveStatus(val labelAr: String, val labelEn: String) {
    IDLE("جاهز", "Idle"),
    PENDING_CHANGES("تغييرات معلقة...", "Pending changes..."),
    SAVING("جارٍ الحفظ التلقائي...", "Auto-saving..."),
    SAVED("تم الحفظ تلقائياً ✓", "Auto-saved ✓"),
    PAUSED("الحفظ التلقائي متوقف", "Auto-save paused"),
    ERROR("تعذر الحفظ التلقائي", "Auto-save failed")
}

/**
 * Trigger source for an auto-save snapshot entry.
 */
enum class AutoSaveTrigger(val code: String, val labelAr: String) {
    INTERVAL("INTERVAL", "دوري تلقائي"),
    DEBOUNCED_EDIT("DEBOUNCED_EDIT", "تعديل مباشر"),
    MANUAL("MANUAL", "حفظ فوري يدوي"),
    LIFECYCLE("LIFECYCLE", "حفظ أمان النظام")
}

/**
 * Observable UI state for the real-time Auto-Save Engine.
 */
data class AutoSaveUiState(
    val isEnabled: Boolean = true,
    val intervalSeconds: Int = ProjectAutoSaveManager.DEFAULT_INTERVAL_SECONDS,
    val status: AutoSaveStatus = AutoSaveStatus.SAVED,
    val lastSavedTimestampMs: Long? = null,
    val lastSavedFormattedTime: String = "الآن",
    val lastChecksumCrc32: Long = 0L,
    val lastPayloadBytes: Int = 0,
    val totalAutoSaveCount: Int = 0,
    val hasUnsavedChanges: Boolean = false,
    val recentSnapshots: List<AutoSaveSnapshotEntity> = emptyList()
)

/**
 * Periodic & Debounced Project Auto-Save Engine.
 *
 * Serializes the complete [KitchenProject] state (including room metadata, wall openings,
 * furniture positions/rotations/dimensions, and camera coordinates) into a local Room
 * database entry ([AutoSaveSnapshotEntity] + relational tables) at defined intervals
 * to prevent data loss.
 */
class ProjectAutoSaveManager(
    private val dao: KitchenProjectDao? = null,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    initialIntervalSeconds: Int = DEFAULT_INTERVAL_SECONDS,
    private val repository: com.example.domain.repository.KitchenProjectRepository? = null
) {
    companion object {
        const val DEFAULT_INTERVAL_SECONDS = 15
        const val MIN_INTERVAL_SECONDS = 5
        const val MAX_INTERVAL_SECONDS = 120
        const val DEBOUNCE_DELAY_MS = 1_500L
        const val MAX_SNAPSHOT_HISTORY = 12

        val AVAILABLE_INTERVAL_SECONDS = listOf(5, 15, 30, 60)

        /**
         * Pure helper that serializes a [KitchenProject] and optional [SavedCameraState]
         * into an [AutoSaveSnapshotEntity] ready for Room persistence.
         */
        fun buildSnapshotEntity(
            project: KitchenProject,
            cameraState: SavedCameraState? = null,
            intervalSeconds: Int = DEFAULT_INTERVAL_SECONDS,
            trigger: AutoSaveTrigger = AutoSaveTrigger.INTERVAL,
            createRollingCheckpoint: Boolean = false,
            timestampMs: Long = System.currentTimeMillis()
        ): AutoSaveSnapshotEntity {
            val jsonPayload = RoomProjectPackager.serializeProjectToJson(project, cameraState)
            val payloadBytes = jsonPayload.toByteArray(StandardCharsets.UTF_8)
            val crc = CRC32().apply { update(payloadBytes) }.value

            val snapshotId = if (createRollingCheckpoint) {
                "autosave_ckpt_${project.id}_$timestampMs"
            } else {
                "autosave_primary_${project.id}"
            }

            return AutoSaveSnapshotEntity(
                snapshotId = snapshotId,
                projectId = project.id,
                projectName = project.name,
                serializedStateJson = jsonPayload,
                roomWidthMm = project.room.widthMm,
                roomDepthMm = project.room.depthMm,
                roomHeightMm = project.room.heightMm,
                wallThicknessMm = project.room.wallThicknessMm,
                furnitureCount = project.elements.size,
                stateChecksumCrc32 = crc,
                payloadSizeBytes = payloadBytes.size,
                intervalSeconds = intervalSeconds,
                triggerType = trigger.code,
                savedAtTimestamp = timestampMs
            )
        }

        /**
         * Deserializes a stored [AutoSaveSnapshotEntity] back into a domain [KitchenProject]
         * and optional [SavedCameraState].
         */
        fun deserializeSnapshot(
            snapshot: AutoSaveSnapshotEntity
        ): Pair<KitchenProject, SavedCameraState?> {
            val project = RoomProjectPackager.parseProjectFromJson(snapshot.serializedStateJson)
            val cameraState = RoomProjectPackager.parseCameraStateFromJson(snapshot.serializedStateJson)
            return project to cameraState
        }

        /**
         * Computes a fast deterministic CRC32 checksum of the project's room metadata
         * and furniture positions/rotations/dimensions to detect dirty state.
         */
        fun computeStateChecksum(
            project: KitchenProject,
            cameraState: SavedCameraState? = null
        ): Long {
            val sb = StringBuilder(512)
            sb.append(project.id).append('|').append(project.name).append('|')
            val r = project.room
            sb.append(r.widthMm).append(',').append(r.depthMm).append(',').append(r.heightMm)
                .append(',').append(r.wallThicknessMm).append(',')
                .append(r.floorMaterial.id).append(',').append(r.wallFinish.id).append('|')
            for (w in r.walls) {
                sb.append(w.side.name).append(':')
                for (op in w.openings) {
                    sb.append(op.id).append('@').append(op.offsetMm).append('x').append(op.widthMm).append(';')
                }
            }
            sb.append('|')
            for (e in project.elements) {
                sb.append(e.id).append(':')
                    .append(e.type.name).append('@')
                    .append(e.position.x).append(',').append(e.position.y).append(',').append(e.position.z).append('#')
                    .append(e.rotationY).append('#')
                    .append(e.dimensions.x).append(',').append(e.dimensions.y).append(',').append(e.dimensions.z).append('#')
                    .append(e.material.id).append('#')
                    .append(e.countertopMaterial?.id ?: "").append('#')
                    .append(e.isVisible).append('#').append(e.isLocked).append(';')
            }
            if (cameraState != null) {
                sb.append("|cam:").append(cameraState.mode).append(',')
                    .append(cameraState.yaw.toInt()).append(',')
                    .append(cameraState.pitch.toInt()).append(',')
                    .append((cameraState.zoom * 100).toInt())
            }
            val bytes = sb.toString().toByteArray(StandardCharsets.UTF_8)
            return CRC32().apply { update(bytes) }.value
        }
    }

    private val _state = MutableStateFlow(
        AutoSaveUiState(
            intervalSeconds = initialIntervalSeconds.coerceIn(MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS)
        )
    )
    val state: StateFlow<AutoSaveUiState> = _state.asStateFlow()

    private var periodicJob: Job? = null
    private var debounceJob: Job? = null

    @Volatile
    private var latestProject: KitchenProject? = null

    @Volatile
    private var latestCameraState: SavedCameraState? = null

    @Volatile
    private var lastPersistedStateChecksum: Long = 0L

    init {
        observeStoredSnapshots()
        startPeriodicLoop()
    }

    private fun observeStoredSnapshots() {
        val flowSource = when {
            repository != null -> repository.observeAutoSaveSnapshots(MAX_SNAPSHOT_HISTORY)
            dao != null -> dao.observeRecentAutoSaveSnapshots(MAX_SNAPSHOT_HISTORY)
            else -> return
        }
        scope.launch(ioDispatcher) {
            flowSource.collect { snapshots ->
                if (snapshots.isNotEmpty()) {
                    _state.update { it.copy(recentSnapshots = snapshots) }
                }
            }
        }
    }

    /**
     * Starts or restarts the periodic interval coroutine that serializes the active project
     * state into Room every [AutoSaveUiState.intervalSeconds] seconds.
     */
    private fun startPeriodicLoop() {
        periodicJob?.cancel()
        if (!_state.value.isEnabled) return

        periodicJob = scope.launch(ioDispatcher) {
            while (isActive && _state.value.isEnabled) {
                val intervalMs = _state.value.intervalSeconds.coerceAtLeast(MIN_INTERVAL_SECONDS) * 1000L
                delay(intervalMs)

                val projectSnapshot = latestProject ?: continue
                val cameraSnapshot = latestCameraState
                val currentChecksum = computeStateChecksum(projectSnapshot, cameraSnapshot)

                if (currentChecksum != lastPersistedStateChecksum || _state.value.totalAutoSaveCount == 0) {
                    performSerializationAndPersist(
                        project = projectSnapshot,
                        cameraState = cameraSnapshot,
                        trigger = AutoSaveTrigger.INTERVAL,
                        createCheckpoint = true
                    )
                }
            }
        }
    }

    /**
     * Notifies the Auto-Save Manager that the project or camera state has mutated.
     * Marks changes as pending and schedules a debounced write in addition to the periodic timer.
     */
    fun onProjectStateModified(
        project: KitchenProject,
        cameraState: SavedCameraState? = latestCameraState
    ) {
        latestProject = project
        latestCameraState = cameraState

        val newChecksum = computeStateChecksum(project, cameraState)
        if (newChecksum == lastPersistedStateChecksum && _state.value.totalAutoSaveCount > 0) {
            return
        }

        _state.update {
            it.copy(
                status = if (it.isEnabled) AutoSaveStatus.PENDING_CHANGES else AutoSaveStatus.PAUSED,
                hasUnsavedChanges = true
            )
        }

        if (!_state.value.isEnabled) return

        debounceJob?.cancel()
        debounceJob = scope.launch(ioDispatcher) {
            delay(DEBOUNCE_DELAY_MS)
            val currentProj = latestProject ?: return@launch
            performSerializationAndPersist(
                project = currentProj,
                cameraState = latestCameraState,
                trigger = AutoSaveTrigger.DEBOUNCED_EDIT,
                createRollingCheckpoint = false
            )
        }
    }

    /**
     * Updates the latest camera coordinates so the next auto-save includes them.
     */
    fun updateCameraCoordinates(cameraState: SavedCameraState) {
        latestCameraState = cameraState
    }

    /**
     * Immediately serializes and persists the current project state into Room.
     * Can be invoked manually by the user, on lifecycle pause, or from unit tests.
     */
    suspend fun saveNow(
        project: KitchenProject? = latestProject,
        cameraState: SavedCameraState? = latestCameraState,
        trigger: AutoSaveTrigger = AutoSaveTrigger.MANUAL,
        createCheckpoint: Boolean = true
    ): AutoSaveSnapshotEntity? {
        val targetProject = project ?: return null
        latestProject = targetProject
        if (cameraState != null) {
            latestCameraState = cameraState
        }
        debounceJob?.cancel()
        return performSerializationAndPersist(
            project = targetProject,
            cameraState = latestCameraState,
            trigger = trigger,
            createRollingCheckpoint = createCheckpoint
        )
    }

    /**
     * Configures the periodic auto-save interval (in seconds).
     */
    fun setAutoSaveIntervalSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS)
        _state.update { it.copy(intervalSeconds = clamped) }
        startPeriodicLoop()
    }

    /**
     * Enables or pauses automatic background saving.
     */
    fun setAutoSaveEnabled(enabled: Boolean) {
        _state.update {
            it.copy(
                isEnabled = enabled,
                status = if (enabled) {
                    if (it.hasUnsavedChanges) AutoSaveStatus.PENDING_CHANGES else AutoSaveStatus.SAVED
                } else {
                    AutoSaveStatus.PAUSED
                }
            )
        }
        if (enabled) {
            startPeriodicLoop()
            val proj = latestProject
            if (proj != null && _state.value.hasUnsavedChanges) {
                scope.launch(ioDispatcher) {
                    performSerializationAndPersist(
                        project = proj,
                        cameraState = latestCameraState,
                        trigger = AutoSaveTrigger.INTERVAL,
                        createRollingCheckpoint = true
                    )
                }
            }
        } else {
            periodicJob?.cancel()
            debounceJob?.cancel()
        }
    }

    private suspend fun performSerializationAndPersist(
        project: KitchenProject,
        cameraState: SavedCameraState?,
        trigger: AutoSaveTrigger,
        createRollingCheckpoint: Boolean = false,
        createCheckpoint: Boolean = createRollingCheckpoint
    ): AutoSaveSnapshotEntity = withContext(ioDispatcher) {
        _state.update { it.copy(status = AutoSaveStatus.SAVING) }

        val now = System.currentTimeMillis()
        val intervalSec = _state.value.intervalSeconds

        val snapshot = buildSnapshotEntity(
            project = project,
            cameraState = cameraState,
            intervalSeconds = intervalSec,
            trigger = trigger,
            createRollingCheckpoint = createCheckpoint,
            timestampMs = now
        )

        try {
            when {
                repository != null -> {
                    repository.saveAutoSaveSnapshot(
                        project = project,
                        cameraState = cameraState,
                        snapshot = snapshot
                    )
                }
                dao != null -> {
                    val (metadata, assets, openings) = ProjectWithAssets.fromDomain(project, cameraState)
                    dao.saveAutoSaveEntryWithRelationalSync(
                        metadata = metadata,
                        assets = assets,
                        openings = openings,
                        snapshot = snapshot,
                        maxHistoryCount = MAX_SNAPSHOT_HISTORY
                    )
                }
            }

            lastPersistedStateChecksum = computeStateChecksum(project, cameraState)
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val formattedTime = timeFormat.format(Date(now))

            _state.update { prev ->
                val updatedHistory = (listOf(snapshot) + prev.recentSnapshots.filter { it.snapshotId != snapshot.snapshotId })
                    .sortedByDescending { it.savedAtTimestamp }
                    .take(MAX_SNAPSHOT_HISTORY)

                prev.copy(
                    status = AutoSaveStatus.SAVED,
                    lastSavedTimestampMs = now,
                    lastSavedFormattedTime = formattedTime,
                    lastChecksumCrc32 = snapshot.stateChecksumCrc32,
                    lastPayloadBytes = snapshot.payloadSizeBytes,
                    totalAutoSaveCount = prev.totalAutoSaveCount + 1,
                    hasUnsavedChanges = false,
                    recentSnapshots = updatedHistory
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(status = AutoSaveStatus.ERROR)
            }
        }

        snapshot
    }
}
