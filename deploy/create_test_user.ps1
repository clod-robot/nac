$ErrorActionPreference = 'Continue'
$gw = 'http://192.168.80.36:9000'
$dir = "$env:TEMP\nactest"; New-Item -ItemType Directory -Force -Path $dir | Out-Null
Import-Module Posh-SSH
$sec = ConvertTo-SecureString 'MAO5201314sm' -AsPlainText -Force
$cred = New-Object System.Management.Automation.PSCredential('mao',$sec)
$ssh = New-SSHSession -ComputerName 192.168.80.36 -Credential $cred -AcceptKey

function Get-CaptchaCode($id) {
    $r = Invoke-SSHCommand -SSHSession $ssh -Command "redis-cli -a MAO5201314sm GET nac:auth:captcha:$id 2>/dev/null" -TimeOut 30
    return ($r.Output | Where-Object { $_ -match '^[A-Za-z0-9]{4}$' } | Select-Object -First 1)
}

# login as admin
$cap = (curl.exe -s "$gw/api/auth/captcha/img") | ConvertFrom-Json
$code = Get-CaptchaCode $cap.data.captchaId
$loginBody = @{ username='admin'; password='Admin@123'; captchaId=$cap.data.captchaId; captcha=$code } | ConvertTo-Json -Compress
[IO.File]::WriteAllText("$dir\login.json", $loginBody, (New-Object Text.UTF8Encoding($false)))
$a = (curl.exe -s -X POST "$gw/api/auth/login" -H "Content-Type: application/json; charset=utf-8" --data-binary "@$dir\login.json") | ConvertFrom-Json
Write-Host "login code=$($a.code) role=$($a.data.role)"
$at = $a.data.token

# delete existing test user first (ignore errors)
$r1 = curl.exe -s -o "$dir\d.txt" -w '%{http_code}' -X DELETE "$gw/api/auth/radius-users/test" -H "Authorization: Bearer $at"
Write-Host "delete old test -> $r1"

# create test/test with PEAP authType
$user = @{ username='test'; password='test'; authType='PEAP'; role='user'; enabled=$true; sessionTimeout=0; realName='test user'; department='test'; employeeId='t001'; isVerified=$true }
$json = $user | ConvertTo-Json -Compress
[IO.File]::WriteAllText("$dir\user.json", $json, (New-Object Text.UTF8Encoding($false)))
$code2 = curl.exe -s -o "$dir\c.txt" -w '%{http_code}' -X POST "$gw/api/auth/radius-users" -H "Authorization: Bearer $at" -H "Content-Type: application/json; charset=utf-8" --data-binary "@$dir\user.json"
Write-Host "create test -> $code2 body=$(Get-Content "$dir\c.txt" -Raw)"

# verify
$list = (curl.exe -s "$gw/api/auth/radius-users" -H "Authorization: Bearer $at") | ConvertFrom-Json
$list.data | Where-Object { $_.username -eq 'test' } | Format-List username, authType, role, enabled

Remove-SSHSession -SSHSession $ssh | Out-Null
