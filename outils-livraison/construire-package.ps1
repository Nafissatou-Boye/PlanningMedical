<#
  Construit « MediPlan portable » : un dossier (et un zip) à donner à l'utilisateur.
  Rien à installer chez lui : Java et la base de données sont inclus.

  À lancer sur l'ordinateur de développement, depuis PowerShell :
      cd C:\Users\gazal\Desktop\Planning\outils-livraison
      .\construire-package.ps1

  Le script utilise ce qui est déjà installé ici : le JDK 21 (JAVA_HOME) et le MySQL de Laragon.
#>
param(
    [string]$Projets = (Split-Path $PSScriptRoot -Parent),
    [string]$Jdk = $env:JAVA_HOME,
    [string]$MySql = '',
    [switch]$SansTest
)

$ErrorActionPreference = 'Stop'
function Etape([string]$t) { Write-Host "`n=== $t" -ForegroundColor Cyan }
function Ok([string]$t) { Write-Host "    $t" -ForegroundColor Green }

$Api    = Join-Path $Projets 'mediplan-api'
$Front  = Join-Path $Projets 'mediplan-front'
$Sortie = Join-Path $Projets 'livraison'
$Paquet = Join-Path $Sortie 'MediPlan'
$Modele = Join-Path $PSScriptRoot 'modele'

# ---------------------------------------------------------------------
Etape '1/7 Vérifications'
if (-not (Test-Path "$Api\pom.xml"))          { throw "mediplan-api introuvable dans $Projets" }
if (-not (Test-Path "$Front\package.json"))   { throw "mediplan-front introuvable dans $Projets" }
if (-not $Jdk -or -not (Test-Path "$Jdk\bin\jlink.exe")) {
    $Jdk = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Select-Object -Last 1 -ExpandProperty FullName
}
if (-not $Jdk -or -not (Test-Path "$Jdk\bin\jlink.exe")) { throw 'JDK 21 introuvable : indiquez-le avec -Jdk "C:\...\jdk-21..."' }
if (-not $MySql) {
    $MySql = Get-ChildItem 'C:\laragon\bin\mysql' -Directory -Filter 'mysql-8*' -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -Last 1 -ExpandProperty FullName
}
if (-not $MySql -or -not (Test-Path "$MySql\bin\mysqld.exe")) { throw 'MySQL introuvable : indiquez-le avec -MySql "C:\laragon\bin\mysql\mysql-8.x-winx64"' }
Ok "JDK   : $Jdk"
Ok "MySQL : $MySql"
$env:JAVA_HOME = $Jdk

# ---------------------------------------------------------------------
Etape "2/7 Écran Angular (ng build)"
Push-Location $Front
$env:NG_CLI_ANALYTICS = 'false'   # pas de question sur les statistiques d'Angular
try {
    if (-not (Test-Path 'node_modules')) { npm install --legacy-peer-deps; if ($LASTEXITCODE) { throw 'npm install a échoué' } }
    npx ng build --configuration production
    if ($LASTEXITCODE) { throw 'ng build a échoué' }
} finally { Pop-Location }
$Browser = Join-Path $Front 'dist\mediplan-front\browser'
Ok 'Écran compilé.'

# ---------------------------------------------------------------------
Etape '3/7 Application Java (écran inclus)'
$Static = Join-Path $Api 'src\main\resources\static'
Remove-Item $Static -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item $Browser $Static -Recurse
Push-Location $Api
try {
    .\mvnw.cmd -q -DskipTests clean package
    if ($LASTEXITCODE) { throw 'La compilation Maven a échoué' }
} finally {
    Pop-Location
    Remove-Item $Static -Recurse -Force -ErrorAction SilentlyContinue   # le code source reste propre
}
$JarSource = Get-ChildItem "$Api\target" -Filter '*.jar' | Where-Object { $_.Name -notlike '*plain*' } | Select-Object -First 1
if (-not $JarSource) { throw 'Le fichier .jar est introuvable dans target' }
Ok $JarSource.Name

# ---------------------------------------------------------------------
Etape '4/7 Dossier MediPlan'
Remove-Item $Sortie -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$Paquet\app" | Out-Null
Copy-Item $JarSource.FullName "$Paquet\app\mediplan.jar"
Copy-Item "$Modele\*" $Paquet -Recurse
$SecretsFournis = Join-Path $PSScriptRoot 'secrets-utilisateur.properties'
if (Test-Path $SecretsFournis) { Copy-Item $SecretsFournis "$Paquet\secrets.properties"; Ok 'E-mail préconfiguré inclus.' }
Ok $Paquet

# ---------------------------------------------------------------------
Etape '5/7 Java allégé (jlink)'
$Modules = 'java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,' +
           'java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,java.xml,' +
           'jdk.charsets,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.localedata,jdk.management,jdk.naming.dns,jdk.unsupported,jdk.zipfs'
& "$Jdk\bin\jlink.exe" --add-modules $Modules --strip-debug --no-man-pages --no-header-files --compress=zip-6 --output "$Paquet\jre"
if ($LASTEXITCODE) { throw 'jlink a échoué' }
Ok ('Java : {0:N0} Mo' -f ((Get-ChildItem "$Paquet\jre" -Recurse | Measure-Object Length -Sum).Sum / 1MB))

