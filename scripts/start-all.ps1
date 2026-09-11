param(
    [ValidateSet('docker', 'local')]
    [string]$Mode = 'docker',
    [switch]$NoBuild,
    [switch]$ReloadAgent,
    [switch]$InstallFrontendDeps
)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $projectRoot '.run'
$logDir = Join-Path $runDir 'logs'
$pidDir = Join-Path $runDir 'pids'

New-Item -ItemType Directory -Force -Path $logDir, $pidDir | Out-Null

function Test-CommandExists([string]$Name) {
    return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Write-Section([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Start-LoggedProcess(
    [string]$Name,
    [string]$WorkingDirectory,
    [string]$Command
) {
    $stdout = Join-Path $logDir "$Name.out.log"
    $stderr = Join-Path $logDir "$Name.err.log"
    $pidFile = Join-Path $pidDir "$Name.pid"

    if (Test-Path -LiteralPath $pidFile) {
        $existingPid = Get-Content -LiteralPath $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($existingPid) {
            $existingProcess = Get-Process -Id ([int]$existingPid) -ErrorAction SilentlyContinue
            if ($existingProcess) {
                Write-Host "$Name already running (PID $existingPid)."
                return
            }
        }
    }

    $arguments = @(
        '-NoLogo',
        '-NoProfile',
        '-ExecutionPolicy',
        'Bypass',
        '-Command',
        $Command
    )

    $process = Start-Process -FilePath 'powershell.exe' `
        -ArgumentList $arguments `
        -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -WindowStyle Hidden `
        -PassThru

    Set-Content -LiteralPath $pidFile -Value $process.Id -Encoding ASCII
    Write-Host "$Name started (PID $($process.Id)). Logs: $stdout / $stderr"
}

function Invoke-DockerCompose([string[]]$Arguments) {
    if (Test-CommandExists 'docker') {
        & docker compose @Arguments
        return
    }
    throw 'Docker CLI is required. Install Docker Desktop or use -Mode local after starting dependencies manually.'
}

if ($Mode -eq 'docker') {
    Write-Section 'Starting full stack with Docker Compose'
    $composeArgs = @('up', '-d')
    if (-not $NoBuild) {
        $composeArgs += '--build'
    }
    Invoke-DockerCompose $composeArgs

    Write-Section 'Services'
    Invoke-DockerCompose @('ps')
    Write-Host ""
    Write-Host 'Frontend:      http://localhost:5173'
    Write-Host 'Backend:       http://localhost:8080/actuator/health'
    Write-Host 'Python Agent:  http://localhost:8000/health'
    Write-Host 'OCR:           http://localhost:8866/health'
    Write-Host ""
    Write-Host 'Demo accounts: employee / employee123, employee2 / employee234, hr / hr123456, admin / admin123456'
    exit 0
}

Write-Section 'Starting infrastructure with Docker Compose'
Invoke-DockerCompose @('up', '-d', 'mysql', 'redis', 'etcd', 'minio', 'milvus', 'ocr', 'mcp-server')

if (-not $env:SPRING_PROFILES_ACTIVE) {
    $env:SPRING_PROFILES_ACTIVE = 'mysql'
}

Write-Section 'Starting Python Agent'
$agentScript = Join-Path $projectRoot 'scripts\start-agent.ps1'
$agentCommand = "& '$agentScript'"
if ($ReloadAgent) {
    $agentCommand += ' -Reload'
}
Start-LoggedProcess -Name 'agent-service' -WorkingDirectory $projectRoot -Command $agentCommand

Write-Section 'Starting Spring Backend'
$backendScript = Join-Path $projectRoot 'scripts\start-backend-python-agent.ps1'
Start-LoggedProcess -Name 'backend' -WorkingDirectory $projectRoot -Command "& '$backendScript'"

Write-Section 'Starting Vue Frontend'
$frontendRoot = Join-Path $projectRoot 'frontend'
if ($InstallFrontendDeps -or -not (Test-Path -LiteralPath (Join-Path $frontendRoot 'node_modules'))) {
    if (-not (Test-CommandExists 'npm')) {
        throw 'npm is required to install/start the frontend.'
    }
    Write-Host 'Installing frontend dependencies...'
    Push-Location $frontendRoot
    try {
        & npm install
    } finally {
        Pop-Location
    }
}
Start-LoggedProcess -Name 'frontend' -WorkingDirectory $frontendRoot -Command 'npm run dev -- --host 127.0.0.1'

Write-Section 'Started'
Write-Host 'Frontend:      http://localhost:5173'
Write-Host 'Backend:       http://localhost:8080/actuator/health'
Write-Host 'Python Agent:  http://localhost:8000/health'
Write-Host 'OCR:           http://localhost:8866/health'
Write-Host ""
Write-Host "Logs:          $logDir"
Write-Host "PID files:     $pidDir"
Write-Host 'Stop local app processes with: .\scripts\stop-all.ps1'
Write-Host 'Stop Docker dependencies with: docker compose down'
