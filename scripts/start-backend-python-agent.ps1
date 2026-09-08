$projectRoot = Split-Path -Parent $PSScriptRoot
$mavenPath = Join-Path $projectRoot '.tools\apache-maven-3.9.9\bin\mvn.cmd'

$env:AGENT_BASE_URL = 'http://localhost:8000'
$env:AGENT_SERVICE_TOKEN = 'change-this-in-production'
$env:AGENT_RUNTIME = 'python'
if (-not $env:AGENT_MODELS) {
    $env:AGENT_MODELS = if ($env:HR_AGENT_MODEL_OPTIONS) { $env:HR_AGENT_MODEL_OPTIONS } elseif ($env:HR_AGENT_MODEL_NAME) { $env:HR_AGENT_MODEL_NAME } else { 'deepseek-chat,qwen-plus,qwen-max,doubao-pro-32k,doubao-lite-32k,demo-rule-agent' }
}
if (-not $env:SPRING_PROFILES_ACTIVE) {
    $env:SPRING_PROFILES_ACTIVE = 'local'
}

function Test-Java21([string]$javaPath) {
    if (-not (Test-Path -LiteralPath $javaPath -PathType Leaf)) {
        return $false
    }

    $versionOutput = (& $javaPath -version 2>&1 | Out-String)
    return $versionOutput -match 'version "21(?:\.|\")'
}

$javaCommand = Get-Command java -ErrorAction SilentlyContinue
$javaPath = if ($null -ne $javaCommand) { $javaCommand.Source } else { $null }

if (-not $javaPath -or -not (Test-Java21 $javaPath)) {
    $jdkCandidates = @()
    if ($env:JAVA_HOME) {
        $jdkCandidates += Get-Item -LiteralPath $env:JAVA_HOME -ErrorAction SilentlyContinue
    }

    $jdkRoot = Join-Path $env:ProgramFiles 'Java'
    $jdkCandidates += Get-ChildItem -LiteralPath $jdkRoot -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending

    $jdk = $jdkCandidates |
        Where-Object { Test-Java21 (Join-Path $_.FullName 'bin\java.exe') } |
        Select-Object -First 1

    if ($null -eq $jdk) {
        throw 'JDK 21 is required. Set JAVA_HOME to a JDK 21 installation or install JDK 21.'
    }

    $env:JAVA_HOME = $jdk.FullName
    $env:Path = "$($env:JAVA_HOME)\bin;$($env:Path)"
}

Write-Host "Using JAVA_HOME=$env:JAVA_HOME"
& java -version

$portInUse = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($portInUse) {
    $processIds = ($portInUse | Select-Object -ExpandProperty OwningProcess -Unique) -join ', '
    throw "Port 8080 is already in use (PID: $processIds). The backend may already be running."
}

if (Test-Path -LiteralPath $mavenPath) {
    & $mavenPath "-Dmaven.repo.local=$projectRoot\.tools\m2-repository" -f (Join-Path $projectRoot 'backend\pom.xml') spring-boot:run
} else {
    & mvn "-Dmaven.repo.local=$projectRoot\.tools\m2-repository" -f (Join-Path $projectRoot 'backend\pom.xml') spring-boot:run
}

if ($LASTEXITCODE -ne 0) {
    throw "Backend failed to start (Maven exit code: $LASTEXITCODE)."
}
