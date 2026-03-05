import urllib.request
import re
import urllib.parse

def extract_stream(url):
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)', 'Referer': 'https://karwan.tv/'})
        html = urllib.request.urlopen(req).read().decode('utf-8')
        
        # find iframe
        match = re.search(r'<iframe[^>]+src=["\']([^"\']+)["\']', html)
        if not match:
            return "No iframe found"
        
        iframe_src = match.group(1)
        if not iframe_src.startswith('http'):
            iframe_src = urllib.parse.urljoin(url, iframe_src)
        
        # fetch iframe
        req2 = urllib.request.Request(iframe_src, headers={'User-Agent': 'Mozilla/5.0', 'Referer': url})
        html2 = urllib.request.urlopen(req2).read().decode('utf-8')
        
        # find stream url
        stream_match = re.search(r"(?:dash|hls)\s*:\s*[\"']([^\"\']+)[\"']", html2)
        if stream_match:
            return stream_match.group(1)
            
        print("HTML2 snippet:", html2[:200])
        return "Not found"
    except Exception as e:
        return str(e)

print(extract_stream("https://karwan.tv/astera-sport-tv/"))
