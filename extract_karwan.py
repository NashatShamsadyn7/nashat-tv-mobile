import json
from bs4 import BeautifulSoup
import urllib.parse
import re

html = open('d:/TV App/karwan.html', encoding='utf-8').read()
soup = BeautifulSoup(html, 'html.parser')

categories = []
for h2 in soup.find_all('h2'):
    cat_name = h2.text.strip()
    if "Most viewed" in cat_name or "Popular" in cat_name:
        continue

    # Find the next div with class row or similar container
    sibling_div = h2.find_next_sibling('div')
    if sibling_div:
        items = sibling_div.find_all(class_='item')
        if not items:
            items = sibling_div.find_all(class_='post')
        
        for item in items:
            a_tag = item.find('h3').find('a') if item.find('h3') else None
            if not a_tag:
                # Try finding any link that contains the channel
                a_tags = item.find_all('a')
                for a in a_tags:
                    if 'karwan.tv' in a.get('href', ''):
                        a_tag = a
                        break
            
            if a_tag:
                href = a_tag.get('href')
                name = a_tag.text.strip()
                
                img = item.find('img')
                # Check for lazy loaded images
                logo = ""
                if img:
                    logo = img.get('data-src') or img.get('data-lazy-src') or img.get('src')
                
                if not logo:
                    logo = "https://i.imgur.com/uGKGtbW.png"
                else:
                    logo = urllib.parse.urljoin("https://karwan.tv/", logo)
                
                if not name and img and img.get('alt'):
                    name = img.get('alt').strip()
                
                name = re.sub(r'(?i)\blive\b', '', name).strip()
                name = re.sub(r'(?i)\btv\b', '', name).strip()
                
                # Check if this channel is already in the list
                if not any(c['url'] == href for c in categories):
                    categories.append({
                        "name": name,
                        "url": href,
                        "logo": logo,
                        "category": cat_name,
                        "description": f"{cat_name} channel from Kurdistan"
                    })

print("Found channels:", len(categories))
with open('d:/TV App/scrape_targets.json', 'w', encoding='utf-8') as f:
    json.dump(categories, f, indent=2)
