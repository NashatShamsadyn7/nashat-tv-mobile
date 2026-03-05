import re
import json
from bs4 import BeautifulSoup
import urllib.parse

html = open('d:/TV App/karwan.html', encoding='utf-8').read()

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

soup = BeautifulSoup(html, 'html.parser')
channels = {}

for a in soup.find_all('a'):
    href = a.get('href')
    # karwan channels usually end with a slash and are subdirectories of karwan.tv
    if href and 'karwan.tv/' in href and href != 'https://karwan.tv/' and href != 'https://karwan.tv/channels/':
        # the ones with channel streams usually have an image inside
        img = a.find('img')
        if img:
            logo = img.get('data-src') or img.get('data-lazy-src') or img.get('src')
            if not logo: continue
            if not logo.startswith('http'): logo = urllib.parse.urljoin("https://karwan.tv/", logo)
            
            name = img.get('alt', '').strip()
            
            # If still no name, check next sibling or parent
            if not name:
                # Look inside the text of the link
                name = a.text.strip()
            
            if not name:
                # Check surrounding h3
                parent = a.find_parent()
                if parent:
                    h3 = parent.find('h3')
                    if h3:
                        name = h3.text.strip()
            
            # Clean name
            name = re.sub(r'(?i)\bviews\b.*', '', name).strip()
            name = re.sub(r'[\d\.]+[KkMm]\b', '', name).strip()
            name = re.sub(r'(?i)\blive\b', '', name).strip()
            name = re.sub(r'(?i)\btv\b', '', name).strip()
            
            key = name.lower()
            if len(name) > 2 and 'logo' not in key and 'search' not in key and 'karwan' not in key:
                if key not in channels:
                    channels[key] = {
                        "name": name,
                        "url": href,
                        "logo": logo,
                        "category": get_category(name),
                        "description": f"{get_category(name)} channel from Kurdistan"
                    }

final_db = {}
i = 0
for v in channels.values():
    final_db[str(i)] = v
    i += 1

print("Found channels:", len(final_db))
with open('d:/TV App/channels_firebase_karwan.json', 'w', encoding='utf-8') as f:
    json.dump(final_db, f, indent=2, ensure_ascii=False)
