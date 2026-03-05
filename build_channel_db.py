#!/usr/bin/env python3
"""
Build a clean, deduplicated Live TV channel database from multiple sources.
Merges iq.m3u, kur.m3u, and karwan.tv data, removes duplicates,
assigns proper categories/countries, and outputs Firebase-ready JSON.
"""

import json
import re
import os

# ========== KNOWN CHANNEL LOGOS FROM IPTV-ORG ==========
KNOWN_LOGOS = {
    "rudaw": "https://i.imgur.com/Zo3IWOn.png",
    "kurdistan 24": "https://i.imgur.com/9QWs2ms.png",
    "kurdsat news": "https://i.imgur.com/F3XRwkt.png",
    "kurdsat": "https://i.imgur.com/UAbSwYA.png",
    "kurdistan tv": "https://i.imgur.com/mjQptpc.png",
    "nrt": "https://i.imgur.com/uGKGtbW.png",
    "kurdmax show": "https://i.imgur.com/XTmyGmL.png",
    "kurdmax music": "https://i.imgur.com/otnIdEu.png",
    "kurdmax sorani": "https://i.imgur.com/JkK8HG8.png",
    "zarok": "https://i.imgur.com/o0eevnb.png",
    "channel 8": "https://upload.wikimedia.org/wikipedia/commons/thumb/4/40/Channel8corp.png/480px-Channel8corp.png",
    "al jazeera": "https://i.imgur.com/7bRVpnu.png",
    "al arabiya": "https://i.imgur.com/NXFkYFj.png",
    "al hadath": "https://i.imgur.com/De4SEWE.png",
    "sky news arabia": "https://i.imgur.com/SvjU4h6.png",
    "france 24": "https://upload.wikimedia.org/wikipedia/commons/c/c1/France_24_logo_%282013%29.svg",
    "bbc arabic": "https://i.imgur.com/ScyTG6P.png",
    "dw arabic": "https://i.imgur.com/8MRNFb9.png",
    "mbc 1": "https://i.imgur.com/CiA3plN.png",
    "mbc 2": "https://i.imgur.com/n9mSHuP.png",
    "mbc iraq": "https://i.imgur.com/D0LxiPE.png",
    "mbc drama": "https://i.imgur.com/g5PWnqp.png",
    "al iraqia": "https://i.imgur.com/8qQouTW.jpg",
    "al sharqiya": "https://i.imgur.com/mJIG5IV.png",
    "dijlah": "https://i.imgur.com/kyzH3G5.png",
    "spacetoon": "https://upload.wikimedia.org/wikipedia/en/2/2b/Spacetoon_logo.png",
    "trt kurdi": "https://upload.wikimedia.org/wikipedia/commons/thumb/7/79/TRT_Kurd%C3%AE_logo.svg/512px-TRT_Kurd%C3%AE_logo.svg.png",
    "rojava": "https://i.imgur.com/42noysO.png",
    "ronahi": "https://i.imgur.com/JSvJpD8.png",
    "jin tv": "https://i.imgur.com/37ajg2j.png",
    "komala": "https://i.imgur.com/D6KBLB8.png",
    "payam": "https://i.imgur.com/qySEibb.png",
    "mixkurdy": "https://i.imgur.com/WHeH5i7.png",
    "med muzik": "https://i.imgur.com/gtUFmCD.png",
    "waar": "https://i.imgur.com/rK0y02d.png",
    "zagros": "https://i.imgur.com/9Mspf32.png",
    "sterk": "https://i.imgur.com/bIheNAI.png",
    "shams": "https://www.semakurd.net/sites/default/files/imagecache/400xY/shemis-tv.jpg",
    "alhurra": "https://i.imgur.com/tiQp2sR.png",
    "cnbc arabiya": "https://i.imgur.com/opBXx1K.png",
    "afarin baxcha": "https://afarin.tv/images/baxcha.png",
    "afarin tv": "https://i.imgur.com/6MnXD7g.png",
    "eman tv": "https://i.imgur.com/QmgaPSi.png",
    "farmoda": "https://i.imgur.com/x4KCQlH.png",
    "nubar": "https://i.imgur.com/h4LWHr5.png",
    "nubar plus": "https://i.ibb.co/XpgWb6B/IMG-20230628-140407.jpg",
    "aryen": "https://i.imgur.com/hIhzT7t.png",
    "avar": "https://i.imgur.com/JazFBkW.jpeg",
    "cira": "https://i.imgur.com/YFnmSLW.png",
    "nat geo abu dhabi": "https://i.imgur.com/fNA00VF.png",
    "rt arabic": "https://i.imgur.com/G8vGrn1.png",
    "trt arabi": "https://i.imgur.com/dEfI2M9.png",
    "dubai one": "https://www.dmi.gov.ae/content/dam/corporate/dev/2023/elogos-ar/dubaione-logo.png",
    "abu dhabi sports": "https://i.imgur.com/6BVWk8z.png",
    "4kurd": "https://i.imgur.com/xy7W0wD.png",
}

