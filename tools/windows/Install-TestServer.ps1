<#
.SYNOPSIS
  Sets up a single-machine IAM Monthly Meeting TEST server on Windows 10/11:
  OpenJDK 17 (Temurin), SQL Server 2022 Express, sqlcmd, Tomcat 9 with TLS on 8443,
  the igamonmeet database and SQL login, the latest WAR built by CI on main, and a
  one-time link to set the first Admin's password.

.DESCRIPTION
  Run from an elevated (Run as Administrator) PowerShell:
    powershell -ExecutionPolicy Bypass -File .\Install-TestServer.ps1 -AdminUser jtan -AdminName "J. Tan" -AdminEmail jtan@company.com

  Safe to run again: installed parts are skipped, the SQL login password and
  setenv.bat are regenerated, and the WAR is redeployed.

  TEST ONLY: the TLS certificate is self-signed (the browser warns once) and the
  database connection trusts SQL Server's own self-signed certificate.
  For production follow docs/deploy.md.
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)] [string] $AdminUser,
  [Parameter(Mandatory = $true)] [string] $AdminName,
  [Parameter(Mandatory = $true)] [string] $AdminEmail,
  [string] $InstallRoot = 'C:\igamonmeet',
  [string] $WarPath = '',              # use a WAR you downloaded instead of fetching it with gh
  [string] $Repo = 'amalgeorge/IGAMonMeet'
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$BaseUrl = 'https://localhost:8443/igamonmeet'

function Step($m) { Write-Host "`n==> $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "    $m" -ForegroundColor Green }
function Refresh-Path {
  $env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
}
function Native([scriptblock] $block) {
  # Windows PowerShell 5.1 turns a program's redirected stderr into a terminating error under
  # ErrorActionPreference=Stop, even on success (keytool, gh and sqlcmd print progress there).
  # Run the program with Continue and let callers check $LASTEXITCODE instead.
  $old = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try { & $block } finally { $ErrorActionPreference = $old }
}
function Winget-Install($id) {
  Native { winget list --id $id -e --accept-source-agreements } *> $null
  if ($LASTEXITCODE -eq 0) { Ok "$id already installed"; return }
  winget install --id $id -e --silent --accept-package-agreements --accept-source-agreements
  if ($LASTEXITCODE -ne 0) { throw "winget could not install $id (exit $LASTEXITCODE)" }
  Refresh-Path
  Ok "$id installed"
}
function New-Secret([int] $n = 24) {
  # Letters and digits only: safe inside setenv.bat and T-SQL, and meets Windows password policy.
  $chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'.ToCharArray()
  $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
  $bytes = New-Object byte[] $n
  $rng.GetBytes($bytes)
  $s = -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })
  return 'Aa1' + $s   # guarantees upper, lower and digit
}
function Sql([string] $query, [string] $db = 'master') {
  $out = Native { & $script:Sqlcmd -S 'localhost,1433' -E -C -b -d $db -W -Q $query 2>&1 }
  if ($LASTEXITCODE -ne 0) { throw "sqlcmd failed: $($out -join [Environment]::NewLine)" }
  return $out
}

# ---------------------------------------------------------------------------
Step 'Checking prerequisites'
$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
  throw 'Run this script from PowerShell started with "Run as administrator".'
}
if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
  throw 'winget not found. Install "App Installer" from the Microsoft Store, then run again.'
}
if (-not (Get-Command curl.exe -ErrorAction SilentlyContinue)) { throw 'curl.exe not found (needs Windows 10 1803 or later).' }
New-Item -ItemType Directory -Force -Path $InstallRoot | Out-Null
Ok "Install folder: $InstallRoot"

# ---------------------------------------------------------------------------
Step 'Java 17 (Eclipse Temurin JDK)'
Winget-Install 'EclipseAdoptium.Temurin.17.JDK'
$JavaHome = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue |
  Sort-Object Name -Descending | Select-Object -First 1 -ExpandProperty FullName
