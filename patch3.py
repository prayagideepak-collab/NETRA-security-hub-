import re

with open('app/src/main/java/com/example/data/sensor/SensorManager.kt', 'r') as f:
    content = f.read()

# Replace startMonitoring, stopMonitoring, and launchSensorStream
pattern = r'    fun startMonitoring\(\).*?    private val readingBuffer = java\.util\.concurrent\.ConcurrentLinkedQueue<RawSensorReading>\(\)'
replacement = '''    fun startMonitoring() {
        if (_isMonitoringActive.value) return
        if (job.isCancelled || !scope.coroutineContext[Job]!!.isActive) {
            job = Job()
            scope = CoroutineScope(Dispatchers.Default + job)
        }
        _isMonitoringActive.value = true
        
        evaluateSensorStreams()

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

    fun stopMonitoring() {
        _isMonitoringActive.value = false
        batteryManager.stopMonitoring()
        sensorJobs.values.forEach { it.cancel() }
        sensorJobs.clear()
        job.cancel()
    }

    private fun evaluateSensorStreams() {
        if (!_isMonitoringActive.value) {
            sensorJobs.values.forEach { it.cancel() }
            sensorJobs.clear()
            return
        }

        val supportedCapabilities = capabilityManager.getSupportedCapabilities()
        supportedCapabilities.forEach { cap ->
            val isEnabledBySettings = _monitoringEnabled.value[cap.type] == true
            val hasSubscribers = subscribers.value[cap.type]?.isNotEmpty() == true

            val isCoreSafety = cap.type == Sensor.TYPE_ACCELEROMETER || cap.type == Sensor.TYPE_STEP_DETECTOR || cap.type == Sensor.TYPE_STEP_COUNTER
            val shouldRun = (isEnabledBySettings && isCoreSafety) || hasSubscribers || isEnabledBySettings
            
            if (shouldRun && !sensorJobs.containsKey(cap.type)) {
                val baseThrottle = _sensorThrottles.value[cap.type] ?: 150L
                val thermalMultiplier = when (thermalState) {
                    ThermalState.NORMAL -> 1L
                    ThermalState.WARM -> 2L
                    ThermalState.HIGH -> 5L
                    ThermalState.THERMAL_PROTECTION -> 20L
                }
                val throttle = baseThrottle * thermalMultiplier
                
                val unit = when (cap.type) {
                    Sensor.TYPE_ACCELEROMETER -> "m/s²"
                    Sensor.TYPE_GYROSCOPE -> "rad/s"
                    Sensor.TYPE_STEP_COUNTER -> "steps"
                    Sensor.TYPE_STEP_DETECTOR -> "pulse"
                    Sensor.TYPE_LIGHT -> "Lux"
                    Sensor.TYPE_MAGNETIC_FIELD -> "µT"
                    Sensor.TYPE_PROXIMITY -> "cm"
                    Sensor.TYPE_PRESSURE -> "hPa"
                    Sensor.TYPE_AMBIENT_TEMPERATURE -> "°C"
                    Sensor.TYPE_RELATIVE_HUMIDITY -> "%"
                    else -> "unknown"
                }
                
                val j = scope.launch {
                    sensorObservers.observeHardwareSensor(cap.type, cap.name, cap.category, unit, throttle).collect { reading ->
                        handleIncomingReading(reading)
                    }
                }
                sensorJobs[cap.type] = j
            } else if (!shouldRun && sensorJobs.containsKey(cap.type)) {
                sensorJobs[cap.type]?.cancel()
                sensorJobs.remove(cap.type)
            }
        }
    }

    private val readingBuffer = java.util.concurrent.ConcurrentLinkedQueue<RawSensorReading>()'''

new_content = re.sub(pattern, replacement, content, flags=re.DOTALL)
with open('app/src/main/java/com/example/data/sensor/SensorManager.kt', 'w') as f:
    f.write(new_content)
