import asyncio
import json
import re
from playwright.async_api import async_playwright

async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        page = await browser.new_page()
        
        # Go to karwan.tv
        await page.goto("https://karwan.tv/")
        
        channels = []
        # Find all anchor tags within post-content
        items = await page.query_selector_all(".post-content a")
        
        print(f"Found {len(items)} channel links on homepage.")
        
        links = []
        for item in items:
            href = await item.get_attribute("href")
            img = await item.query_selector("img")
            name = await item.text_content()
            if img:
                logo = await img.get_attribute("src")
                name = await img.get_attribute("alt") or name
            else:
                logo = ""
            name = name.strip() if name else ""
            if href and 'karwan.tv' in href and name:
                links.append({"name": name, "url": href, "logo": logo})
        
        print(f"Extracted {len(links)} channels to process.")
        
        # Process a subset or all, but let's try first 3 as a test
        # We'll visit each and intercept m3u8
        
        # To avoid hanging, let's just use the M3U matching if possible, unless the m3u8 is not in the html.
        # Wait, earlier grep failed to find m3u8 in the html. But wait, karwan.tv loads iframe from karwan.tv/embed/...
        
        results = []
        for l in links[:5]:
            await page.goto(l['url'])
            # The player is probably in an iframe like <iframe src="https://karwan.tv/embed/...
            iframe_el = await page.query_selector("iframe.vp-embed-2")
            m3u8_url = ""
            if iframe_el:
                src = await iframe_el.get_attribute("src")
                if src:
                    # Visit embed page and wait for m3u8 network request
                    embed_page = await browser.new_page()
                    
                    reqs = []
                    embed_page.on("request", lambda req: reqs.append(req.url))
                    await embed_page.goto(src)
                    await embed_page.wait_for_timeout(2000) # wait for playlist request
                    
                    for r in reqs:
                        if '.m3u8' in r:
                            m3u8_url = r
                            break
                    await embed_page.close()
            
            l['m3u8'] = m3u8_url
            results.append(l)
            print(f"Prop: {l['name']} -> {m3u8_url}")

        await browser.close()

if __name__ == "__main__":
    asyncio.run(main())
