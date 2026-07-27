"""Scrape ALL channels from karwan.tv and add them to Firebase."""
import json
import re
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed

BASE = "https://karwan.tv"
HEADERS = {"User-Agent": "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"}


def fetch(url, timeout=20):
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read().decode("utf-8", errors="ignore")


def get_all_channel_links():
    """Walk karwan.tv pages and collect every channel page URL + name + logo."""
    channels = {}
    # The homepage has infinite-scroll via /page/N/. Walk pages until 404.
    for page in range(1, 30):
        url = BASE + "/" if page == 1 else f"{BASE}/page/{page}/"
        try:
            html = fetch(url, timeout=15)
        except Exception as e:
            print(f"  Stop at page {page}: {e}")
            break

        # Match channel cards: <a href="https://karwan.tv/SLUG/"><img src="LOGO" alt="NAME">
        pattern = re.compile(
            r'<a\s+href="(https?://karwan\.tv/[^"/]+/)"[^>]*>\s*<img[^>]*src="([^"]+)"[^>]*alt="([^"]*)"',
            re.IGNORECASE
        )
        found = 0
        for m in pattern.finditer(html):
            slug_url, logo, name = m.group(1), m.group(2), m.group(3).strip()
            # Skip obvious non-channel pages
            if any(x in slug_url for x in ["/category/", "/tag/", "/page/", "/about/",
                                            "/contact/", "/wp-", "/feed/"]):
                continue
            if slug_url in channels:
                continue
            channels[slug_url] = {"name": name, "logo": logo}
            found += 1
        print(f"  Page {page}: +{found} channels (total {len(channels)})")
        if found == 0:
            break
    return channels


def classify(name):
    """Detect category from channel name."""
    n = name.lower()
    if any(k in n for k in ["nrt", "rudaw", "kurdistan 24", "kurdsat news", "k24",
                            "news", "خبار", "اخبار", "iraqia news", "alhurra"]):
        return "أخبار"
    if any(k in n for k in ["sport", "bein", "ssc", "abu dhabi sport", "kss",
                            "dubai sport", "ava sport", "رياض"]):
        return "رياضة"
    if any(k in n for k in ["movie", "cinema", "mbc 2", "mbc max", "mbc action",
                            "أفلام", "افلام", "rotana cinema"]):
        return "أفلام"
    if any(k in n for k in ["drama", "series", "مسلسل"]):
        return "مسلسلات"
    if any(k in n for k in ["kids", "baby", "cartoon", "pepule", "afarin",
                            "zarok", "spacetoon", "mbc 3", "أطفال", "اطفال"]):
        return "أطفال"
    if any(k in n for k in ["music", "muzik", "rotana", "mazzika", "موسيق",
                            "kurdmax music"]):
        return "موسيقى"
    if any(k in n for k in ["quran", "قرآن", "islamic", "اسلام", "إسلام",
                            "iqra", "abalfadhl", "imam hussein", "alaimma",
                            "marjaeyat", "bayyinat", "religious"]):
        return "إسلامية"
    if any(k in n for k in ["documentary", "national geographic", "discovery",
                            "history", "وثائقي"]):
        return "وثائقية"
    if any(k in n for k in ["kurdmax", "kurdsat", "kurdistan tv", "kurdistan",
                            "kurd", "rudaw tv", "waar", "zagros", "ava ",
                            "newroz", "speda", "k tv", "tishk", "korek"]):
        return "كوردية"
    return "عام"


def detect_language(name):
    n = name.lower()
    if any(k in n for k in ["kurd", "kurmanji", "sorani", "بادینی",
                            "newroz", "rudaw", "nrt", "kurdistan",
                            "zagros", "waar", "tishk", "ava ", "k24"]):
        return "ku"
    if any(k in n for k in ["trt", "show tv", "atv", "fox tv", "kanal"]):
        return "tr"
    if any(k in n for k in ["bbc", "cnn", "discovery", "history channel"]):
        return "en"
    return "ar"


def detect_country(name, language):
    n = name.lower()
    if language == "ku":
        return "IQ"
    if language == "tr":
        return "TR"
    if any(k in n for k in ["iraq", "iraqi", "iraqia", "alsharqiya",
                            "dijlah", "utv", "al rasheed", "mbc iraq",
                            "asia tv", "al-sumaria"]):
        return "IQ"
    if any(k in n for k in ["saudi", "ksa", "mbc 1", "mbc 2", "mbc 3", "mbc 4",
                            "ssc", "rotana"]):
        return "SA"
    if any(k in n for k in ["dubai", "abu dhabi", "sharjah", "ad sport", "uae"]):
        return "AE"
    if any(k in n for k in ["aljazeera", "bein", "qatar", "alkass"]):
        return "QA"
    if any(k in n for k in ["egypt", "egy", "ontv", "cbc"]):
        return "EG"
    return "IQ"  # default for Karwan (Kurdish/Iraqi audience)


def slugify(name, idx):
    s = re.sub(r'[^a-zA-Z0-9_]', '_', name).lower()
    s = re.sub(r'_+', '_', s).strip('_')
    if not s:
        s = "ch"
    return f"{s}_karwan_{idx}"


def main():
    print("Scraping karwan.tv homepage…")
    found = get_all_channel_links()
    print(f"\n✓ Found {len(found)} channels on karwan.tv\n")

    # Load existing channels, remove old karwan entries
    try:
        data = json.load(open("d:/TV App/scripts/channels_for_firebase.json", encoding="utf-8"))
    except Exception:
        data = {}

    # Remove existing karwan-sourced entries
    before = len(data)
    data = {k: v for k, v in data.items() if v.get("source") != "karwan.tv"}
    print(f"Removed {before - len(data)} old karwan entries")

    # Add fresh karwan list
    added = 0
    for idx, (slug_url, info) in enumerate(sorted(found.items()), start=1):
        name = info["name"]
        if not name or name.lower() in ("", "logo", "image"):
            continue
        lang = detect_language(name)
        country = detect_country(name, lang)
        category = classify(name)
        key = slugify(name, idx)
        data[key] = {
            "name": name,
            "logo": info["logo"],
            "logoUrl": info["logo"],
            "category": category,
            "url": slug_url,           # karwan.tv URL → KarwanScraper extracts real stream
            "streamUrl": slug_url,
            "description": "Karwan TV",
            "country": country,
            "language": lang,
            "isAlive": True,
            "source": "karwan.tv",
        }
        added += 1

    print(f"\nAdded {added} Karwan channels. Total in file: {len(data)}")
    with open("d:/TV App/scripts/channels_for_firebase.json", "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    # Stats
    by_cat = {}
    by_country = {}
    for v in data.values():
        if v.get("source") == "karwan.tv":
            by_cat[v["category"]] = by_cat.get(v["category"], 0) + 1
            by_country[v["country"]] = by_country.get(v["country"], 0) + 1
    print("\nKarwan by category:")
    for k, n in sorted(by_cat.items(), key=lambda x: -x[1]):
        print(f"  {k}: {n}")
    print("\nKarwan by country:")
    for k, n in sorted(by_country.items(), key=lambda x: -x[1]):
        print(f"  {k}: {n}")


if __name__ == "__main__":
    main()
