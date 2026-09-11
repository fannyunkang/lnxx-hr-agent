$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$pidDir = Join-Path $projectRoot '.run\pids'

function Stop-ProcessTree([int]$ProcessId) {
    Get-CimInstance Win32_Process -Filter "ParentProcessId = $ProcessId" -ErrorAction SilentlyContinue |
        ForEach-Object { Stop-ProcessTree -ProcessId ([int]$_.ProcessId) }

    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($process) {
        Stop-Process -Id $ProcessId -Force
    }
}

if (-not (Test-Path -LiteralPath $pidDir)) {
    Write-Host 'No local PID directory found.'
    exit 0
}

Get-ChildItem -LiteralPath $pidDir -Filter '*.pid' | ForEach-Object {
    $name = $_.BaseName
    $pidValue = Get-Content -LiteralPath $_.FullName -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $pidValue) {
        Remove-Item -LiteralPath $_.FullName -Force
        return
    }

    $process = Get-Process -Id ([int]$pidValue) -ErrorAction SilentlyContinue
    if ($process) {
        Stop-ProcessTree -ProcessId $process.Id
        Write-Host "$name stopped (PID $pidValue)."
    } else {
        Write-Host "$name was not running (PID $pidValue)."
    }
    Remove-Item -LiteralPath $_.FullName -Force
}

Write-Host 'Local app processes stopped.'
