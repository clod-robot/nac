# Telnet mini-client to inspect switch 192.168.100.1 (Comware/VRP style)
$ErrorActionPreference = 'Continue'
$Ip = '192.168.100.1'

function Clean-Bytes([byte[]]$b) {
    $out = New-Object System.Collections.Generic.List[byte]
    $i = 0
    while ($i -lt $b.Length) {
        if ($b[$i] -eq 255) {
            if ($i + 1 -ge $b.Length) { break }
            $cmd = $b[$i+1]
            if ($cmd -eq 250) {
                $j = $i + 2
                while ($j -lt $b.Length - 1 -and -not ($b[$j] -eq 255 -and $b[$j+1] -eq 240)) { $j++ }
                $i = $j + 2
            } elseif ($cmd -ge 251 -and $cmd -le 254) { $i += 3 } else { $i += 2 }
        } else { $out.Add($b[$i]); $i++ }
    }
    return $out.ToArray()
}

function Read-Telnet($s, $ms = 1500) {
    $buf = New-Object byte[] 65536
    $acc = New-Object System.Collections.Generic.List[byte]
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.ElapsedMilliseconds -lt $ms) {
        try {
            while ($s.DataAvailable) {
                $n = $s.Read($buf, 0, $buf.Length)
                if ($n -gt 0) { $acc.AddRange([byte[]]$buf[0..($n-1)]); $sw.Restart() }
            }
        } catch {}
        Start-Sleep -Milliseconds 80
    }
    $clean = Clean-Bytes $acc.ToArray()
    return [Text.Encoding]::GetEncoding('GB18030').GetString($clean)
}

function Send-Line($s, $text) {
    $b = [Text.Encoding]::ASCII.GetBytes($text + "`r`n")
    $s.Write($b, 0, $b.Length)
}

$credList = @(
    @('admin', 'admin'),
    @('admin', 'admin123'),
    @('admin', 'MAO5201314sm')
)

$session = $null
$usedCred = $null
foreach ($c in $credList) {
    $client = New-Object Net.Sockets.TcpClient
    try { $client.Connect($Ip, 23) } catch { Write-Host "CONNECT_FAIL $_"; exit 1 }
    $s = $client.GetStream()
    $banner = Read-Telnet $s 2500
    Write-Host "---- banner ----`n$banner"
    if ($banner -notmatch 'Username|login|用户|Login') {
        # maybe already at prompt or requires Enter first
        Send-Line $s ''
        $banner = Read-Telnet $s 1500
        Write-Host "---- banner2 ----`n$banner"
    }
    Send-Line $s $c[0]
    $r1 = Read-Telnet $s 1800
    if ($r1 -notmatch 'Password|口令') {
        Write-Host "NO_PASSWORD_PROMPT for $($c[0]): $r1"
        $client.Close(); continue
    }
    Send-Line $s $c[1]
    $r2 = Read-Telnet $s 2500
    Write-Host "---- after auth ($($c[0])) ----`n$r2"
    if ($r2 -match '<\S+>' -and $r2 -notmatch 'Failed|失败|错误|wrong|invalid|再试') {
        Write-Host "LOGIN_SUCCESS $($c[0])"
        $session = $s; $usedCred = $c[0]; break
    }
    $client.Close()
}

if (-not $session) { Write-Host 'ALL_CREDENTIALS_FAILED'; exit 2 }

# -- in CLI: disable paging (try Comware then VRP), dump radius/802.1x config --
Send-Line $session 'screen-length disable'
$null = Read-Telnet $session 1200
Send-Line $session 'screen-length 0 temporary'
$null = Read-Telnet $session 1000

$cmds = @(
    'display version',
    'display current-configuration configuration radius',
    'display current-configuration | include radius',
    'display domain',
    'display dot1x',
    'display mac-authentication',
    'display ip routing-table'
)
foreach ($cmd in $cmds) {
    Send-Line $session $cmd
    $out = Read-Telnet $session 3000
    Write-Host "`n===== $cmd ====="
    Write-Host $out
}
Send-Line $session 'quit'
Start-Sleep -Milliseconds 500
Write-Host 'DONE'
