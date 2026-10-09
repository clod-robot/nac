# =====================================================================
# NAC 一键部署脚本（Posh-SSH 3.2.7）
# 用法：
#   .\deploy_nac.ps1 -Step All     # 全量：DDL + nac-auth + 前端
#   .\deploy_nac.ps1 -Step Ddl     # 只执行 DDL
#   .\deploy_nac.ps1 -Step App     # 只换 nac-auth jar 并重启
#   .\deploy_nac.ps1 -Step Cert    # 只上传 PEAP 证书
#   .\deploy_nac.ps1 -Step Verify  # 只做部署后探测
# 前置：192.168.80.36 22 端口可达
# =====================================================================
param(
    [ValidateSet('All','Ddl','App','Web','Cert','Verify')]
    [string]$Step = 'All'
)

$ErrorActionPreference = 'Stop'
$Server   = '192.168.80.36'
$User     = 'mao'
$Pass     = 'MAO5201314sm'
$MysqlPw  = 'MAO5201314m'
$AppDir   = '/home/mao/nac/app'
$NacSh    = '/home/mao/nac/nac.sh'
$WebDir   = '/usr/share/nginx/html/nce'
$Repo     = 'c:\Users\king\Documents\trae_projects\nac\deploy'
$CloudDir = 'D:\迅雷下载\cloud\dist'
$Svc      = 'nac-auth'

function Write-Step($m){ Write-Host "`n===== $m =====" -ForegroundColor Cyan }

function Invoke-Remote($session, $cmd, $timeout = 90) {
    Write-Host ">> $cmd" -ForegroundColor DarkGray
    $r = Invoke-SSHCommand -SSHSession $session -Command $cmd -TimeOut $timeout
    if ($r.Output) { $r.Output | ForEach-Object { Write-Host $_ } }
    if ($r.Error)  { $r.Error  | ForEach-Object { Write-Host $_ -ForegroundColor Yellow } }
    if ($r.ExitStatus -ne 0) { Write-Host "ExitStatus=$($r.ExitStatus)" -ForegroundColor Yellow }
    return $r
}

# 0) 连通性
Write-Step "探测 $Server:22"
$tcp = Test-NetConnection -ComputerName $Server -Port 22 -WarningAction SilentlyContinue
if (-not $tcp.TcpTestSucceeded) { throw "服务器 $Server:22 不可达，终止部署" }

$sec  = ConvertTo-SecureString $Pass -AsPlainText -Force
$cred = New-Object System.Management.Automation.PSCredential($User, $sec)

