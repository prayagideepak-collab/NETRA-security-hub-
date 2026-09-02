import re

with open('app/src/main/java/com/example/ui/MainScreen.kt', 'r') as f:
    content = f.read()

content = content.replace(
    '''                        NavigationTab.LIVE_GRAPH -> LiveGraphScreen(
                            liveReadings = liveReadings
                        )''',
    '''                        NavigationTab.LIVE_GRAPH -> {
                            val liveGraphState by viewModel.liveGraphState.androidx.lifecycle.compose.collectAsStateWithLifecycle()
                            LiveGraphScreen(
                                state = liveGraphState,
                                onSelectSensor = { viewModel.selectLiveGraphSensor(it) },
                                onTogglePause = { viewModel.setLiveGraphPaused(it) },
                                onStartSession = { viewModel.startLiveGraphSession() },
                                onStopSession = { viewModel.stopLiveGraphSession() }
                            )
                        }'''
)
with open('app/src/main/java/com/example/ui/MainScreen.kt', 'w') as f:
    f.write(content)
