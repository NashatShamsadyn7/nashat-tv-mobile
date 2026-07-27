"""Fetch IPTV channels from iptv-org and convert to Firebase JSON format."""
import json
import re
import urllib.request
from urllib.error import URLError

# Arabic-speaking countries + Kurdish language + Turkish
SOURCES = [
    # (source_id, kind, ISO_country_code, arabic_name, language)
    ("iq", "country", "IQ", "العراق", "ar"),
    ("sa", "country", "SA", "السعودية", "ar"),
    ("ae", "country", "AE", "الإمارات", "ar"),
    ("eg", "country", "EG", "مصر", "ar"),
    ("sy", "country", "SY", "سوريا", "ar"),
    ("jo", "country", "JO", "الأردن", "ar"),
    ("kw", "country", "KW", "الكويت", "ar"),
    ("qa", "country", "QA", "قطر", "ar"),
    ("lb", "country", "LB", "لبنان", "ar"),
    ("ps", "country", "PS", "فلسطين", "ar"),
    ("ye", "country", "YE", "اليمن", "ar"),
    ("ma", "country", "MA", "المغرب", "ar"),
    ("dz", "country", "DZ", "الجزائر", "ar"),
    ("tn", "country", "TN", "تونس", "ar"),
    ("ly", "country", "LY", "ليبيا", "ar"),
    ("sd", "country", "SD", "السودان", "ar"),
    ("om", "country", "OM", "عُمان", "ar"),
    ("bh", "country", "BH", "البحرين", "ar"),
    # Kurdish language channels — country=IQ so they get prioritized in Iraq
    ("kur", "language", "IQ", "كوردستان", "ku"),
    ("tr", "country", "TR", "تركيا", "tr"),
]

BASE_URL = "https://iptv-org.github.io/iptv"

EXTINF_RE = re.compile(
    r'#EXTINF:-?\d+\s*(?P<attrs>.*?),\s*(?P<name>.+)',
    re.IGNORECASE
)
ATTR_RE = re.compile(r'(\w[\w-]*)="([^"]*)"')


def fetch(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read().decode("utf-8", errors="ignore")


def parse_m3u(content: str, country_iso: str, country_ar: str, language: str, source_id: str):
    """Parse M3U into list of channel dicts. country=ISO code, description=arabic name."""
    channels = []
    lines = content.splitlines()
    i = 0
    while i < len(lines):
        line = lines[i].strip()
        if line.startswith("#EXTINF"):
            m = EXTINF_RE.match(line)
            if m:
                attrs = dict(ATTR_RE.findall(m.group("attrs")))
                name = m.group("name").strip()
                url = ""
                j = i + 1
                while j < len(lines):
                    l = lines[j].strip()
                    if l and not l.startswith("#"):
                        url = l
                        break
                    j += 1
                if url and name:
                    logo = attrs.get("tvg-logo", "")
                    group = attrs.get("group-title", "")
                    # Kurdish-language source overrides category
                    if source_id == "kur":
                        category = "كوردية"
                    else:
                        category = classify_category(name, group)
                    channels.append({
                        "name": name,
                        "logo": logo,
                        "logoUrl": logo,
                        "category": category,
                        "url": url,
                        "streamUrl": url,
                        "description": country_ar + " — " + (group or "عام"),
                        "country": country_iso,
                        "language": language,
                        "isAlive": True,
                        "source": "iptv-org",
                    })
                i = j
                continue
        i += 1
    return channels


def classify_category(name: str, group: str) -> str:
    """Smart category detection in Arabic."""
    text = (name + " " + group).lower()
    if any(k in text for k in ["news", "أخبار", "اخبار", "alarabiya", "aljazeera", "sky news", "bbc", "cnn"]):
        return "أخبار"
    if any(k in text for k in ["sport", "رياض", "bein", "ssc", "dubai sports"]):
        return "رياضة"
    if any(k in text for k in ["movie", "cinema", "أفلام", "افلام", "rotana cinema"]):
        return "أفلام"
    if any(k in text for k in ["series", "drama", "مسلسلات", "دراما"]):
        return "مسلسلات"
    if any(k in text for k in ["kids", "cartoon", "أطفال", "اطفال", "mbc 3", "spacetoon"]):
        return "أطفال"
    if any(k in text for k in ["music", "موسيقى", "rotana", "mazzika"]):
        return "موسيقى"
    if any(k in text for k in ["religious", "quran", "قرآن", "اسلامي", "إسلامي", "iqra"]):
        return "إسلامية"
    if any(k in text for k in ["documentary", "وثائقي", "national geographic", "discovery"]):
        return "وثائقية"
    if any(k in text for k in ["kurdistan", "kurd", "كورد", "كرد"]):
        return "كوردية"
    return "عام"


def slugify(name: str, idx: int) -> str:
    """Generate a Firebase-safe key from channel name."""
    s = re.sub(r'[^a-zA-Z0-9_]', '_', name)
    s = re.sub(r'_+', '_', s).strip('_').lower()
    if not s:
        s = "ch"
    return f"{s}_{idx}"


def main():
    all_channels = {}
    stats = {}
    total = 0

    for code, kind, en_name, ar_name, language in SOURCES:
        url = f"{BASE_URL}/{kind.replace('country', 'countries').replace('language', 'languages')}/{code}.m3u"
        try:
            print(f"Fetching {ar_name} ({code})...")
            content = fetch(url)
            parsed = parse_m3u(content, en_name, ar_name, language, code)
            for idx, ch in enumerate(parsed):
                key = slugify(ch["name"], len(all_channels) + 1)
                all_channels[key] = ch
            stats[ar_name] = len(parsed)
            total += len(parsed)
            print(f"  → {len(parsed)} channels")
        except URLError as e:
            print(f"  ✗ Failed: {e}")
            stats[ar_name] = 0

    # Deduplicate by URL (keep first occurrence)
    seen_urls = set()
    deduped = {}
    for k, v in all_channels.items():
        u = v.get("url", "").strip().lower()
        if u and u not in seen_urls:
            seen_urls.add(u)
            deduped[k] = v

    print(f"\n{'=' * 50}")
    print(f"Total channels: {total}")
    print(f"After deduplication: {len(deduped)}")
    print(f"{'=' * 50}")
    for k, v in stats.items():
        print(f"  {k}: {v}")

    output_path = "d:/TV App/scripts/channels_for_firebase.json"
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(deduped, f, ensure_ascii=False, indent=2)
    print(f"\n✓ Saved to: {output_path}")
    print(f"  Size: {len(json.dumps(deduped, ensure_ascii=False))/1024:.1f} KB")


if __name__ == "__main__":
    main()
