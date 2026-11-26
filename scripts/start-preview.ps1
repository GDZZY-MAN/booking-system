param(
    [ValidateRange(1024, 65535)][int]$Port = 15173,
    [System.Net.IPAddress]$ListenAddress = '127.0.0.1',
    [switch]$OpenBrowser
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $projectRoot 'frontend'
if ($ListenAddress.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork) {
    throw 'Use an IPv4 address for this preview.'
}
$listenHost = $ListenAddress.IPAddressToString
$previewHost = if ($ListenAddress.Equals([System.Net.IPAddress]::Any)) { '127.0.0.1' } else { $listenHost }
$previewUrl = "http://${previewHost}:$Port"

function Test-HttpEndpoint([string]$Url) {
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
        return $response.StatusCode -eq 200
    } catch {
        return $false
    }
}

if (-not (Test-HttpEndpoint "$previewUrl/@vite/client")) {
    $viteEntry = Join-Path $frontendRoot 'node_modules/vite/bin/vite.js'
    if (-not (Test-Path -LiteralPath $viteEntry)) {
        throw 'Frontend dependencies are missing. Run npm ci in frontend first.'
    }
    $nodePath = (Get-Command node -ErrorAction Stop).Source
    $logRoot = Join-Path $projectRoot '.local'
    New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
    $logStem = Join-Path $logRoot ("preview-" + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    # Detach the preview from the invoking terminal; retain Vite output for diagnosis.
    $previewProcess = Start-Process -FilePath $nodePath -WorkingDirectory $frontendRoot `
        -ArgumentList @('node_modules/vite/bin/vite.js', '--host', $listenHost, '--port', "$Port", '--strictPort') `
        -WindowStyle Hidden -RedirectStandardOutput "$logStem.out.log" `
        -RedirectStandardError "$logStem.err.log" -PassThru
    $ready = $false
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        if (Test-HttpEndpoint "$previewUrl/@vite/client") { $ready = $true; break }
        $previewProcess.Refresh()
        if ($previewProcess.HasExited) { break }
        Start-Sleep -Milliseconds 250
    }
    if (-not $ready) { throw "Preview failed to start. Inspect $logStem.err.log" }
}

Write-Host "Preview ready: $previewUrl"
if (-not (Test-HttpEndpoint "$previewUrl/api/v1/auth/csrf")) {
    Write-Warning 'The preview is running, but the backend is unavailable. Start the Java backend before logging in.'
} else {
    Write-Host 'Backend connection verified.'
}
if ($OpenBrowser) { Start-Process $previewUrl }