# ========== COUNTRY CLASSIFICATION ==========
KURDISH_CHANNELS = {
    "rudaw", "kurdistan 24", "kurdsat news", "kurdsat", "kurdistan tv",
    "nrt", "kurdmax show", "kurdmax music", "kurdmax sorani", "zarok",
    "channel 8", "rojava", "ronahi", "jin tv", "komala", "payam",
    "mixkurdy", "med muzik", "waar", "zagros", "sterk", "shams",
    "aryen", "avar", "cira", "nubar", "nubar plus", "afarin baxcha",
    "afarin tv", "eman tv", "farmoda", "trt kurdi", "4kurd",
    # Karwan channels
    "amozhgary academy", "bahra", "ashti family", "aro sport",
    "istar quran", "baboly", "ashna quran", "naqshi media", "asman",
    "shar quran", "azadi", "rozh", "aro family", "aro baby",
    "sima sport", "delal sport", "astera culture", "zhyar", "slemani",
    "shaista", "rasan", "utv duhok", "parwarday duhok", "parwarday hawler",
    "utv hawler", "kurdana", "shna quran", "bnar movies", "barin movies",
    "imk quran", "ld sport chek", "qallam ainy", "qallam geographic",
    "qallam kids", "qallam", "kurdi", "walla", "graryan", "shna",
    "shar", "ala", "soran entertainment", "kurd 7", "welat",
    "ava sport", "afarin radio", "delal", "kurdmax show kurmanci",
    "kurdmax kurmanci", "bnar action", "srusht", "bnar", "kidco",
    "ashti", "speda drama", "reng", "esra 2", "zaxo sport", "zaxo",
    "xabir", "rojhelat", "bmc", "kurdmax pepule", "minara", "ranya",
    "dimen", "astera documentary", "astera baby", "jamawar", "newline 2",
    "newline", "nrt sport", "mmn show", "reng documentary", "astera show",
    "astera family", "sima", "derwaze", "zed plus", "dever", "jojo mama",
    "astera movies", "kani", "mix", "khak", "afarin baxcha", "zed",
    "astera sport", "infinity", "befrin", "esra", "kanal 21", "bask",
    "aso sport", "halabja", "nrt 4", "soz", "bangxwazen badinan",
    "cpi", "nrt 3 kids", "znar", "barin quran", "barin family",
    "euro kurd", "mihrab", "mmn quran", "sada", "alternative shorai",
    "mmn movies", "mmn news", "mmn drama", "duhok sport",
    "mmn documentary", "bangawaz", "4 kurd", "mmn kids", "mmn clip",
    "derin", "duhok", "medya haber", "geli kurdistan", "speda",
    "kurdistan", "nalia 2", "nalia", "rudaw", "ava",
    "kurdistan24", "can", "al yaum", "afarin kids",
    "amozhgary", "kirkuk", "kurd channel", "kurdmax show sorani",
    "cihan", "kurdmax sorani",
}

IRAQI_ARABIC_CHANNELS = {
    "al iraqia", "al iraqia news", "al sharqiya", "al sharqiya news",
    "dijlah", "dijlah tarab", "al rasheed", "mbc iraq", "ishtar",
    "alawla", "alforat", "alhurra iraq", "utv", "al rabiaa",
    "al ahad", "al etejah", "al nujaba", "al janoub", "al eshraq",
    "al ayyam", "inews", "iraq future", "beladi satellite",
    "anwar tv2",
}

def parse_m3u(filepath):
    """Parse M3U file into channel list."""
    channels = []
    if not os.path.exists(filepath):
        print(f"  Skipping {filepath} (not found)")
        return channels
    
    with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
        lines = f.readlines()
    
    i = 0
    while i < len(lines):
        line = lines[i].strip()
        if line.startswith('#EXTINF:'):
            # Parse EXTINF line
            logo_match = re.search(r'tvg-logo="([^"]*)"', line)
            group_match = re.search(r'group-title="([^"]*)"', line)
            name_match = re.search(r',(.+)$', line)
            
            logo = logo_match.group(1) if logo_match else ""
            category = group_match.group(1) if group_match else "General"
            name = name_match.group(1).strip() if name_match else ""
            
            # Clean name - remove quality tags
            name = re.sub(r'\s*\(\d+p\)\s*', ' ', name)
            name = re.sub(r'\s*\[Not 24/7\]\s*', '', name)
            name = re.sub(r'\s*\[Geo-blocked\]\s*', '', name)
            name = name.strip()
            
            # Get URL (skip EXTVLCOPT lines)
            url = ""
            j = i + 1
            while j < len(lines):
                next_line = lines[j].strip()
                if next_line.startswith('#EXTVLCOPT'):
                    j += 1
                    continue
                if next_line and not next_line.startswith('#'):
                    url = next_line
                    break
                j += 1
            
            if name and url and url.startswith('http'):
                # Only keep direct streams (m3u8, mpd), skip page URLs
                if '.m3u8' in url or '.mpd' in url:
                    channels.append({
                        "name": name,
                        "logo": logo,
                        "category": category.split(';')[0] if ';' in category else category,
                        "url": url,
                        "source": os.path.basename(filepath)
                    })
            i = j + 1 if j > i else i + 1
        else:
            i += 1
    
    return channels


