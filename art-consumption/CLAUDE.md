# Art Consumption App

## Status (2026-08-11)

- **Scraper**: Done. gallery-dl backend. 732 posts / 8,718 images / 6.5 GB from @explainingpaintings.
- **Firebase upload**: In progress. Uploading at original quality (no compression). ~8,718 images to `art-consumption.firebasestorage.app`.
- **Android APK**: Built — `ArtConsumption.apk` (8.8 MB). Now fetches content from Firebase Storage.
- **Pending**: Firebase Storage security rules need to be set to allow public read (see Setup section).
- **Not started**: GitHub Actions scheduled scraper, archive/delete in app.

## What This Project Is

A two-part system for passive art consumption on Android, inspired by @explainingpaintings (~732 carousel posts explaining paintings with multi-slide context and commentary).

The user does NOT want to open Instagram. Content appears on their phone via a home screen widget without any action — like art on a wall. The value is the full editorial carousel (5-8 slides per painting), NOT just a random painting image.

## Architecture

### Part 1: Python Scraper (`scraper/`)
- Uses **gallery-dl** with browser cookies (Chrome) for authentication
- Two-step: gallery-dl downloads raw → script reorganizes into app format
- Supports multiple Instagram accounts via `config.json`
- Modes: `--full`, `--update`, `--add <handle>`
- Tracks downloaded posts via `tracking.json`

### Part 2: Upload to Firebase (`scraper/upload_to_firebase.py`)
- Uploads scraped content to Firebase Storage at original quality (no resize/compress)
- Creates `manifest.json` per account listing all posts with slide blob paths
- Service account auth via `GOOGLE_APPLICATION_CREDENTIALS` env var
- Bucket: `art-consumption.firebasestorage.app`

### Part 3: Android App (`android/`)
- **Home screen widget** — auto-rotates art every 30 min (THE core feature, non-negotiable)
- Tap widget → **full-screen carousel viewer** with all slides, swipeable
- Caption overlay on tap, page indicators, account name
- Room DB indexes posts, least-recently-shown algorithm
- **FirebaseSync** downloads manifests from Firebase, indexes posts, downloads slides on demand
- **Widget**: downloads first slide via HTTP, caches locally
- **Carousel**: Coil loads all slides directly from Firebase URLs (with disk caching)
- WorkManager syncs manifests + preloads next 5 slides every 30 min
- Falls back to local content scanning if Firebase unavailable

### Part 4: Cloud Pipeline (TODO)
- **Scheduled scraper** on GitHub Actions (not on Mac — Mac shouldn't need to be on)
- **Archive/delete** in app to manage rotation and free space

### Content Format (Firebase Storage)
```
content/<handle>/manifest.json       # Array of {shortcode, date, caption, slide_count, handle, slides}
content/<handle>/<shortcode>/01.jpg  # Original quality image files
```

## File Map

- `ArtConsumption.apk` — Built debug APK, ready to install
- `scraper/scrape.py` — Main scraper (gallery-dl backend, post-processing, tracking)
- `scraper/upload_to_firebase.py` — Uploads scraped content to Firebase Storage
- `scraper/set_firebase_rules.py` — Sets Firebase Storage rules for public read
- `scraper/config.json` — Accounts list, browser for cookies
- `scraper/requirements.txt` — Python deps (gallery-dl, google-cloud-storage)
- `scraper/venv/` — Python venv (not committed)
- `scraper/content/` — Downloaded content, 6.5 GB (not committed)
- `firebase-service-account.json` — Firebase service account key (gitignored, NEVER commit)
- `android/` — Android app source (Kotlin, Jetpack Compose)
- `android/app/src/main/java/com/artconsumption/data/FirebaseSync.kt` — Firebase content sync
- `android/gradlew` — Gradle wrapper for CLI builds
- `learnings.md` — Debugging log, dead ends, gotchas

## Setup & Run

### Firebase Storage Rules (one-time)
Go to Firebase Console → Storage → Rules and paste:
```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /{allPaths=**} {
      allow read;
      allow write: if false;
    }
  }
}
```

### Upload content to Firebase
```bash
cd scraper && source venv/bin/activate
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/firebase-service-account.json"
caffeinate -s python3 -u upload_to_firebase.py          # Upload all
caffeinate -s python3 -u upload_to_firebase.py --force   # Re-upload (e.g., after quality fix)
```

### Build Android APK (CLI, no Android Studio)
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=~/Library/Android/sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH
cd android
./gradlew --no-daemon assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

### Scraper
```bash
cd scraper && python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt
# Must be logged into Instagram in Chrome
caffeinate -s python scrape.py --full
```

### Prerequisites (one-time)
```bash
brew install openjdk@17
# Android SDK: download cmdline-tools, then:
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
pip install google-cloud-storage  # For upload script
```

## Roadmap

### Phase 1: Local MVP [x]
1. [x] Scraper setup + full scrape of @explainingpaintings
2. [x] Android APK built from CLI

### Phase 2: Cloud Pipeline [~]
1. [x] Choose cloud storage → Firebase Storage
2. [~] Upload existing scraped content to Firebase (in progress)
3. [x] Modify Android app to fetch from Firebase + on-demand caching
4. [ ] Set Firebase Storage rules for public read
5. [ ] Install APK on phone, test widget + carousel
6. [ ] Set up GitHub Actions for scheduled scraping
7. [ ] Add archive/delete functionality in app

### Phase 3: Multi-Account + Polish [ ]
1. [ ] Add more Instagram accounts to scrape
2. [ ] Config served from cloud (accounts list, rotation interval)
3. [ ] Desktop wallpaper rotation

## User Preferences
- Android phone user
- Does NOT want AI-generated explanations — only real curator write-ups
- Does NOT want apps requiring opening — passive consumption only
- Wants zero Mac dependency after initial setup — cloud handles everything
- Wants to add multiple Instagram accounts over time
- Concerned about bans — conservative rate limiting (3s+ delays)
- Art should NOT be downscaled — upload/serve at original quality
- Instagram login: 5pmclick
- macOS, Apple Silicon, Homebrew

## Tech Stack
- **Scraper**: Python 3, gallery-dl, google-cloud-storage
- **Cloud**: Firebase Storage (bucket: art-consumption.firebasestorage.app)
- **Android**: Kotlin, Jetpack Compose, Room, WorkManager, Coil, RemoteViews
- **Build**: Gradle 8.5, AGP 8.2.2, Kotlin 1.9.22, compileSdk 34, minSdk 26
- **Planned**: GitHub Actions for scheduled scraping
