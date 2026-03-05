# Channel Stream Health Checker - Write to file
$ErrorActionPreference = "SilentlyContinue"
$out = "d:\TV App\channel_results.txt"

$m3uFile = "d:\TV App\app\src\main\assets\kurdish_channels.m3u"
$lines = Get-Content $m3uFile

$channels = @()
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '#EXTINF:.*,(.+)$') {
        $name = $Matches[1].Trim()
        if (($i+1) -lt $lines.Count) {
            $url = $lines[$i+1].Trim()
            if ($url -and $url -ne "" -and $url -notmatch "^#") {
                $channels += [PSCustomObject]@{ Name = $name; Url = $url }
            }
        }
    }
}

$sb = [System.Text.StringBuilder]::new()
[void]$sb.AppendLine("Nashat TV Channel Health Check Results")
[void]$sb.AppendLine("======================================")
[void]$sb.AppendLine("Tested: $($channels.Count) channels")
[void]$sb.AppendLine("")

$deadList = @()
$okList = @()

foreach ($ch in $channels) {
    $status = "DEAD"
    try {
        $req = [System.Net.HttpWebRequest]::Create($ch.Url)
        $req.Method = "GET"
        $req.Timeout = 10000
        $req.ReadWriteTimeout = 10000
        $req.AllowAutoRedirect = $true
        $req.UserAgent = "Mozilla/5.0"
        $resp = $req.GetResponse()
        $code = [int]$resp.StatusCode
        $resp.Close()
        if ($code -ge 200 -and $code -lt 400) { $status = "OK" }
    } catch { $status = "DEAD" }
    
    if ($status -eq "OK") { $okList += $ch }
    else { $deadList += $ch }
}

[void]$sb.AppendLine("=== DEAD CHANNELS ($($deadList.Count)) ===")
foreach ($d in $deadList) {
    [void]$sb.AppendLine("  X  $($d.Name)")
    [void]$sb.AppendLine("     $($d.Url)")
}
[void]$sb.AppendLine("")
[void]$sb.AppendLine("=== WORKING CHANNELS ($($okList.Count)) ===")
foreach ($w in $okList) {
    [void]$sb.AppendLine("  OK $($w.Name)")
}
[void]$sb.AppendLine("")
[void]$sb.AppendLine("SUMMARY: Working=$($okList.Count) Dead=$($deadList.Count) Total=$($channels.Count)")

$sb.ToString() | Out-File $out -Encoding UTF8
Write-Host "Done! Results saved to channel_results.txt"
