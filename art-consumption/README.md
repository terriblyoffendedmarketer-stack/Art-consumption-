# Instagram Widget

An Android home screen widget that passively delivers content from Instagram carousel accounts — art, history, science, anything with multi-slide editorial posts. Every 30 minutes, a new post appears on your home screen. Tap it to swipe through all the slides.

Built for accounts like [@explainingpaintings](https://www.instagram.com/explainingpaintings/) but works with **any Instagram account** that posts carousels.

## How It Works

```
Instagram Account ──► Scraper (Python) ──► Firebase Storage ──► Android Widget
                      runs on your Mac       stores images        auto-rotates
                      or GitHub Actions       + metadata           every 30 min
```

1. **Scraper** downloads all carousel posts (images + captions) from Instagram accounts you choose
2. **Firebase Storage** hosts everything in the cloud so your phone doesn't need gigabytes of images
3. **Android widget** pulls content from Firebase, shows a new post every 30 minutes, and lets you tap into a full carousel viewer

The widget is the whole point — no opening apps, no scrolling feeds. Content comes to you.

---

## Quick Start (Use the Existing Setup)

If someone shared the APK with you and the Firebase backend is already populated:

1. Get `ArtConsumption.apk` onto your Android phone
2. Tap to install (allow installs from unknown sources)
3. Long-press home screen → Widgets → find "Art Widget"
4. Place it — first painting appears within a minute

That's it. No accounts, no sign-ups.

---

## Full Setup (Your Own Accounts)

Want to set this up from scratch with your own Instagram accounts? Here's everything you need.

### Prerequisites

- **A Mac** (or Linux — the scraper is Python, but the build instructions are Mac-focused)
- **An Instagram account** logged into Chrome (the scraper uses your browser cookies)
- **A Firebase project** (free tier is fine for personal use)
- **Java 17** and **Android SDK** for building the APK

### Step 1: Firebase Setup

1. Go to [Firebase Console](https://console.firebase.google.com/) → Create a new project
2. Enable **Storage** in the Firebase console
3. Note your **bucket name** — it'll be something like `your-project.firebasestorage.app`
4. Download a **service account key**:
   - Firebase Console → Project Settings → Service Accounts → Generate New Private Key
   - Save it as `firebase-service-account.json` in the project root (it's gitignored)
5. Set storage rules to allow public read:

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

   Or run the included script:
   ```bash
   export GOOGLE_APPLICATION_CREDENTIALS="$(pwd)/firebase-service-account.json"
   cd scraper && python set_firebase_rules.py
   ```

### Step 2: Configure Your Accounts

Edit `scraper/config.json`:

```json
{
  "accounts": [
    "explainingpaintings",
    "arthistoryfeed",
    "your_favorite_account"
  ],
  "content_dir": "content",
  "cookies_browser": "chrome"
}
```

- **accounts**: Instagram handles to scrape (no `@` prefix)
- **cookies_browser**: which browser has your Instagram login — `chrome`, `firefox`, or `safari`

### Step 3: Scrape Content

```bash
cd scraper
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt
```

Make sure you're logged into Instagram in your browser, then:

```bash
# Scrape all posts from all configured accounts (first run — takes a while)
caffeinate -s python scrape.py --full

# Or scrape just one account
caffeinate -s python scrape.py --full --account explainingpaintings

# Later, only fetch new posts
caffeinate -s python scrape.py --update

# Add a new account on the fly
caffeinate -s python scrape.py --add newaccount
```

`caffeinate -s` prevents your Mac from sleeping during long scrapes.

**Rate limiting**: The scraper uses 3-second delays between requests. Don't reduce this — Instagram will rate-limit or ban your session.

### Step 4: Upload to Firebase

```bash
export GOOGLE_APPLICATION_CREDENTIALS="$(pwd)/../firebase-service-account.json"
python upload_to_firebase.py

# Upload only one account
python upload_to_firebase.py --account explainingpaintings

# Re-upload everything (after fixing images, etc.)
python upload_to_firebase.py --force
```

This uploads all images at original quality and creates a `manifest.json` per account that the app uses to know what's available.

### Step 5: Point the Android App at Your Firebase

Edit two values in `android/app/src/main/java/com/artconsumption/data/FirebaseSync.kt`:

```kotlin
companion object {
    private const val BUCKET = "your-project.firebasestorage.app"  // ← your bucket
    private val ACCOUNTS = listOf("explainingpaintings", "arthistoryfeed")  // ← your accounts
}
```

These are the only two values you need to change in the Android code.

### Step 6: Build the APK

One-time setup:
```bash
brew install openjdk@17
# Download Android cmdline-tools from https://developer.android.com/studio#command-line-tools-only
# Extract to ~/Library/Android/sdk/cmdline-tools/latest/
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

Build:
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=~/Library/Android/sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH

cd android
./gradlew --no-daemon assembleDebug
```

The APK lands at `android/app/build/outputs/apk/debug/app-debug.apk`. Transfer it to your phone and install.

### Step 7: Add the Widget

1. Install the APK on your phone
2. Long-press home screen → Widgets
3. Find "Art Widget" under Art Consumption
4. Place it — it'll sync content and show the first post within a minute
5. Resize the widget to your liking (taller = more text visible)

---

## Using the App

### Widget
- Shows the painting image with a hook line from the caption below
- Auto-rotates to a new post every 30 minutes
- Tap it to open the full carousel
- When you browse to a different post in the carousel, the widget updates to match

### Carousel
- **Swipe left/right** through slides within a post
- **Tap the image** to show/hide the full caption
- **"View on IG"** opens the original Instagram post
- **Prev / Next** buttons to browse between posts
- Remembers your position when you reopen the app

---

## Adding More Accounts Later

1. Add the handle to `scraper/config.json`
2. Run `caffeinate -s python scrape.py --add newhandle`
3. Run `python upload_to_firebase.py --account newhandle`
4. Add the handle to the `ACCOUNTS` list in `FirebaseSync.kt`
5. Rebuild the APK and install

Backend changes (scraping + uploading) take effect on the next app sync (every 30 minutes). Changes to the `ACCOUNTS` list in the Android code require reinstalling the APK.

---

## Removing Promo Slides

Many Instagram accounts add promotional "follow us" slides at the end of their posts. The included `remove_promo_slides.py` script detects and strips these from the manifest:

```bash
cd scraper
export GOOGLE_APPLICATION_CREDENTIALS="$(pwd)/../firebase-service-account.json"

# Preview what would be removed
python remove_promo_slides.py --dry-run

# Actually remove them
python remove_promo_slides.py
```

It works by finding images that appear across multiple posts (real content is unique per post; promo slides are reused). It also uses perceptual hashing to catch re-encoded variants.

---

## Project Structure

```
scraper/
  scrape.py                 # Main scraper (gallery-dl backend)
  upload_to_firebase.py     # Uploads content to Firebase Storage
  remove_promo_slides.py    # Strips promo slides from manifest
  fix_slide_order.py        # Fixes slide ordering using gallery-dl metadata
  set_firebase_rules.py     # Sets Firebase Storage rules programmatically
  config.json               # Account list + browser config
  requirements.txt          # Python dependencies (gallery-dl)
  content/                  # Downloaded content (gitignored, can be 5+ GB)

android/
  app/src/main/java/com/artconsumption/
    data/
      FirebaseSync.kt       # Downloads manifests + images from Firebase
      ArtDatabase.kt        # Room database for post indexing
      ArtPost.kt            # Data model
      ContentScanner.kt     # Local content fallback
    ui/
      CarouselActivity.kt   # Full-screen swipeable viewer
    widget/
      ArtWidgetProvider.kt  # Home screen widget logic
    worker/
      SyncWorker.kt         # Background sync every 30 minutes
  app/src/main/res/
    layout/widget_art.xml   # Widget layout
    xml/art_widget_info.xml # Widget config

firebase-service-account.json  # Your Firebase key (gitignored — NEVER commit)
```

---

## Content Format in Firebase

```
content/<handle>/manifest.json
content/<handle>/<shortcode>/01.jpg
content/<handle>/<shortcode>/02.jpg
...
```

Each entry in `manifest.json`:
```json
{
  "shortcode": "ABC123",
  "date": "2024-01-15",
  "caption": "The full Instagram caption text...",
  "slide_count": 6,
  "handle": "explainingpaintings",
  "slides": [
    "content/explainingpaintings/ABC123/01.jpg",
    "content/explainingpaintings/ABC123/02.jpg"
  ]
}
```

The app uses `shortcode` as the unique ID. Slides are ordered by their Instagram position (first slide = first in the post).

---

## Troubleshooting

**Scraper says "No results"**: Make sure you're logged into Instagram in the browser specified in `config.json`. gallery-dl uses your browser's cookies.

**Widget is blank**: Give it a minute after first install — it needs to download the manifest and first image. Check your internet connection.

**Images are in wrong order**: Run `python fix_slide_order.py` — it reads gallery-dl's metadata to get the correct Instagram slide positions.

**Promo slides showing up**: Run `python remove_promo_slides.py` to strip them from the manifest.

**Build fails with Java errors**: Make sure `JAVA_HOME` points to Java 17 specifically: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Scraper | Python 3, gallery-dl |
| Cloud Storage | Firebase Storage (free tier) |
| Android App | Kotlin, Jetpack Compose, Room, WorkManager, Coil |
| Widget | Android RemoteViews |
| Build | Gradle 8.5, Android SDK 34, Java 17 |
