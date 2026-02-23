# Visual Life Log (VisualLog)

Welcome to **Visual Life Log** (VisualLog), a desktop application built with Kotlin and Compose Multiplatform. This app is designed to help you visually track your daily life, moods, and memories through a highly interactive whiteboard interface.

## 🌟 Features

- **Life Grid View**: Navigate through your timeline with a visually appealing infinite grid. Each cell represents a day in your life.
- **Interactive Whiteboard Editor**: Click on any day to open a freeform canvas where you can document your day.
- **Rich Media & Stickers**: Add background images, place customized Samsung-style stickers, and arrange them freely on your daily canvas.
- **Mood & Weather Tracking**: Quickly log how you felt and what the weather was like for each day.
- **Journaling**: Write rich text entries to remember the details of your day.
- **Hardware Acceleration**: Built-in support for GPU acceleration (e.g., Apple Metal) ensuring silky smooth 60+ FPS animations and UI transitions.
- **Local & Private**: All your data is stored securely offline using SQLite and Jetbrains Exposed.

## 📸 Screenshots

### 1. Life Grid View
![Life Grid View](docs/images/grid_view.png)
*An overview of your days with thumbnail previews.*

### 2. Daily Whiteboard Editor 
![Whiteboard Editor](docs/images/whiteboard_editor.png)
*The interactive canvas for adding stickers, photos, and journal entries.*

### 3. Journaling & Text Entry
![Journal Entry](docs/images/text_editor.png)
*Writing rich text updates to remember the details of your day.*

### 4. Stickers & Customization
![Sticker Interaction](docs/images/sticker_interaction.png)
*Placing, resizing, and interacting with custom stickers on the canvas.*

### 5. Emoji & Photo Tools
![Emoji Stickers](docs/images/emoji_stickers.png)
![Photo Import](docs/images/photo_import.png)
*Quickly dropping in emojis and importing your favorite photos.*

### 6. Mood & Weather Tracking
![Mood Tracking](docs/images/mood_tracking.png)
![Weather Tracking](docs/images/weather_tracking.png)
*Easily logging your daily mood and weather for a quick glance.*

## 🚀 Getting Started

### Prerequisites
- JDK 18 or higher

### Running the App
You can run the application directly using Gradle:

```bash
./gradlew run
```

Or use the provided convenience script (if on macOS/Linux):
```bash
./run.sh
```

### Building Native Distributions
To build installable packages (`.dmg`, `.msi`, `.deb`) for your operating system:

```bash
./gradlew package
# Distributions will be generated in build/compose/binaries/
```

## 🛠 Tech Stack

- **Language**: [Kotlin](https://kotlinlang.org/) (1.9.22)
- **UI Framework**: [Compose Multiplatform for Desktop](https://www.jetbrains.com/lp/compose-multiplatform/) (1.6.0)
- **Database**: [SQLite](https://sqlite.org/) + [JetBrains Exposed](https://github.com/JetBrains/Exposed)
- **Concurrency**: Kotlinx Coroutines
- **Logging**: SLF4J

## ⚙️ Advanced Configuration (Debug Mode)

The app features a hidden debug menu. To access it, focus the application window and press the `K` key 8 times in quick succession. This allows you to arbitrarily change the app's initialization date for testing the grid layout.
