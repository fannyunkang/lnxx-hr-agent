param([switch]$Reload)

$projectRoot = Split-Path -Parent $PSScriptRoot
$pythonPath = Join-Path $projectRoot 'agent-service\.venv\Scripts\python.exe'

if (-not (Test-Path -LiteralPath $pythonPath)) {
    throw 'Python virtual environment is missing. Create agent-service/.venv and install the project first.'
}

Push-Location (Join-Path $projectRoot 'agent-service')
try {
    if (-not $env:HR_AGENT_TOOL_TRANSPORT) {
        $env:HR_AGENT_TOOL_TRANSPORT = 'mcp'
    }
    if (-not $env:HR_AGENT_MCP_BASE_URL) {
        $env:HR_AGENT_MCP_BASE_URL = 'http://localhost:8090'
    }
    if (-not $env:HR_AGENT_MCP_ENDPOINT) {
        $env:HR_AGENT_MCP_ENDPOINT = '/mcp'
    }
    if (-not $env:HR_AGENT_MODEL_OPTIONS -and $env:HR_AGENT_MODEL_NAME) {
        $env:HR_AGENT_MODEL_OPTIONS = $env:HR_AGENT_MODEL_NAME
    }

    $arguments = @('-m', 'uvicorn', 'hr_agent.main:app', '--app-dir', 'src',
        '--host', '127.0.0.1', '--port', '8000')
    if ($Reload) { $arguments += '--reload' }
    & $pythonPath @arguments
} finally {
    Pop-Location
}
