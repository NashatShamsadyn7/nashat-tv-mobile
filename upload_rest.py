#!/usr/bin/env python3
"""Upload channels to Firebase RTDB via REST API."""
import json
import urllib.request

DB_URL = "https://nashat-tv-default-rtdb.asia-southeast1.firebasedatabase.app"

with open("channels_firebase_final.json", "r", encoding="utf-8") as f:
    data = json.load(f)

print(f"Uploading {len(data)} channels to Firebase RTDB...")

# Upload under /channels path using PUT (replaces entire node)
url = f"{DB_URL}/channels.json"
payload = json.dumps(data, ensure_ascii=False).encode('utf-8')

req = urllib.request.Request(url, data=payload, method='PUT')
req.add_header('Content-Type', 'application/json')

try:
    with urllib.request.urlopen(req) as resp:
        result = resp.read().decode('utf-8')
        print(f"SUCCESS! Response length: {len(result)} bytes")
        print(f"Status: {resp.status}")
except urllib.error.HTTPError as e:
    body = e.read().decode('utf-8')
    print(f"HTTP Error {e.code}: {body}")
except Exception as e:
    print(f"Error: {e}")
