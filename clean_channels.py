import json
from collections import OrderedDict

channels_json = json.load(open('d:/TV App/channels_min.json', encoding='utf-8'))
clean_channels = OrderedDict()
added = set()
idx = 0

for k, c in channels_json.items():
    name = c['name'].strip()
    # Clean names
    name = name.replace('FHD', '').replace(' HD', '').replace(' SD', '').replace(' TV', '')
    name = name.strip()
    
    if name not in added:
        added.add(name)
        c['name'] = name
        clean_channels[str(idx)] = c
        idx += 1

print("Total channels after deduplication:", len(clean_channels))
with open('d:/TV App/channels_firebase.json', 'w', encoding='utf-8') as f:
    json.dump(clean_channels, f, ensure_ascii=False)
