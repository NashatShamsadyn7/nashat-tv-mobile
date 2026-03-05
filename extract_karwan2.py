import json
from bs4 import BeautifulSoup
import urllib.parse
import re

html = open('d:/TV App/karwan.html', encoding='utf-8').read()
soup = BeautifulSoup(html, 'html.parser')

categories = []
for h2 in soup.find_all('h2'):
    block = h2.find_parent('div', class_='wp-block-group')
    if block:
        h2 = block.find('h2')
    cat_name = h2.text.strip()
    if not cat_name or "Most viewed" in cat_name or "Popular" in cat_name:
        continue
        
    for a in soup.find_all('a'):
        href = a.get('href')
        if href and 'karwan.tv' in href and a.find('img'):
            name = a.text.strip()
            img = a.find('img')
            
            logo = img.get('data-src') or img.get('data-lazy-src') or img.get('src')
            if not logo:
                logo = "https://i.imgur.com/uGKGtbW.png"
            elif not logo.startswith('http'):
                logo = urllib.parse.urljoin("https://karwan.tv/", logo)
                
            if not name and img.get('alt'):
                name = img.get('alt').strip()
            
            # Since karwan puts link everywhere, let's regex clean the name
            name = re.sub(r'(?i)\bviews\b.*', '', name).strip()
            name = re.sub(r'[\d\.]+[KkMm]\b', '', name).strip()
            name = re.sub(r'(?i)\blive\b', '', name).strip()
            name = re.sub(r'(?i)\btv\b', '', name).strip()
            
            if name and len(name) > 1 and 'facebook' not in href and 'twitter' not in href:
                if not any(c['url'] == href for c in categories):
                    categories.append({
                        "name": name,
                        "url": href,
                        "logo": logo,
                        "category": "Kurdish TV", # Defaulting all to Kurdish TV unless we find a better way
                        "description": "Kurdish channel from Karwan.tv"
                    })

print("Found channels:", len(categories))
with open('d:/TV App/scrape_targets2.json', 'w', encoding='utf-8') as f:
    json.dump(categories, f, indent=2)
