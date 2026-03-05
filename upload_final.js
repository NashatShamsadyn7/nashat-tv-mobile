const { execSync } = require('child_process');
const fs = require('fs');

const channels = [
    { name: "KurdMax Pepule", logo: "https://i.imgur.com/JkK8HG8.png", category: "Kids", url: "https://6476e46b58f91.streamlock.net/liveTrans/KURDS2211/playlist.m3u8", description: "KurdMax kids channel Pepule" },
    { name: "KurdMax Show Kurmanci", logo: "https://i.imgur.com/XTmyGmL.png", category: "Entertainment", url: "https://6476e46b58f91.streamlock.net/liveTrans/SHOWS123/playlist.m3u8", description: "KurdMax Show Kurmanci Badini" },
    { name: "NRT TV", logo: "https://i.imgur.com/uGKGtbW.png", category: "News", url: "https://media.streambrothers.com:1936/8226/8226/playlist.m3u8", description: "Independent Kurdish news" },
    { name: "Sterk TV", logo: "https://i.imgur.com/bIheNAI.png", category: "News", url: "https://trn09.bozztv.com/gin-sterk/index.m3u8", description: "Kurdish news channel" },
    { name: "EmanTv", logo: "https://i.imgur.com/QmgaPSi.png", category: "Family", url: "https://avr.host247.net/live/emantv/playlist.m3u8", description: "Family channel" },
    { name: "Gali Kurdistan TV", logo: "https://upload.wikimedia.org/wikipedia/en/f/f7/Gali_Kurdistan_logo.png", category: "General", url: "https://live.galikurdistan.tv/live/galikurdistan/playlist.m3u8", description: "Voice of Kurdistan" },
    { name: "Speda TV", logo: "https://upload.wikimedia.org/wikipedia/en/2/23/Speda_TV_logo.png", category: "News", url: "https://live.spedatv.com/live/spedatv/playlist.m3u8", description: "Kurdish news" },
    { name: "Slemani TV", logo: "https://upload.wikimedia.org/wikipedia/en/5/5e/Slemani_TV_logo.png", category: "Kurdish", url: "https://live.slemanytv.com/live/slemanytv/playlist.m3u8", description: "Local TV Sulaymaniyah" }
];

for (let i = 0; i < channels.length; i++) {
    const ch = channels[i];
    const idx = 186 + i;
    const tmpFile = `d:/TV App/tmp_ch.json`;
    fs.writeFileSync(tmpFile, JSON.stringify(ch));
    try {
        const result = execSync(
            `npx firebase-tools database:set /channels/${idx} --project nashat-tv --instance nashat-tv-default-rtdb --force "${tmpFile}"`,
            { encoding: 'utf8', cwd: 'd:/TV App', timeout: 30000 }
        );
        console.log(`[${i+1}/${channels.length}] OK: ${ch.name} at index ${idx}`);
    } catch (err) {
        console.log(`[${i+1}/${channels.length}] FAIL: ${ch.name}: ${err.stderr ? err.stderr.trim() : err.message}`);
    }
}
try { fs.unlinkSync('d:/TV App/tmp_ch.json'); } catch {}
console.log('Done!');
