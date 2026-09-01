# Netra AI Power Architecture Guidelines

This document contains persistent core guidelines and design rules for the **Netra AI Assistant** (previously known as Battery Sentinel Pro AI). These rules are automatically loaded by the agent system and must be strictly enforced across all future iterations, development phases, and features.

---

## 1. Sleep-First Design (Core Principle)

The AI is designed to be **Always Available but Never Always Running**. It should remain in a deep Sleep State (consuming 0% CPU, no background threads, and no microphone listening) to prevent heating the phone or draining the battery.

- **App Closed:** The AI is completely asleep and uninitialized.
- **App Open:** The AI wakes up only to display an initial welcome greeting, then automatically suspends to a Deep Sleep State within seconds.
- **Query Completion:** Immediately after processing a query, answering, or playing text-to-speech, the AI must go back to Sleep State.
- **Skip/Close:** If the user skips or closes the assistant overlay, it is fully suspended.

---

## 2. Event-Based Activation

The AI Assistant is strictly event-activated. It must never run passive background loops or threads. It awakens *only* on:
1. **App Launch / Manual Summoning:** Initial screen greeting.
2. **User Voice Command:** Actively triggered by user via Push-to-Talk or Tap-to-Talk button.
3. **User Text Query:** Standard query submission.
4. **Critical System Alert:** Automatic wake-up when critical thresholds are exceeded (e.g., Battery Temp > 45°C) to alert the user.

---

## 3. Sensor Data Integration (Single Source of Truth)

The AI must **never** read hardware sensors directly, nor poll them. It must consume already-cached, validated live data provided by the `MainViewModel` or `NetraSafetyRepository`. This prevents duplicate threads and redundant power usage.

---

## 4. No Continuous Listening

Always-on background microphone monitoring is strictly prohibited. Microphone capture/speech recognition must initiate *only* when the user explicitly clicks the Microphone or Push-to-Talk button.

---

## 5. Avatar Frame-Rate & Animation Optimization

- **Base Target:** Frame rate of any avatar animations must be limited (maximum 30 FPS, preferred state-based pulse).
- **Idle State:** Idle animations should shut off or transition to extremely low-frequency breath pulses when not actively speaking or thinking.
- **Off-Screen:** If scrolled off-screen or invisible, rendering of any animations must pause completely.

---

## 6. On-Device Processing & Response Cache

- **Latency:** Deliver response calculations within a few hundred milliseconds.
- **Caching:** Maintain a lightweight local query cache. If the exact same query is asked within a short timeframe (e.g., 5 seconds), deliver the response from cache instantly without rebuilding the execution pipeline.

---

## 7. Adaptive Performance (Lite Mode)

If the device falls into a low-power state or thermal stress:
- **Condition:** Battery < 20% or Battery Temperature > 42°C.
- **Action:** Auto-switch the assistant interface into **Lite Mode**.
- **Lite Mode Specs:** Freeze all avatar rendering to static wireframe outlines, remove glowing drop shadows, disable non-essential sound/vibrations, and scale down operations to save maximum power.

---

## 8. Zero Fabrication (Absolute Truth Policy)

The AI must never invent, estimate (without verified mathematical formulas), or assume data. Every value, alert, or security advice must be traced directly back to real-time, verified sensor readings or diagnostic results. If verified data is unavailable, declare: **"Verified data is currently unavailable."**

---

## 9. Universal AI Repair & Upgrade Policy v1.0

### Global Rule

During every bug fix, feature implementation, optimization, refactoring, or UI update, automatically use all available AI capabilities **only when they are relevant to the current task**.

The repair process must intelligently select the required capabilities instead of enabling every feature indiscriminately.

---

## Capability Mapping

### High Thinking

Use for:

* Logic bugs
* Algorithm design
* Battery prediction
* Recovery architecture
* Watchdog rules
* Root cause analysis

Always enabled for engineering decisions.

---

### Low Latency

Use for:

* Live battery updates
* Sensor monitoring
* Notification updates
* UI refresh
* Real-time graphs

---

### Gemini Intelligence

Use for:

* System analysis
* Code reasoning
* Recovery planning
* Performance optimization
* Architecture validation

---

### Voice

Use only for:

* TTS announcements
* Voice assistant
* Speech interaction

Do not invoke during unrelated fixes.

---

### Google Maps

Use only when:

* Location services
* Safe Zones
* Driving Mode
* Weather by location
* Emergency routing

---

### Google Search

Use only for:

* Weather provider validation
* Battery best practices
* API verification
* Documentation lookup
* Standards compliance

Never replace local offline logic with search results.

---

### Image Generation / Editing

Use only for:

* UI mockups
* Icons
* Splash screens
* Infographics
* Tutorials
* Documentation assets

Not for backend debugging.

---

### Music

Use only for:

* Alarm tones
* Notification sounds
* TTS resources
* Audio assets

---

### Aspect Ratio

Use only while generating:

* Posters
* Widgets
* Marketing images
* Tutorials

---

### Image Analysis

Use for:

* Screenshot debugging
* UI verification
* Layout comparison
* OCR validation
* Visual regression testing

This should always be used when the user provides screenshots to diagnose UI problems.

---

### Database & Authentication

Use whenever:

* Login
* User profiles
* Settings sync
* Cloud backup
* Notification history
* Logs
* Preferences

---

# Universal Repair Pipeline

Every repair must follow:

```text
Analyze Problem
        │
        ▼
Select Required AI Capabilities
        │
        ▼
Root Cause Analysis
        │
        ▼
Implement Fix
        │
        ▼
Compile
        │
        ▼
Run Tests
        │
        ▼
Regression Testing
        │
        ▼
Performance Validation
        │
        ▼
Battery Impact Analysis
        │
        ▼
Production Verification
```

---

# Mandatory Validation

Every completed fix must verify:

* No compile errors
* No runtime crashes
* No UI regressions
* No duplicate logs
* No duplicate notifications
* No duplicate announcements
* Battery impact acceptable
* Memory usage stable
* CPU usage stable
* Background services healthy
* Watchdog healthy
* Prediction engine validated
* All existing features still functional

---

## Engineering Principle

**Use the most appropriate AI capability for each task rather than enabling every capability by default.** This keeps the repair process faster, more reliable, easier to debug, and avoids introducing unrelated complexity while still taking advantage of advanced AI analysis where it adds value.
