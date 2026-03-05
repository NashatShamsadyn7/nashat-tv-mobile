import urllib.request
try:
    req = urllib.request.Request('https://karwan.tv/', headers={'User-Agent': 'Mozilla/5.0'})
    html = urllib.request.urlopen(req).read().decode('utf-8')
    with open('d:/TV App/karwan.html', 'w', encoding='utf-8') as f:
        f.write(html)
except Exception as e:
    print(e)
