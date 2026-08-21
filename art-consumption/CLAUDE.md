# Art Consumption App

## Status (2026-08-21)

- **Scraper**: Done. gallery-dl backend. 719 posts (after promo removal) from @explainingpaintings.
- **Firebase**: Live. All content uploaded to `art-consumption.firebasestorage.app`. Public read rules set.
- **Android APK**: Built and working. Widget shows last slide (painting) + hook text. Carousel-widget sync working.
- **Promo removal**: Done. 882 promo slides stripped, 13 all-promo posts removed, verified clean.
- **Not started**: GitHub Actions scheduled scraper, archive/delete in app, multi-account settings page.

## Architecture

### Scraper (`scraper/`)
- **gallery-dl** with browser cookies (Chrome) for Instagram auth
- Two-step: gallery-dl downloads raw → reorganize into `content/<handle>/<shortcode>/01.jpg`
- Multi-account support via `config.json`
- Modes: `--full`, `--update`, `--add <handle>`

### Firebase Storage (`scraper/upload_to_firebase.py`)
- Uploads at original quality (no resize)
- Creates `manifest.json` per account
- Bucket: `art-consumption.firebasestorage.app`
- Auth: service account via `GOOGLE_APPLICATION_CREDENTIALS`

### Android App (`android/`)
- **Widget**: shows last slide (painting) + hook text from caption + subtitle (painting name + artist)
- **Carousel**: swipeable viewer with caption overlay, "View on IG" link, prev/next navigation
- **Sync**: carousel browsing updates widget in real-time via SharedPreferences + broadcast
- **Position persistence**: carousel remembers last viewed post
- Room DB for indexing, WorkManager for 30-min background sync, Coil for image loading
- No Firebase SDK — plain HTTP via `HttpURLConnection` + Coil for images

### Configurable Values
Two places need updating when pointing at different content:
1. `scraper/config.json` — `accounts` array
2. `FirebaseSync.kt` — `BUCKET` and `ACCOUNTS` constants (lines ~177-178)

## File Map

- `scraper/scrape.py` — Main scraper (gallery-dl backend, post-processing, tracking)
- `scraper/upload_to_firebase.py` — Uploads scraped content to Firebase Storage
- `scraper/remove_promo_slides.py` — Strips duplicate/promo trailing slides from manifest
- `scraper/fix_slide_order.py` — Fixes slide ordering using gallery-dl `num` metadata
- `scraper/set_firebase_rules.py` — Sets Firebase Storage rules for public read
- `scraper/config.json` — Accounts list, browser for cookies
- `scraper/requirements.txt` — Python deps (gallery-dl)
- `android/app/src/main/java/com/artconsumption/data/FirebaseSync.kt` — Firebase sync + image download
- `android/app/src/main/java/com/artconsumption/ui/CarouselActivity.kt` — Carousel viewer
- `android/app/src/main/java/com/artconsumption/widget/ArtWidgetProvider.kt` — Widget logic
- `android/app/src/main/res/layout/widget_art.xml` — Widget layout
- `learnings.md` — Debugging log, dead ends, gotchas

## Setup & Run

### Build APK
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=~/Library/Android/sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH
cd android && ./gradlew --no-daemon assembleDebug
```

### Scraper
```bash
cd scraper && source venv/bin/activate
caffeinate -s python scrape.py --full
```

### Upload
```bash
export GOOGLE_APPLICATION_CREDENTIALS="$(pwd)/../firebase-service-account.json"
python upload_to_firebase.py
```

## Roadmap

### Phase 1: Core [x]
1. [x] Scraper + full scrape of @explainingpaintings
2. [x] Firebase upload (original quality)
3. [x] Android app — widget + carousel
4. [x] Widget redesign — last slide image, hook text, taller layout
5. [x] Promo slide removal (thorough — 882 slides stripped)
6. [x] Widget-app sync (browsing updates widget)
7. [x] Carousel position persistence

### Phase 2: Automation [ ]
1. [ ] GitHub Actions scheduled scraper
2. [ ] Settings page to add more accounts (cap: 4-5)
3. [ ] Archive/delete so seen art doesn't repeat

### Phase 3: Polish [ ]
1. [ ] Address first-slide redundancy ("Now I understand..." text)
2. [ ] Config served from cloud (accounts list, rotation interval)

## User Preferences
- Android phone user
- Does NOT want AI-generated content — only real curator write-ups
- Passive consumption only — widget is the product
- Zero Mac dependency after setup — cloud handles everything
- Art at original quality — never downscale
- Conservative rate limiting on scraper (3s+ delays)
- Strongly prefers backend/data fixes over APK reinstalls

## Tech Stack
- **Scraper**: Python 3, gallery-dl, google-cloud-storage
- **Cloud**: Firebase Storage (bucket: art-consumption.firebasestorage.app)
- **Android**: Kotlin, Jetpack Compose, Room, WorkManager, Coil, RemoteViews
- **Build**: Gradle 8.5, AGP 8.2.2, Kotlin 1.9.22, compileSdk 34, minSdk 26
