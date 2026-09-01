package com.example.data.sensor

import android.content.Context
import android.hardware.Sensor
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.data.model.RawSensorReading
import com.example.data.model.SensorCapabilityInfo
import com.example.data.model.SensorCategory
import com.example.data.model.SensorFusionState
import com.example.data.engine.PowerMode
import com.example.data.engine.HealthCenterEngine
import com.example.data.event.SensorEvent
import com.example.data.event.SensorEventBus
import com.example.data.event.EventPriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.data.service.BatteryManager
import com.example.data.repository.SettingsRepository
import com.example.util.LoggingManager

/**
 * SensorManager handles lifecycle-aware initialization, active monitoring, and pause/resume management
 * for Android hardware sensors while establishing the capability discovery process via [CapabilityManager].
 */
class SensorManager(
    private val context: Context,
    private val capabilityManager: CapabilityManager = CapabilityManager(context),
    private val sensorObservers: SensorObservers = SensorObservers(context),
    val fusionEngine: SensorFusionEngine = SensorFusionEngine(context),
    val healthCenterEngine: HealthCenterEngine = HealthCenterEngine(context),
    val motionIntelligenceEngine: com.example.data.engine.MotionIntelligenceEngine = com.example.data.engine.MotionIntelligenceEngine(context),
    val batteryManager: BatteryManager = BatteryManager(context),
    private val powerManagerEngine: com.example.data.engine.PowerManagerEngine = com.example.data.engine.PowerManagerEngine(),
    private val settingsRepository: SettingsRepository = SettingsRepository(context),
    val stateManager: SensorStateManager = SensorStateManager()
) : DefaultLifecycleObserver {

    private var thermalState: ThermalState = ThermalState.NORMAL

    enum class ThermalState { NORMAL, WARM, HIGH, THERMAL_PROTECTION }

    private fun updateThermalState(tempC: Float) {
        val newState = when {
            tempC >= 45f -> ThermalState.THERMAL_PROTECTION
            tempC >= 42f -> ThermalState.HIGH
            tempC >= 38f -> ThermalState.WARM
            else -> ThermalState.NORMAL
        }
        
        if (newState != thermalState) {
            thermalState = newState
            if (_isMonitoringActive.value) {
                // Throttle adjustments on thermal state change
                stopMonitoring()
                startMonitoring()
            }
        }
    }

    private val _liveReadings = MutableStateFlow<Map<String, RawSensorReading>>(emptyMap())
    val liveReadings: StateFlow<Map<String, RawSensorReading>> = _liveReadings.asStateFlow()

    private var job = Job()
    private var scope = CoroutineScope(Dispatchers.Default + job)

    private val _capabilities = MutableStateFlow<List<SensorCapabilityInfo>>(emptyList())
    val capabilities: StateFlow<List<SensorCapabilityInfo>> = _capabilities.asStateFlow()

    private val _fusionState = MutableStateFlow(SensorFusionState())
    val fusionState: StateFlow<SensorFusionState> = _fusionState.asStateFlow()

    private val _isMonitoringActive = MutableStateFlow(false)
    val isMonitoringActive: StateFlow<Boolean> = _isMonitoringActive.asStateFlow()

    private val _sensorThrottles = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val sensorThrottles: StateFlow<Map<Int, Long>> = _sensorThrottles.asStateFlow()

    private val _monitoringEnabled = MutableStateFlow<Map<Int, Boolean>>(
        mapOf(
            Sensor.TYPE_ACCELEROMETER to true,
            Sensor.TYPE_GYROSCOPE to true,
            Sensor.TYPE_LIGHT to true,
            Sensor.TYPE_MAGNETIC_FIELD to true,
            Sensor.TYPE_PROXIMITY to true
        )
    )

    private val sequenceNumbers = java.util.concurrent.ConcurrentHashMap<String, Int>()

    init {
        com.example.util.LoggingManager.init(context)
        // Establish capability discovery process upon manager instantiation
        runCapabilityDiscovery()
        
        // Observe power mode changes
        scope.launch {
            powerManagerEngine.powerMode.collect { mode ->
                updateThrottlingBasedOnPowerMode(mode)
            }
        }


        scope.launch { settingsRepository.monitorMagnetic.collect { updateMonitoringState(Sensor.TYPE_MAGNETIC_FIELD, it) } }
        scope.launch { settingsRepository.monitorLight.collect { updateMonitoringState(Sensor.TYPE_LIGHT, it) } }
        scope.launch { settingsRepository.monitorProximity.collect { updateMonitoringState(Sensor.TYPE_PROXIMITY, it) } }
    }

    private fun updateMonitoringState(type: Int, enabled: Boolean) {
        val current = _monitoringEnabled.value.toMutableMap()
        current[type] = enabled
        _monitoringEnabled.value = current
        
        // Restart monitoring to apply changes if active
        if (_isMonitoringActive.value) {
            stopMonitoring()
            startMonitoring()
        }
    }

    private fun updateThrottlingBasedOnPowerMode(mode: PowerMode) {
        val multiplier = when (mode) {
            PowerMode.ACTIVE -> 1L
            PowerMode.PASSIVE -> 2L
            PowerMode.IDLE -> 5L
            PowerMode.ADAPTIVE_QUIET -> 10L
        }
        
        // Define base intervals
        val baseIntervals = mapOf(
            Sensor.TYPE_ACCELEROMETER to 150L,
            Sensor.TYPE_GYROSCOPE to 200L,
            Sensor.TYPE_LIGHT to 1000L,
            Sensor.TYPE_MAGNETIC_FIELD to 500L
        )
        
        baseIntervals.forEach { (type, baseInterval) ->
            setSensorThrottle(type, baseInterval * multiplier)
        }
    }

    fun setSensorThrottle(sensorType: Int, intervalMs: Long) {
        val current = _sensorThrottles.value.toMutableMap()
        current[sensorType] = intervalMs
        _sensorThrottles.value = current
        if (_isMonitoringActive.value) {
            stopMonitoring()
            startMonitoring()
        }
    }

    fun setThermalThreshold(threshold: Int) {
        fusionEngine.thermalThresholdC = threshold
    }

    /**
     * Triggers capability discovery process via CapabilityManager.
     */
    fun runCapabilityDiscovery(): List<SensorCapabilityInfo> {
        val discoveredList = capabilityManager.discoverCapabilities()
        _capabilities.value = discoveredList
        com.example.data.engine.NetraWatchdogEngine.notifyUpdate("Sensor Status")
        return discoveredList
    }

    /**
     * Starts active lifecycle-aware sensor monitoring streams for all supported hardware capabilities.
     */
    fun startMonitoring() {
        if (_isMonitoringActive.value) return

        if (job.isCancelled || !scope.coroutineContext[Job]!!.isActive) {
            job = Job()
            scope = CoroutineScope(Dispatchers.Default + job)
        }

        _isMonitoringActive.value = true

        val supportedCapabilities = capabilityManager.getSupportedCapabilities()

        // 1. Motion & Environmental Sensors
        supportedCapabilities.forEach { cap ->
            if (_monitoringEnabled.value[cap.type] == false) return@forEach
            
            val baseThrottle = _sensorThrottles.value[cap.type] ?: 150L
            val thermalMultiplier = when (thermalState) {
                ThermalState.NORMAL -> 1L
                ThermalState.WARM -> 2L
                ThermalState.HIGH -> 5L
                ThermalState.THERMAL_PROTECTION -> 20L
            }
            val throttle = baseThrottle * thermalMultiplier

            when (cap.type) {
                Sensor.TYPE_ACCELEROMETER -> launchSensorStream(Sensor.TYPE_ACCELEROMETER, "Accelerometer", SensorCategory.MOTION, "m/s²", throttle)
                Sensor.TYPE_GYROSCOPE -> launchSensorStream(Sensor.TYPE_GYROSCOPE, "Gyroscope", SensorCategory.MOTION, "rad/s", throttle)
                Sensor.TYPE_STEP_COUNTER -> launchSensorStream(Sensor.TYPE_STEP_COUNTER, "Step Counter", SensorCategory.MOTION, "steps", 500L)
                Sensor.TYPE_STEP_DETECTOR -> launchSensorStream(Sensor.TYPE_STEP_DETECTOR, "Step Detector", SensorCategory.MOTION, "pulse", 50L)
                Sensor.TYPE_LIGHT -> launchSensorStream(Sensor.TYPE_LIGHT, "Ambient Light", SensorCategory.ENVIRONMENTAL, "Lux", throttle)
                Sensor.TYPE_MAGNETIC_FIELD -> launchSensorStream(Sensor.TYPE_MAGNETIC_FIELD, "Magnetometer", SensorCategory.ENVIRONMENTAL, "µT", throttle)
                Sensor.TYPE_PROXIMITY -> launchSensorStream(Sensor.TYPE_PROXIMITY, "Proximity", SensorCategory.ENVIRONMENTAL, "cm", throttle)
                Sensor.TYPE_PRESSURE -> launchSensorStream(Sensor.TYPE_PRESSURE, "Barometer", SensorCategory.ENVIRONMENTAL, "hPa", throttle)
                Sensor.TYPE_AMBIENT_TEMPERATURE -> launchSensorStream(Sensor.TYPE_AMBIENT_TEMPERATURE, "Ambient Temp", SensorCategory.ENVIRONMENTAL, "°C", throttle)
                Sensor.TYPE_RELATIVE_HUMIDITY -> launchSensorStream(Sensor.TYPE_RELATIVE_HUMIDITY, "Humidity", SensorCategory.ENVIRONMENTAL, "%", throttle)
            }
        }

        // 2. Battery & Power Stream
        batteryManager.startMonitoring()
        scope.launch {
            batteryManager.batteryState.collect { _ ->
                val reading = batteryManager.getAsRawReading()
                handleIncomingReading(reading)
            }
        }

        // 3. Thermal Subsystem Stream
        scope.launch {
            sensorObservers.observeThermalState().collect { reading ->
                handleIncomingReading(reading)
            }
        }

        // 4. GNSS / Location Stream
        scope.launch {
            sensorObservers.observeLocation().collect { reading ->
                handleIncomingReading(reading)
            }
        }
    }

    /**
     * Pauses sensor streams to prevent power draw and resource leakage when lifecycle pauses/stops.
     */
    fun stopMonitoring() {
        _isMonitoringActive.value = false
        batteryManager.stopMonitoring()
        job.cancel()
    }

    private fun launchSensorStream(type: Int, name: String, category: SensorCategory, unit: String, refreshIntervalMs: Long) {
        scope.launch {
            sensorObservers.observeHardwareSensor(type, name, category, unit, refreshIntervalMs).collect { reading ->
                handleIncomingReading(reading)
            }
        }
    }

    private val readingBuffer = java.util.concurrent.ConcurrentLinkedQueue<RawSensorReading>()
    private var lastAggregationTime = 0L
    private val AGGREGATION_INTERVAL = 5000L // 5 seconds

    private fun handleIncomingReading(reading: RawSensorReading) {
        val seq = (sequenceNumbers[reading.sensorId] ?: 0) + 1
        sequenceNumbers[reading.sensorId] = seq

        val enrichedReading = reading.copy(
            lastUpdateTimestamp = System.currentTimeMillis(),
            sequenceNumber = seq
        )

        val currentReadings = _liveReadings.value.toMutableMap()
        currentReadings[reading.sensorId] = enrichedReading
        _liveReadings.value = currentReadings

        val updatedFusion = fusionEngine.updateReading(enrichedReading)
        val watchdogStates = com.example.data.engine.NetraWatchdogEngine.moduleStates.value
        val refreshing = watchdogStates.filterValues { it.status == "Refreshing" || it.isRefreshing }.keys.toList()

        val finalFusion = updatedFusion.copy(
            isAnyModuleRefreshing = refreshing.isNotEmpty(),
            refreshingModules = refreshing,
            lastUpdateTimestamp = System.currentTimeMillis()
        )
        _fusionState.value = finalFusion
        
        // Update Single Source of Truth
        stateManager.updateSnapshot { current ->
            current.copy(
                batteryPercent = finalFusion.batteryLevelPercent,
                batteryTempC = finalFusion.batteryTempC,
                ambientTempC = finalFusion.ambientTemperatureC,
                magneticFieldUT = finalFusion.magneticMagnitudeuT,
                ambientLightLux = finalFusion.ambientLightLux,
                isCharging = finalFusion.isCharging,
                timestamp = System.currentTimeMillis()
            )
        }
        updateThermalState(finalFusion.batteryTempC)
        
        // Forward to Motion Intelligence Engine
        motionIntelligenceEngine.processSensorReading(enrichedReading)

        // Buffering for health center update
        readingBuffer.add(enrichedReading)
        val now = System.currentTimeMillis()
        if (now - lastAggregationTime > AGGREGATION_INTERVAL) {
            val batch = readingBuffer.toList()
            readingBuffer.clear()
            lastAggregationTime = now
            healthCenterEngine.update(finalFusion, batch.associateBy { it.sensorId })
        }

        // Watchdog notification mapping
        when (reading.sensorId) {
            "battery_telemetry" -> {
                SensorEventBus.post(SensorEvent(EventPriority.INFO, "Battery"))
                SensorEventBus.post(SensorEvent(EventPriority.INFO, "Charging"))
                SensorEventBus.post(SensorEvent(EventPriority.INFO, "Temperature"))
            }
            "sensor_2" -> { // TYPE_MAGNETIC_FIELD
                SensorEventBus.post(SensorEvent(EventPriority.INFO, "Magnetic Field"))
            }
            "sensor_13", "sensor_7" -> { // TYPE_AMBIENT_TEMPERATURE / thermal
                SensorEventBus.post(SensorEvent(EventPriority.INFO, "Temperature"))
            }
            "location_telemetry" -> {
                SensorEventBus.post(SensorEvent(EventPriority.CRITICAL, "Driving"))
            }
            "thermal_telemetry" -> {
                SensorEventBus.post(SensorEvent(EventPriority.CRITICAL, "Temperature"))
            }
        }
    }

    // --- LifecycleAware Observer Implementation ---

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        startMonitoring()
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        if (!_isMonitoringActive.value) {
            startMonitoring()
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        // Optionally pause non-essential sensor monitoring if required
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        stopMonitoring()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        stopMonitoring()
    }
}
