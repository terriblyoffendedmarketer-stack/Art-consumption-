#!/bin/bash
cd "$(dirname "$0")"
source venv/bin/activate
export GOOGLE_APPLICATION_CREDENTIALS="$(cd .. && pwd)/firebase-service-account.json"
caffeinate -s python3 -u upload_to_firebase.py --force
