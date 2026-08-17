# Learnings — Art Consumption Project

## 2026-08-11: Instaloader is broken for large/business Instagram accounts

**What broke**: `instaloader.Profile.from_username()` returns `ProfileNotExistsException` for @explainingpaintings even though the account exists (2M followers, 315 posts). The REST API (`api/v1/users/web_profile_info/`) returns `{"status": "ok"}` with no `data` field for this account, while small personal accounts work fine. Other business/creator accounts (natgeo, painting_explained) return a 400 error: `"Asset asset://laser.provider/ig_business_category_subvertical has been deleted."` This is Instagram's API-level anti-scraping, not a session or auth issue.

**What we tried**:
1. `Profile.from_username()` — ProfileNotExistsException
2. `Profile.from_id(80611479105)` — session invalidated, redirected to login
3. GraphQL query — 400 Bad Request "invalid request"
4. User search API — 401 Unauthorized
5. Multiple API probing attempts — triggered rate limiting ("Please wait a few minutes")

**What fixed it**: Switched to **gallery-dl** which uses browser cookies from Chrome instead of a separate API session. gallery-dl's Instagram extractor works where instaloader doesn't because it uses different API endpoints and authentication flow.

**Key lesson**: Don't probe Instagram's API repeatedly trying to debug — each failed request counts against rate limits and can temporarily ban the session. Pick a known-working tool and switch.

## 2026-08-11: gallery-dl config quirks

**What broke**: Custom gallery-dl config with `directory: ["{username}", "{shortcode}"]` caused "No results" even though the same URL worked without config. The `-d` CLI flag also didn't interpolate template variables (created literal `{username}/{shortcode}` directory).

**What fixed it**: Two-step approach — let gallery-dl download with its default output format into `.raw/`, then post-process to reorganize files into `content/<handle>/<shortcode>/01.jpg` structure. More reliable than fighting gallery-dl's config templating.

## 2026-08-11: Long scrapes on macOS

**Problem**: Full scrape of 315 posts × ~6 slides × 3s delay ≈ 2-3 hours. Mac sleeps if lid closed.

**Fix**: Wrap with `caffeinate -s` (prevents system sleep on AC power). User also had Amphetamine app installed — either works, don't need both. Note: `caffeinate` keeps the Mac awake but can't prevent the Claude Code session itself from ending — if the session drops, the background process may be killed. gallery-dl is resumable though (skips existing files).

## 2026-08-11: Android CLI build setup on Mac (no Android Studio)

**Setup steps**:
1. `brew install openjdk@17` — no sudo needed (unlike temurin)
2. Download Android cmdline-tools from `dl.google.com/android/repository/commandlinetools-mac-*_latest.zip`
3. Install to `~/Library/Android/sdk/cmdline-tools/latest/`
4. `sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"`
5. Download official `gradlew` + `gradle-wrapper.jar` from Gradle's GitHub (v8.5.0 tag)

**Gotchas**:
- `gradle-wrapper.properties` must use `GRADLE_USER_HOME` not `GRADLE_WRAPPER_DISTS` — the latter causes `RuntimeException: Base: GRADLE_WRAPPER_DISTS is unknown`.
- Don't extract Gradle to a temp dir and then delete it — the daemon caches the extraction path. If you must, kill daemons (`pkill -f GradleDaemon`) and clean `~/.gradle/daemon/` before rebuilding.
- `settings.gradle.kts` had `dependencyResolution` — must be `dependencyResolutionManagement`.
- Set `JAVA_HOME`, `ANDROID_HOME`, and `PATH` before every `./gradlew` call.

## 2026-08-11: Architecture decision — cloud-backed native app

**User requirement**: Native Android app with widget (non-negotiable — the widget IS the product), BUT content must be served from a cloud server, not synced from the Mac. Mac should not need to be on for the app to work. User wants the same deploy flow as their PWA projects: push changes → server updates → app reflects changes automatically.

**Architecture**:
1. Scraper runs locally (or as GitHub Action)
2. Content pushed to cloud storage (Supabase/Cloudflare R2/Firebase Storage)
3. Android app fetches content from cloud, caches locally
4. Widget reads from local cache, periodically checks for new content
5. Config changes (rotation interval, etc.) served from cloud too

**Key insight**: Never suggest removing the widget — it's the core differentiator. Without it, this is just another gallery app the user has to remember to open, which defeats the "passive consumption" purpose.

## 2026-08-11: Firebase Storage integration — on-demand image loading

**Decision**: Don't download all 6.5GB of images to the phone. Instead:
1. Sync manifests (tiny JSON files) → index all posts in Room DB
2. Widget downloads just the first slide of each post on demand
3. Carousel uses Coil with Firebase Storage URLs — Coil handles disk caching
4. WorkManager preloads next 5 posts' first slides in background

**Firebase Storage URL pattern**: `https://firebasestorage.googleapis.com/v0/b/{bucket}/o/{url_encoded_blob_path}?alt=media`
- Path separators `/` must be encoded as `%2F`
- Requires Firebase Storage rules to allow public read (`allow read;`)

**Why no Firebase SDK in the Android app**: Adding the Firebase SDK would add ~5MB to the APK and require google-services.json. Plain HTTP via `HttpURLConnection` + Coil for images is zero-dependency and the APK stays at 8.8MB.

## 2026-08-15: gallery-dl slide ordering — sort by `num`, not filename

**What broke**: All carousel slides were in random order. The first slide shown was the "follow us" promo slide instead of the painting hook. Every post was affected.

**Root cause**: gallery-dl names files like `{post_id}_{media_id}.jpg`. The media IDs don't correlate with slide order. The scraper's `reorganize_posts()` did `sorted(data["images"])` which sorted alphabetically by filename, assigning wrong slide numbers.

**What fixed it**: gallery-dl's metadata JSON has a `num` field with the correct Instagram slide position. Changed the sort to `sorted(data["images"], key=lambda x: x[0])` where x[0] is the `num` from metadata. Also wrote `fix_slide_order.py` to fix the already-uploaded manifest without re-uploading images.

## 2026-08-11: Image quality — don't compress art

**What was happening**: Upload script was resizing to 1080px max width + JPEG 80% quality. User correctly flagged that art should not be downscaled.

**Fix**: Changed `upload_to_firebase.py` to upload original bytes — no resize, no recompress. Instagram images are already 1080-1440px which is optimal for phone screens. Re-upload with `--force` flag to replace any already-compressed images.