def normalize_name(name):
    """Normalize channel name for deduplication."""
    n = name.lower().strip()
    # Remove common suffixes
    n = re.sub(r'\s*(tv|channel|hd|sd|live)\s*', ' ', n)
    n = re.sub(r'\s+', ' ', n).strip()
    return n


def classify_country(channel):
    """Assign country based on channel name."""
    name_lower = channel["name"].lower().strip()
    norm = normalize_name(channel["name"])
    
    # Check Kurdish
    for kc in KURDISH_CHANNELS:
        if kc in name_lower or kc in norm:
            return "KR"  # Kurdistan Region
    
    # Check Iraqi Arabic
    for ic in IRAQI_ARABIC_CHANNELS:
        if ic in name_lower or ic in norm:
            return "IQ"
    
    # Check by known patterns
    if any(x in name_lower for x in ["mbc", "shahid"]):
        return "SA"
    if any(x in name_lower for x in ["dubai", "abu dhabi"]):
        return "AE"
    if "al jazeera" in name_lower or "al araby" in name_lower:
        return "QA"
    if "france 24" in name_lower:
        return "FR"
    if "bbc" in name_lower:
        return "GB"
    if "dw" in name_lower:
        return "DE"
    if "trt" in name_lower:
        return "TR"
    if "rt arabic" in name_lower:
        return "RU"
    if "cgtn" in name_lower:
        return "CN"
    if "sky news" in name_lower:
        return "AE"
    if "cnbc" in name_lower:
        return "AE"
    if "spacetoon" in name_lower:
        return "AE"
    if "roya" in name_lower:
        return "JO"
    if "alhurra" in name_lower:
        return "US"
    if any(x in name_lower for x in ["imam hussein", "imam asr", "marjaeyat", "hadi tv", "beitolabbas", "towheed"]):
        return "IQ"
    
    # Default to IQ for Arabic channels
    return "IQ"


def get_best_logo(name, existing_logo):
    """Get the best logo URL for a channel."""
    name_lower = name.lower().strip()
    
    # Check known logos database
    for key, logo_url in KNOWN_LOGOS.items():
        if key in name_lower:
            return logo_url
    
    # If existing logo is valid (not a generic fallback), keep it
    if existing_logo and existing_logo.startswith('http') and 'karwan.tv/wp-content/uploads/karwan-tv-2' not in existing_logo:
        return existing_logo
    
    return ""


def classify_category(channel):
    """Improve category classification."""
    name_lower = channel["name"].lower()
    cat = channel.get("category", "General")
    
    if cat == "Undefined" or cat == "General":
        if any(x in name_lower for x in ["news", "24", "rudaw", "nrt"]):
            return "News"
        if any(x in name_lower for x in ["sport"]):
            return "Sports"
        if any(x in name_lower for x in ["kid", "baby", "zarok", "afarin", "spacetoon"]):
            return "Kids"
        if any(x in name_lower for x in ["music", "muzik", "tarab", "clip"]):
            return "Music"
        if any(x in name_lower for x in ["quran", "iman", "eman", "religious", "imam", "hadi", "mihrab", "ashti", "bangawaz", "farmoda"]):
            return "Religious"
        if any(x in name_lower for x in ["movie", "film", "drama", "show", "action"]):
            return "Movies"
        if any(x in name_lower for x in ["document"]):
            return "Documentary"
        return "Entertainment"
    
    # Normalize category names
    cat_map = {
        "Religion": "Religious",
        "Family": "Entertainment",
        "Culture": "Entertainment",
        "Education": "Education",
        "Business": "News",
        "Comedy": "Entertainment",
        "Animation": "Kids",
        "Series": "Movies",
    }
    return cat_map.get(cat, cat)


