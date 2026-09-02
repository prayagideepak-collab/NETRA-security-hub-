import re

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'r') as f:
    content = f.read()

# 1. Remove properties from `private var activeThermalEventId: Long? = null` down to `private var lastHighSpeedAlertTime = 0L`
content = re.sub(
    r'    private var activeThermalEventId.*?    private var lastHighSpeedAlertTime = 0L\n',
    '',
    content,
    flags=re.DOTALL
)

# 2. Remove thermal and magnetic loops in first init block
content = re.sub(
    r'        // Observe fusionState & thermalThresholdC for detailed critical thermal monitoring and alerts\n.*?        // Observe fusionState for Level 1 - Early Heat Warning\n',
    '        // Observe fusionState for Level 1 - Early Heat Warning\n',
    content,
    flags=re.DOTALL
)

# 3. Remove second init block loops (from "init {" around 973 to end)
content = re.sub(
    r'    // Observe fusionState for Level 1 - Early Heat Warning\n    init \{\n        // Observe fusionState & monitorMagnetic for human magnetic safety classification.*?    fun refreshAiAnalysis\(\) \{',
    '    fun refreshAiAnalysis() {',
    content,
    flags=re.DOTALL
)

# 4. Remove `triggerTestEvent` and `resolveActiveMagneticEvent` and `detectRapidCooling`
content = re.sub(
    r'    private fun getMagneticSafetyZone.*?    private fun isBluetoothConnected\(\): Boolean \{',
    '    private fun isBluetoothConnected(): Boolean {',
    content,
    flags=re.DOTALL
)
content = re.sub(
    r'    private fun resolveActiveMagneticEvent.*?    private fun detectRapidCooling.*?    fun refreshAiAnalysis',
    '    fun refreshAiAnalysis',
    content,
    flags=re.DOTALL
)
content = re.sub(
    r'    fun triggerTestEvent.*?    fun clearLogs\(\) \{',
    '    fun clearLogs() {',
    content,
    flags=re.DOTALL
)

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'w') as f:
    f.write(content)
