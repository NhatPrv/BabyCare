# Update local.properties with current ngrok public URL
# Usage: Start ngrok (eg. ngrok http 4000), then run this script from the repo root.
# It queries the ngrok local API at http://127.0.0.1:4040/api/tunnels
# and writes SERVER_BASE_URL and DOCTOR_URL into local.properties

$api = 'http://127.0.0.1:4040/api/tunnels'
try {
    $resp = Invoke-RestMethod -Uri $api -UseBasicParsing -ErrorAction Stop
} catch {
    Write-Error "Failed to query ngrok API at $api. Is ngrok running?"
    exit 1
}

if (-not $resp.tunnels -or $resp.tunnels.count -eq 0) {
    Write-Error "No tunnels found. Is ngrok running and exposing port 4000?"
    exit 1
}

# Prefer an https tunnel
$tunnel = $resp.tunnels | Where-Object { $_.public_url -match '^https://' } | Select-Object -First 1
if (-not $tunnel) { $tunnel = $resp.tunnels[0] }
$public = $tunnel.public_url.TrimEnd('/') + '/'  # ensure trailing slash
$doctor = ($public.TrimEnd('/') + '/doctor').Replace('//doctor','/doctor')

$localFile = Join-Path (Get-Location) 'local.properties'
# Preserve existing other properties if any
$lines = @()
if (Test-Path $localFile) { $lines = Get-Content $localFile }
# Remove existing keys
$lines = $lines | Where-Object { $_ -notmatch '^(SERVER_BASE_URL|DOCTOR_URL)=' }
# Append new values
$lines += "SERVER_BASE_URL=$public"
$lines += "DOCTOR_URL=$doctor"

Set-Content -Path $localFile -Value $lines -Encoding UTF8
Write-Output "Updated local.properties with SERVER_BASE_URL=$public and DOCTOR_URL=$doctor"
Write-Output "Run .\gradlew.bat :app:assembleDebug to rebuild the app with the new URL."