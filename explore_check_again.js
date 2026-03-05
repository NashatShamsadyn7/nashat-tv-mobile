const fs = require('fs');
const http = require('http');
const https = require('https');

const existingFile = 'd:/TV App/firebase_working.json';
let existingChannels = JSON.parse(fs.readFileSync(existingFile, 'utf8'));

// Filter out explore channels? If the user meant "remove the ones from explore from firebase", or "remove the explore UI button".
// User said "check again all in firebase and remove explor"
// I will check all first.

async function testStream(url) {
    return new Promise((resolve) => {
        const client = url.startsWith('https') ? https : http;
        const req = client.get(url, { timeout: 4000 }, (res) => {
            if (res.statusCode >= 200 && res.statusCode < 400) {
                resolve(true); 
            } else {
                resolve(false);
            }
            req.abort();
        });
        req.on('error', () => resolve(false));
        req.on('timeout', () => { req.abort(); resolve(false); });
    });
}

async function run() {
    console.log('Total to check:', existingChannels.length);
    const BATCH_SIZE = 30;
    const workingChannels = [];
    
    for (let i = 0; i < existingChannels.length; i += BATCH_SIZE) {
        const batch = existingChannels.slice(i, i + BATCH_SIZE);
        await Promise.all(batch.map(async (ch) => {
            const isWorking = await testStream(ch.url);
            if (isWorking) {
                workingChannels.push(ch);
            }
        }));
        console.log(`Checked ${i + batch.length}...`);
    }
    
    console.log(`Finished. ${workingChannels.length} out of ${existingChannels.length} are working.`);
    fs.writeFileSync('d:/TV App/firebase_working.json', JSON.stringify(workingChannels, null, 4), 'utf8');
}
run();