$ssh = New-SSHSession -ComputerName $Server -Credential $cred -AcceptConnection
$sftp = New-SFTPSession -ComputerName $Server -Credential $cred -AcceptConnection
try {
    # ---------------- DDL ----------------
    if ($Step -in 'All','Ddl') {
        Write-Step '上传并执行 DDL'
        Set-SFTPItem -SessionId $sftp.SessionId -PathType File `
            -Path (Join-Path $Repo 'nac_aaa_ddl.sql') -Destination '/tmp/'
        $r = Invoke-Remote $ssh "mysql -uroot -p$MysqlPw --default-character-set=utf8mb4 nac_db < /tmp/nac_aaa_ddl.sql && echo DDL_OK"
        if ($r.ExitStatus -ne 0) { throw 'DDL 执行失败' }
        Invoke-Remote $ssh "echo 'SHOW TABLES LIKE \"sys_nas%\"; SHOW TABLES LIKE \"sys_radius_session\";' | mysql -uroot -p$MysqlPw nac_db"
    }

    # ---------------- PEAP 服务器证书 ----------------
    if ($Step -in 'All','App','Cert') {
        Write-Step '上传 PEAP 证书到 /home/mao/nac/certs'
        Invoke-Remote $ssh "mkdir -p /home/mao/nac/certs"
        $peapJks = 'c:\Users\king\Documents\trae_projects\nac\deploy\crypto-selftest\peap.jks'
        if (-not (Test-Path $peapJks)) { throw "peap.jks 不存在: $peapJks" }
        Set-SFTPItem -SessionId $sftp.SessionId -PathType File -Path $peapJks -Destination '/home/mao/nac/certs/'
        # ca.crt 备置于同目录，供终端分发（8021x-client "选择 CA 证书"）
        Set-SFTPItem -SessionId $sftp.SessionId -PathType File -Path 'D:\迅雷下载\certs\ca.crt' -Destination '/home/mao/nac/certs/'
        Invoke-Remote $ssh "ls -l /home/mao/nac/certs/"
    }

    # ---------------- nac-auth ----------------
    if ($Step -in 'All','App') {
        Write-Step '上传 nac-auth jar'
        $jar = Join-Path $Repo 'nac-auth-1.0-SNAPSHOT.jar'
        Invoke-Remote $ssh "rm -f $AppDir/nac-auth-1.0-SNAPSHOT.jar && echo RM_OK"
        Set-SFTPItem -SessionId $sftp.SessionId -PathType File -Path $jar -Destination "$AppDir/"
        Invoke-Remote $ssh "ls -l $AppDir/nac-auth-1.0-SNAPSHOT.jar"

        # nac.sh 无 restart：stop/start 必须用各自独立的 SSH 会话
        Write-Step '停止 nac-auth（独立会话）'
        $stopSess = New-SSHSession -ComputerName $Server -Credential $cred -AcceptConnection
        try {
            Invoke-Remote $stopSess "bash $NacSh stop $Svc; sleep 2; ps -ef | grep nac-auth | grep -v grep || echo STOPPED"
        } finally { Remove-SSHSession -SSHSession $stopSess | Out-Null }

        Write-Step '启动 nac-auth（独立会话）'
        $startSess = New-SSHSession -ComputerName $Server -Credential $cred -AcceptConnection
        try {
            Invoke-Remote $startSess "bash $NacSh start $Svc" 120
            Start-Sleep -Seconds 10
            Invoke-Remote $startSess "ps -ef | grep nac-auth | grep -v grep; ss -lun | grep -E '1812|1813|3799' || echo NO_UDP_LISTEN"
        } finally { Remove-SSHSession -SSHSession $startSess | Out-Null }
    }

    # ---------------- 前端 ----------------
    if ($Step -in 'All','Web') {
        Write-Step '上传前端 dist 到 /tmp/dist 并发布到 nginx'
        Invoke-Remote $ssh "rm -rf /tmp/dist && mkdir -p /tmp/dist"
        # 3.2.7 递归上传整个目录（远端得到 /tmp/dist 的内容需以父目录为 Destination）
        Set-SFTPItem -SessionId $sftp.SessionId -PathType Folder -Path $CloudDir -Destination '/tmp'
        $sudo = "echo $Pass | sudo -S"
        $publish = "$sudo bash -c '" +
            "mkdir -p $WebDir && " +
            "rm -rf $WebDir/js $WebDir/css $WebDir/fonts $WebDir/img $WebDir/icons $WebDir/index.html && " +
            "cp -r /tmp/dist/* $WebDir/ && ls $WebDir'"
        Invoke-Remote $ssh $publish
    }

    # ---------------- 验证 ----------------
    Write-Step '部署后探测'
    Invoke-Remote $ssh "ss -lnt | grep -E ':9000|:9001'; ss -lun | grep -E '1812|1813|3799' || true; ps -ef | grep nac-auth | grep -v grep || true"
}
finally {
    Remove-SFTPSession -SFTPSession $sftp | Out-Null
    Remove-SSHSession -SSHSession $ssh | Out-Null
}

Write-Host "`n本地侧端口探测：" -ForegroundColor Cyan
foreach ($p in 9000,9001) {
    $t = Test-NetConnection -ComputerName $Server -Port $p -WarningAction SilentlyContinue
    Write-Host ("{0}:{1} = {2}" -f $Server, $p, $t.TcpTestSucceeded)
}
Write-Host "`n完成。RADIUS UDP 1812/1813/3799 需在远端 ss 输出中确认。" -ForegroundColor Green
