# Maipu IS320: enable with password + full running-config dump
$ErrorActionPreference = 'Continue'
$Ip = '192.168.100.1'
$EnablePass = 'Maipu@1993'

function Clean-Bytes([byte[]]$b) {
    if ($null -eq $b -or $b.Length -eq 0) { return ,@() }
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
    return ,$out.ToArray()
}
function Read-Telnet($s, $ms = 1500) {
    $buf = New-Object byte[] 65536
    $acc = New-Object System.Collections.Generic.List[byte]
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.ElapsedMilliseconds -lt $ms) {
        try {
            while ($s.DataAvailable) {
                $n = $s.Read($buf, 0, $buf.Length)
                if ($n -gt 0) { for ($k=0; $k -lt $n; $k++) { $acc.Add($buf[$k]) }; $sw.Restart() }
            }
        } catch {}
        Start-Sleep -Milliseconds 80
    }
    if ($acc.Count -eq 0) { return '' }
    return [Text.Encoding]::GetEncoding('GB18030').GetString((Clean-Bytes $acc.ToArray()))
}
function Send-Line($s, $text) {
    $b = [Text.Encoding]::ASCII.GetBytes($text + "`r`n")
    $s.Write($b, 0, $b.Length)
}
function Read-UntilQuiet($s, $quietMs = 2600, $maxMs = 60000) {
    $all = ''
    $lastData = [Diagnostics.Stopwatch]::StartNew()
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.ElapsedMilliseconds -lt $maxMs) {
        $chunk = Read-Telnet $s 700
        if ($chunk) {
            $all += $chunk
            $lastData.Restart()
            if ($chunk -match 'More\s*--') {
                $sp = [Text.Encoding]::ASCII.GetBytes(' ')
                $s.Write($sp, 0, $sp.Length)
            }
        } elseif ($lastData.ElapsedMilliseconds -gt $quietMs -and $all.Length -gt 0) {
            break
        }
    }
    return $all
}

$client = New-Object Net.Sockets.TcpClient
$client.Connect($Ip, 23)
$s = $client.GetStream()
$r = Read-Telnet $s 2500
Write-Host "---- banner ----`n[$r]"

Send-Line $s 'terminal length 0'
$null = Read-Telnet $s 1000

Send-Line $s 'enable'
$r2 = Read-Telnet $s 1800
Write-Host "---- after enable ----`n[$r2]"
if ($r2 -match 'Password') {
    Send-Line $s $EnablePass
    $r3 = Read-Telnet $s 2200
    Write-Host "---- after password ----`n[$r3]"
    if ($r3 -notmatch '#') { Write-Host 'ENABLE_FAILED'; Send-Line $s 'exit'; exit 2 }
} elseif ($r2 -match '#') { Write-Host 'ENABLE_OK_NOPASSWORD' }

Write-Host "`n===== show running-config ====="
Send-Line $s 'show running-config'
$cfg = Read-UntilQuiet $s
Write-Host $cfg

Send-Line $s 'exit'
Start-Sleep -Milliseconds 400
Write-Host 'DONE'
