import urllib.request
import json
import re
import urllib.parse

def get_category(name):
    name = name.upper()
    if 'NEWS' in name or 'K24' in name or 'RUDAW' in name or 'NRT 1' in name or 'NRT 2' in name or 'PAYAM' in name or 'SPEDA' in name or 'KNN' in name or 'STERK' in name or 'RONAHI' in name or 'CHANNEL 8' in name:
        if 'DOCUMENTARY' not in name:
            return 'News'
    if 'SPORT' in name or 'KORA' in name or 'YARI' in name:
        return 'Sports'
    if 'MUSIC' in name or 'MUZIK' in name:
        return 'Music'
    if 'AMOZHGARY' in name or 'QURAN' in name or 'ISLAM' in name or 'NRT 4' in name or 'ASHTI' in name or 'EMAN' in name or 'BANGAWAZ' in name or 'ARIFAN' in name or 'MIHRAB' in name or 'TASAWAF' in name:
        return 'Religion'
    if 'MOVIE' in name or 'CINEMA' in name or 'FILM' in name or 'MAX' in name:
        return 'Movies'
    if 'KIDS' in name or 'PEPULE' in name or 'ZAROK' in name or 'PELISTANK' in name or 'BABY' in name or 'AFARIN' in name or 'KIDCO' in name:
        return 'Kids'
    if 'DOC' in name:
        return 'Documentary'
    if 'CULTURE' in name:
        return 'Culture'
    if 'EDUCATION' in name or 'PARWARDA' in name:
        return 'Education'
    if 'COOK' in name or 'FOOD' in name:
        return 'Cooking'
    return 'Entertainment'

channels = {}
page = 1
while True:
    try:
        url = f'https://karwan.tv/wp-json/wp/v2/posts?per_page=100&page={page}'
        print(f"Fetching {url}")
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        html = urllib.request.urlopen(req).read()
        posts = json.loads(html)
        if not posts:
            break
            
        for p in posts:
            name = p['title']['rendered']
            # Decode HTML entities
            import html as html_lib
            name = html_lib.unescape(name)
            
            link = p['link']
            # Get featured image if exists
            logo = "https://i.imgur.com/uGKGtbW.png"
            if p.get('yoast_head_json', {}).get('og_image'):
                logo = p['yoast_head_json']['og_image'][0]['url']
                
            name = re.sub(r'(?i)\blive\b', '', name).strip()
            name = re.sub(r'(?i)\btv\b', '', name).strip()
            
            key = name.lower()
            if len(name) > 2 and 'logo' not in key and 'search' not in key:
                if key not in channels:
                    channels[key] = {
                        "name": name,
                        "url": link,
                        "logo": logo,
                        "category": get_category(name),
                        "description": f"{get_category(name)} channel from Kurdistan"
                    }
        page += 1
    except Exception as e:
        print(e)
        break

final_db = {}
i = 0
for v in channels.values():
    final_db[str(i)] = v
    i += 1

print("Found channels:", len(final_db))
with open('d:/TV App/channels_firebase_karwan_full.json', 'w', encoding='utf-8') as f:
    json.dump(final_db, f, indent=2, ensure_ascii=False)
