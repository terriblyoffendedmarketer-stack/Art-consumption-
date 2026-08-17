#!/usr/bin/env python3
"""
Fixes slide ordering in the Firebase manifest.

gallery-dl's raw filenames don't sort in Instagram slide order. The scraper
sorted alphabetically, so 01.jpg might actually be slide 14 on Instagram.
This script reads the raw metadata (which has the correct `num` field),
determines which content file maps to which slide number, and re-uploads
the manifest with slides in the correct order.

No images are re-uploaded — only the manifest JSON changes.
"""

import json
import os
from collections import defaultdict
from pathlib import Path
from google.cloud import storage

BUCKET_NAME = "art-consumption.firebasestorage.app"


def get_correct_order(raw_dir, handle):
    """Read raw metadata to determine correct slide ordering for each post."""
    raw_account = raw_dir / "instagram" / handle
    if not raw_account.exists():
        print(f"  Raw directory not found: {raw_account}")
        return {}

    # Group raw files by post shortcode, collect (num, filename) pairs
    posts = defaultdict(list)

    for meta_file in sorted(raw_account.glob("*.json")):
        with open(meta_file) as f:
            meta = json.load(f)

        shortcode = meta.get("post_shortcode", "")
        num = meta.get("num")
        if not shortcode or num is None:
            continue

        # Find corresponding image file
        stem = meta_file.stem
        if stem.endswith(".jpg") or stem.endswith(".jpeg") or stem.endswith(".png") or stem.endswith(".webp"):
            stem = stem  # The stem already includes the image extension
        image_name = meta_file.name.replace(".json", "")

        posts[shortcode].append((num, image_name))

    # For each post, sort raw files alphabetically (matching what the scraper did),
    # then create a mapping: content_file_number -> instagram_slide_num
    order_map = {}
    for shortcode, files in posts.items():
        # Sort by filename (what the scraper did to assign 01.jpg, 02.jpg, etc.)
        by_filename = sorted(files, key=lambda x: x[1])
        # Each file at position i became {i+1:02d}.ext in the content dir
        # We want to reorder so that num=1 comes first, num=2 second, etc.

        # Create mapping: content_number (1-indexed) -> instagram_num
        content_to_num = {}
        for i, (num, fname) in enumerate(by_filename):
            content_number = i + 1  # This file became {content_number:02d}.ext
            content_to_num[content_number] = num

        # Now create the correct order: sort content files by their instagram num
        # Result: list of content_numbers in correct instagram order
        correct_order = sorted(content_to_num.keys(), key=lambda cn: content_to_num[cn])
        order_map[shortcode] = correct_order

    return order_map


def fix_manifest(content_dir, handle, order_map):
    """Regenerate manifest with correct slide ordering."""
    account_dir = content_dir / handle

    post_dirs = sorted([
        d for d in account_dir.iterdir()
        if d.is_dir() and (d / "metadata.json").exists()
    ])

    manifest = []
    fixed = 0
    total = 0

    for post_dir in post_dirs:
        shortcode = post_dir.name
        with open(post_dir / "metadata.json") as f:
            metadata = json.load(f)

        slides = sorted([
            f for f in post_dir.iterdir()
            if f.suffix.lower() in (".jpg", ".jpeg", ".png", ".webp")
        ])
        if not slides:
            continue

        total += 1

        if shortcode in order_map:
            correct_order = order_map[shortcode]
            # Reorder slides: correct_order[0] is the content_number that should be first
            reordered = []
            for cn in correct_order:
                # Find the file with this content number
                target_name = f"{cn:02d}"
                matching = [s for s in slides if s.stem == target_name]
                if matching:
                    blob_name = f"content/{handle}/{shortcode}/{matching[0].name}"
                    reordered.append(blob_name)

            if len(reordered) == len(slides):
                slide_blobs = reordered
                fixed += 1
            else:
                # Fallback to original order if mapping is incomplete
                slide_blobs = [f"content/{handle}/{shortcode}/{s.name}" for s in slides]
        else:
            slide_blobs = [f"content/{handle}/{shortcode}/{s.name}" for s in slides]

        manifest.append({
            "shortcode": shortcode,
            "date": metadata.get("date", ""),
            "caption": metadata.get("caption", ""),
            "slide_count": len(slides),
            "handle": handle,
            "slides": slide_blobs,
        })

    print(f"  Fixed ordering for {fixed}/{total} posts")
    return manifest


def main():
    script_dir = Path(__file__).parent
    os.chdir(script_dir)

    client = storage.Client()
    bucket = client.bucket(BUCKET_NAME)

    handle = "explainingpaintings"
    content_dir = Path("content")
    raw_dir = content_dir / ".raw"

    print("Reading raw metadata for correct slide ordering...")
    order_map = get_correct_order(raw_dir, handle)
    print(f"  Found ordering info for {len(order_map)} posts")

    print("Generating fixed manifest...")
    manifest = fix_manifest(content_dir, handle, order_map)

    print(f"Uploading fixed manifest ({len(manifest)} posts)...")
    blob = bucket.blob(f"content/{handle}/manifest.json")
    blob.upload_from_string(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        content_type="application/json",
        timeout=60,
    )
    print("Done! App will show slides in correct order on next sync.")


if __name__ == "__main__":
    main()
