$urls = @(
    @{ Name = 'KurdMax Sorani (new)'; Url = 'https://6476e46b58f91.streamlock.net/liveTrans/KURDS2211/playlist.m3u8' },
    @{ Name = 'KurdMax Sorani (old)'; Url = 'https://6476e46b58f91.streamlock.net/liveTrans/KurdmaxS0rani!/playlist.m3u8' },
    @{ Name = 'KurdMax Show (new)'; Url = 'https://6476e46b58f91.streamlock.net/liveTrans/SHOWS123/playlist.m3u8' },
    @{ Name = 'KurdMax Show (old)'; Url = 'https://6476e46b58f91.streamlock.net/liveTrans/SHOW1/playlist.m3u8' },
    @{ Name = 'KurdMax Music (new)'; Url = 'https://6476e46b58f91.streamlock.net/music/MUSIC2402/playlist.m3u8' },
    @{ Name = 'KurdMax Music (old)'; Url = 'https://6476e46b58f91.streamlock.net/music/livestream/playlist.m3u8' },
    @{ Name = 'KurdMax Pepule/Badini'; Url = 'http://31.14.40.139:1935/liveTrans/Kurmanci/chunklist.m3u8' },
    @{ Name = 'KurdMax Pepule/Badini (alt)'; Url = 'https://6476e46b58f91.streamlock.net/liveTrans/Kurmanci/playlist.m3u8' },
    @{ Name = 'KurdMax Badini test1'; Url = 'https://6476e46b58f91.streamlock.net/liveTrans/KurdmaxBadini/playlist.m3u8' },
    @{ Name = 'KurdMax Badini test2'; Url = 'https://6476e46b58f91.streamlock.net/liveTrans/BADINI/playlist.m3u8' },
    @{ Name = 'KurdMax Pepule test'; Url = 'https://6476e46b58f91.streamlock.net/liveTrans/PEPULE/playlist.m3u8' }
)

foreach ($item in $urls) {
    $name = $item.Name
    $url = $item.Url
    try {
        $response = Invoke-WebRequest -Uri $url -TimeoutSec 10 -UseBasicParsing -ErrorAction Stop
        $status = $response.StatusCode
        $contentLen = $response.Content.Length
        $hasM3U = $response.Content.Contains('#EXTM3U') -or $response.Content.Contains('#EXTINF') -or $response.Content.Contains('#EXT-X')
        Write-Host "[OK] $name (Status:$status, Size:${contentLen}B, ValidM3U8:$hasM3U)"
        Write-Host "     URL: $url"
    } catch {
        $errMsg = $_.Exception.Message
        Write-Host "[FAIL] $name"
        Write-Host "     URL: $url"
        Write-Host "     Error: $errMsg"
    }
    Write-Host ""
}