def main():
    print("=" * 60)
    print("Building Live TV Channel Database")
    print("=" * 60)
    
    all_channels = []
    
    # 1. Parse M3U files
    print("\n[1] Parsing M3U files...")
    
    for m3u_file in ["kur.m3u", "iq.m3u"]:
        path = os.path.join(os.path.dirname(__file__), m3u_file)
        channels = parse_m3u(path)
        print(f"  {m3u_file}: {len(channels)} channels")
        all_channels.extend(channels)
    
    # 2. Load karwan.tv data from Firebase export
    print("\n[2] Loading karwan.tv channels...")
    karwan_file = os.path.join(os.path.dirname(__file__), "channels_firebase_karwan_full.json")
    if os.path.exists(karwan_file):
        with open(karwan_file, 'r', encoding='utf-8') as f:
            karwan_data = json.load(f)
        # Handle both dict (numbered keys) and list format
        if isinstance(karwan_data, dict):
            karwan_channels = list(karwan_data.values())
        else:
            karwan_channels = karwan_data
        count = 0
        for kc in karwan_channels:
            if isinstance(kc, dict):
                all_channels.append({
                    "name": kc.get("name", ""),
                    "logo": kc.get("logo", ""),
                    "category": kc.get("category", "Entertainment"),
                    "url": kc.get("url", ""),
                    "source": "karwan.tv"
                })
                count += 1
        print(f"  karwan.tv: {count} channels")
    
    print(f"\n  Total raw channels: {len(all_channels)}")
    
    # 3. Deduplicate
    print("\n[3] Deduplicating...")
    seen = {}  # normalized_name -> best channel
    
    for ch in all_channels:
        if not ch["name"] or not ch["url"]:
            continue
        
        norm = normalize_name(ch["name"])
        
        if norm in seen:
            existing = seen[norm]
            # Prefer direct HLS streams over karwan.tv page URLs
            if '.m3u8' in ch["url"] and '.m3u8' not in existing["url"]:
                # Replace karwan page URL with direct stream
                existing["url"] = ch["url"]
            # Prefer higher quality logo
            better_logo = get_best_logo(ch["name"], ch["logo"])
            if better_logo and (not existing["logo"] or 'karwan-tv-2' in existing.get("logo", "")):
                existing["logo"] = better_logo
        else:
            seen[norm] = ch
    
    unique_channels = list(seen.values())
    print(f"  Unique channels: {len(unique_channels)}")
    
    # 4. Classify and enhance
    print("\n[4] Classifying channels...")
    final_channels = []
    
    for ch in unique_channels:
        country = classify_country(ch)
        category = classify_category(ch)
        logo = get_best_logo(ch["name"], ch["logo"])
        
        final_channels.append({
            "name": ch["name"],
            "logo": logo if logo else ch.get("logo", ""),
            "category": category,
            "url": ch["url"],
            "country": country,
            "description": f"{category} channel"
        })
    
    # 5. Sort: Kurdish first, then Iraqi, then others
    country_priority = {"KR": 0, "IQ": 1, "SA": 2, "AE": 3, "QA": 4, "TR": 5}
    final_channels.sort(key=lambda c: (
        country_priority.get(c["country"], 99),
        c["category"],
        c["name"]
    ))
    
    # 6. Stats
    print("\n[5] Channel Statistics:")
    countries = {}
    categories = {}
    for ch in final_channels:
        countries[ch["country"]] = countries.get(ch["country"], 0) + 1
        categories[ch["category"]] = categories.get(ch["category"], 0) + 1
    
    print("  By Country:")
    for k, v in sorted(countries.items(), key=lambda x: -x[1]):
        print(f"    {k}: {v}")
    
    print("  By Category:")
    for k, v in sorted(categories.items(), key=lambda x: -x[1]):
        print(f"    {k}: {v}")
    
    # 7. Separate direct streams from karwan page URLs
    direct_streams = [ch for ch in final_channels if '.m3u8' in ch["url"] or '.mpd' in ch["url"]]
    karwan_pages = [ch for ch in final_channels if 'karwan.tv/' in ch["url"] and '.m3u8' not in ch["url"]]
    
    print(f"\n  Direct streams: {len(direct_streams)}")
    print(f"  Karwan page URLs (need scraper): {len(karwan_pages)}")
    
    # 8. Save outputs
    output_file = os.path.join(os.path.dirname(__file__), "channels_final.json")
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(final_channels, f, ensure_ascii=False, indent=2)
    print(f"\n  Saved all channels to {output_file}")
    
    # Save Firebase-ready format (numbered keys)
    firebase_data = {}
    for i, ch in enumerate(final_channels):
        firebase_data[str(i)] = ch
    
    firebase_file = os.path.join(os.path.dirname(__file__), "channels_firebase_final.json")
    with open(firebase_file, 'w', encoding='utf-8') as f:
        json.dump(firebase_data, f, ensure_ascii=False, indent=2)
    print(f"  Saved Firebase format to {firebase_file}")
    
    print(f"\n{'='*60}")
    print(f"DONE! Total channels: {len(final_channels)}")
    print(f"{'='*60}")


if __name__ == "__main__":
    main()
