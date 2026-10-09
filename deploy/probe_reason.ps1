# Probe switch 192.168.100.1 for dot1x failure reason
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
    $buf = New-Object byte[] 131072
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
Send-Line $s 'enable'
$r2 = Read-Telnet $s 1200
if ($r2 -match 'Password') {
    $enabled = $false
    foreach ($pw in @('MAO5201314sm', 'admin', 'testing123', 'cisco', '')) {
        Send-Line $s $pw
        $r3 = Read-Telnet $s 1500
        if ($r3 -match '#\s*$') { Write-Host "ENABLE_OK ($pw)"; $enabled = $true; break }
        Send-Line $s 'enable'
        $null = Read-Telnet $s 800
        $chk = Read-Telnet $s 400
        if (-not ($chk -match 'Password')) { if ($chk -match '#') { $enabled=$true; break } }
    }
    if (-not $enabled) { Write-Host 'ENABLE_FAILED' }
} else { Write-Host 'ENABLE_NOPWD' }

Send-Line $s 'terminal length 0'
$null = Read-Telnet $s 800

$cmds = @(
    @('show running-config | section radius', 4000),
    @('show running-config | include aaa', 4000),
    @('show run interface GigabitEthernet0/22', 4000),
    @('show authentication sessions interface GigabitEthernet0/22 details', 4000),
    @('show dot1x interface GigabitEthernet0/22 details', 4000),
    @('show logging | include AUTHMGR|dot1x|DOT1X|RADIUS|EAP|MAB|AUTH', 6000),
    @('show vlan brief', 4000),
    @('show aaa servers', 4000)
)
foreach ($c in $cmds) {
    Send-Line $s $c[0]
    $out = Read-Telnet $s $c[1]
    Write-Host "`n===== $($c[0]) ====="
    Write-Host $out
}
Send-Line $s 'exit'
Start-Sleep -Milliseconds 300
Write-Host 'DONE'
