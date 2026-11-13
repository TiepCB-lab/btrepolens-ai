param(
    [ValidateRange(1, 65535)]
    [int]$Port
)

$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env'
if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    throw 'Missing .env. Copy .env.example to .env and configure it first.'
}

$allowed = @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'GITHUB_TOKEN', 'AI_CHAT_MODEL', 'AI_API_KEY', 'AI_MODEL', 'SPRING_PROFILES_ACTIVE', 'GEMINI_API_KEY', 'GEMINI_MODEL')
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$' -and $allowed -contains $Matches[1]) {
        $name = $Matches[1]
        $value = $Matches[2]
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}
if ($PSBoundParameters.ContainsKey('Port')) {
    $env:SERVER_PORT = $Port.ToString()
}

Push-Location (Join-Path $projectRoot 'backend')
try {
    & .\mvnw.cmd spring-boot:run
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
