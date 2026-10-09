# Elevated setup: start dot3svc + apply 802.1X (EAP-MSCHAPv2) profile to USB Realtek NIC
$ErrorActionPreference = 'Continue'
$log = 'C:\Users\king\Documents\trae_projects\nac\deploy\lan_setup_result.txt'
"=== 802.1X setup start $(Get-Date) ===" | Out-File $log -Encoding UTF8

# locate NIC by description (avoids non-ASCII interface name in script)
$nic = Get-NetAdapter | Where-Object { $_.InterfaceDescription -like 'Realtek Gaming USB*' -and $_.Status -eq 'Up' } | Select-Object -First 1
if (-not $nic) { "ERROR: Realtek USB NIC not found" | Out-File $log -Append -Encoding UTF8; exit 1 }
"NIC found: $($nic.Name) / $($nic.InterfaceDescription)" | Out-File $log -Append -Encoding UTF8

# start Wired AutoConfig service (makes the Authentication tab visible)
try {
    Set-Service dot3svc -StartupType Automatic -ErrorAction Stop
    Start-Service dot3svc -ErrorAction Stop
    $svc = Get-Service dot3svc
    "dot3svc: $($svc.Status)" | Out-File $log -Append -Encoding UTF8
} catch {
    "ERROR dot3svc: $($_.Exception.Message)" | Out-File $log -Append -Encoding UTF8
}

# apply 802.1X profile to this interface only
$xml = 'C:\Users\king\Documents\trae_projects\nac\deploy\lan_8021x.xml'
$addOut = netsh lan add profile filename="$xml" interface="$($nic.Name)" 2>&1
$addOut | Out-File $log -Append -Encoding UTF8

# show resulting state
$showOut = netsh lan show interface name="$($nic.Name)" 2>&1
$showOut | Out-File $log -Append -Encoding UTF8
"=== done ===" | Out-File $log -Append -Encoding UTF8
