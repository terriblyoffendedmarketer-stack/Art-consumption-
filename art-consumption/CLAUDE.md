# Art Consumption App

## What This Project Is

A two-part system for passive art consumption on Android, inspired by the Instagram account @explainingpaintings (2M followers, ~315 carousel posts explaining paintings with multiple slides of context, history, and commentary).

The user does NOT want to open Instagram. They want this content to appear on their phone without any action — like art on a wall. The value is NOT just the painting image, it's the full editorial carousel (5-8 slides of write-up per painting). Apps like Daily Art already show random paintings — this is different because it preserves the curator's actual multi-slide explanations.

## Architecture

### Part 1: Python Scraper (`scraper/`)
- Uses **instaloader** to download Instagram carousel posts (all slides in order + captions)
- Supports multiple Instagram accounts via `config.json`
- Two modes: `--full` (bulk scrape everything) and `--update` (only new posts)
- `--add <handle>` to add new accounts on the fly
- Tracks already-downloaded posts to avoid re-downloading
- Outputs to `content/<handle>/<shortcode>/` with numbered slides and metadata.json

### Part 2: Android App (`android/`)
- **Home screen widget** showing art that auto-rotates every 30 minutes
- Tap widget → opens **full-screen carousel viewer** with all slides, swipeable
- Shows page indicators (1/5, 2/5, etc.) and account name
- Tap image to toggle caption overlay
- Reads content from `ArtConsumption/content/` on device storage
- Room database indexes posts, tracks what's been shown (least-recently-shown algorithm)
- WorkManager periodically rescans for new content

### Content Format
```
content/
└── explainingpaintings/
    ├── tracking.json
    └── <shortcode>/
        ├── metadata.json   # { shortcode, date, caption, slide_count, handle }
        ├── 01.jpg           # First slide (the hook)
        ├── 02.jpg           # Explanation slides
        └── 03.jpg
```

## Current Status

- All code is written and committed
- Scraper needs to be run locally (Instagram blocks cloud IPs)
- Android app needs to be built in Android Studio
- Content needs to be synced to phone after scraping

## What Needs To Happen Next

### Immediate: Run the scraper
1. Set up Python venv in `scraper/`
2. Install requirements (`pip install -r requirements.txt`)
3. Login to instaloader: `instaloader --login prashilprakash`
4. Edit `config.json` — set username to `prashilprakash`, set session_file path
5. Run `python scrape.py --full` to download all ~315 posts from @explainingpaintings

### Then: Build and install Android app
1. Open `android/` in Android Studio
2. Build and install on phone
3. Copy `scraper/content/` to `ArtConsumption/content/` on phone storage
4. Add widget to home screen

### Later: Automation
- Cron job for `python scrape.py --update` every 6 hours
- Syncthing or similar to auto-push new content to phone
- Desktop wallpaper rotation (user also wants this)

## User Preferences
- Android phone user (all projects should default to Android)
- Does NOT want AI-generated art explanations — only real curator write-ups
- Does NOT want apps that require opening — passive consumption only
- Instagram username: prashilprakash
- macOS user (Apple Silicon Mac, uses Homebrew)
- The scraper should support adding any new Instagram account, not just @explainingpaintings

## Tech Stack
- **Scraper**: Python 3, instaloader
- **Android**: Kotlin, Jetpack Compose, Room, WorkManager, Coil, RemoteViews (widget)
- **Build**: Gradle 8.2.2, Kotlin 1.9.22, compileSdk 34, minSdk 26