if (-not $JavaHome) { throw 'JDK 17 not found under C:\Program Files\Eclipse Adoptium.' }
Ok "JAVA_HOME = $JavaHome"

# ---------------------------------------------------------------------------
Step 'SQL Server 2022 Express'
if (-not (Get-Service -Name 'MSSQL$SQLEXPRESS' -ErrorAction SilentlyContinue)) {
  Write-Host '    Installing (this takes 5 to 15 minutes)...'
  Winget-Install 'Microsoft.SQLServer.2022.Express'
}
if (-not (Get-Service -Name 'MSSQL$SQLEXPRESS' -ErrorAction SilentlyContinue)) {
  throw 'Service MSSQL$SQLEXPRESS not found. Install SQL Server 2022 Express by hand ("Basic" install), then run this script again.'
}
$instanceId = (Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server\Instance Names\SQL').SQLEXPRESS
$reg = "HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server\$instanceId\MSSQLServer"
# JDBC needs TCP on a fixed port and SQL logins (mixed mode); Express ships with both off.
Set-ItemProperty -Path $reg -Name LoginMode -Value 2
Set-ItemProperty -Path "$reg\SuperSocketNetLib\Tcp" -Name Enabled -Value 1
Set-ItemProperty -Path "$reg\SuperSocketNetLib\Tcp\IPAll" -Name TcpDynamicPorts -Value ''
Set-ItemProperty -Path "$reg\SuperSocketNetLib\Tcp\IPAll" -Name TcpPort -Value '1433'
Restart-Service -Name 'MSSQL$SQLEXPRESS' -Force
Ok "Instance ${instanceId}: TCP 1433 and mixed-mode sign-in enabled"

# ---------------------------------------------------------------------------
Step 'sqlcmd'
Winget-Install 'Microsoft.Sqlcmd'
$script:Sqlcmd = @(
  (Get-Command sqlcmd.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source),
  'C:\Program Files\SqlCmd\sqlcmd.exe'
) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
if (-not $script:Sqlcmd) { throw 'sqlcmd.exe not found after install.' }
for ($i = 0; $i -lt 30; $i++) {
  Native { & $script:Sqlcmd -S 'localhost,1433' -E -C -Q 'SELECT 1' } *> $null
  if ($LASTEXITCODE -eq 0) { break }
  Start-Sleep -Seconds 2
}
if ($LASTEXITCODE -ne 0) { throw 'Cannot connect to SQL Server on localhost,1433 with Windows sign-in.' }
Ok "Connected with $($script:Sqlcmd)"

# ---------------------------------------------------------------------------
Step 'Database igamonmeet and SQL login'
$DbPassword = New-Secret
Sql "IF DB_ID('igamonmeet') IS NULL CREATE DATABASE igamonmeet;" | Out-Null
Sql @"
IF SUSER_ID('igamonmeet') IS NULL
  CREATE LOGIN igamonmeet WITH PASSWORD = N'$DbPassword', DEFAULT_DATABASE = igamonmeet, CHECK_POLICY = ON;
ELSE
  ALTER LOGIN igamonmeet WITH PASSWORD = N'$DbPassword';
"@ | Out-Null
Sql @"
IF USER_ID('igamonmeet') IS NULL CREATE USER igamonmeet FOR LOGIN igamonmeet;
ALTER ROLE db_owner ADD MEMBER igamonmeet;
"@ 'igamonmeet' | Out-Null
Ok 'Database ready; login igamonmeet owns only this database'

# ---------------------------------------------------------------------------
Step 'Tomcat 9'
$Tomcat = Join-Path $InstallRoot 'tomcat'
if (Test-Path "$Tomcat\bin\catalina.bat") {
  Ok "Tomcat already in $Tomcat"
  if (Test-NetConnection -ComputerName localhost -Port 8443 -InformationLevel Quiet -WarningAction SilentlyContinue) {
    Write-Host '    Stopping running Tomcat...'
    $env:JAVA_HOME = $JavaHome
    Native { & "$Tomcat\bin\shutdown.bat" } *> $null
    Start-Sleep -Seconds 8
  }
} else {
  $index = (Invoke-WebRequest 'https://dlcdn.apache.org/tomcat/tomcat-9/' -UseBasicParsing).Content
  $version = [regex]::Matches($index, 'v(9\.0\.\d+)/') | ForEach-Object { [version]$_.Groups[1].Value } |
    Sort-Object -Descending | Select-Object -First 1
  if (-not $version) { throw 'Could not find the current Tomcat 9 version on dlcdn.apache.org.' }
  $zip = Join-Path $env:TEMP "apache-tomcat-$version-windows-x64.zip"
  Write-Host "    Downloading Tomcat $version..."
  Invoke-WebRequest "https://dlcdn.apache.org/tomcat/tomcat-9/v$version/bin/apache-tomcat-$version-windows-x64.zip" -OutFile $zip -UseBasicParsing
  Expand-Archive -Path $zip -DestinationPath $InstallRoot -Force
  Move-Item -Path (Join-Path $InstallRoot "apache-tomcat-$version") -Destination $Tomcat
  Ok "Tomcat $version in $Tomcat"
}

# TLS: self-signed certificate for localhost, HTTPS connector on 8443, plain HTTP connector removed.
$keystore = "$Tomcat\conf\igamonmeet.p12"
$ksPassword = New-Secret
if (Test-Path $keystore) { Remove-Item $keystore }
Native { & "$JavaHome\bin\keytool.exe" -genkeypair -alias tomcat -keyalg RSA -keysize 2048 -validity 825 `
  -dname 'CN=localhost' -ext 'SAN=dns:localhost,ip:127.0.0.1' `
  -keystore $keystore -storetype PKCS12 -storepass $ksPassword -keypass $ksPassword -noprompt 2>&1 } | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'keytool could not create the certificate.' }

