"""Add high-quality Kurdish channels via karwan.tv (handled by KarwanScraper in app)."""
import json

KARWAN_CHANNELS = [
    # (name, karwan_url, category, logo)
    ("NRT TV HD", "https://karwan.tv/nrt/", "أخبار",
     "https://upload.wikimedia.org/wikipedia/en/d/d6/NRT_TV_logo.png"),
    ("NRT 2", "https://karwan.tv/nrt-2/", "أخبار",
     "https://upload.wikimedia.org/wikipedia/en/d/d6/NRT_TV_logo.png"),
    ("KurdMax Sorani", "https://karwan.tv/kurdmax-tv/", "كوردية",
     "https://i.imgur.com/JkK8HG8.png"),
    ("KurdMax Show", "https://karwan.tv/kurdmax-show/", "كوردية",
     "https://i.imgur.com/XTmyGmL.png"),
    ("KurdMax Music", "https://karwan.tv/kurdmax-music/", "موسيقى",
     "https://i.imgur.com/otnIdEu.png"),
    ("KurdMax Pepule", "https://karwan.tv/kurdmax-pepule/", "أطفال",
     "https://i.imgur.com/XTmyGmL.png"),
    ("Rudaw TV", "https://karwan.tv/rudaw/", "أخبار",
     "https://i.imgur.com/Zo3IWOn.png"),
    ("Kurdistan 24", "https://karwan.tv/kurdistan24/", "أخبار",
     "https://i.imgur.com/9QWs2ms.png"),
    ("Kurdsat HD", "https://karwan.tv/kurdsat/", "كوردية",
     "https://i.imgur.com/UAbSwYA.png"),
    ("Kurdsat News", "https://karwan.tv/kurdsat-news/", "أخبار",
     "https://i.imgur.com/F3XRwkt.png"),
    ("Kurdistan TV", "https://karwan.tv/kurdistan-tv/", "كوردية",
     "https://i.imgur.com/mjQptpc.png"),
    ("Waar TV", "https://karwan.tv/waar/", "عام",
     "https://i.imgur.com/rK0y02d.png"),
    ("Zagros TV", "https://karwan.tv/zagros/", "كوردية",
     "https://i.imgur.com/zagros.png"),
    ("Sterk TV", "https://karwan.tv/sterk-tv/", "كوردية",
     "https://i.imgur.com/sterk.png"),
]


def slugify(name, idx):
    import re
    s = re.sub(r'[^a-zA-Z0-9_]', '_', name).lower()
    s = re.sub(r'_+', '_', s).strip('_')
    return f"{s}_karwan_{idx}"


def main():
    data = json.load(open("d:/TV App/scripts/channels_for_firebase.json", encoding="utf-8"))
    before = len(data)
    for idx, (name, url, category, logo) in enumerate(KARWAN_CHANNELS, start=1):
        key = slugify(name, idx)
        data[key] = {
            "name": name,
            "logo": logo,
            "logoUrl": logo,
            "category": category,
            "url": url,
            "streamUrl": url,
            "description": "كوردستان — Karwan TV",
            "country": "IQ",
            "language": "ku",
            "isAlive": True,
            "source": "karwan.tv",
        }
    print(f"Added {len(KARWAN_CHANNELS)} Karwan-scraped channels. Total: {before} → {len(data)}")
    with open("d:/TV App/scripts/channels_for_firebase.json", "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    main()
