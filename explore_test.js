const fs = require('fs');
const http = require('http');
const https = require('https');

const M3U_URLS = [
    // Kurdish
    "https://iptv-org.github.io/iptv/languages/kur.m3u",
    // Iraq
    "https://iptv-org.github.io/iptv/countries/iq.m3u",
    // Arabic (just a sample, otherwise it's 1000s of channels)
    "https://iptv-org.github.io/iptv/languages/ara.m3u",
    // Global Free TV
    "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8"
];

// Read existing working channels
const existingFile = 'd:/TV App/firebase_working.json';
let existingChannels = [];
if (fs.existsSync(existingFile)) {
    existingChannels = JSON.parse(fs.readFileSync(existingFile, 'utf8'));
}

const existingUrls = new Set(existingChannels.map(c => c.url));

async function fetchM3u(url) {
    return new Promise((resolve, reject) => {
        const req = url.startsWith('https') ? https.get(url) : http.get(url);
        req.on('response', (res) => {
            if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
                return resolve(fetchM3u(res.headers.location));
            }
            if (res.statusCode !== 200) return resolve("");
            let data = "";
            res.on('data', chunk => data += chunk);
            res.on('end', () => resolve(data));
        }).on('error', () => resolve(""));
    });
}

async function testStream(url) {
    return new Promise((resolve) => {
        const client = url.startsWith('https') ? https : http;
        const req = client.get(url, { timeout: 4000 }, (res) => {
            if (res.statusCode >= 200 && res.statusCode < 400) {
                resolve(true); // Working
            } else {
                resolve(false);
            }
            req.abort();
        });
        req.on('error', () => resolve(false));
        req.on('timeout', () => { req.abort(); resolve(false); });
    });
}

function parseM3u(data, categoryName) {
    const lines = data.split(/\r?\n/);
    const channels = [];
    let currentName = "";
    let logo = "";
    
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim();
        if (line.startsWith("#EXTINF:")) {
            const nameMatch = line.match(/tvg-name="([^"]+)"/) || line.match(/,(.+)$/);
            const logoMatch = line.match(/tvg-logo="([^"]+)"/);
            
            currentName = nameMatch ? (nameMatch[1] || nameMatch[0].replace(',', '')) : `Channel ${channels.length+1}`;
            logo = logoMatch ? logoMatch[1] : "";
            
            // Clean up name
            currentName = currentName.trim().replace(/^,/, '').trim();
        } else if (line.startsWith("http")) {
            if (!existingUrls.has(line)) {
                channels.push({
                    name: currentName || 'Unknown Channel',
                    logo: logo,
                    category: categoryName,
                    url: line,
                    description: "Discovered from Explore section"
                });
            }
            currentName = "";
            logo = "";
        }
    }
    return channels;
}

async function run() {
    console.log("Fetching M3U playlists...");
    let newChannels = [];
    
    // Parse all M3Us
    const kurData = await fetchM3u(M3U_URLS[0]);
    newChannels.push(...parseM3u(kurData, "Kurdish"));
    
    const iqData = await fetchM3u(M3U_URLS[1]);
    newChannels.push(...parseM3u(iqData, "Iraq"));
    
    // To save time, we'll just take the first 100 unique from Arabic and Free-TV
    const araData = await fetchM3u(M3U_URLS[2]);
    const araCh = parseM3u(araData, "Arabic").slice(0, 100);
    newChannels.push(...araCh);
    
    const freeData = await fetchM3u(M3U_URLS[3]);
    const freeCh = parseM3u(freeData, "Global").slice(0, 100);
    newChannels.push(...freeCh);

    // Limit to max 300 to test quickly (8 seconds per batch of 50)
    newChannels = newChannels.slice(0, 300);
    console.log(`Found ${newChannels.length} new unique channels to test (limited to 300 for speed)...`);
    
    const workingNewChannels = [];
    
    // Test in batches of 30 to not overwhelm the network
    const BATCH_SIZE = 30;
    for (let i = 0; i < newChannels.length; i += BATCH_SIZE) {
        const batch = newChannels.slice(i, i + BATCH_SIZE);
        console.log(`Testing batch ${i/BATCH_SIZE + 1} of ${Math.ceil(newChannels.length/BATCH_SIZE)}...`);
        
        const results = await Promise.all(batch.map(async (ch) => {
            const isWorking = await testStream(ch.url);
            if (isWorking) {
                workingNewChannels.push(ch);
                process.stdout.write('🟢');
            } else {
                process.stdout.write('❌');
            }
            return isWorking;
        }));
        console.log(); // newline
    }
    
    console.log(`\nTesting complete! Found ${workingNewChannels.length} working new channels out of ${newChannels.length}.`);
    
    if (workingNewChannels.length > 0) {
        const combinedChannels = [...existingChannels, ...workingNewChannels];
        fs.writeFileSync('d:/TV App/firebase_working.json', JSON.stringify(combinedChannels, null, 4), 'utf8');
        console.log(`Total channels in database is now ${combinedChannels.length}. Saved to firebase_working.json`);
    } else {
        console.log("No new working channels found.");
    }
}

run();
