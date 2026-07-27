import json
import urllib.request
import ssl
import sys

def main():
    db_url = "https://nashat-tv-default-rtdb.asia-southeast1.firebasedatabase.app"
    
    # Configure SSL bypass for urllib (some systems might have root CA issues)
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE

    print("Step 1: Fetching current movies from Firebase to preserve them...")
    movies_url = f"{db_url}/movies.json"
    try:
        req = urllib.request.Request(movies_url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, context=ctx, timeout=15) as r:
            movies_data = json.loads(r.read().decode('utf-8'))
            print("Successfully fetched current movies.")
            print(f"Movies node type: {type(movies_data)}")
            if isinstance(movies_data, list):
                print(f"Count: {len(movies_data)} items (including nulls)")
            elif isinstance(movies_data, dict):
                print(f"Count: {len(movies_data)} keyed items")
            else:
                print("Movies node is empty or null")
    except Exception as e:
        print(f"Error fetching movies: {e}")
        print("Cannot proceed without preserving movies. Exiting.")
        sys.exit(1)

    print("\nStep 2: Reading and parsing channels_for_firebase.json...")
    filepath = "d:/TV App/scripts/channels_for_firebase.json"
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            all_channels_data = json.load(f)
    except Exception as e:
        print(f"Error reading channels file: {e}")
        sys.exit(1)

    # Filter to only source = karwan.tv
    karwan_channels_list = []
    
    # Category translation map from Arabic to English
    category_map = {
        "أخبار": "News",
        "رياضة": "Sports",
        "أفلام": "Movies",
        "مسلسلات": "Series",
        "أطفال": "Kids",
        "موسيقى": "Music",
        "كوردية": "Kurdish",
        "إسلامية": "Religion",
        "وثائقية": "Documentary",
        "عام": "General"
    }

    # Sort channels by key to keep a consistent order
    sorted_keys = sorted(all_channels_data.keys())
    
    for key in sorted_keys:
        val = all_channels_data[key]
        if val.get("source") == "karwan.tv":
            # Translate category
            arabic_cat = val.get("category", "عام")
            english_cat = category_map.get(arabic_cat, "General")
            
            channel_obj = {
                "id": key,
                "name": val.get("name"),
                "logo": val.get("logo"),
                "logoUrl": val.get("logoUrl", val.get("logo")),
                "category": english_cat,
                "url": val.get("url"),
                "streamUrl": val.get("streamUrl", val.get("url")),
                "description": val.get("description", "Karwan TV"),
                "country": val.get("country", "IQ"),
                "language": val.get("language", "ku"),
                "ping": val.get("ping", 0),
                "loadSpeed": val.get("loadSpeed", 0),
                "isAlive": True,
                "lastChecked": val.get("lastChecked", ""),
                "source": "karwan.tv"
            }
            karwan_channels_list.append(channel_obj)

    print(f"Filtered {len(karwan_channels_list)} Karwan TV channels.")

    print("\nStep 3: Constructing final database payload...")
    # Create the channels map
    channels_map = {}
    for ch in karwan_channels_list:
        channels_map[ch["id"]] = ch

    # Create root payload
    payload = {
        "movies": movies_data,
        "channels": channels_map,
        "live_tv_channels": channels_map
    }

    # Add numeric keys 0 to 175 at the root level
    for idx, ch in enumerate(karwan_channels_list):
        payload[str(idx)] = ch

    print("Payload statistics:")
    print(f"  - '/movies': {type(movies_data)}")
    print(f"  - '/channels': {len(channels_map)} items")
    print(f"  - '/live_tv_channels': {len(channels_map)} items")
    print(f"  - Root numeric keys: {len(karwan_channels_list)} items (0 to {len(karwan_channels_list)-1})")

    # Double check if any old numeric keys above the limit could remain
    # Using PUT on the root ('/') replaces the entire database content.
    # Therefore, any key that is not in our payload will be deleted automatically.
    # This is exactly what we want!

    print("\nStep 4: Writing atomic PUT payload to root of Firebase...")
    root_url = f"{db_url}/.json"
    
    # Serialize payload to JSON
    json_data = json.dumps(payload, ensure_ascii=False).encode('utf-8')
    
    try:
        req = urllib.request.Request(
            root_url,
            data=json_data,
            headers={
                'Content-Type': 'application/json',
                'User-Agent': 'Mozilla/5.0'
            },
            method='PUT'
        )
        with urllib.request.urlopen(req, context=ctx, timeout=30) as r:
            response_code = r.getcode()
            response_content = r.read().decode('utf-8')
            print(f"✓ PUT request completed with response code: {response_code}")
            print("✓ Database successfully wiped and updated!")
    except Exception as e:
        print(f"Error uploading payload: {e}")
        sys.exit(1)

    print("\nStep 5: Verifying root keys of the database...")
    shallow_url = f"{db_url}/.json?shallow=true"
    try:
        req = urllib.request.Request(shallow_url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, context=ctx, timeout=10) as r:
            keys_data = json.loads(r.read().decode('utf-8'))
            keys = list(keys_data.keys())
            print(f"Current root keys in DB: {len(keys)}")
            
            # Group into numeric vs non-numeric
            num_keys = []
            str_keys = []
            for k in keys:
                if k.isdigit():
                    num_keys.append(int(k))
                else:
                    str_keys.append(k)
            print(f"Non-numeric keys: {str_keys}")
            print(f"Numeric keys: min={min(num_keys) if num_keys else None}, max={max(num_keys) if num_keys else None}, total={len(num_keys)}")
            
            if 'movies' in str_keys and 'channels' in str_keys and 'live_tv_channels' in str_keys and len(num_keys) == len(karwan_channels_list):
                print("\n🎉 VERIFICATION SUCCESSFUL! Database structure is exactly as expected.")
            else:
                print("\n⚠️ WARNING: Database structure might not match expectations. Check root keys.")
    except Exception as e:
        print(f"Error verifying database: {e}")

if __name__ == '__main__':
    main()
