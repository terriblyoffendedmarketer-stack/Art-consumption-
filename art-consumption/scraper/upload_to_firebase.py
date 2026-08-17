#!/usr/bin/env python3
"""
Uploads scraped content to Firebase Storage at original quality.

Usage:
    python upload_to_firebase.py                    # Upload all content
    python upload_to_firebase.py --account handle   # Upload one account only
    python upload_to_firebase.py --force            # Re-upload existing files

Requires: google-cloud-storage
Auth: Set GOOGLE_APPLICATION_CREDENTIALS to the service account JSON path.

Gotchas:
- Images are uploaded at original quality — no resize or recompress.
  Instagram images are already phone-sized (1080-1440px). Art should not be downscaled.
- Uploads a manifest.json per account listing all posts — the app fetches this
  to know what's available without listing the entire bucket.
- Skips already-uploaded files (checks by blob name existence). Use --force to re-upload.
- Each upload has a 120s timeout to prevent hanging on dropped connections.
- Failed uploads are retried up to 3 times with exponential backoff.
"""

import argparse
import json
import os
import sys
import time
from pathlib import Path

from google.cloud import storage
from google.api_core import retry as api_retry
from google.api_core import exceptions as api_exceptions

BUCKET_NAME = "art-consumption.firebasestorage.app"
UPLOAD_TIMEOUT = 120
MAX_RETRIES = 3


def upload_with_retry(blob, data, content_type="image/jpeg"):
    for attempt in range(MAX_RETRIES):
        try:
            blob.upload_from_string(data, content_type=content_type, timeout=UPLOAD_TIMEOUT)
            return True
        except Exception as e:
            if attempt < MAX_RETRIES - 1:
                wait = 2 ** (attempt + 1)
                print(f"    Retry {attempt + 1}/{MAX_RETRIES} after error: {e}")
                time.sleep(wait)
            else:
                print(f"    FAILED after {MAX_RETRIES} attempts: {e}")
                return False


def upload_account(bucket, content_dir, handle, force=False):
    """Upload all posts for one account to Firebase Storage."""
    account_dir = content_dir / handle
    if not account_dir.is_dir():
        print(f"  Account directory not found: {account_dir}")
        return 0

    post_dirs = sorted([
        d for d in account_dir.iterdir()
        if d.is_dir() and (d / "metadata.json").exists()
    ])

    print(f"\nUploading @{handle}: {len(post_dirs)} posts")

    manifest = []
    uploaded = 0
    skipped = 0
    failed = 0

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

        slide_urls = []
        for slide in slides:
            blob_name = f"content/{handle}/{shortcode}/{slide.name}"
            blob = bucket.blob(blob_name)

            if not force:
                try:
                    if blob.exists(timeout=10):
                        skipped += 1
                        slide_urls.append(blob_name)
                        continue
                except Exception:
                    pass

            data = slide.read_bytes()
            if upload_with_retry(blob, data):
                slide_urls.append(blob_name)
                uploaded += 1
            else:
                failed += 1

            total = uploaded + skipped + failed
            if total % 10 == 0:
                print(f"  Progress: {total} images ({uploaded} uploaded, {skipped} existing, {failed} failed)")

        manifest.append({
            "shortcode": shortcode,
            "date": metadata.get("date", ""),
            "caption": metadata.get("caption", ""),
            "slide_count": len(slides),
            "handle": handle,
            "slides": slide_urls,
        })

    # Upload manifest
    manifest_blob = bucket.blob(f"content/{handle}/manifest.json")
    upload_with_retry(manifest_blob, json.dumps(manifest, ensure_ascii=False, indent=2).encode(), "application/json")

    print(f"  Done: {uploaded} uploaded, {skipped} skipped, {failed} failed, {len(manifest)} posts in manifest")
    return uploaded


def main():
    parser = argparse.ArgumentParser(description="Upload content to Firebase Storage")
    parser.add_argument("--account", help="Upload only this account")
    parser.add_argument("--force", action="store_true", help="Re-upload existing files")
    parser.add_argument("--content-dir", default="content", help="Content directory")
    args = parser.parse_args()

    script_dir = Path(__file__).parent
    os.chdir(script_dir)

    content_dir = Path(args.content_dir)
    if not content_dir.is_dir():
        print(f"Content directory not found: {content_dir}")
        sys.exit(1)

    client = storage.Client()
    bucket = client.bucket(BUCKET_NAME)

    if args.account:
        upload_account(bucket, content_dir, args.account, force=args.force)
    else:
        for account_dir in sorted(content_dir.iterdir()):
            if account_dir.is_dir() and account_dir.name != ".raw":
                upload_account(bucket, content_dir, account_dir.name, force=args.force)


if __name__ == "__main__":
    main()
