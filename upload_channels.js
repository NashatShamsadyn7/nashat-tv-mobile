// Upload new channels to Firebase Realtime Database
const https = require('https');
const fs = require('fs');

const RTDB_URL = 'https://nashat-tv-default-rtdb.asia-southeast1.firebasedatabase.app';

// New channels to add
const newChannels = JSON.parse(fs.readFileSync('d:\\TV App\\new_channels.json', 'utf8'));

// Read existing channels
const existingChannels = JSON.parse(fs.readFileSync('d:\\TV App\\firebase_working.json', 'utf8'));
const existingNames = new Set(existingChannels.map(c => c.name.toLowerCase().trim()));

// Filter out duplicates
const toAdd = newChannels.filter(ch => !existingNames.has(ch.name.toLowerCase().trim()));
console.log(`Found ${toAdd.length} new channels to add (${newChannels.length - toAdd.length} duplicates skipped)`);

if (toAdd.length === 0) {
    console.log('No new channels to add.');
    process.exit(0);
}

// First get current channel count from Firebase
function httpsRequest(url, method, data) {
    return new Promise((resolve, reject) => {
        const urlObj = new URL(url);
        const options = {
            hostname: urlObj.hostname,
            port: 443,
            path: urlObj.pathname + urlObj.search,
            method: method,
            headers: { 'Content-Type': 'application/json' }
        };
        
        const req = https.request(options, (res) => {
            let body = '';
            res.on('data', chunk => body += chunk);
            res.on('end', () => {
                try {
                    resolve({ status: res.statusCode, data: JSON.parse(body) });
                } catch {
                    resolve({ status: res.statusCode, data: body });
                }
            });
        });
        req.on('error', reject);
        if (data) req.write(JSON.stringify(data));
        req.end();
    });
}

async function main() {
    try {
        // Get current channels count
        console.log('Fetching current channel count...');
        const getRes = await httpsRequest(`${RTDB_URL}/channels.json`, 'GET');
        
        let nextIndex = 0;
        if (getRes.data && typeof getRes.data === 'object') {
            if (Array.isArray(getRes.data)) {
                nextIndex = getRes.data.length;
            } else {
                const keys = Object.keys(getRes.data).map(Number).filter(n => !isNaN(n));
                nextIndex = keys.length > 0 ? Math.max(...keys) + 1 : 0;
            }
        }
        console.log(`Current channel count: ${nextIndex}`);
        
        // Upload each new channel
        for (let i = 0; i < toAdd.length; i++) {
            const ch = toAdd[i];
            const idx = nextIndex + i;
            console.log(`[${i+1}/${toAdd.length}] Adding: ${ch.name}`);
            
            const putRes = await httpsRequest(
                `${RTDB_URL}/channels/${idx}.json`,
                'PUT',
                ch
            );
            
            if (putRes.status === 200) {
                console.log(`  ✓ Added at index ${idx}`);
            } else {
                console.log(`  ✗ Failed (status ${putRes.status}): ${JSON.stringify(putRes.data)}`);
            }
        }
        
        // Also update the local firebase_working.json
        const updatedChannels = [...existingChannels, ...toAdd];
        fs.writeFileSync('d:\\TV App\\firebase_working.json', JSON.stringify(updatedChannels, null, 4), 'utf8');
        console.log(`\nLocal firebase_working.json updated (${updatedChannels.length} total channels)`);
        console.log('Done!');
        
    } catch (err) {
        console.error('Error:', err.message);
    }
}

main();
