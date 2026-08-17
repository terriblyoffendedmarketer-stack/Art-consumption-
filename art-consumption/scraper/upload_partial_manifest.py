#!/usr/bin/env python3
"""Uploads a partial manifest from the first N posts so the app has something to show."""

import json
import os
from pathlib import Path
from google.cloud import storage

BUCKET_NAME = "art-consumption.firebasestorage.app"

def main():
    script_dir = Path(__file__).parent
    os.chdir(script_dir)

    client = storage.Client()
    bucket = client.bucket(BUCKET_NAME)

    handle = "explainingpaintings"
    content_dir = Path("content") / handle

    post_dirs = sorted([
        d for d in content_dir.iterdir()
        if d.is_dir() and (d / "metadata.json").exists()
    ])

    manifest = []
    checked = 0

    for post_dir in post_dirs:
        shortcode = post_dir.name
        slides = sorted([
            f for f in post_dir.iterdir()
            if f.suffix.lower() in (".jpg", ".jpeg", ".png", ".webp")
        ])
        if not slides:
            continue

        first_blob = f"content/{handle}/{shortcode}/{slides[0].name}"
        try:
            if bucket.blob(first_blob).exists(timeout=10):
                with open(post_dir / "metadata.json") as f:
                    metadata = json.load(f)
                slide_blobs = [f"content/{handle}/{shortcode}/{s.name}" for s in slides]
                manifest.append({
                    "shortcode": shortcode,
                    "date": metadata.get("date", ""),
                    "caption": metadata.get("caption", ""),
                    "slide_count": len(slides),
                    "handle": handle,
                    "slides": slide_blobs,
                })
            else:
                break
        except Exception:
            break

        checked += 1
        if checked % 10 == 0:
            print(f"  Checked {checked} posts, {len(manifest)} available")

    if manifest:
        blob = bucket.blob(f"content/{handle}/manifest.json")
        blob.upload_from_string(
            json.dumps(manifest, ensure_ascii=False, indent=2),
            content_type="application/json",
            timeout=60,
        )
        print(f"Uploaded partial manifest: {len(manifest)} posts")
    else:
        print("No posts found uploaded yet")


if __name__ == "__main__":
    main()
