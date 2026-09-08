param(
    [string]$BaseUrl = "http://localhost:8080",
    [string[]]$Dataset = @(),
    [switch]$IncludeRealModels,
    [switch]$Judge,
    [string]$JudgeBaseUrl = $env:JUDGE_MODEL_BASE_URL,
    [string]$JudgeApiKey = $env:JUDGE_MODEL_API_KEY,
    [string]$JudgeModel = $env:JUDGE_MODEL_NAME,
    [double]$JudgeThreshold = $(if ($env:JUDGE_MODEL_THRESHOLD) { [double]$env:JUDGE_MODEL_THRESHOLD } else { 0.75 })
)

$projectRoot = Split-Path -Parent $PSScriptRoot
$pythonPath = Join-Path $projectRoot "agent-service\.venv\Scripts\python.exe"
$runnerPath = Join-Path $projectRoot "evals\runners\run_agent_eval.py"

if (-not (Test-Path -LiteralPath $pythonPath)) {
    throw "Python virtual environment is missing: $pythonPath"
}

$env:NO_PROXY = "localhost,127.0.0.1,$env:NO_PROXY"
$env:no_proxy = "localhost,127.0.0.1,$env:no_proxy"

$arguments = @($runnerPath, "--base-url", $BaseUrl)
foreach ($item in $Dataset) {
    $arguments += @("--dataset", $item)
}
if ($IncludeRealModels) {
    $arguments += "--include-real-models"
}
if ($Judge) {
    $arguments += @("--judge", "--judge-threshold", $JudgeThreshold)
    if ($JudgeBaseUrl) {
        $arguments += @("--judge-base-url", $JudgeBaseUrl)
    }
    if ($JudgeApiKey) {
        $arguments += @("--judge-api-key", $JudgeApiKey)
    }
    if ($JudgeModel) {
        $arguments += @("--judge-model", $JudgeModel)
    }
}

& $pythonPath @arguments
