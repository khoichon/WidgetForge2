# WidgetForge

> **Production-ready Android homescreen widget engine supporting Text, Image, Animated GIF, and JavaScript Code-powered widgets — with cross-widget communication, battery-aware rendering, and full import/export.**

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Project Structure](#project-structure)
4. [Widget Types & File Formats](#widget-types--file-formats)
5. [Code Widget JavaScript API](#code-widget-javascript-api)
6. [Cross-Widget Communication Bus](#cross-widget-communication-bus)
7. [Battery Optimization & Visibility Tracking](#battery-optimization--visibility-tracking)
8. [Widget Registry & Instance Differentiation](#widget-registry--instance-differentiation)
9. [Building & Running](#building--running)
10. [Adding a Widget to Your Homescreen](#adding-a-widget-to-your-homescreen)
11. [Import / Export](#import--export)
12. [Sample Bundles](#sample-bundles)
13. [Extending WidgetForge](#extending-widgetforge)
14. [Architecture Decision Records](#architecture-decision-records)

---

## Overview

WidgetForge is a complete Android application (minSdk 26, targetSdk 35) that lets users:

- **Create** any of four widget types from a clean Compose dashboard
- **Preview** widgets before placing them on the homescreen
- **Place** widgets directly via the Android AppWidget system (drag from launcher)
- **Export** widgets to their canonical file formats (`.txt`, `.png`, `.gif`, `.zip`)
- **Import** those same files to re-create widgets on any device
- **Connect** widgets together via a real-time publish/subscribe channel bus

---

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                    Jetpack Compose UI                     │
│   DashboardScreen ─ TextEditor ─ ImagePicker ─ CodeEditor│
└────────────────────────┬─────────────────────────────────┘
                         │ ViewModel (Hilt)
┌────────────────────────▼─────────────────────────────────┐
│                   WidgetRegistry (Room DB)                │
│     appWidgetId → WidgetType + sourceFilePath + dims      │
└────┬──────────┬──────────┬────────────────────┬──────────┘
     │          │          │                    │
  TEXT       IMAGE        GIF               CODE ZIP
  Engine     Engine      Engine             Engine
     │          │          │                    │
  Bitmap     Bitmap    Movie frames       WebView +
  Canvas     Canvas    Coroutine loop     JS Bridge
     │          │          │                    │
     └──────────┴──────────┴──────── RemoteViews│
                                    AppWidgetManager.updateAppWidget()
```

### Key Components

| Component | File | Role |
|-----------|------|------|
| `WidgetRegistry` | `data/repository/WidgetRegistry.kt` | Single source of truth: maps every `appWidgetId` to its type, path, and cell dimensions |
| `BaseWidgetProvider` | `widget/BaseWidgetProvider.kt` | Routes `onUpdate()` to the correct engine; handles `onAppWidgetOptionsChanged` resizing |
| `CodeWidgetEngine` | `engine/code/CodeWidgetEngine.kt` | Off-screen WebView + `CanvasBridge` JavascriptInterface; frame-rate-capped render loop |
| `CodeWidgetEngineManager` | `engine/code/CodeWidgetEngineManager.kt` | Global registry of active engines; pause/resume/channel routing |
| `BundleExtractor` | `engine/code/BundleExtractor.kt` | Unzips `.zip` bundles to internal storage; parses `manifest.json` |
| `GifWidgetEngine` | `engine/gif/GifWidgetEngine.kt` | `android.graphics.Movie`-based frame decoder; visibility-paused coroutine loop |
| `TextWidgetRenderer` | `engine/text/TextWidgetRenderer.kt` | Rich text → Bitmap with word-wrap, corner radius, custom typeface |
| `ImageWidgetRenderer` | `engine/image/ImageWidgetRenderer.kt` | PNG/JPG decode → matrix-scaled Bitmap with rounded corners |
| `WidgetExportEngine` | `export/WidgetExportEngine.kt` | Serializes all four types to their canonical formats |
| `WidgetCommunicationBus` | `receiver/WidgetCommunicationBus.kt` | Global BroadcastReceiver event hub; routes channel messages between engines |
| `ScreenStateReceiver` | `receiver/ScreenStateReceiver.kt` | Listens to `SCREEN_ON/OFF`, `USER_PRESENT`; drives `WidgetVisibilityTracker` |
| `WidgetVisibilityTracker` | `receiver/ScreenStateReceiver.kt` | Singleton boolean flag queried by all render loops to gate CPU usage |
| `CodeWidgetRenderService` | `service/CodeWidgetRenderService.kt` | Foreground service (channel: `widget_rendering`) managing WebView lifecycle |
| `GifWidgetRenderService` | `service/GifWidgetRenderService.kt` | Foreground service managing all GIF animation coroutines |

---

## Project Structure

```
WidgetForge/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── assets/
│   │   │   └── sample_bundles/
│   │   │       ├── clock_widget/
│   │   │       │   ├── manifest.json
│   │   │       │   └── main.js
│   │   │       └── weather_widget/
│   │   │           ├── manifest.json
│   │   │           └── main.js
│   │   ├── java/com/widgetforge/
│   │   │   ├── WidgetForgeApp.kt              # @HiltAndroidApp
│   │   │   ├── data/
│   │   │   │   ├── db/
│   │   │   │   │   └── WidgetForgeDatabase.kt # Room DB + DAO
│   │   │   │   ├── models/
│   │   │   │   │   └── WidgetModels.kt        # All data classes + enums
│   │   │   │   ├── prefs/
│   │   │   │   │   └── PrefsManager.kt        # DataStore preferences
│   │   │   │   └── repository/
│   │   │   │       └── WidgetRegistry.kt      # Central state registry
│   │   │   ├── di/
│   │   │   │   └── AppModule.kt               # Hilt DI providers
│   │   │   ├── engine/
│   │   │   │   ├── code/
│   │   │   │   │   ├── BundleExtractor.kt     # ZIP unzipper + manifest parser
│   │   │   │   │   ├── CodeWidgetEngine.kt    # WebView + JS bridge
│   │   │   │   │   └── CodeWidgetEngineManager.kt
│   │   │   │   ├── gif/
│   │   │   │   │   └── GifWidgetEngine.kt     # Movie-based GIF decoder
│   │   │   │   ├── image/
│   │   │   │   │   └── ImageWidgetRenderer.kt # PNG/JPG renderer
│   │   │   │   └── text/
│   │   │   │       └── TextWidgetRenderer.kt  # Rich text renderer
│   │   │   ├── export/
│   │   │   │   └── WidgetExportEngine.kt      # All format exporters
│   │   │   ├── receiver/
│   │   │   │   ├── BootReceiver.kt            # Re-init on boot/update
│   │   │   │   ├── ScreenStateReceiver.kt     # Battery optimization
│   │   │   │   └── WidgetCommunicationBus.kt  # Cross-widget event hub
│   │   │   ├── service/
│   │   │   │   ├── CodeWidgetRenderService.kt # Foreground service (JS)
│   │   │   │   └── GifWidgetRenderService.kt  # Foreground service (GIF)
│   │   │   ├── ui/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── NavHost.kt
│   │   │   │   ├── DashboardViewModel.kt
│   │   │   │   ├── dashboard/
│   │   │   │   │   └── DashboardScreen.kt
│   │   │   │   ├── editor/
│   │   │   │   │   ├── TextEditorScreen.kt
│   │   │   │   │   ├── ImagePickerScreen.kt
│   │   │   │   │   ├── CodeEditorScreen.kt
│   │   │   │   │   └── WidgetConfigActivity.kt
│   │   │   │   └── theme/
│   │   │   │       └── Theme.kt
│   │   │   ├── util/
│   │   │   │   ├── FileUtils.kt
│   │   │   │   └── SampleBundleManager.kt
│   │   │   └── widget/
│   │   │       ├── BaseWidgetProvider.kt      # Routing logic
│   │   │       └── WidgetProviders.kt         # 4 concrete providers
│   │   └── res/
│   │       ├── drawable/                      # Placeholder + icons
│   │       ├── layout/
│   │       │   ├── widget_image_frame.xml     # Shared ImageView layout
│   │       │   └── widget_code.xml            # Code widget layout
│   │       ├── mipmap-*/ic_launcher*.xml
│   │       ├── values/
│   │       │   ├── colors.xml
│   │       │   ├── strings.xml
│   │       │   └── themes.xml
│   │       └── xml/
│   │           ├── text_widget_info.xml       # AppWidgetProviderInfo
│   │           ├── image_widget_info.xml
│   │           ├── gif_widget_info.xml
│   │           ├── code_widget_info.xml
│   │           ├── backup_rules.xml
│   │           └── data_extraction_rules.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
│   ├── libs.versions.toml                     # Version catalog
│   └── wrapper/gradle-wrapper.properties
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Widget Types & File Formats

### 1. Text Widget → `.txt`

```
---WIDGETFORGE_METADATA---
widgetType = TEXT
fontSize = 18.0
textColor = #FFFFFF
backgroundColor = #CC000000
bold = false
italic = false
alignment = CENTER
padding = 8
---END_METADATA---

Your widget text content goes here.
Supports multiline content.
```

**Import:** Drop a `.txt` file with this header into the app's import flow.
**Export:** Tap the export icon on any Text widget card → saved to `Downloads/WidgetForge/exports/`.

### 2. Static Image Widget → `.png`

The source image is losslessly exported as a PNG file, preserving full resolution. Non-PNG sources (JPG, WEBP) are re-encoded to PNG at 100% quality.

**Aspect ratio modes:**
- `FILL` — crops to fill widget bounds (default)
- `FIT` — letterboxed to fit within bounds
- `ORIGINAL` — no scaling

**Import:** Select any `.png` file; the app reads pixel dimensions from the file header.

### 3. Animated GIF Widget → `.gif`

The GIF file is stored verbatim (no re-encoding). Playback uses `android.graphics.Movie` for hardware-accelerated frame decoding. The animation loop runs in a coroutine with frame delay derived from the GIF's own timing metadata.

**Import:** Select any `.gif` file.

### 4. Code Widget → `.zip`

```
my_widget.zip
├── manifest.json        ← Required metadata
├── main.js              ← Drawing script (HTML5 Canvas API)
└── assets/              ← Optional subfolder
    ├── background.png
    ├── icon.svg
    └── font.ttf
```

#### `manifest.json` Schema

```json
{
  "id": "com.example.my_widget",
  "name": "My Widget",
  "version": "1.0.0",
  "author": "Your Name",
  "description": "What this widget does",
  "cellWidth": 2,
  "cellHeight": 2,
  "fps": 30,
  "channels": [
    {
      "name": "weather",
      "type": "SUBSCRIBE_ONLY",
      "initialState": "{\"temp\": 20}"
    }
  ],
  "assets": ["background.png"]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `cellWidth` | Int 1–5 | Launcher grid columns |
| `cellHeight` | Int 1–5 | Launcher grid rows |
| `fps` | Int 1–60 | Frame rate of the render loop |
| `channels` | Array | Event channels this widget participates in |
| `channels[].type` | Enum | `PUBLISH_SUBSCRIBE`, `BROADCAST_ONLY`, `SUBSCRIBE_ONLY` |
| `channels[].initialState` | JSON string | Starting value for the channel state object |

---

## Code Widget JavaScript API

Your `main.js` **must** export a `draw(ctx, WIDTH, HEIGHT)` function. It is called by the engine at the FPS defined in your manifest.

```javascript
/**
 * @param {CanvasRenderingContext2D} ctx - Standard HTML5 Canvas 2D context
 * @param {number} WIDTH  - Canvas width in pixels (updated on resize)
 * @param {number} HEIGHT - Canvas height in pixels (updated on resize)
 */
function draw(ctx, WIDTH, HEIGHT) {
    // Your drawing code here
    ctx.fillStyle = '#1a1a2e';
    ctx.fillRect(0, 0, WIDTH, HEIGHT);

    ctx.fillStyle = '#FFFFFF';
    ctx.font = '20px sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('Hello from JS!', WIDTH / 2, HEIGHT / 2);
}
```

### AndroidBridge API

The `AndroidBridge` global object is injected automatically into every code widget's WebView context:

```javascript
// Publish a JSON payload to a named channel
AndroidBridge.publish('my_channel', JSON.stringify({ value: 42 }));

// Read the current state of a channel
var state = JSON.parse(AndroidBridge.getChannelState('my_channel'));

// Log a debug message (visible in ADB logcat with tag Widget[ID]JS)
AndroidBridge.log('Debug: ' + state.value);
```

### Lifecycle Hooks

Implement any of these optional functions in `main.js` to receive lifecycle events:

```javascript
// Called when the screen turns off (stop heavy computation)
function onWidgetPause() {
    clearInterval(myInterval);
}

// Called when the user unlocks and the launcher is visible
function onWidgetResume() {
    myInterval = setInterval(tick, 100);
}

// Called when the launcher resizes this widget
function onResize(newWidth, newHeight) {
    WIDTH = newWidth;
    HEIGHT = newHeight;
    // Recalculate layout constants here
}

// Called when another widget publishes to a subscribed channel
function onChannelMessage(channel, payload) {
    if (channel === 'weather') {
        currentTemp = payload.temp;
    }
}
```

### Loading Assets

Assets placed in the `assets/` folder of your ZIP are accessible via relative paths because the WebView's `baseURL` is set to `file://[extracted_bundle_dir]/`:

```javascript
var img = new Image();
img.src = 'assets/background.png';  // resolves to extracted bundle path
img.onload = function() {
    ctx.drawImage(img, 0, 0, WIDTH, HEIGHT);
};

// Load a custom font
var style = document.createElement('style');
style.textContent = "@font-face { font-family: 'MyFont'; src: url('assets/font.ttf'); }";
document.head.appendChild(style);
```

---

## Cross-Widget Communication Bus

The communication system uses Android's `BroadcastReceiver` as an event backbone. This allows any widget to affect any other widget's rendered state in real time.

### How it works

```
Widget A (publisher)                    Widget B (subscriber, channel: "score")
──────────────────                      ──────────────────────────────────────
JS: AndroidBridge.publish(             BroadcastReceiver receives intent
      'score',                         WidgetCommunicationBus.deliverChannelMessage()
      '{"value": 42}'                  → CodeWidgetEngine.deliverChannelMessage()
    )                                  → evaluates in WebView:
  │                                      window.__channels['score'] = {value:42};
  ▼                                      onChannelMessage('score', {value:42});
WidgetCommunicationBus.publish()
  → context.sendBroadcast(intent)
```

### Example: Score Publisher → Score Display

**Widget A `main.js`** (publisher):
```javascript
var score = 0;
function draw(ctx, WIDTH, HEIGHT) {
    score++;
    AndroidBridge.publish('score', JSON.stringify({ value: score }));
    // draw score locally...
}
```

**Widget B `manifest.json`** (subscriber):
```json
{ "channels": [{ "name": "score", "type": "SUBSCRIBE_ONLY", "initialState": "{\"value\":0}" }] }
```

**Widget B `main.js`**:
```javascript
var currentScore = 0;
function onChannelMessage(channel, payload) {
    if (channel === 'score') currentScore = payload.value;
}
function draw(ctx, WIDTH, HEIGHT) {
    ctx.fillText('Score: ' + currentScore, WIDTH/2, HEIGHT/2);
}
```

---

## Battery Optimization & Visibility Tracking

WidgetForge implements a three-state visibility model to prevent unnecessary battery drain from render loops running when the screen is off or locked.

```
ACTION_SCREEN_OFF  ──► WidgetVisibilityTracker.isVisible = false
                        CodeWidgetEngineManager.pauseAll()
                        GifWidgetEngineManager.pauseAll()
                        JS: window.onWidgetPause()

ACTION_SCREEN_ON   ──► isScreenOn = true (but still LOCKED — stay paused)

ACTION_USER_PRESENT──► WidgetVisibilityTracker.isVisible = true
                        CodeWidgetEngineManager.resumeAll()
                        GifWidgetEngineManager.resumeAll()
                        JS: window.onWidgetResume()
```

Every render loop (code widget coroutine, GIF animation loop) checks `WidgetVisibilityTracker.isVisible` before each frame:

```kotlin
while (running) {
    if (!paused && WidgetVisibilityTracker.isVisible) {
        // render frame
    }
    delay(frameIntervalMs)
}
```

This ensures **zero GPU/CPU rendering work** occurs when the screen is off.

---

## Widget Registry & Instance Differentiation

The `WidgetRegistry` (backed by Room) maps every `appWidgetId` (allocated by Android) to its full descriptor:

```kotlin
data class WidgetRegistryEntry(
    val appWidgetId: Int,        // Unique ID from Android system
    val widgetType: WidgetType,  // TEXT | IMAGE | GIF | CODE
    val sourceFilePath: String,  // Absolute path to .txt/.png/.gif/.zip
    val label: String,           // User-defined display name
    val cellWidth: Int,          // Launcher grid columns
    val cellHeight: Int,         // Launcher grid rows
    val pixelWidth: Int,         // Resolved from onAppWidgetOptionsChanged
    val pixelHeight: Int
)
```

### `onUpdate` routing flow

```
AppWidgetManager → onUpdate(ids: [101, 204, 308])
                        │
              for each id:
                        │
              registry.getEntry(id)
                        │
              ┌─────────┴──────────┐
        type=TEXT             type=CODE
              │                    │
     TextWidgetRenderer     CodeWidgetEngineManager
       .buildRemoteViews()         .startEngine()
              │                    │
     manager.updateAppWidget()  (renders async via WebView)
```

### Resize handling

```
onAppWidgetOptionsChanged(id, bundle)
    minWidth = bundle.getInt(OPTION_APPWIDGET_MIN_WIDTH)  // dp
    widthPx = dpToPx(minWidth)
    registry.updateDimensions(id, widthPx, heightPx)
    CodeWidgetEngineManager.updateDimensions(id, widthPx, heightPx)
      → JS: window.onResize(widthPx, heightPx)
    onUpdate(id)  // re-render at new size
```

---

## Building & Running

### Prerequisites

- **Android Studio** Ladybug (2024.2) or newer
- **JDK 17**
- **Android SDK** 35 (install via SDK Manager)
- Gradle 8.9 (included via wrapper)

### Build steps

```bash
# Clone the repo
git clone https://github.com/yourorg/WidgetForge.git
cd WidgetForge

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Build release APK (requires keystore config)
./gradlew assembleRelease
```

### Signing (release)

Add to `~/.gradle/gradle.properties`:
```properties
WIDGETFORGE_KEYSTORE_PATH=/path/to/keystore.jks
WIDGETFORGE_KEY_ALIAS=widgetforge
WIDGETFORGE_STORE_PASSWORD=your_password
WIDGETFORGE_KEY_PASSWORD=your_key_password
```

Then update `app/build.gradle.kts`:
```kotlin
signingConfigs {
    create("release") {
        storeFile = file(project.property("WIDGETFORGE_KEYSTORE_PATH") as String)
        storePassword = project.property("WIDGETFORGE_STORE_PASSWORD") as String
        keyAlias = project.property("WIDGETFORGE_KEY_ALIAS") as String
        keyPassword = project.property("WIDGETFORGE_KEY_PASSWORD") as String
    }
}
```

---

## Adding a Widget to Your Homescreen

1. **Long-press** an empty area on your home screen
2. Tap **"Widgets"**
3. Scroll to find **WidgetForge** (shows four widget types)
4. **Drag** the desired widget type to your home screen
5. The **WidgetConfigActivity** opens automatically
6. Choose your widget type, configure it, and tap **Save**
7. The widget renders immediately on your home screen

### Resizing

Long-press the placed widget → drag the resize handles. WidgetForge listens to `onAppWidgetOptionsChanged` and re-renders at the exact pixel dimensions reported by the launcher.

---

## Import / Export

### Export

1. Open WidgetForge
2. Tap the **↓ export icon** on any widget card
3. The file is saved to `/sdcard/Android/data/com.widgetforge/files/exports/`
4. Share via Files, Drive, email, etc.

### Import

#### Text widget
```bash
adb push my_widget.txt /sdcard/Download/
```
Then in the app → Create → Text Widget → Import File

#### Image widget
Select any `.png` file from your gallery or Files app.

#### GIF widget
Select any `.gif` file from storage.

#### Code widget ZIP
```bash
# Assemble a valid bundle
zip my_widget.zip manifest.json main.js assets/bg.png
adb push my_widget.zip /sdcard/Download/
```
Then in Code Editor → Import ZIP.

---

## Sample Bundles

Two complete example code widgets are bundled in `assets/sample_bundles/`:

### `clock_widget`
- **Size:** 2×2
- **FPS:** 1 (updates every second)
- **Features:** Analog + digital clock with animated gradient background
- **Channels:** None

### `weather_widget`
- **Size:** 2×1
- **FPS:** 1
- **Features:** Subscribes to the `weather` channel; displays temperature, condition, humidity
- **Channels:** `weather` (SUBSCRIBE_ONLY, initial state: `{temp:22, condition:"Sunny"}`)

To test cross-widget communication with the weather widget, publish from any other widget:
```javascript
AndroidBridge.publish('weather', JSON.stringify({
    temp: 28,
    condition: 'Cloudy',
    humidity: 80
}));
```

---

## Extending WidgetForge

### Adding a new widget type

1. Add the new type to `WidgetType` enum in `WidgetModels.kt`
2. Create a new renderer in `engine/yourtype/`
3. Create a concrete provider in `WidgetProviders.kt`
4. Register the provider in `AndroidManifest.xml`
5. Add the `appwidget-provider` XML in `res/xml/`
6. Add routing in `BaseWidgetProvider.onUpdate()`
7. Add export/import logic to `WidgetExportEngine`

### Adding a new channel event type

1. Define the channel in your `manifest.json`
2. Publish from JS: `AndroidBridge.publish('myChannel', payload)`
3. The `WidgetCommunicationBus` routes it to all subscribed engines automatically

### Custom rendering quality

Adjust `fps` in `manifest.json` or add a quality tier to `PrefsManager.renderingQuality`:
- `LOW` → max 10 FPS
- `MEDIUM` → max 30 FPS  
- `HIGH` → max 60 FPS

---

## Architecture Decision Records

### ADR-001: WebView over ScriptEngine
**Decision:** Use `android.webkit.WebView` for JavaScript execution rather than Rhino or QuickJS.  
**Reason:** WebView gives us a full HTML5 Canvas API, GPU-accelerated 2D rendering, and proper `ImageBitmap` support without any additional libraries. The frame buffer extraction via `toDataURL()` adds minimal overhead at typical widget FPS (1–30).

### ADR-002: BroadcastReceiver for cross-widget bus
**Decision:** Use `sendBroadcast()` / `BroadcastReceiver` rather than a Kotlin `Flow` or `SharedPreferences` listener.  
**Reason:** Widgets run in separate execution contexts (different WebViews, potentially different coroutine scopes). BroadcastReceiver is the only Android primitive that reliably crosses these boundaries without tight coupling. The messages are always small JSON strings, so the overhead is negligible.

### ADR-003: Room over SharedPreferences for registry
**Decision:** Use Room database for `WidgetRegistry` rather than SharedPreferences.  
**Reason:** Room gives us type-safe queries, coroutine-native Flow observation, and proper schema migration. SharedPreferences has no transaction guarantees and becomes unwieldy beyond ~10 key-value pairs.

### ADR-004: android.graphics.Movie for GIF decoding
**Decision:** Use `android.graphics.Movie` rather than a third-party GIF library.  
**Reason:** `Movie` is part of the Android SDK, zero added APK size, and hardware-decodes most GIF formats correctly. The `@SuppressLint("Deprecated")` annotation is accepted since the alternative (Coil GIF decoder) requires the full GIF to be loaded in a Compose `Image`, which cannot be rendered to RemoteViews.

### ADR-005: Foreground services for rendering
**Decision:** Run GIF animation and code widget rendering in foreground services.  
**Reason:** Android aggressively kills background processes that update widgets. A foreground service with a silent ongoing notification is the only reliable mechanism to keep render loops alive on the homescreen while respecting Doze mode.

---

## License

MIT License — see `LICENSE` file.

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-widget-type`
3. Commit your changes: `git commit -am 'Add my widget type'`
4. Push to the branch: `git push origin feature/my-widget-type`
5. Open a Pull Request
