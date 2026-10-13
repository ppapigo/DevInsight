# Run in a separate process: powershell -NoProfile -File scripts/verify-compose.ps1
# Requires Docker Compose 2.24.4+, Java 21, and Node.js 24. No external API keys are used.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$previousLocation = Get-Location
$verificationProject = 'devinsight-verify-' + [Guid]::NewGuid().ToString('N').Substring(0, 10)
$composeArgs = @('compose', '--project-name', $verificationProject, '-f', 'docker-compose.yml', '-f', 'docker-compose.verify.yml', '--profile', 'app')
$temporaryKeys = @('DB_USERNAME', 'DB_PASSWORD', 'MYSQL_ROOT_PASSWORD', 'FRONTEND_URL', 'GRADLE_USER_HOME', 'SMOKE_BASE_URL', 'SMOKE_REPORT_PATH', 'SMOKE_FRONTEND', 'TEST_DB_URL', 'TEST_DB_USERNAME', 'TEST_DB_PASSWORD', 'TEST_DB_DRIVER')
$previousValues = @{}
foreach ($key in $temporaryKeys) { $previousValues[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
$composeStarted = $false
try {
  Set-Location $projectRoot
  New-Item -ItemType Directory -Path 'work' -Force | Out-Null
  & docker info --format '{{.ServerVersion}}'
  if ($LASTEXITCODE -ne 0) { throw 'Docker engine is not accessible.' }
  foreach ($port in @(33071, 18081, 15173)) {
    $portProbe = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $port)
    try { $portProbe.Start() } finally { $portProbe.Stop() }
  }
  $env:DB_USERNAME = 'devinsight'
  # These are credentials for a newly created disposable database, never provider API keys.
  $env:DB_PASSWORD = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
  $env:MYSQL_ROOT_PASSWORD = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
  $env:FRONTEND_URL = 'http://localhost:15173'
  $env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-user-home'
  $composeStarted = $true
  & docker @composeArgs up --build --detach --wait --wait-timeout 180
  if ($LASTEXITCODE -ne 0) { throw 'Compose build/start failed.' }
  $ready = $false
  for ($attempt = 0; $attempt -lt 60; $attempt++) {
    try {
      $response = Invoke-RestMethod -Uri 'http://127.0.0.1:15173/actuator/health' -TimeoutSec 3
      if ($response.status -eq 'UP') { $ready = $true; break }
    } catch { }
    Start-Sleep -Seconds 2
  }
  if (!$ready) { throw 'Application did not become healthy.' }
  $env:SMOKE_BASE_URL = 'http://127.0.0.1:15173'
  $env:SMOKE_REPORT_PATH = 'work/compose-http-smoke-result.json'
  $env:SMOKE_FRONTEND = 'true'
  & node scripts/verify-http.cjs
  if ($LASTEXITCODE -ne 0) { throw 'Compose HTTP verification failed.' }
  # SQL travels over stdin so PowerShell/Windows native argument quoting cannot rewrite it.
  $testSchemaSql = @'
SELECT VERSION() AS mysql_version;
CREATE DATABASE devinsight_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL ON devinsight_test.* TO 'devinsight'@'%';
'@
  $testSchemaSql | & docker @composeArgs exec -T mysql sh -c 'export MYSQL_PWD=$MYSQL_ROOT_PASSWORD; exec mysql -uroot'
  if ($LASTEXITCODE -ne 0) { throw 'Cannot prepare isolated test schema.' }
  $env:TEST_DB_URL = 'jdbc:mysql://127.0.0.1:33071/devinsight_test?serverTimezone=UTC'
  $env:TEST_DB_USERNAME = 'devinsight'
  $env:TEST_DB_PASSWORD = $env:DB_PASSWORD
  $env:TEST_DB_DRIVER = 'com.mysql.cj.jdbc.Driver'
  & .\backend\gradlew.bat -p backend test --rerun-tasks --no-daemon
  if ($LASTEXITCODE -ne 0) { throw 'MySQL 8.4 tests failed.' }
  & docker @composeArgs ps
  'COMPOSE_VERIFICATION_PASSED'
} catch {
  if ($composeStarted) { & docker @composeArgs logs --tail 80 backend frontend }
  throw
} finally {
  try {
    if ($composeStarted) {
      # Only this invocation's randomly named project and its disposable test volumes are removed.
      & docker @composeArgs down --volumes --remove-orphans
      if ($LASTEXITCODE -ne 0) { Write-Warning "Cleanup failed for $verificationProject; inspect this test project manually." }
    }
  } finally {
    foreach ($key in $temporaryKeys) { [Environment]::SetEnvironmentVariable($key, $previousValues[$key], 'Process') }
    Set-Location $previousLocation
  }
}
