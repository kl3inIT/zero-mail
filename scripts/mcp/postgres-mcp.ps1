$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")

function Read-DotEnvFile {
    param([string] $Path)

    $values = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $values
    }

    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*$' -or $line -match '^\s*#') {
            continue
        }
        if ($line -notmatch '^\s*([^=]+?)\s*=\s*(.*)\s*$') {
            continue
        }

        $key = $Matches[1].Trim()
        $value = $Matches[2].Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $values[$key] = $value
    }
    return $values
}

$envValues = Read-DotEnvFile (Join-Path $repoRoot ".env.local")
if ($envValues.Count -eq 0) {
    $envValues = Read-DotEnvFile (Join-Path $repoRoot ".env")
}

$dbUrl = if ($env:DB_URL) { $env:DB_URL } elseif ($envValues.ContainsKey("DB_URL")) { $envValues["DB_URL"] } else { "jdbc:postgresql://127.0.0.1:5555/zeromail_dev?sslmode=disable" }
$dbUser = if ($env:DB_USER) { $env:DB_USER } elseif ($envValues.ContainsKey("DB_USER")) { $envValues["DB_USER"] } else { "zeromail_dev" }
$dbPassword = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } elseif ($envValues.ContainsKey("DB_PASSWORD")) { $envValues["DB_PASSWORD"] } else { "" }

if ([string]::IsNullOrWhiteSpace($dbPassword)) {
    throw "DB_PASSWORD is required for Postgres MCP. Set it in .env.local or the current process environment."
}

$postgresUrl = $dbUrl -replace '^jdbc:', ''
$parsedUrl = [Uri] $postgresUrl
$targetHost = $parsedUrl.Host
if ($targetHost -in @("localhost", "127.0.0.1", "::1")) {
    $targetHost = "host.docker.internal"
}

$encodedUser = [Uri]::EscapeDataString($dbUser)
$encodedPassword = [Uri]::EscapeDataString($dbPassword)
$databasePath = $parsedUrl.AbsolutePath
$query = $parsedUrl.Query
$env:DATABASE_URI = "postgresql://{0}:{1}@{2}:{3}{4}{5}" -f $encodedUser, $encodedPassword, $targetHost, $parsedUrl.Port, $databasePath, $query

$docker = if ($env:DOCKER) { $env:DOCKER } else { "C:\Program Files\Docker\Docker\resources\bin\docker.exe" }
if (-not (Test-Path -LiteralPath $docker)) {
    $docker = "docker"
}

& $docker run -i --rm -e DATABASE_URI crystaldba/postgres-mcp --access-mode=unrestricted
exit $LASTEXITCODE