$serverXml = "$Tomcat\conf\server.xml"
$xml = Get-Content $serverXml -Raw
$connector = @"
<Connector port="8443" protocol="org.apache.coyote.http11.Http11NioProtocol"
               SSLEnabled="true" maxPostSize="57671680">
      <SSLHostConfig protocols="TLSv1.2+TLSv1.3">
        <Certificate certificateKeystoreFile="conf/igamonmeet.p12"
                     certificateKeystoreType="PKCS12"
                     certificateKeystorePassword="$ksPassword"/>
      </SSLHostConfig>
    </Connector>
"@
$httpConnector = [regex]'(?s)<Connector port="8080".*?/>'
$tlsConnector = [regex]'(?s)<Connector port="8443" protocol="org\.apache\.coyote\.http11\.Http11NioProtocol"\s+SSLEnabled="true" maxPostSize="57671680">.*?</Connector>'
if ($tlsConnector.IsMatch($xml)) { $xml = $tlsConnector.Replace($xml, $connector, 1) }
elseif ($httpConnector.IsMatch($xml)) { $xml = $httpConnector.Replace($xml, $connector, 1) }
else { throw "Could not find the HTTP connector in $serverXml to replace." }
Set-Content -Path $serverXml -Value $xml -Encoding UTF8
Ok 'HTTPS on port 8443 (self-signed certificate), HTTP connector removed'

@"
@echo off
rem Written by Install-TestServer.ps1. TEST settings: trustServerCertificate=true accepts SQL Express's self-signed certificate.
set "JAVA_HOME=$JavaHome"
rem A machine-wide JRE_HOME (e.g. from another Java install) would override JAVA_HOME in catalina.bat.
set "JRE_HOME="
set "DB_URL=jdbc:sqlserver://localhost:1433;databaseName=igamonmeet;encrypt=true;trustServerCertificate=true"
set "DB_USER=igamonmeet"
set "DB_PASSWORD=$DbPassword"
set "JAVA_OPTS=%JAVA_OPTS% -Duser.timezone=UTC -Dfile.encoding=UTF-8"
"@ | Set-Content -Path "$Tomcat\bin\setenv.bat" -Encoding ASCII
# setenv.bat holds the DB password: limit it to Administrators and SYSTEM.
icacls "$Tomcat\bin\setenv.bat" /inheritance:r /grant:r 'Administrators:F' 'SYSTEM:F' | Out-Null
Ok 'setenv.bat written (readable by Administrators only)'

