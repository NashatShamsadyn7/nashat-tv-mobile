import asyncio
import json
from playwright.async_api import async_playwright
import urllib.parse

async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        page = await browser.new_page()
        
        reqs = []
        page.on("request", lambda req: reqs.append(req.url))
        
        print("Visiting astera-sport-tv")
        # Go to karwan.tv
        await page.goto("https://karwan.tv/astera-sport-tv/")
        await page.wait_for_timeout(2000)
        
        iframe_el = await page.query_selector("iframe")
        if iframe_el:
            src = await iframe_el.get_attribute("src")
            print("Found iframe: ", src)
            if src:
                src = urllib.parse.urljoin("https://karwan.tv/astera-sport-tv/", src)
                print("Absolute URL: ", src)
                embed_page = await browser.new_page()
                embed_page.on("request", lambda req: reqs.append(req.url))
                await embed_page.goto(src)
                await embed_page.wait_for_timeout(5000)
                await embed_page.close()
                print("Finished checking iframe requests")

        for r in reqs:
            if '.m3u8' in r:
                print("M3U8 Found:", r)

        await browser.close()

if __name__ == "__main__":
    asyncio.run(main())
