# Cisco IOS switch probe: 192.168.100.1 (telnet, user mode -> enable)
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
    return [Text.Encoding]::GetEncoding('GB18030').GetString((Clean-Bytes $acc.ToArray()))
}
function Send-Line($s, $text) {
    $b = [Text.Encoding]::ASCII.GetBytes($text + "`r`n")
    $s.Write($b, 0, $b.Length)
}

$client = New-Object Net.Sockets.TcpClient
$client.Connect($Ip, 23)
$s = $client.GetStream()
$r = Read-Telnet $s 2500
Write-Host "---- banner ----`n$r"

if ($r -match 'Username|Password|login') {
    Write-Host 'DEVICE_REQUIRES_LOGIN (unexpected)'; Send-Line $s 'admin'; Read-Telnet $s 1500 | Out-Null; Send-Line $s 'admin'; Read-Telnet $s 2000 | Out-Null
} else {
    Send-Line $s 'enable'
    $r2 = Read-Telnet $s 1500
    if ($r2 -match 'Password') {
        $enabled = $false
        foreach ($pw in @('', 'admin', 'MAO5201314sm', 'cisco', 'testing123')) {
            Send-Line $s $pw
            $r3 = Read-Telnet $s 2000
            if ($r3 -match '#') { Write-Host 'ENABLE_OK'; $enabled = $true; break }
            Send-Line $s 'enable'
            $null = Read-Telnet $s 1200
            $chk = Read-Telnet $s 400
            if ($chk -match 'Password') { continue }
        }
        if (-not $enabled) { Write-Host 'ENABLE_FAILED - continuing in user mode' }
    } elseif ($r2 -match '#') { Write-Host 'ENABLE_OK_NOPASSWORD' }
}

Send-Line $s 'terminal length 0'
$null = Read-Telnet $s 1200

$cmds = @(
    @('show version', 4000),
    @('show ip interface brief', 4000),
    @('show ip route 192.168.80.36', 3000),
    @('show ip route', 4000),
    @('show running-config | include radius|aaa|dot1x|access-list', 6000),
    @('show running-config | section radius', 5000),
    @('show aaa servers', 4000),
    @('show dot1x all summary', 4000),
    @('ping 192.168.80.36', 14000),
    @('ping 192.168.100.250', 14000)
)
foreach ($c in $cmds) {
    Send-Line $s $c[0]
    $out = Read-Telnet $s $c[1]
    Write-Host "`n===== $($c[0]) ====="
    Write-Host $out
}
Send-Line $s 'exit'
Start-Sleep -Milliseconds 400
Write-Host 'DONE'
