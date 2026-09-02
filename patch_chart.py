import re

with open('app/src/main/java/com/example/ui/components/SensorWaveformChart.kt', 'r') as f:
    content = f.read()

content = re.sub(r'import com\.example\.ui\.theme\..*', '', content)
content = content.replace('import com.example.data.model.RawSensorReading', 'import com.example.data.model.RawSensorReading\nimport com.example.ui.theme.*')

with open('app/src/main/java/com/example/ui/components/SensorWaveformChart.kt', 'w') as f:
    f.write(content)

