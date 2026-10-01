<#
  MediPlan - lancement sur l'ordinateur de l'utilisateur (sans installation).
  Actions : demarrer | arreter | sauvegarder | restaurer | email
  Les données sont dans %LOCALAPPDATA%\MediPlan : elles survivent à une mise à jour du dossier MediPlan.
#>
param([ValidateSet('demarrer', 'arreter', 'sauvegarder', 'restaurer', 'email')][string]$Action = 'demarrer')

$ErrorActionPreference = 'Stop'
$Base        = Split-Path $PSScriptRoot -Parent
$Donnees     = Join-Path $env:LOCALAPPDATA 'MediPlan'
$DossierBase = Join-Path $Donnees 'base'
$Journaux    = Join-Path $Donnees 'journaux'
$Secrets     = Join-Path $Donnees 'secrets.properties'
$Ini         = Join-Path $Donnees 'my.ini'
$Sauvegardes = Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'MediPlan - sauvegardes'
$MySqlBin    = Join-Path $Base 'mysql\bin'
$Java        = Join-Path $Base 'jre\bin\javaw.exe'
$Jar         = Join-Path $Base 'app\mediplan.jar'
$PortBase    = 3307          # différent de 3306 pour ne pas gêner un autre MySQL (Laragon, XAMPP...)
$PortApp     = 8080
$NomBase     = 'mediplan_db'

function Ecrire([string]$texte, [string]$couleur = 'Gray') { Write-Host $texte -ForegroundColor $couleur }

function Port-Ouvert([int]$port) {
    $c = New-Object System.Net.Sockets.TcpClient
    try { $r = $c.BeginConnect('127.0.0.1', $port, $null, $null); return ($r.AsyncWaitHandle.WaitOne(300) -and $c.Connected) }
    catch { return $false } finally { $c.Close() }
}

function Chemin-Ini([string]$p) { return $p.Replace('\', '/') }

# Lance un programme de la base (mysqld, mysql...) sans que ses messages sur stderr ne stoppent le script
function Executer([string]$programme, [string[]]$arguments) {
    $ancien = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & (Join-Path $MySqlBin $programme) @arguments 2>&1 | Out-Null; return $LASTEXITCODE }
    finally { $ErrorActionPreference = $ancien }
}

function Preparer {
    foreach ($d in @($Donnees, $DossierBase, $Journaux, $Sauvegardes)) { New-Item -ItemType Directory -Force -Path $d | Out-Null }

    # Réglages de la base (un seul fichier, dans les données de l'utilisateur)
    @"
[mysqld]
basedir=$(Chemin-Ini (Join-Path $Base 'mysql'))
datadir=$(Chemin-Ini $DossierBase)
port=$PortBase
bind-address=127.0.0.1
mysqlx=0
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
log-error=$(Chemin-Ini (Join-Path $Journaux 'base.err'))
"@ | Set-Content -Path $Ini -Encoding ASCII

    # Clé de sécurité propre à cet ordinateur + réglages e-mail (fournis avec le dossier, ou vides)
    if (-not (Test-Path $Secrets)) {
        $cle = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 48 | ForEach-Object { [char]$_ })
        $lignes = @("MEDIPLAN_JWT_SECRET=$cle")
        $fourni = Join-Path $Base 'secrets.properties'
        if (Test-Path $fourni) { $lignes += Get-Content $fourni | Where-Object { $_ -notmatch '^MEDIPLAN_JWT_SECRET' } }
        else { $lignes += @('# Lancez "Configurer les e-mails" pour renseigner ces deux lignes', 'MEDIPLAN_MAIL_USER=', 'MEDIPLAN_MAIL_PASSWORD=') }
        $lignes | Set-Content -Path $Secrets -Encoding ASCII
    }

    # Première utilisation : création de la base
    if (-not (Test-Path (Join-Path $DossierBase 'mysql'))) {
        Ecrire 'Première utilisation : création de la base de données (une minute environ)...' Cyan
        Executer 'mysqld.exe' @("--defaults-file=$Ini", '--initialize-insecure') | Out-Null
        if (-not (Test-Path (Join-Path $DossierBase 'mysql'))) { throw "La base n'a pas pu être créée. Détails : $Journaux\base.err" }
    }
}

# Raccourcis « MediPlan » sur le Bureau et dans le menu Démarrer.
# Ils lancent PowerShell (un .exe) et non le .bat : c'est ce qui permet de les épingler à la barre des tâches.
function Creer-Raccourcis([switch]$Forcer) {
    $cibles = @(
        (Join-Path ([Environment]::GetFolderPath('Desktop')) 'MediPlan.lnk'),
        (Join-Path ([Environment]::GetFolderPath('Programs')) 'MediPlan.lnk')
    )
    $shell = New-Object -ComObject WScript.Shell
    $crees = 0
    foreach ($chemin in $cibles) {
        if ((Test-Path $chemin) -and -not $Forcer) {
            # Raccourci existant : on le met à jour seulement s'il pointe vers un autre dossier MediPlan
            $ancien = $shell.CreateShortcut($chemin)
            if ($ancien.Arguments -like "*$PSScriptRoot*") { continue }
        }
        $lien = $shell.CreateShortcut($chemin)
        $lien.TargetPath = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
        $lien.Arguments = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Minimized -File `"$PSScriptRoot\mediplan.ps1`" demarrer"
        $lien.WorkingDirectory = $Base
        $lien.IconLocation = Join-Path $PSScriptRoot 'mediplan.ico'
        $lien.WindowStyle = 7
        $lien.Description = 'Ouvrir MediPlan'
        $lien.Save()
        $crees++
    }
    if ($crees -gt 0) { Ecrire 'Raccourcis « MediPlan » prêts : Bureau et menu Démarrer.' Green }
}

