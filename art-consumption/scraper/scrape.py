#!/usr/bin/env python3
"""
Scrapes Instagram carousel posts (all slides + captions) from configured accounts.
Uses gallery-dl with browser cookies for authentication (no separate login needed).

Usage:
    python scrape.py --full                  # Bulk scrape all posts from all accounts
    python scrape.py --update                # Only fetch new posts since last check
    python scrape.py --full --account handle # Scrape a specific account
    python scrape.py --add handle            # Add a new account and scrape it

Requires: gallery-dl (pip install gallery-dl)
Auth: Uses cookies from your browser. Log into Instagram in Chrome/Safari/Firefox first.

Gotchas:
- gallery-dl uses browser cookies, not a separate login session. This avoids
  Instagram's API blocks on large accounts that broke instaloader.
- sleep-request must be >= 2s or Instagram will rate-limit/ban you.
- Two-step process: gallery-dl downloads raw files, then we reorganize into
  content/<handle>/<shortcode>/01.jpg format with metadata.json.
- Use `caffeinate -s` wrapper to prevent Mac sleep during long scrapes.
"""

import argparse
import json
import os
import shutil
import sys
from collections import defaultdict
from datetime import datetime
from pathlib import Path


def load_config(config_path="config.json"):
    with open(config_path) as f:
        return json.load(f)


def save_config(config, config_path="config.json"):
    with open(config_path, "w") as f:
        json.dump(config, f, indent=2)


def run_gallery_dl(handle, config, raw_dir):
    """Download posts from an Instagram account using gallery-dl."""
    import subprocess

    browser = config.get("cookies_browser", "chrome")
    raw_dir.mkdir(parents=True, exist_ok=True)

    cmd = [
        sys.executable, "-m", "gallery_dl",
        "--cookies-from-browser", browser,
        "--write-metadata",
        "--no-download" if False else "",
        "--dest", str(raw_dir),
        "--sleep-request", "3",
        "--option", "videos=false",
        f"https://www.instagram.com/{handle}/",
    ]
    cmd = [c for c in cmd if c]

    print(f"  Downloading with gallery-dl...")

    try:
        proc = subprocess.run(cmd, timeout=7200)
        return proc.returncode == 0
    except subprocess.TimeoutExpired:
        print("  gallery-dl timed out after 2 hours")
        return False
    except Exception as e:
        print(f"  Error running gallery-dl: {e}")
        return False


def reorganize_posts(raw_dir, content_dir, handle):
    """Reorganize gallery-dl output into content/<handle>/<shortcode>/ structure."""
    raw_account = raw_dir / "instagram" / handle
    if not raw_account.exists():
        print(f"  No downloaded files found at {raw_account}")
        return 0

    output_dir = content_dir / handle
    output_dir.mkdir(parents=True, exist_ok=True)

    # Group files by post shortcode using metadata
    posts = defaultdict(lambda: {"images": [], "meta": None})

    for meta_file in sorted(raw_account.glob("*.json")):
        with open(meta_file) as f:
            meta = json.load(f)

        shortcode = meta.get("post_shortcode", "")
        if not shortcode:
            continue

        image_file = raw_account / meta_file.name.replace(".json", "")
        if not image_file.exists():
            stem = meta_file.stem.rsplit(".", 1)[0] if "." in meta_file.stem else meta_file.stem
            for ext in (".jpg", ".jpeg", ".png", ".webp"):
                candidate = raw_account / (stem + ext)
                if candidate.exists():
                    image_file = candidate
                    break

        if image_file.exists() and image_file.suffix.lower() in (".jpg", ".jpeg", ".png", ".webp"):
            slide_num = meta.get("num", 0)
            posts[shortcode]["images"].append((slide_num, image_file))

        if posts[shortcode]["meta"] is None:
            posts[shortcode]["meta"] = meta

    new_count = 0

    for shortcode, data in sorted(posts.items()):
        post_dir = output_dir / shortcode
        post_dir.mkdir(exist_ok=True)

        was_new = not (post_dir / "metadata.json").exists()

        images = sorted(data["images"], key=lambda x: x[0])
        for i, (slide_num, img) in enumerate(images, 1):
            target = post_dir / f"{i:02d}{img.suffix.lower()}"
            if not target.exists():
                shutil.copy2(img, target)

        meta = data["meta"] or {}
        metadata = {
            "shortcode": shortcode,
            "date": meta.get("date", meta.get("post_date", "")),
            "caption": meta.get("description", ""),
            "slide_count": len(images),
            "handle": handle,
            "is_video": False,
        }

        with open(post_dir / "metadata.json", "w") as f:
            json.dump(metadata, f, indent=2, ensure_ascii=False)

        if was_new:
            new_count += 1
            date_str = metadata["date"][:10] if metadata["date"] else "unknown"
            print(f"  [{new_count}] {shortcode} - {len(images)} slides - {date_str}")

    return new_count


