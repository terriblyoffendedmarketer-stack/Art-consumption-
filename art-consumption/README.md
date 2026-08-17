# Art Consumption

A home screen widget for Android that puts art on your phone like art on a wall — no scrolling, no opening apps, no effort. Every 30 minutes, a new painting appears on your home screen with a short writeup about it.

Tap it to see the full story: a swipeable carousel of slides breaking down the painting, its history, and why it matters — pulled from real art curators on Instagram.

## What it does

1. **Scrapes** art posts from Instagram accounts like [@explainingpaintings](https://www.instagram.com/explainingpaintings/) (732 posts, ~8,700 images)
2. **Stores** everything in the cloud (Firebase) so your phone doesn't need 6 GB of images
3. **Delivers** a new painting to your home screen widget every 30 minutes — automatically, silently, beautifully

## The widget

The widget is the whole point. It shows:
- The painting (uncropped, full view)
- The title and artist name as a teaser
- A slide count if there's more to see

Tap it and you're in the full carousel — swipe through all the slides, read the caption, jump to the original Instagram post.

## How to install

1. Get the `ArtConsumption.apk` file onto your Android phone
2. Tap it to install (you'll need to allow installs from unknown sources)
3. Long-press your home screen → Widgets → find "Art Widget" under Art Consumption
4. Place it on your home screen
5. Give it a minute to sync — the first painting will appear

That's it. No accounts, no sign-ups, no settings to configure.

## Inside the carousel

When you tap the widget:
- **Swipe left/right** to see all slides for that painting
- **Tap the image** to show/hide the curator's caption
- **"View on IG"** in the top bar opens the original Instagram post
- **Prev / Next** buttons at the bottom to browse other paintings
- Images load on demand — only what you're looking at gets downloaded

## How it stays fresh

The app checks for new content every 30 minutes in the background. As long as you have internet, new paintings will keep appearing. The content comes from Firebase, so you don't need a computer running or anything special set up.

## For the curious

The project has three parts:

| Part | What it does |
|------|-------------|
| **Scraper** (Python) | Downloads art posts from Instagram using gallery-dl |
| **Cloud** (Firebase Storage) | Stores all the images and metadata online |
| **App** (Android/Kotlin) | The widget + carousel viewer on your phone |

If you want to add more Instagram art accounts or set up the scraper yourself, see the [CLAUDE.md](CLAUDE.md) file for the full technical setup.
