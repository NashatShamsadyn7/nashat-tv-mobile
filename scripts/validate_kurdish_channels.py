"""Validate every Kurdish channel and report which work / which don't."""
import json
import urllib.request
import urllib.error
import socket
from concurrent.futures import ThreadPoolExecutor, as_completed

KURDISH_HINTS = [
    "nrt", "rudaw", "kurdmax", "kurdsat", "kurdistan", "kurd ", "kurdy",
    "waar", "payam", "speda", "zagros", "korek", "channel 8", "newroz",
    "mixkurdy", "k24", "khoshk", "tishk", "kurdish", "kurd channel",
    "asoo", "ava", "avar", "avatv"
]


def is_kurdish(channel):
    name = channel.get("name", "").lower()
    cat = channel.get("category", "")
    lang = channel.get("language", "")
    if cat == "كوردية" or lang in ("ku", "kur"):
        return True
    return any(h in name for h in KURDISH_HINTS)


def check_url(url, timeout=8):
    """Returns (working: bool, reason: str)"""
    try:
        req = urllib.request.Request(url, method="GET", headers={
            "User-Agent": "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36",
            "Range": "bytes=0-1024",
        })
        with urllib.request.urlopen(req, timeout=timeout) as r:
            status = r.status
            # Read a few bytes to confirm connection works
            try:
                r.read(256)
            except Exception:
                pass
            if 200 <= status < 400:
                return True, f"HTTP {status}"
            return False, f"HTTP {status}"
    except urllib.error.HTTPError as e:
        # For HLS streams, some return 403/401 even when alive
        if e.code in (403, 401):
            return False, f"HTTP {e.code} (blocked)"
        return False, f"HTTP {e.code}"
    except urllib.error.URLError as e:
        reason = str(e.reason)
        if "Name or service not known" in reason or "getaddrinfo failed" in reason:
            return False, "DNS fail (domain dead)"
        if "timed out" in reason or "timeout" in reason.lower():
            return False, "Timeout"
        if "refused" in reason.lower():
            return False, "Connection refused"
        return False, reason[:50]
    except socket.timeout:
        return False, "Timeout"
    except Exception as e:
        return False, str(e)[:50]


def main():
    data = json.load(open("d:/TV App/scripts/channels_for_firebase.json", encoding="utf-8"))
    kurdish = [(k, v) for k, v in data.items() if is_kurdish(v)]
    print(f"Found {len(kurdish)} Kurdish channels. Validating...")
    print("=" * 70)

    working = []
    broken = []
    with ThreadPoolExecutor(max_workers=15) as pool:
        futures = {pool.submit(check_url, ch[1]["url"]): ch for ch in kurdish}
        for fut in as_completed(futures):
            key, ch = futures[fut]
            ok, reason = fut.result()
            mark = "✓" if ok else "✗"
            print(f"{mark} {ch['name']:<45} | {reason}")
            (working if ok else broken).append((key, ch, reason))

    print("=" * 70)
    print(f"WORKING: {len(working)} | BROKEN: {len(broken)}")
    print()
    print("--- BROKEN CHANNELS (will be removed) ---")
    for k, c, r in broken:
        print(f"  {c['name']:<45} | {r}")

    # Save lists
    with open("d:/TV App/scripts/kurdish_working.json", "w", encoding="utf-8") as f:
        json.dump({k: c for k, c, _ in working}, f, ensure_ascii=False, indent=2)
    with open("d:/TV App/scripts/kurdish_broken.json", "w", encoding="utf-8") as f:
        json.dump({k: {"name": c["name"], "url": c["url"], "reason": r}
                   for k, c, r in broken}, f, ensure_ascii=False, indent=2)

    # Also: remove broken from main channels file
    for k, _, _ in broken:
        if k in data:
            del data[k]
    with open("d:/TV App/scripts/channels_for_firebase.json", "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print(f"\n✓ Cleaned channels file: {len(data)} remaining (removed {len(broken)} dead Kurdish)")


if __name__ == "__main__":
    main()
