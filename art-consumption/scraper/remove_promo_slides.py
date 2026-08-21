#!/usr/bin/env python3
"""
Removes promotional slides from the Firebase manifest.

Detects promo slides by finding images reused across multiple posts —
real art is unique per post, promo/follow pages are shared. Strips ALL
trailing promo slides in one pass, and removes posts that are entirely promo.

Usage: python remove_promo_slides.py [--dry-run]
Requires: Pillow, google-cloud-storage
          GOOGLE_APPLICATION_CREDENTIALS env var set

Gotchas:
- Uses manifest slide order, not filesystem sort order.
- Builds a global map of every slide image's MD5 across all posts.
  Any image appearing in 2+ posts is promo (real art is never reused).
- Perceptual hashing catches re-encoded variants (hamming <= 10).
- Strips ALL trailing promos, not just the last one.
- Posts left with 0 slides after stripping are removed entirely.
"""

import json
import sys
import hashlib
from pathlib import Path
from collections import defaultdict
from PIL import Image
from google.cloud import storage

BUCKET_NAME = "art-consumption.firebasestorage.app"
SIMILARITY_THRESHOLD = 10


def perceptual_hash(img_path, size=16):
    try:
        img = Image.open(img_path).convert("L").resize((size, size), Image.LANCZOS)
        pixels = list(img.getdata())
        avg = sum(pixels) / len(pixels)
        bits = "".join("1" if p > avg else "0" for p in pixels)
        return int(bits, 2)
    except:
        return None


def hamming_distance(h1, h2):
    return bin(h1 ^ h2).count("1")


def main():
    dry_run = "--dry-run" in sys.argv
    content_dir = Path("content/explainingpaintings")

    client = storage.Client()
    bucket = client.bucket(BUCKET_NAME)

    print("Downloading current manifest...")
    blob = bucket.blob("content/explainingpaintings/manifest.json")
    manifest = json.loads(blob.download_as_text())
    print(f"  {len(manifest)} posts")

    print("\nBuilding global slide fingerprint map...")
    # Hash every slide across all posts
    slide_md5_count = defaultdict(int)
    slide_phash_map = {}

    for post in manifest:
        for slide_blob in post["slides"]:
            fname = slide_blob.split("/")[-1]
            local = content_dir / post["shortcode"] / fname
            if not local.exists():
                continue
            md5 = hashlib.md5(local.read_bytes()).hexdigest()
            slide_md5_count[md5] += 1
            if md5 not in slide_phash_map:
                slide_phash_map[md5] = perceptual_hash(local)

    # Build set of promo MD5s (images appearing in 2+ posts)
    promo_md5s = {md5 for md5, count in slide_md5_count.items() if count >= 2}

    # Extend with perceptual matches
    promo_phashes = [(md5, slide_phash_map[md5]) for md5 in promo_md5s if slide_phash_map.get(md5) is not None]
    for md5, ph in slide_phash_map.items():
        if md5 in promo_md5s or ph is None:
            continue
        for promo_md5, promo_ph in promo_phashes:
            if hamming_distance(ph, promo_ph) <= SIMILARITY_THRESHOLD:
                promo_md5s.add(md5)
                break

    print(f"  {len(promo_md5s)} unique promo images identified")

    print("\nStripping trailing promo slides...")
    slides_removed = 0
    posts_removed = 0
    posts_trimmed = 0
    new_manifest = []

    for post in manifest:
        slides = post["slides"]
        original_count = len(slides)

        # Strip trailing promos
        while len(slides) >= 1:
            last_blob = slides[-1]
            fname = last_blob.split("/")[-1]
            local = content_dir / post["shortcode"] / fname
            if not local.exists():
                break
            md5 = hashlib.md5(local.read_bytes()).hexdigest()
            if md5 in promo_md5s:
                slides = slides[:-1]
            else:
                break

        removed = original_count - len(slides)
        if removed > 0:
            slides_removed += removed
            if len(slides) == 0:
                posts_removed += 1
                continue
            else:
                posts_trimmed += 1

        post["slides"] = slides
        post["slide_count"] = len(slides)
        new_manifest.append(post)

    print(f"  {slides_removed} promo slides stripped")
    print(f"  {posts_trimmed} posts trimmed")
    print(f"  {posts_removed} all-promo posts removed entirely")
    print(f"  {len(new_manifest)} posts remaining")

    if dry_run:
        print("\nDry run — no changes applied.")
    else:
        blob.upload_from_string(
            json.dumps(new_manifest, ensure_ascii=False, indent=2),
            content_type="application/json",
            timeout=60,
        )
        print("\nManifest updated. App will reflect on next sync.")


if __name__ == "__main__":
    main()
