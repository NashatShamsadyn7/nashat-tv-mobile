// Upload new channels to Firebase using firebase-tools
const { execSync } = require('child_process');
const fs = require('fs');

const newChannels = JSON.parse(fs.readFileSync('d:\\TV App\\new_channels.json', 'utf8'));

// Read existing channels
const existingChannels = JSON.parse(fs.readFileSync('d:\\TV App\\firebase_working.json', 'utf8'));

// First get the current channels
let currentChannels = [];
try {
    const result = execSync(
        'npx -y firebase-tools database:get /channels --project nashat-tv --instance nashat-tv-default-rtdb',
        { encoding: 'utf8', cwd: 'd:\\TV App', timeout: 30000 }
    );
    currentChannels = JSON.parse(result.trim()) || [];
    console.log(`Current Firebase channels: ${currentChannels.length}`);
} catch (err) {
    console.log('Could not read existing channels, will use local data');
    console.log('Error:', err.message);
    currentChannels = existingChannels;
}

// Build existing name set
const existingNames = new Set(currentChannels.map(c => c.name.toLowerCase().trim()));

// Filter duplicates
const toAdd = newChannels.filter(ch => !existingNames.has(ch.name.toLowerCase().trim()));
console.log(`New channels to add: ${toAdd.length}`);

if (toAdd.length === 0) {
    console.log('No new channels to add.');
    process.exit(0);
}

// Merge and upload all channels
const allChannels = [...currentChannels, ...toAdd];
const tmpFile = 'd:\\TV App\\upload_tmp.json';
fs.writeFileSync(tmpFile, JSON.stringify(allChannels, null, 2));

try {
    console.log('Uploading to Firebase...');
    const uploadResult = execSync(
        `npx -y firebase-tools database:set /channels --project nashat-tv --instance nashat-tv-default-rtdb --data "${tmpFile.replace(/\\/g,'/')}" -y`,
        { encoding: 'utf8', cwd: 'd:\\TV App', timeout: 60000 }
    );
    console.log('Upload result:', uploadResult);
} catch (err) {
    console.log('Firebase CLI upload error:', err.message);
    console.log('Trying alternative...');
    try {
        const uploadResult2 = execSync(
            `npx -y firebase-tools database:update /channels --project nashat-tv --instance nashat-tv-default-rtdb "${tmpFile.replace(/\\/g,'/')}" -y`,
            { encoding: 'utf8', cwd: 'd:\\TV App', timeout: 60000 }
        );
        console.log('Upload result:', uploadResult2);
    } catch (err2) {
        console.log('Alternative also failed:', err2.message);
    }
}

// cleanup
try { fs.unlinkSync(tmpFile); } catch {}

// Update local file
fs.writeFileSync('d:\\TV App\\firebase_working.json', JSON.stringify(allChannels, null, 4), 'utf8');
console.log(`Done! Total channels: ${allChannels.length}`);
