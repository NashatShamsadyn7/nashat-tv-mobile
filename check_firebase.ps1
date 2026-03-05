# Test ALL Firebase channel URLs
$ErrorActionPreference = "SilentlyContinue"
$out = "d:\TV App\firebase_results.txt"

$data = Get-Content "d:\TV App\firebase_data.json" -Raw | ConvertFrom-Json
$channels = $data.value | Where-Object { $_ -ne $null -and $_.url -ne $null }

$sb = [System.Text.StringBuilder]::new()
[void]$sb.AppendLine("Firebase Channel Health Check")
[void]$sb.AppendLine("Total channels to test: $($channels.Count)")
[void]$sb.AppendLine("")

$deadList = @()
$okList = @()
$count = 0

foreach ($ch in $channels) {
    $count++
    Write-Host "[$count/$($channels.Count)] $($ch.name)..."
    $status = "DEAD"
    try {
        $req = [System.Net.HttpWebRequest]::Create($ch.url)
        $req.Method = "GET"
        $req.Timeout = 8000
        $req.ReadWriteTimeout = 8000
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
    [void]$sb.AppendLine("  X  $($d.name)")
    [void]$sb.AppendLine("     $($d.url)")
}
[void]$sb.AppendLine("")
[void]$sb.AppendLine("=== WORKING CHANNELS ($($okList.Count)) ===")
foreach ($w in $okList) {
    [void]$sb.AppendLine("  OK $($w.name)")
    [void]$sb.AppendLine("     $($w.url)")
}
[void]$sb.AppendLine("")
[void]$sb.AppendLine("SUMMARY: Working=$($okList.Count) Dead=$($deadList.Count) Total=$($channels.Count)")

$sb.ToString() | Out-File $out -Encoding UTF8

# Also save working channels as JSON for Firebase upload
$workingJson = $okList | ForEach-Object {
    [PSCustomObject]@{
        name = $_.name
        logo = $_.logo
        category = $_.category
        url = $_.url
        description = $_.description
    }
} | ConvertTo-Json -Depth 3
$workingJson | Out-File "d:\TV App\firebase_working.json" -Encoding UTF8

Write-Host "Done! Results saved."
