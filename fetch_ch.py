import urllib.request
req = urllib.request.Request(
    'https://karwan.tv/embed/179/', 
    data=None, 
    headers={
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'
    }
)
try:
    html = urllib.request.urlopen(req).read().decode("utf-8")
    with open("d:/TV App/embed.html", "w", encoding="utf-8") as f:
        f.write(html)
except Exception as e:
    print(str(e))
