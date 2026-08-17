#!/usr/bin/env python3
"""
Sets Firebase Storage security rules to allow public read access.

Usage: python set_firebase_rules.py
Requires: google-auth, requests
Auth: Set GOOGLE_APPLICATION_CREDENTIALS to the service account JSON path.
"""

import json
import os

import google.auth
import google.auth.transport.requests
from google.oauth2 import service_account

PROJECT_ID = "art-consumption"
BUCKET = "art-consumption.firebasestorage.app"

RULES = """rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /{allPaths=**} {
      allow read;
      allow write: if false;
    }
  }
}
"""

def main():
    creds = service_account.Credentials.from_service_account_file(
        os.environ["GOOGLE_APPLICATION_CREDENTIALS"],
        scopes=["https://www.googleapis.com/auth/firebase"],
    )
    creds.refresh(google.auth.transport.requests.Request())

    import urllib.request

    headers = {
        "Authorization": f"Bearer {creds.token}",
        "Content-Type": "application/json",
    }

    # Step 1: Create a new ruleset
    ruleset_body = json.dumps({
        "source": {
            "files": [{
                "name": "storage.rules",
                "content": RULES,
            }]
        }
    }).encode()

    req = urllib.request.Request(
        f"https://firebaserules.googleapis.com/v1/projects/{PROJECT_ID}/rulesets",
        data=ruleset_body,
        headers=headers,
        method="POST",
    )
    resp = urllib.request.urlopen(req)
    ruleset = json.loads(resp.read())
    ruleset_name = ruleset["name"]
    print(f"Created ruleset: {ruleset_name}")

    # Step 2: Update the release to use the new ruleset
    release_name = f"projects/{PROJECT_ID}/releases/firebase.storage/{BUCKET}"
    release_body = json.dumps({
        "release": {
            "name": release_name,
            "rulesetName": ruleset_name,
        }
    }).encode()

    req = urllib.request.Request(
        f"https://firebaserules.googleapis.com/v1/{release_name}",
        data=release_body,
        headers=headers,
        method="PATCH",
    )
    resp = urllib.request.urlopen(req)
    print(f"Updated release: {json.loads(resp.read())['name']}")
    print("Firebase Storage is now publicly readable.")


if __name__ == "__main__":
    main()
