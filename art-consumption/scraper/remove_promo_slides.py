#!/usr/bin/env python3
"""
Removes promotional last slides from posts.

Uses perceptual hashing to detect duplicate last slides across posts.
Any last slide that appears in 3+ posts (visually similar) is a promo page
and gets removed from the manifest. Does NOT delete image files from Firebase
(they just won't be referenced anymore).

Usage: python remove_promo_slides.py [--dry-run]
Requires: Pillow, google-cloud-storage
          GOOGLE_APPLICATION_CREDENTIALS env var set

Gotchas:
- Uses perceptual hash with hamming distance <= 10 to catch re-encoded variants
- Only removes the LAST slide of a post — never interior slides
- Posts with only 1-2 slides are never touched (too risky)
- Threshold of 3+ duplicates prevents false positives on actual art
"""

import json
import sys
from pathlib import Path
from collections import defaultdict
from PIL import Image
from google.cloud import storage

BUCKET_NAME = "art-consumption.firebasestorage.app"
SIMILARITY_THRESHOLD = 10  # hamming distance
MIN_GROUP_SIZE = 3  # minimum duplicates to consider it promotional
MIN_SLIDES_TO_TOUCH = 3  # don't remove from posts with fewer slides


def perceptual_hash(img_path, size=16):
    try:
        img = Image.open(img_path).convert("L").resize((size, size), Image.LANCZOS)
        pixels = list(img.getdata())
        avg = sum(pixels) / len(pixels)
        bits = "".join("1" if p > avg else "0" for p in pixels)
        return int(bits, 2)
    except Exception as e:
        print(f"  Warning: could not hash {img_path}: {e}")
        return None


def hamming_distance(h1, h2):
    return bin(h1 ^ h2).count("1")


def find_promo_slides(content_dir, handle):
    account_dir = content_dir / handle
    post_dirs = sorted([
        d for d in account_dir.iterdir()
        if d.is_dir() and (d / "metadata.json").exists()
    ])

    last_slides = []
    for post_dir in post_dirs:
        slides = sorted([
            f for f in post_dir.iterdir()
            if f.suffix.lower() in (".jpg", ".jpeg", ".png", ".webp")
        ])
        if len(slides) < MIN_SLIDES_TO_TOUCH:
            continue
        last = slides[-1]
        phash = perceptual_hash(last)
        if phash is not None:
            last_slides.append((phash, post_dir.name, last.name, len(slides)))

    # Group by visual similarity
    groups = []
    assigned = set()
    for i, (h1, sc1, name1, total1) in enumerate(last_slides):
        if i in assigned:
            continue
        group = [(sc1, name1, total1)]
        assigned.add(i)
        for j, (h2, sc2, name2, total2) in enumerate(last_slides):
            if j in assigned:
                continue
            if hamming_distance(h1, h2) <= SIMILARITY_THRESHOLD:
                group.append((sc2, name2, total2))
                assigned.add(j)
        if len(group) >= MIN_GROUP_SIZE:
            groups.append(group)

    # Collect shortcodes that have promo last slides
    promo_posts = {}
    for group in groups:
        for shortcode, slide_name, total in group:
            promo_posts[shortcode] = slide_name

    return promo_posts, groups


def update_manifest(handle, promo_posts, dry_run=False):
    client = storage.Client()
    bucket = client.bucket(BUCKET_NAME)

    blob = bucket.blob(f"content/{handle}/manifest.json")
    manifest = json.loads(blob.download_as_text())

    removed = 0
    for post in manifest:
        sc = post["shortcode"]
        if sc not in promo_posts:
            continue
        slides = post["slides"]
        if len(slides) < MIN_SLIDES_TO_TOUCH:
            continue
        last_slide_filename = slides[-1].split("/")[-1]
        promo_filename = promo_posts[sc]
        # Verify the last slide in the manifest matches what we detected
        if last_slide_filename == promo_filename:
            post["slides"] = slides[:-1]
            post["slide_count"] = len(post["slides"])
            removed += 1

    if dry_run:
        print(f"  Would remove promo slides from {removed} posts (dry run)")
    else:
        blob.upload_from_string(
            json.dumps(manifest, ensure_ascii=False, indent=2),
            content_type="application/json",
            timeout=60,
        )
        print(f"  Removed promo slides from {removed} posts")

    return removed


def main():
    dry_run = "--dry-run" in sys.argv

    content_dir = Path("content")
    handle = "explainingpaintings"

    print("Scanning for promotional last slides...")
    promo_posts, groups = find_promo_slides(content_dir, handle)

    print(f"\nFound {len(groups)} promo variations across {len(promo_posts)} posts:")
    for i, group in enumerate(sorted(groups, key=len, reverse=True)):
        print(f"  Variation {i + 1}: {len(group)} posts")

    if not promo_posts:
        print("No promotional slides detected.")
        return

    print(f"\nUpdating Firebase manifest...")
    removed = update_manifest(handle, promo_posts, dry_run=dry_run)

    if dry_run:
        print(f"\nDry run complete. Run without --dry-run to apply.")
    else:
        print(f"\nDone! {removed} promo slides removed. App will reflect on next sync.")


if __name__ == "__main__":
    main()