function Demarrer-Base {
    if (Port-Ouvert $PortBase) { return }
    Start-Process -FilePath (Join-Path $MySqlBin 'mysqld.exe') -ArgumentList "--defaults-file=`"$Ini`"" -WindowStyle Hidden
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Milliseconds 500
        if (Port-Ouvert $PortBase) {
            Executer 'mysql.exe' @('--host=127.0.0.1', "--port=$PortBase", '--user=root',
                '-e', "CREATE DATABASE IF NOT EXISTS $NomBase CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci") | Out-Null
            return
        }
    }
    throw "La base de données ne démarre pas. Détails : $Journaux\base.err"
}

function Sauvegarde([string]$fichier) {
    Executer 'mysqldump.exe' @('--host=127.0.0.1', "--port=$PortBase", '--user=root', '--single-transaction',
        '--default-character-set=utf8mb4', '--databases', $NomBase, "--result-file=$fichier") | Out-Null
    return (Test-Path $fichier) -and ((Get-Item $fichier).Length -gt 1000)
}

function Sauvegarde-Du-Jour {
    $f = Join-Path $Sauvegardes ("mediplan-{0:yyyy-MM-dd}.sql" -f (Get-Date))
    if (-not (Test-Path $f)) {
        if (Sauvegarde $f) {
            # On garde les 30 dernières sauvegardes automatiques
            Get-ChildItem $Sauvegardes -Filter 'mediplan-????-??-??.sql' | Sort-Object Name -Descending | Select-Object -Skip 30 | Remove-Item -Force
        } else { Remove-Item $f -Force -ErrorAction SilentlyContinue }
    }
}

function Processus-Application {
    $c = Get-NetTCPConnection -LocalPort $PortApp -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($c) { return Get-Process -Id $c.OwningProcess -ErrorAction SilentlyContinue }
    return $null
}

function Demarrer-Application {
    $p = Processus-Application
    if ($p) {
        if ($p.Path -and $p.Path.StartsWith($Base)) { return }   # déjà lancée
        throw "Le port $PortApp est déjà utilisé par un autre programme ($($p.ProcessName)). Fermez-le puis relancez MediPlan."
    }
    $env:MEDIPLAN_DB_URL = "jdbc:mysql://127.0.0.1:$PortBase/$($NomBase)?useSSL=false&serverTimezone=Africa/Dakar&allowPublicKeyRetrieval=true"
    $env:MEDIPLAN_DB_USER = 'root'
    $env:MEDIPLAN_DB_PASSWORD = ''
    $env:MEDIPLAN_PORT = "$PortApp"
    # javaw : pas de fenêtre ; le journal est écrit par l'application elle-même
    Start-Process -FilePath $Java -WorkingDirectory $Donnees -ArgumentList @(
        '-Xmx512m', '-Dfile.encoding=UTF-8', '-jar', "`"$Jar`"",
        "--logging.file.name=`"$(Join-Path $Journaux 'mediplan.log')`"")
    Write-Host 'Démarrage de MediPlan' -ForegroundColor Cyan -NoNewline
    for ($i = 0; $i -lt 120; $i++) {
        Start-Sleep -Seconds 1
        Write-Host '.' -NoNewline
        try {
            $r = Invoke-WebRequest -Uri "http://127.0.0.1:$PortApp/api/installation/statut" -UseBasicParsing -TimeoutSec 2
            if ($r.StatusCode -eq 200) { Write-Host ''; return }
        } catch { }
    }
    Write-Host ''
    throw "MediPlan ne répond pas. Détails : $Journaux\mediplan.log"
}

# MediPlan s'ouvre dans sa propre fenêtre, sans barre d'adresse, comme une application
function Ouvrir-Fenetre {
    $url = "http://localhost:$PortApp"
    $navigateurs = @(
        (Join-Path ${env:ProgramFiles(x86)} 'Microsoft\Edge\Application\msedge.exe'),
        (Join-Path $env:ProgramFiles 'Microsoft\Edge\Application\msedge.exe'),
        (Join-Path $env:ProgramFiles 'Google\Chrome\Application\chrome.exe'),
        (Join-Path ${env:ProgramFiles(x86)} 'Google\Chrome\Application\chrome.exe'),
        (Join-Path $env:LOCALAPPDATA 'Google\Chrome\Application\chrome.exe')
    )
    $nav = $navigateurs | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
    if ($nav) { Start-Process $nav -ArgumentList "--app=$url", '--start-maximized' }
    else { Start-Process $url }
}

function Adresse-Reseau {
    try {
        $a = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop | Where-Object {
            $_.IPAddress -notmatch '^(127\.|169\.254\.)' -and $_.InterfaceAlias -notmatch 'vEthernet|VirtualBox|VMware|WSL|Loopback|Bluetooth'
        } | Sort-Object { if ($_.IPAddress -like '192.168.*') { 0 } else { 1 } } | Select-Object -First 1
        return $a.IPAddress
    } catch { return $null }
}

function Arreter {
    $p = Processus-Application
    if ($p -and $p.Path -and $p.Path.StartsWith($Base)) { Stop-Process -Id $p.Id -Force; Ecrire 'Application arrêtée.' }
    if (Port-Ouvert $PortBase) {
        Executer 'mysqladmin.exe' @('--host=127.0.0.1', "--port=$PortBase", '--user=root', 'shutdown') | Out-Null
        Ecrire 'Base de données arrêtée.'
    }
}

try {
    switch ($Action) {
        'demarrer' {
            Preparer
            Creer-Raccourcis
            Demarrer-Base
            Sauvegarde-Du-Jour
            Demarrer-Application
            Ouvrir-Fenetre
            Ecrire 'MediPlan est ouvert. Vous pouvez fermer cette fenêtre noire.' Green
            $ip = Adresse-Reseau
            if ($ip) { Ecrire "Sur le téléphone (même Wi-Fi) : http://$($ip):$PortApp" Cyan }
            Start-Sleep -Seconds 4
        }
        'arreter' {
            Arreter
            Ecrire 'MediPlan est arrêté.' Green
            Start-Sleep -Seconds 2
        }
        'sauvegarder' {
            Preparer; Demarrer-Base
            $f = Join-Path $Sauvegardes ("mediplan-{0:yyyy-MM-dd_HH'h'mm}-manuelle.sql" -f (Get-Date))
            if (Sauvegarde $f) { Ecrire "Sauvegarde enregistrée : $f" Green; Start-Process explorer.exe "/select,`"$f`"" }
            else { throw 'La sauvegarde a échoué.' }
            Start-Sleep -Seconds 3
        }
        'restaurer' {
            Add-Type -AssemblyName System.Windows.Forms
            $d = New-Object System.Windows.Forms.OpenFileDialog
            $d.InitialDirectory = $Sauvegardes; $d.Filter = 'Sauvegardes MediPlan (*.sql)|*.sql'; $d.Title = 'Choisir la sauvegarde à restaurer'
            if ($d.ShowDialog() -ne 'OK') { return }
            $ok = [System.Windows.Forms.MessageBox]::Show("Remplacer toutes les données actuelles par la sauvegarde :`n$($d.FileName) ?`n`nUne copie des données actuelles sera faite avant.", 'Restaurer MediPlan', 'YesNo', 'Warning')
            if ($ok -ne 'Yes') { return }
            Preparer; Demarrer-Base
            $avant = Join-Path $Sauvegardes ("mediplan-{0:yyyy-MM-dd_HH'h'mm}-avant-restauration.sql" -f (Get-Date))
            Sauvegarde $avant | Out-Null
            $p = Processus-Application; if ($p -and $p.Path.StartsWith($Base)) { Stop-Process -Id $p.Id -Force }
            Executer 'mysql.exe' @('--host=127.0.0.1', "--port=$PortBase", '--user=root', '--default-character-set=utf8mb4',
                '-e', "DROP DATABASE IF EXISTS $NomBase; source $(Chemin-Ini $d.FileName)") | Out-Null
            Ecrire 'Sauvegarde restaurée. Relancez MediPlan.' Green
            Start-Sleep -Seconds 3
        }
        'email' {
            Preparer
            Ecrire "Adresse Gmail qui enverra les plannings :" Cyan
            $adresse = Read-Host
            Ecrire "Mot de passe d'application Gmail (16 lettres, sans espaces) - il ne s'affiche pas :" Cyan
            $s = Read-Host -AsSecureString
            $mdp = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s)) -replace '\s', ''
            $lignes = Get-Content $Secrets | Where-Object { $_ -notmatch '^MEDIPLAN_MAIL_(USER|PASSWORD)=' -and $_ -notmatch 'Configurer les e-mails' }
            $lignes += "MEDIPLAN_MAIL_USER=$($adresse.Trim())", "MEDIPLAN_MAIL_PASSWORD=$mdp"
            $lignes | Set-Content -Path $Secrets -Encoding ASCII
            Ecrire 'E-mail enregistré. Redémarrage de MediPlan pour en tenir compte...' Green
            $p = Processus-Application; if ($p -and $p.Path.StartsWith($Base)) { Stop-Process -Id $p.Id -Force; Start-Sleep 2 }
            Demarrer-Base; Demarrer-Application
            Ecrire 'C''est prêt.' Green
            Start-Sleep -Seconds 2
        }
    }
} catch {
    Ecrire ''
    Ecrire "Problème : $($_.Exception.Message)" Red
    Ecrire 'Appuyez sur Entrée pour fermer.'
    Read-Host | Out-Null
    exit 1
}
