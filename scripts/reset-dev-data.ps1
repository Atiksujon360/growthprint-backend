<#
.SYNOPSIS
  Resets GrowthPrint business data on a LOCAL development database (see reset-dev-data.sql for exactly what goes and what stays).

.DESCRIPTION
  Safety rails:
   * The database host must be localhost, 127.0.0.1 or ::1. Anything else (including every render.com host) is refused.
   * You must pass -Confirm AND type the database name (or pass it with -ConfirmDatabase for scripted runs).
   * A pg_dump backup is written to .\backups\ first. Without pg_dump the script stops unless you pass -SkipBackup.
   * The whole reset runs in one transaction: it either completes or changes nothing.
  Connection settings come from the environment (the same variables the backend uses), so no secret is typed or printed:
   DATABASE_URL (jdbc:postgresql://host:port/db), DATABASE_USERNAME, DATABASE_PASSWORD.

.EXAMPLE
  $env:DATABASE_URL = 'jdbc:postgresql://localhost:5432/growthprint'
  $env:DATABASE_USERNAME = 'postgres'; $env:DATABASE_PASSWORD = '...'
  .\scripts\reset-dev-data.ps1 -Confirm
#>
[CmdletBinding()]
param(
    [switch]$Confirm,
    [string]$ConfirmDatabase,
    [switch]$SkipBackup
)

$ErrorActionPreference = 'Stop'

function Fail([string]$message) { Write-Error $message; exit 1 }

if (-not $Confirm) { Fail 'Add -Confirm to run. This deletes business data from the database. Read scripts/reset-dev-data.sql first.' }

# --- Connection from the environment ---------------------------------------------------------------------------
$url = $env:DATABASE_URL
if (-not $url) { Fail 'DATABASE_URL is not set (expected jdbc:postgresql://host:port/database).' }
if ($url -notmatch '^jdbc:postgresql://(?<host>\[[^\]]+\]|[^:/?]+)(:(?<port>\d+))?/(?<db>[^?]+)') {
    Fail 'DATABASE_URL must look like jdbc:postgresql://host:port/database.'
}
$dbHost = $Matches['host'].Trim('[', ']').ToLowerInvariant()
$dbPort = if ($Matches['port']) { $Matches['port'] } else { '5432' }
$dbName = $Matches['db']

# --- Host guard: local only ------------------------------------------------------------------------------------
$localHosts = @('localhost', '127.0.0.1', '::1')
if ($dbHost -like '*render.com*' -or $localHosts -notcontains $dbHost) {
    Fail "Refusing to run: '$dbHost' is not a local database host. This script only resets localhost, 127.0.0.1 or ::1. For a hosted database follow docs/production/DATA-RESET-RUNBOOK.md."
}

# --- Typed confirmation ----------------------------------------------------------------------------------------
$typed = $ConfirmDatabase
if (-not $typed) { $typed = Read-Host "Type the database name '$dbName' to confirm the reset" }
if ($typed -ne $dbName) { Fail 'Database name did not match. Nothing was changed.' }

# --- Find psql / pg_dump ---------------------------------------------------------------------------------------
function Find-PgTool([string]$name) {
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($root in @($env:ProgramFiles, ${env:ProgramFiles(x86)})) {
        if (-not $root) { continue }
        $pg = Join-Path $root 'PostgreSQL'
        if (Test-Path $pg) {
            $hit = Get-ChildItem $pg -Directory | Sort-Object { [int]($_.Name -replace '\D', '0') } -Descending |
                ForEach-Object { Join-Path $_.FullName "bin\$name.exe" } | Where-Object { Test-Path $_ } | Select-Object -First 1
            if ($hit) { return $hit }
        }
    }
    return $null
}
$psql = Find-PgTool 'psql'
if (-not $psql) { Fail 'psql was not found. Install the PostgreSQL client tools or add them to PATH.' }

$user = if ($env:DATABASE_USERNAME) { $env:DATABASE_USERNAME } else { 'postgres' }
$previousPassword = $env:PGPASSWORD
if ($env:DATABASE_PASSWORD) { $env:PGPASSWORD = $env:DATABASE_PASSWORD }

try {
    # --- Backup ------------------------------------------------------------------------------------------------
    if (-not $SkipBackup) {
        $pgDump = Find-PgTool 'pg_dump'
        if (-not $pgDump) { Fail 'pg_dump was not found, so no backup can be taken. Install it, or pass -SkipBackup if you accept losing this data.' }
        $backupDir = Join-Path (Split-Path $PSScriptRoot -Parent) 'backups'
        New-Item -ItemType Directory -Force $backupDir | Out-Null
        $backupFile = Join-Path $backupDir ("{0}-{1}.dump" -f $dbName, (Get-Date -Format 'yyyyMMdd-HHmmss'))
        & $pgDump -h $dbHost -p $dbPort -U $user -d $dbName -Fc -f $backupFile
        if ($LASTEXITCODE -ne 0) { Fail 'Backup failed. Nothing was changed.' }
        Write-Host "Backup written to $backupFile"
    }

    # --- Reset (single transaction, stops at the first error) ----------------------------------------------------
    $sql = Join-Path $PSScriptRoot 'reset-dev-data.sql'
    & $psql -h $dbHost -p $dbPort -U $user -d $dbName -v ON_ERROR_STOP=1 --single-transaction -f $sql
    if ($LASTEXITCODE -ne 0) { Fail 'The reset failed and was rolled back. Nothing was changed.' }
    Write-Host 'Reset complete. If no ADMIN is left, start the app with BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD set once, then remove the password.'
}
finally {
    if ($null -ne $previousPassword) { $env:PGPASSWORD = $previousPassword } else { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
}