# ---------------------------------------------------------------------
Etape '6/7 Base de données (MySQL de Laragon)'
New-Item -ItemType Directory -Force -Path "$Paquet\mysql\bin" | Out-Null
Get-ChildItem "$MySql\bin" | Where-Object {
    $_.Name -in @('mysqld.exe', 'mysql.exe', 'mysqladmin.exe', 'mysqldump.exe') -or $_.Extension -eq '.dll'
} | Copy-Item -Destination "$Paquet\mysql\bin"
Copy-Item "$MySql\share" "$Paquet\mysql\share" -Recurse
if (Test-Path "$MySql\lib\plugin") { Copy-Item "$MySql\lib" "$Paquet\mysql\lib" -Recurse }
# Bibliothèques Visual C++ nécessaires à MySQL, au cas où l'ordinateur de l'utilisateur ne les a pas
foreach ($dll in 'msvcp140.dll', 'vcruntime140.dll', 'vcruntime140_1.dll', 'msvcp140_1.dll', 'msvcp140_2.dll') {
    $src = Join-Path $env:WINDIR "System32\$dll"
    if ((Test-Path $src) -and -not (Test-Path "$Paquet\mysql\bin\$dll")) { Copy-Item $src "$Paquet\mysql\bin" }
}
Ok ('MySQL : {0:N0} Mo' -f ((Get-ChildItem "$Paquet\mysql" -Recurse | Measure-Object Length -Sum).Sum / 1MB))

# ---------------------------------------------------------------------
Etape '7/7 Test de démarrage et zip'
if (-not $SansTest) {
    # Test complet dans un dossier de données temporaire (ne touche ni à Laragon ni aux vraies données)
    $tmp = Join-Path $env:TEMP ('mediplan-test-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
    $ini = Join-Path $tmp 'my.ini'
    New-Item -ItemType Directory -Force -Path "$tmp\base" | Out-Null
    @"
[mysqld]
basedir=$(("$Paquet\mysql").Replace('\','/'))
datadir=$(("$tmp\base").Replace('\','/'))
port=3317
bind-address=127.0.0.1
mysqlx=0
log-error=$(("$tmp\base.err").Replace('\','/'))
"@ | Set-Content $ini -Encoding ASCII
    $ErrorActionPreference = 'Continue'
    & "$Paquet\mysql\bin\mysqld.exe" "--defaults-file=$ini" --initialize-insecure 2>&1 | Out-Null
    $db = Start-Process "$Paquet\mysql\bin\mysqld.exe" -ArgumentList "--defaults-file=`"$ini`"" -WindowStyle Hidden -PassThru
    Start-Sleep 6
    & "$Paquet\mysql\bin\mysql.exe" --host=127.0.0.1 --port=3317 --user=root -e 'CREATE DATABASE mediplan_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci' 2>&1 | Out-Null
    $env:MEDIPLAN_DB_URL = 'jdbc:mysql://127.0.0.1:3317/mediplan_db?useSSL=false&serverTimezone=Africa/Dakar&allowPublicKeyRetrieval=true'
    $env:MEDIPLAN_DB_USER = 'root'; $env:MEDIPLAN_DB_PASSWORD = ''; $env:MEDIPLAN_PORT = '8097'
    $app = Start-Process "$Paquet\jre\bin\java.exe" -ArgumentList '-jar', "`"$Paquet\app\mediplan.jar`"" -WorkingDirectory $tmp `
        -RedirectStandardOutput "$tmp\app.log" -RedirectStandardError "$tmp\app-err.log" -WindowStyle Hidden -PassThru
    $reussi = $false
    for ($i = 0; $i -lt 90 -and -not $reussi; $i++) {
        Start-Sleep 1
        try {
            $a = Invoke-WebRequest 'http://127.0.0.1:8097/api/installation/statut' -UseBasicParsing -TimeoutSec 2
            $b = Invoke-WebRequest 'http://127.0.0.1:8097/planning' -UseBasicParsing -TimeoutSec 2
            $reussi = $a.StatusCode -eq 200 -and $b.Content -match '<app-root'
        } catch { }
    }
    Stop-Process -Id $app.Id -Force -ErrorAction SilentlyContinue
    & "$Paquet\mysql\bin\mysqladmin.exe" --host=127.0.0.1 --port=3317 --user=root shutdown 2>&1 | Out-Null
    Start-Sleep 2
    $ErrorActionPreference = 'Stop'
    if (-not $reussi) {
        Write-Host "`nLe test a échoué. Journal de l'application : $tmp\app.log" -ForegroundColor Red
        Get-Content "$tmp\app.log" -Tail 40
        throw 'Test de démarrage en échec'
    }
    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
    Ok 'Test réussi : base créée, API et écran répondent.'
}

$Zip = Join-Path $Sortie ('MediPlan-portable-{0:yyyy-MM-dd}.zip' -f (Get-Date))
Compress-Archive -Path $Paquet -DestinationPath $Zip -CompressionLevel Optimal
Ok ('{0} ({1:N0} Mo)' -f $Zip, ((Get-Item $Zip).Length / 1MB))
Write-Host "`nTerminé. Donnez ce fichier zip à l'utilisateur, avec LISEZ-MOI.txt." -ForegroundColor Green
