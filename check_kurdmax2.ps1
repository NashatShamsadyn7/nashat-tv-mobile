$r = Invoke-WebRequest -Uri 'https://6476e46b58f91.streamlock.net/liveTrans/KURDS2211/playlist.m3u8' -TimeoutSec 10 -UseBasicParsing
Write-Output "SORANI CONTENT:"
Write-Output $r.Content
Write-Output ""

$r2 = Invoke-WebRequest -Uri 'https://6476e46b58f91.streamlock.net/liveTrans/SHOWS123/playlist.m3u8' -TimeoutSec 10 -UseBasicParsing
Write-Output "SHOW CONTENT:"
Write-Output $r2.Content
Write-Output ""

$r3 = Invoke-WebRequest -Uri 'https://6476e46b58f91.streamlock.net/music/MUSIC2402/playlist.m3u8' -TimeoutSec 10 -UseBasicParsing
Write-Output "MUSIC CONTENT:"
Write-Output $r3.Content

# Try some more potential Badini URLs
$badiniUrls = @(
    'https://6476e46b58f91.streamlock.net/liveTrans/PEPULE1/playlist.m3u8',
    'https://6476e46b58f91.streamlock.net/liveTrans/PEPULE123/playlist.m3u8',
    'https://6476e46b58f91.streamlock.net/liveTrans/KURDB/playlist.m3u8',
    'https://6476e46b58f91.streamlock.net/liveTrans/KURDB2211/playlist.m3u8',
    'https://6476e46b58f91.streamlock.net/liveTrans/Pepule/playlist.m3u8',
    'https://6476e46b58f91.streamlock.net/liveTrans/BADINI123/playlist.m3u8',
    'https://6476e46b58f91.streamlock.net/liveTrans/pepule2402/playlist.m3u8'
)
Write-Output ""
Write-Output "BADINI ATTEMPTS:"
foreach ($url in $badiniUrls) {
    try {
        $r = Invoke-WebRequest -Uri $url -TimeoutSec 5 -UseBasicParsing -ErrorAction Stop
        Write-Output "[OK] $url (Size: $($r.Content.Length))"
    } catch {
        Write-Output "[FAIL] $url"
    }
}
