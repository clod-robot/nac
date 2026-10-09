# =====================================================================
# NAC vertical-privilege hardening: full-rollout deploy (7 services + web)
# nac-common adds GatewayTrustFilter -> all 7 jars must be replaced together,
# and nac-gateway must start BEFORE downstream services (old gateway sends no
# signature, which would make new downstream return 403 for everything).
# Usage:
#   .\deploy_all_v2.ps1 -Step All     # backup + upload jars + restart + web
#   .\deploy_all_v2.ps1 -Step App     # backup + upload jars + restart only
#   .\deploy_all_v2.ps1 -Step Web     # publish frontend only
#   .\deploy_all_v2.ps1 -Step Status  # remote status only
# =====================================================================
param(
    [ValidateSet('All','App','Web','Status')]
    [string]$Step = 'All'
)

$ErrorActionPreference = 'Stop'
$Server   = '192.168.80.36'
$User     = 'mao'
$Pass     = 'MAO5201314sm'
$AppDir   = '/home/mao/nac/app'
$NacSh    = '/home/mao/nac/nac.sh'
$WebDir   = '/usr/share/nginx/html/nce'
$Repo     = 'c:\Users\king\Documents\trae_projects\nac\deploy'
$CloudDir = 'D:\迅雷下载\cloud\dist'
$Backup   = 'backup-0920-authz'
$Services = @('nac-gateway','nac-auth','nac-user','nac-device','nac-config','nac-log','nac-monitor')

function Write-Step($m){ Write-Host "`n===== $m =====" -ForegroundColor Cyan }
function Invoke-Remote($session, $cmd, $timeout = 120) {
    Write-Host ">> $cmd" -ForegroundColor DarkGray
    $r = Invoke-SSHCommand -SSHSession $session -Command $cmd -TimeOut $timeout
    if ($r.Output) { $r.Output | ForEach-Object { Write-Host $_ } }
    if ($r.Error)  { $r.Error  | ForEach-Object { Write-Host $_ -ForegroundColor Yellow } }
    return $r
}

Import-Module Posh-SSH
$sec  = ConvertTo-SecureString $Pass -AsPlainText -Force
$cred = New-Object System.Management.Automation.PSCredential($User, $sec)

if ($Step -eq 'Status') {
    $ssh = New-SSHSession -ComputerName $Server -Credential $cred -AcceptKey
    try { Invoke-Remote $ssh "bash $NacSh status" } finally { Remove-SSHSession -SSHSession $ssh | Out-Null }
    return
}

Write-Step "Probe $Server:22"
$tcp = Test-NetConnection -ComputerName $Server -Port 22 -WarningAction SilentlyContinue
if (-not $tcp.TcpTestSucceeded) { throw "Server $Server:22 unreachable, abort" }

$ssh  = New-SSHSession -ComputerName $Server -Credential $cred -AcceptKey
$sftp = New-SFTPSession -ComputerName $Server -Credential $cred -AcceptKey
try {
    if ($Step -in 'All','App') {
        Write-Step "Backup current jars to $Backup"
        Invoke-Remote $ssh "mkdir -p $AppDir/$Backup; for j in nac-gateway nac-auth nac-user nac-device nac-config nac-log nac-monitor; do f=$AppDir/`${j}-1.0-SNAPSHOT.jar; [ -f `$f ] && cp -n `$f $AppDir/$Backup/; done; ls -l $AppDir/$Backup/" | Out-Null

        Write-Step 'Upload 7 new jars to /tmp/nac-jars'
        Invoke-Remote $ssh "mkdir -p /tmp/nac-jars && rm -f /tmp/nac-jars/*.jar" | Out-Null
        foreach ($s in $Services) {
            $jar = "$s-1.0-SNAPSHOT.jar"
            $local = Join-Path $Repo $jar
            if (-not (Test-Path $local)) { throw "Missing $local" }
            Write-Host "  SFTP upload $jar ($([math]::Round((Get-Item $local).Length/1MB,1)) MB)"
            Set-SFTPItem -SessionId $sftp.SessionId -Path $local -Destination '/tmp/nac-jars/'
        }
        Write-Step 'Atomically move jars into AppDir'
        Invoke-Remote $ssh "for j in nac-gateway nac-auth nac-user nac-device nac-config nac-log nac-monitor; do mv -f /tmp/nac-jars/`${j}-1.0-SNAPSHOT.jar $AppDir/; done; ls -l $AppDir/nac-*-1.0-SNAPSHOT.jar"

        Write-Step 'stop-all (separate session)'
        $stopSess = New-SSHSession -ComputerName $Server -Credential $cred -AcceptKey
        try {
            Invoke-Remote $stopSess "bash $NacSh stop-all; sleep 2; pgrep -af 'app/nac-' || echo ALL_STOPPED" 180 | Out-Null
        } finally { Remove-SSHSession -SSHSession $stopSess | Out-Null }

        Write-Step 'start-all (gateway first; separate session for nohup)'
        $startSess = New-SSHSession -ComputerName $Server -Credential $cred -AcceptKey
        try {
            Invoke-Remote $startSess "bash $NacSh start-all" 120 | Out-Null
        } finally { Remove-SSHSession -SSHSession $startSess | Out-Null }

        Write-Step 'Wait for ports 9000-9006 (up to 150s)'
        $deadline = (Get-Date).AddSeconds(150)
        $ok = $false
        while ((Get-Date) -lt $deadline) {
            Start-Sleep -Seconds 10
            $r = Invoke-SSHCommand -SSHSession $ssh -Command "ss -lnt | grep -oE ':900[0-6]' | sort -u | tr '\n' ' '" -TimeOut 30
            $up = ($r.Output | Where-Object { $_ -match ':900' }) -join ''
            Write-Host "  listening: $up"
            $cnt = ([regex]::Matches($up, ':900[0-6]')).Count
            if ($cnt -ge 7) { $ok = $true; break }
        }
        if (-not $ok) {
            Write-Host 'Not all ports up; process list and recent logs:' -ForegroundColor Red
            Invoke-Remote $ssh "bash $NacSh status; for s in nac-gateway nac-auth nac-user nac-device nac-config nac-log nac-monitor; do echo ==== `$s ====; tail -n 15 /home/mao/nac/logs/`$s.out; done" 60 | Out-Null
            throw 'Service startup timeout'
        }
        Write-Host 'All 7 services listening on 9000-9006' -ForegroundColor Green
        Invoke-Remote $ssh "bash $NacSh status" | Out-Null
    }

    if ($Step -in 'All','Web') {
        Write-Step 'Upload dist and publish to nginx'
        Invoke-Remote $ssh "rm -rf /tmp/dist && mkdir -p /tmp/dist" | Out-Null
        Set-SFTPItem -SessionId $sftp.SessionId -Path $CloudDir -Destination '/tmp'
        $sudo = "echo $Pass | sudo -S"
        $publish = "$sudo bash -c '" +
            "mkdir -p $WebDir; " +
            "rm -rf $WebDir/js $WebDir/css $WebDir/fonts $WebDir/img $WebDir/icons $WebDir/index.html; " +
            "cp -r /tmp/dist/* $WebDir/; ls $WebDir'"
        Invoke-Remote $ssh $publish | Out-Null
        Write-Host 'Frontend published' -ForegroundColor Green
    }
}
finally {
    Remove-SFTPSession -SFTPSession $sftp -ErrorAction SilentlyContinue | Out-Null
    Remove-SSHSession -SSHSession $ssh -ErrorAction SilentlyContinue | Out-Null
}
Write-Host "`nDeploy finished." -ForegroundColor Green
