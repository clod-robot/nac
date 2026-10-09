# Elevated: import CA cert + apply PEAP profile to USB Realtek NIC
$ErrorActionPreference = 'Continue'
$log = 'C:\Users\king\Documents\trae_projects\nac\deploy\peap_setup_result.txt'
"=== PEAP client setup start $(Get-Date) ===" | Out-File $log -Encoding UTF8

# 1) start dot3svc
try {
    Set-Service dot3svc -StartupType Automatic -ErrorAction Stop
    Start-Service dot3svc -ErrorAction Stop
    "dot3svc: $((Get-Service dot3svc).Status)" | Out-File $log -Append -Encoding UTF8
} catch { "dot3svc error: $_" | Out-File $log -Append -Encoding UTF8 }

# 2) import CA cert into LocalMachine trusted root
$caPath = 'D:\迅雷下载\certs\ca.crt'
try {
    certutil -addstore -f Root $caPath 2>&1 | Out-File $log -Append -Encoding UTF8
    "CA cert imported to LocalMachine\Root" | Out-File $log -Append -Encoding UTF8
} catch { "CA import error: $_" | Out-File $log -Append -Encoding UTF8 }

# 3) locate USB Realtek NIC
$nic = Get-NetAdapter | Where-Object { $_.InterfaceDescription -like 'Realtek Gaming USB*' -and $_.Status -eq 'Up' } | Select-Object -First 1
if (-not $nic) { "ERROR: Realtek USB NIC not found" | Out-File $log -Append -Encoding UTF8; exit 1 }
"NIC: $($nic.Name)" | Out-File $log -Append -Encoding UTF8

# 4) apply PEAP profile (use interface GUID to avoid non-ASCII name issues)
$xml = 'C:\Users\king\Documents\trae_projects\nac\deploy\lan_peap.xml'
# remove existing profile on this interface if any
netsh lan delete profile interface="$($nic.Name)" 2>&1 | Out-File $log -Append -Encoding UTF8
$addOut = netsh lan add profile filename="$xml" interface="$($nic.Name)" 2>&1
$addOut | Out-File $log -Append -Encoding UTF8

# 5) show interface auth state
"=== interface state ===" | Out-File $log -Append -Encoding UTF8
netsh lan show interface name="$($nic.Name)" 2>&1 | Out-File $log -Append -Encoding UTF8

# 6) restart the interface to trigger auth
Disable-NetAdapter -Name $nic.Name -Confirm:$false
Start-Sleep -Seconds 2
Enable-NetAdapter -Name $nic.Name -Confirm:$false
"interface restarted" | Out-File $log -Append -Encoding UTF8
"=== done ===" | Out-File $log -Append -Encoding UTF8
