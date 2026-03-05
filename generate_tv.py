import re
import json

m3u_file = 'd:/TV App/app/src/main/assets/kurdish_channels.m3u'

def get_category(name):
    name = name.upper()
    if 'NEWS' in name or 'K24' in name or 'RUDAW' in name or 'NRT 1' in name or 'NRT 2' in name or 'PAYAM' in name or 'SPEDA' in name or 'KNN' in name or 'STERK' in name or 'RONAHI' in name or 'CHANNEL 8' in name:
        if 'DOCUMENTARY' not in name:
            return 'News'
    if 'SPORT' in name or 'KORA' in name:
        return 'Sports'
    if 'MUSIC' in name or 'MUZIK' in name:
        return 'Music'
    if 'AMOZHGARY' in name or 'QURAN' in name or 'ISLAM' in name or 'NRT 4' in name or 'ASHTI' in name or 'EMAN' in name or 'BANGAWAZ' in name or 'ARIFAN' in name or 'MIHRAB' in name or 'TASAWAF' in name:
        return 'Religion'
    if 'MOVIE' in name or 'CINEMA' in name or 'FILM' in name:
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
    return 'Entertainment'  # Default for Kurdmax, Waar, etc.

channels = []
with open(m3u_file, 'r', encoding='utf-8') as f:
    lines = f.readlines()

current_name = None
current_logo = "https://i.imgur.com/uGKGtbW.png"  # default
# mapping some well known logos if we don't have them
logos = {
    'K24': 'https://upload.wikimedia.org/wikipedia/commons/thumb/c/ca/Kurdistan24_Logo.jpg/220px-Kurdistan24_Logo.jpg',
    'RUDAW': 'https://upload.wikimedia.org/wikipedia/commons/e/ec/Rudaw_Media_Network_Logo.png',
    'NRT': 'https://upload.wikimedia.org/wikipedia/commons/1/1a/NRT_TV_logo.png',
    'AVA': 'https://i.imgur.com/XkMyXbQ.png',  # Random fallback
    'WAAR': 'https://i.imgur.com/QhMxw.png',
    'KURDMAX': 'https://i.imgur.com/XTmyGmL.png',
    'ZAGROS': 'https://upload.wikimedia.org/wikipedia/en/2/22/Zagros_TV_Logo.jpg',
    'CHANNEL 8': 'https://i.imgur.com/vHqQ.png',
}

added_names = set()

for line in lines:
    line = line.strip()
    if line.startswith('#EXTINF'):
        parts = line.split(',')
        if len(parts) > 1:
            name = parts[-1].replace('KURD ?', '').strip()
            # Clean up the name a bit
            current_name = name
    elif line.startswith('http'):
        if current_name and current_name not in added_names:
            cat = get_category(current_name)
            logo = current_logo
            for k, v in logos.items():
                if k in current_name.upper():
                    logo = v
                    break
            
            channels.append({
                "name": current_name,
                "url": line,
                "category": cat,
                "logo": logo,
                "description": f"{cat} channel from Kurdistan"
            })
            added_names.add(current_name)
            current_name = None

db_obj = {}
for i, c in enumerate(channels):
    db_obj[str(i)] = c

with open('d:/TV App/channels.json', 'w', encoding='utf-8') as f:
    json.dump(db_obj, f, ensure_ascii=False, indent=2)
print("Channels generated:", len(channels))