# ---------------------------------------------------------------------------
Step 'Application WAR'
if (-not $WarPath) {
  Winget-Install 'GitHub.cli'
  Native { gh auth status } *> $null
  if ($LASTEXITCODE -ne 0) {
    Write-Host '    Sign in to GitHub once (a browser opens) so the CI build can be downloaded.'
    gh auth login --hostname github.com --git-protocol https --web
    if ($LASTEXITCODE -ne 0) { throw 'GitHub sign-in failed.' }
  }
  $runId = gh run list -R $Repo --branch main --workflow CI --status success --limit 1 --json databaseId --jq '.[0].databaseId'
  if (-not $runId) { throw 'No successful CI run on main found.' }
  $dl = Join-Path $env:TEMP "igamonmeet-war-$runId"
  if (Test-Path $dl) { Remove-Item $dl -Recurse -Force }
  gh run download $runId -R $Repo -n igamonmeet-war -D $dl
  if ($LASTEXITCODE -ne 0) { throw 'Could not download the igamonmeet-war artifact.' }
  $WarPath = Join-Path $dl 'igamonmeet.war'
  Ok "Downloaded WAR from CI run $runId"
}
if (-not (Test-Path $WarPath)) { throw "WAR not found: $WarPath" }
Remove-Item "$Tomcat\webapps\igamonmeet" -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item $WarPath "$Tomcat\webapps\igamonmeet.war" -Force
Ok 'Deployed to webapps\igamonmeet.war'

# ---------------------------------------------------------------------------
Step 'Starting Tomcat'
Start-Process -FilePath "$Tomcat\bin\startup.bat" -WorkingDirectory "$Tomcat\bin"
Write-Host '    Waiting for the app (first start runs the database migrations)...'
$up = $false
for ($i = 0; $i -lt 60; $i++) {
  Start-Sleep -Seconds 5
  $health = Native { curl.exe -k -s "$BaseUrl/api/health" 2>$null }
  if ($health -match '"UP"') { $up = $true; break }
}
if (-not $up) { throw "App did not report UP. Check $Tomcat\logs\catalina.*.log and localhost.*.log." }
Ok "$BaseUrl/api/health is UP"

# ---------------------------------------------------------------------------
Step 'First Admin'
$u = $AdminUser.Replace("'", "''"); $n = $AdminName.Replace("'", "''"); $e = $AdminEmail.Replace("'", "''")
try {
  $out = Sql "SET NOCOUNT ON; EXEC dbo.usp_AdminRecovery @username = '$u', @fullName = N'$n', @email = '$e', @baseUrl = '$BaseUrl';" 'igamonmeet'
  $link = ($out | Select-String -Pattern 'https://\S+' -AllMatches).Matches | Select-Object -First 1 -ExpandProperty Value
  Write-Host ''
  Write-Host "    Open this link within 1 hour to set the password for '$AdminUser' (it works once):" -ForegroundColor Yellow
  Write-Host "    $link" -ForegroundColor Yellow
} catch {
  Write-Warning "Admin link not issued: $($_.Exception.Message)"
  Write-Warning 'If another Admin already exists, sign in as that Admin, or rerun with -AdminUser set to that username.'
}

Write-Host "`nDone. App: $BaseUrl  (the browser warns once about the self-signed certificate: choose Advanced, then Continue)." -ForegroundColor Green
Write-Host "Stop:  $Tomcat\bin\shutdown.bat     Start: $Tomcat\bin\startup.bat     Logs: $Tomcat\logs"
