<#
.SYNOPSIS
  End-to-end check of the reset script and the first-admin bootstrap on a THROWAWAY local database.

.DESCRIPTION
  Creates database gp_reset_test on localhost, then:
   1. starts the backend with the seed profile (Flyway migrates, seed data is created, credentials go to a temp file),
   2. adds edge-case rows: a real admin that must survive, an extra business, a stored AI key,
   3. runs scripts/reset-dev-data.ps1 against it and checks what stayed and what went,
   4. removes the surviving admin, starts the backend with BOOTSTRAP_ADMIN_* and checks the admin and its OWNER role,
   5. always drops gp_reset_test. Your real database and your .dev-credentials file are never touched.
  Needs: JDK 21 (JAVA_HOME or the default path below), psql/createdb/dropdb, and the postgres password, taken from
  DATABASE_PASSWORD / PGPASSWORD or asked for once (never printed or stored).

.EXAMPLE
  .\scripts\verify-reset-local.ps1            # user postgres
  .\scripts\verify-reset-local.ps1 -User myuser
#>
[CmdletBinding()]
param([string]$User = 'postgres', [string]$DbHost = 'localhost', [int]$Port = 5432, [int]$AppPort = 18099)

$ErrorActionPreference = 'Stop'
$TestDb = 'gp_reset_test'
$DefaultBusiness = '6a1e0b7c-0000-4000-8000-00000000b001'
$repo = Split-Path $PSScriptRoot -Parent
$tmp = Join-Path ([IO.Path]::GetTempPath()) ("gp-reset-verify-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $tmp | Out-Null
$failures = New-Object System.Collections.Generic.List[string]

function Pass($m) { Write-Host "PASS  $m" -ForegroundColor Green }
function Check([bool]$ok, [string]$m) { if ($ok) { Pass $m } else { Write-Host "FAIL  $m" -ForegroundColor Red; $failures.Add($m) } }

function Find-PgTool([string]$name) {
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $pg = Join-Path $env:ProgramFiles 'PostgreSQL'
    if (Test-Path $pg) {
        $hit = Get-ChildItem $pg -Directory | Sort-Object { [int]($_.Name -replace '\D', '0') } -Descending |
            ForEach-Object { Join-Path $_.FullName "bin\$name.exe" } | Where-Object { Test-Path $_ } | Select-Object -First 1
        if ($hit) { return $hit }
    }
    throw "$name not found. Install the PostgreSQL client tools."
}
$psql = Find-PgTool 'psql'; $createdb = Find-PgTool 'createdb'; $dropdb = Find-PgTool 'dropdb'

# Password: environment first, otherwise ask once (hidden).
if (-not $env:PGPASSWORD) {
    if ($env:DATABASE_PASSWORD) { $env:PGPASSWORD = $env:DATABASE_PASSWORD }
    else {
        $secure = Read-Host "Password for PostgreSQL user '$User'" -AsSecureString
        $env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
    }
}

function Sql([string]$statement, [string]$db = $TestDb) {
    $out = & $psql -h $DbHost -p $Port -U $User -d $db -v ON_ERROR_STOP=1 -At -c $statement
    if ($LASTEXITCODE -ne 0) { throw "SQL failed: $statement" }
    return ($out -join "`n").Trim()
}

function Start-Backend([hashtable]$extraEnv, [string]$readyPattern, [int]$timeoutSeconds = 240) {
    $log = Join-Path $tmp ("app-" + [guid]::NewGuid().ToString('N') + ".log")
    $env:DATABASE_URL = "jdbc:postgresql://${DbHost}:$Port/$TestDb"
    $env:DATABASE_USERNAME = $User
    $env:DATABASE_PASSWORD = $env:PGPASSWORD
    $env:JWT_SECRET = ([guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N'))
    $env:SERVER_PORT = "$AppPort"
    foreach ($k in $extraEnv.Keys) { Set-Item "Env:$k" $extraEnv[$k] }
    $proc = Start-Process -FilePath (Join-Path $repo 'mvnw.cmd') -ArgumentList '-B', '-q', 'spring-boot:run' `
        -WorkingDirectory $repo -RedirectStandardOutput $log -RedirectStandardError ($log + '.err') -PassThru -WindowStyle Hidden
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    $ok = $false
    while ((Get-Date) -lt $deadline -and -not $proc.HasExited) {
        Start-Sleep -Seconds 3
        if ((Test-Path $log) -and (Select-String -Path $log -Pattern $readyPattern -Quiet)) { $ok = $true; break }
    }
    & taskkill.exe /PID $proc.Id /T /F | Out-Null
    foreach ($k in $extraEnv.Keys) { Remove-Item "Env:$k" -ErrorAction SilentlyContinue }
    return @{ Ok = $ok; Log = $log }
}

try {
    if ($env:JAVA_HOME -eq $null -or -not (Test-Path $env:JAVA_HOME)) {
        $jdk = Join-Path $env:USERPROFILE '.jdks\ms-21.0.12.1'
        if (Test-Path $jdk) { $env:JAVA_HOME = $jdk }
    }

    $exists = & $psql -h $DbHost -p $Port -U $User -d postgres -At -c "select 1 from pg_database where datname='$TestDb'"
    if ($exists) { throw "Database $TestDb already exists. Drop it first (dropdb $TestDb) so nothing real is touched." }
    & $createdb -h $DbHost -p $Port -U $User $TestDb
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the test database.' }
    Pass "created throwaway database $TestDb"

    # 1. Migrate and seed (credentials file goes to the temp folder, never to your .dev-credentials)
    $seedCreds = Join-Path $tmp 'seed-credentials.txt'
    $seed = Start-Backend @{ SPRING_PROFILES_ACTIVE = 'seed'; APP_SEED_CREDENTIALS_FILE = $seedCreds } 'Seed data completed successfully'
    Check $seed.Ok 'backend migrated the database and ran the seed'
    if (-not $seed.Ok) { throw "Seed did not finish. See $($seed.Log)" }
    Check ((Sql 'select count(*) from clients') -eq '2') 'seed created 2 clients'
    Check ((Sql "select count(*) from users where normalized_email='admin@growthprint.dev'") -eq '1') 'seed created the seed admin'
    Check ((Test-Path $seedCreds) -and -not (Select-String -Path $seed.Log -Pattern 'Admin123|ClientA123|ClientB123' -Quiet)) 'seed passwords were written to the temp file, not logged, and no known literal is used'
    $flywayBefore = Sql 'select count(*) from flyway_schema_history'

    # 2. Edge cases
    Sql "insert into users (id,email,normalized_email,password_hash,role,account_status) values (gen_random_uuid(),'keep@example.test','keep@example.test','x','ADMIN','ACTIVE')" | Out-Null
    Sql "insert into businesses (id,name,slug,created_at,updated_at) values (gen_random_uuid(),'Sample Co','sample-co',now(),now())" | Out-Null
    Sql "update ai_settings set provider='OPENAI', model='gpt-test', api_key_ciphertext='x', api_key_iv='x', api_key_last4='abcd', enabled=true where id=1" | Out-Null

    # 3. Reset through the real script
    $env:DATABASE_URL = "jdbc:postgresql://${DbHost}:$Port/$TestDb"
    $env:DATABASE_USERNAME = $User; $env:DATABASE_PASSWORD = $env:PGPASSWORD
    & (Join-Path $PSScriptRoot 'reset-dev-data.ps1') -Confirm -ConfirmDatabase $TestDb
    Check ($LASTEXITCODE -eq 0 -or $null -eq $LASTEXITCODE) 'reset script completed'
    Check ((Sql 'select count(*) from clients') -eq '0') 'clients removed'
    Check ((Sql 'select count(*)+0 from (select 1 from projects union all select 1 from invoices union all select 1 from support_requests union all select 1 from crm_leads union all select 1 from outreach_drafts) t') -eq '0') 'projects, invoices, requests, leads and drafts removed'
    Check ((Sql "select count(*) from users where normalized_email in ('admin@growthprint.dev','clienta@growthprint.dev','clientb@growthprint.dev') or role='CLIENT'") -eq '0') 'seed accounts and client accounts removed'
    Check ((Sql "select count(*) from users where normalized_email='keep@example.test'") -eq '1') 'the real admin survived'
    Check ((Sql "select count(*) from businesses where id='$DefaultBusiness'") -eq '1') 'default business kept'
    Check ((Sql 'select count(*) from businesses') -eq '1') 'sample business removed'
    Check ((Sql 'select count(*) from ai_settings where id=1 and enabled=false and api_key_ciphertext is null and provider is null') -eq '1') 'AI key cleared and AI switched off'
    Check ((Sql 'select count(*) from flyway_schema_history') -eq $flywayBefore) 'flyway history untouched'
    Check ((Get-ChildItem (Join-Path $repo 'backups') -Filter "$TestDb-*.dump" -ErrorAction SilentlyContinue | Measure-Object).Count -ge 1) 'a backup dump was written to backups\'

    # 4. Bootstrap on a database with no admin
    Sql "delete from users where normalized_email='keep@example.test'" | Out-Null
    $bootPassword = 'Verify-' + [guid]::NewGuid().ToString('N').Substring(0, 14) + '-9'
    $boot = Start-Backend @{ BOOTSTRAP_ADMIN_EMAIL = 'first.admin@example.test'; BOOTSTRAP_ADMIN_PASSWORD = $bootPassword } 'Bootstrap admin account created'
    Check $boot.Ok 'bootstrap created the first admin'
    Check ((Sql "select count(*) from users where normalized_email='first.admin@example.test' and role='ADMIN' and account_status='ACTIVE'") -eq '1') 'first admin is an active ADMIN'
    Check ((Sql "select count(*) from business_memberships m join users u on u.id=m.user_id where m.business_id='$DefaultBusiness' and m.role='OWNER' and u.normalized_email='first.admin@example.test'") -eq '1') 'first admin owns the default business'
    Check (-not (Select-String -Path $boot.Log -Pattern ([regex]::Escape($bootPassword)) -Quiet)) 'bootstrap password does not appear in the log'
}
catch {
    Write-Host "ERROR $($_.Exception.Message)" -ForegroundColor Red
    $failures.Add($_.Exception.Message)
}
finally {
    & $dropdb -h $DbHost -p $Port -U $User --if-exists $TestDb 2>$null
    Write-Host "dropped $TestDb"
    Remove-Item Env:PGPASSWORD, Env:DATABASE_URL, Env:DATABASE_USERNAME, Env:DATABASE_PASSWORD, Env:JWT_SECRET, Env:SERVER_PORT -ErrorAction SilentlyContinue
    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
}

if ($failures.Count -eq 0) { Write-Host "`nALL CHECKS PASSED" -ForegroundColor Green; exit 0 }
Write-Host "`n$($failures.Count) CHECK(S) FAILED" -ForegroundColor Red
$failures | ForEach-Object { Write-Host " - $_" -ForegroundColor Red }
exit 1
