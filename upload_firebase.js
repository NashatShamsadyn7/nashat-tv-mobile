const { execSync } = require('child_process');
const fs = require('fs');

const channels = [
    { name: "KurdMax Pepule", logo: "https://i.imgur.com/JkK8HG8.png", category: "Kids", url: "https://6476e46b58f91.streamlock.net/liveTrans/KURDS2211/playlist.m3u8", description: "KurdMax kids channel Pepule - Kurdish children entertainment" },
    { name: "KurdMax Show Kurmanci", logo: "https://i.imgur.com/XTmyGmL.png", category: "Entertainment", url: "https://6476e46b58f91.streamlock.net/liveTrans/SHOWS123/playlist.m3u8", description: "KurdMax Show in Kurmanci Badini dialect" },
    { name: "Ala TV", logo: "https://karwan.tv/wp-content/uploads/2021/04/ala-tv.png", category: "Kurdish", url: "https://ala-tv.com/live/stream.m3u8", description: "Kurdish general channel" },
    { name: "Duhok TV", logo: "https://karwan.tv/wp-content/uploads/2021/04/duhok-tv.png", category: "Kurdish", url: "https://live.duhoktv.com/live/duhoktv/playlist.m3u8", description: "Local TV from Duhok Kurdistan" },
    { name: "Bangawaz TV", logo: "https://karwan.tv/wp-content/uploads/2021/04/bangawaz-tv.png", category: "Kurdish", url: "https://avr.host247.net/live/bangawaz/playlist.m3u8", description: "Kurdish general television" },
    { name: "Speda TV", logo: "https://upload.wikimedia.org/wikipedia/en/2/23/Speda_TV_logo.png", category: "News", url: "https://live.spedatv.com/live/spedatv/playlist.m3u8", description: "Kurdish news and current affairs" },
    { name: "Gali Kurdistan TV", logo: "https://upload.wikimedia.org/wikipedia/en/f/f7/Gali_Kurdistan_logo.png", category: "General", url: "https://live.galikurdistan.tv/live/galikurdistan/playlist.m3u8", description: "Voice of Kurdistan" },
    { name: "NRT TV", logo: "https://i.imgur.com/uGKGtbW.png", category: "News", url: "https://media.streambrothers.com:1936/8226/8226/playlist.m3u8", description: "Independent Kurdish news channel" },
    { name: "Sterk TV", logo: "https://i.imgur.com/bIheNAI.png", category: "News", url: "https://trn09.bozztv.com/gin-sterk/index.m3u8", description: "Kurdish news and current events" }
];

let startIdx = 186;
for (let i = 0; i < channels.length; i++) {
    const ch = channels[i];
    const idx = startIdx + i;
    const tmpFile = `d:\\TV App\\tmp_ch_${idx}.json`;
    fs.writeFileSync(tmpFile, JSON.stringify(ch));
    
    try {
        const result = execSync(
            `npx -y firebase-tools database:set /channels/${idx} --project nashat-tv --instance nashat-tv-default-rtdb "${tmpFile.replace(/\\/g, '/')}" -y`,
            { encoding: 'utf8', cwd: 'd:\\TV App', timeout: 30000, stdio: ['pipe', 'pipe', 'pipe'] }
        );
        console.log(`[${i+1}/${channels.length}] OK: ${ch.name} -> index ${idx}`);
    } catch (err) {
        console.log(`[${i+1}/${channels.length}] FAIL: ${ch.name} -> ${err.stderr || err.message}`);
    }
    
    try { fs.unlinkSync(tmpFile); } catch {}
}

console.log('Done!');