def update_tracking(account_dir):
    """Update tracking.json with the current state of downloaded posts."""
    tracking_file = account_dir / "tracking.json"
    downloaded = []

    for post_dir in sorted(account_dir.iterdir()):
        if not post_dir.is_dir():
            continue
        if (post_dir / "metadata.json").exists():
            downloaded.append(post_dir.name)

    tracking = {
        "downloaded": sorted(downloaded),
        "last_check": datetime.now().isoformat(),
        "post_count": len(downloaded),
    }

    with open(tracking_file, "w") as f:
        json.dump(tracking, f, indent=2)

    return len(downloaded)


def scrape_account(handle, config, content_dir, full=False):
    """Scrape an account: download with gallery-dl, then reorganize."""
    account_dir = content_dir / handle
    account_dir.mkdir(parents=True, exist_ok=True)

    tracking_file = account_dir / "tracking.json"
    existing_count = 0
    if tracking_file.exists():
        with open(tracking_file) as f:
            tracking = json.load(f)
        existing_count = tracking.get("post_count", 0)

    print(f"\nScraping @{handle} ({'full' if full else 'update'} mode)")
    print(f"  Already have {existing_count} posts")

    raw_dir = content_dir / ".raw"
    run_gallery_dl(handle, config, raw_dir)

    print(f"  Reorganizing into content format...")
    new_count = reorganize_posts(raw_dir, content_dir, handle)
    total = update_tracking(account_dir)

    print(f"  Done. {new_count} new posts, {total} total.")
    return new_count


def main():
    parser = argparse.ArgumentParser(description="Scrape Instagram art carousel posts")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--full", action="store_true", help="Bulk scrape all posts")
    mode.add_argument("--update", action="store_true", help="Only fetch new posts")
    mode.add_argument("--add", metavar="HANDLE", help="Add a new account and scrape it")

    parser.add_argument("--account", help="Scrape only this account (with --full or --update)")
    parser.add_argument("--config", default="config.json", help="Path to config file")

    args = parser.parse_args()

    script_dir = Path(__file__).parent
    os.chdir(script_dir)

    config = load_config(args.config)
    content_dir = Path(config.get("content_dir", "content"))
    content_dir.mkdir(exist_ok=True)

    if args.add:
        handle = args.add.lstrip("@")
        if handle not in config["accounts"]:
            config["accounts"].append(handle)
            save_config(config, args.config)
            print(f"Added @{handle} to config")
        else:
            print(f"@{handle} already in config")

        scrape_account(handle, config, content_dir, full=True)
        return

    accounts = [args.account] if args.account else config["accounts"]

    total_new = 0
    for handle in accounts:
        total_new += scrape_account(handle, config, content_dir, full=args.full)

    print(f"\nTotal: {total_new} new posts across {len(accounts)} account(s)")


if __name__ == "__main__":
    main()
