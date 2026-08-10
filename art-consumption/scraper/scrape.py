#!/usr/bin/env python3
"""
Scrapes Instagram carousel posts (all slides + captions) from configured accounts.

Usage:
    python scrape.py --full                  # Bulk scrape all posts from all accounts
    python scrape.py --update                # Only fetch new posts since last check
    python scrape.py --full --account handle # Scrape a specific account
    python scrape.py --add handle            # Add a new account and scrape it
"""

import argparse
import json
import os
import sys
import time
from datetime import datetime
from pathlib import Path

import instaloader


def load_config(config_path="config.json"):
    with open(config_path) as f:
        return json.load(f)


def save_config(config, config_path="config.json"):
    with open(config_path, "w") as f:
        json.dump(config, f, indent=2)


def create_loader(config):
    loader = instaloader.Instaloader(
        download_videos=False,
        download_video_thumbnails=False,
        download_comments=False,
        save_metadata=False,
        compress_json=False,
        post_metadata_txt_pattern="",
        max_connection_attempts=3,
    )

    session_file = config.get("session_file", "")
    username = config.get("username", "")

    if session_file and os.path.exists(session_file):
        loader.load_session_from_file(username, session_file)
        print(f"Loaded session for @{username}")
    elif username:
        password = config.get("password", "")
        if password:
            loader.login(username, password)
            print(f"Logged in as @{username}")
        else:
            print("Warning: username set but no password or session file. Proceeding without login.")
            print("Some content may not be accessible. Run with session file for best results.")

    return loader


def download_slide(loader, url, filepath):
    try:
        resp = loader.context.get_raw(url)
        with open(filepath, "wb") as f:
            f.write(resp.content)
        return True
    except Exception as e:
        print(f"    Failed to download slide: {e}")
        return False


def scrape_account(loader, handle, content_dir, full=False):
    account_dir = content_dir / handle
    account_dir.mkdir(parents=True, exist_ok=True)

    tracking_file = account_dir / "tracking.json"
    tracking = {}
    if tracking_file.exists():
        with open(tracking_file) as f:
            tracking = json.load(f)

    downloaded = set(tracking.get("downloaded", []))

    print(f"\nScraping @{handle} ({'full' if full else 'update'} mode)")
    print(f"  Already have {len(downloaded)} posts")

    try:
        profile = instaloader.Profile.from_username(loader.context, handle)
    except Exception as e:
        print(f"  Error loading profile: {e}")
        return 0

    print(f"  Profile has {profile.mediacount} posts")

    new_count = 0
    skipped = 0

    for post in profile.get_posts():
        if post.shortcode in downloaded:
            skipped += 1
            if not full and skipped > 5:
                print(f"  Found {skipped} existing posts in a row, stopping (update mode)")
                break
            continue

        skipped = 0
        post_dir = account_dir / post.shortcode
        post_dir.mkdir(exist_ok=True)

        if post.typename == "GraphSidecar":
            slide_count = post.mediacount
            nodes = list(post.get_sidecar_nodes())
        else:
            slide_count = 1
            nodes = None

        metadata = {
            "shortcode": post.shortcode,
            "date": post.date_utc.isoformat(),
            "caption": post.caption or "",
            "slide_count": slide_count,
            "handle": handle,
            "is_video": post.is_video,
        }

        with open(post_dir / "metadata.json", "w") as f:
            json.dump(metadata, f, indent=2, ensure_ascii=False)

        success = True
        if nodes:
            for i, node in enumerate(nodes, 1):
                if node.is_video:
                    continue
                ext = "jpg"
                if not download_slide(loader, node.display_url, post_dir / f"{i:02d}.{ext}"):
                    success = False
        else:
            if not post.is_video:
                download_slide(loader, post.url, post_dir / f"01.jpg")

        if success:
            downloaded.add(post.shortcode)
            new_count += 1
            date_str = post.date_utc.strftime("%Y-%m-%d")
            print(f"  [{new_count}] {post.shortcode} - {slide_count} slides - {date_str}")

        time.sleep(1)

    tracking["downloaded"] = sorted(downloaded)
    tracking["last_check"] = datetime.now().isoformat()
    tracking["post_count"] = len(downloaded)
    with open(tracking_file, "w") as f:
        json.dump(tracking, f, indent=2)

    print(f"  Done. {new_count} new posts downloaded.")
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

        loader = create_loader(config)
        scrape_account(loader, handle, content_dir, full=True)
        return

    loader = create_loader(config)
    accounts = [args.account] if args.account else config["accounts"]

    total_new = 0
    for handle in accounts:
        total_new += scrape_account(loader, handle, content_dir, full=args.full)

    print(f"\nTotal: {total_new} new posts across {len(accounts)} account(s)")


if __name__ == "__main__":
    main()
