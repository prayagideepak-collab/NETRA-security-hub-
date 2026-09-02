import re
with open('app/src/main/java/com/example/data/repository/NetraSafetyRepository.kt', 'r') as f:
    content = f.read()
content = re.sub(
    r'    suspend fun triggerSampleEvent.*?\n    suspend fun logActivity',
    '    suspend fun logActivity',
    content,
    flags=re.DOTALL
)
with open('app/src/main/java/com/example/data/repository/NetraSafetyRepository.kt', 'w') as f:
    f.write(content)

with open('app/src/main/java/com/example/ui/screens/ReportsScreen.kt', 'r') as f:
    content = f.read()
content = re.sub(
    r'        // Action Buttons: Run Test Trigger\n        item \{\n            Button\(\n.*?            \)\n        \}\n',
    '',
    content,
    flags=re.DOTALL
)
with open('app/src/main/java/com/example/ui/screens/ReportsScreen.kt', 'w') as f:
    f.write(content)

with open('app/src/main/java/com/example/ui/screens/HistoryLogsContainerScreen.kt', 'r') as f:
    content = f.read()
content = re.sub(
    r'                    onTriggerTestEvent = \{.*?                    \},\n',
    '',
    content,
    flags=re.DOTALL
)
with open('app/src/main/java/com/example/ui/screens/HistoryLogsContainerScreen.kt', 'w') as f:
    f.write(content)

