# NAC vertical-privilege end-to-end tests against http://192.168.80.36:9000
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

function Login($username, $password) {
    $cap = (curl.exe -s "$gw/api/auth/captcha/img") | ConvertFrom-Json
    $code = Get-CaptchaCode $cap.data.captchaId
    if (-not $code) { throw "captcha fetch failed for $username" }
    $loginBody = @{ username=$username; password=$password; captchaId=$cap.data.captchaId; captcha=$code } | ConvertTo-Json -Compress
    [IO.File]::WriteAllText("$dir\login.json", $loginBody, (New-Object Text.UTF8Encoding($false)))
    $resp = (curl.exe -s -X POST "$gw/api/auth/login" -H "Content-Type: application/json; charset=utf-8" --data-binary "@$dir\login.json") | ConvertFrom-Json
    return $resp
}

function Req($method, $url, $token, $extraHeaders, $bodyObj) {
    $hargs = @('-s','-o',"$dir\body.txt",'-w','%{http_code}','-X',$method,$url)
    if ($token) { $hargs += @('-H',"Authorization: Bearer $token") }
    if ($extraHeaders) { foreach ($h in $extraHeaders) { $hargs += @('-H',$h) } }
    if ($bodyObj) {
        $json = $bodyObj | ConvertTo-Json -Compress
        [IO.File]::WriteAllText("$dir\body.json", $json, (New-Object Text.UTF8Encoding($false)))
        $hargs += @('-H','Content-Type: application/json; charset=utf-8','--data-binary',"@$dir\body.json")
    }
    $code = & curl.exe @hargs
    $body = ''
    if (Test-Path "$dir\body.txt") { $body = Get-Content "$dir\body.txt" -Raw -Encoding UTF8 }
    return [pscustomobject]@{ http=[int]$code; body=$body }
}

$pass=0; $fail=0
function Check($name, $cond, $detail) {
    if ($cond) { Write-Host "[PASS] $name" -ForegroundColor Green; $script:pass++ }
    else { Write-Host "[FAIL] $name  $detail" -ForegroundColor Red; $script:fail++ }
}

Write-Host '== 1. admin login (role=super admin CN)' -ForegroundColor Cyan
$a = Login 'admin' 'Admin@123'
Check 'admin login code=0' ($a.code -eq 0) ($a | ConvertTo-Json -Compress)
$at = $a.data.token
Check 'admin role returned' ([bool]$at) "no token"
Write-Host "   role=$($a.data.role)"

Write-Host '== 2. admin read/write via gateway' -ForegroundColor Cyan
$r = Req 'GET' "$gw/api/users/" $at $null $null
Check 'admin GET /users 200' ($r.http -eq 200) "http=$($r.http) $($r.body)"

$tester = @{ username='sec_readonly'; name='sec readonly'; role='只读用户'; authType='本地认证'; accessType='Web'; email='ro@test.local'; phone='13800009999'; enabled=1; password='Read@Test123' }
$r = Req 'POST' "$gw/api/users/" $at $null $tester
Check 'admin POST /users (sys_user) 200' ($r.http -eq 200) "http=$($r.http) $($r.body)"

# 登录认证源是 nac-auth 的 RADIUS 用户存储，role=user 即垂直越权防护的只读角色
$radTester = @{ username='sec_readonly'; password='Read@Test123'; role='user'; enabled=$true; authType='本地认证'; vlanId=$null; sessionTimeout=0; realName='sec readonly'; phone='13800009999'; department='sec'; employeeId='t001'; isVerified=$false }
$r = Req 'POST' "$gw/api/auth/radius-users" $at $null $radTester
Check 'admin POST /auth/radius-users create 200' ($r.http -eq 200) "http=$($r.http) $($r.body)"

Write-Host '== 3. readonly user login' -ForegroundColor Cyan
$u = Login 'sec_readonly' 'Read@Test123'
Check 'readonly login code=0' ($u.code -eq 0) ($u | ConvertTo-Json -Compress)
$ut = $u.data.token
Write-Host "   role=$($u.data.role)"

Write-Host '== 4. readonly read allowed / writes blocked' -ForegroundColor Cyan
$r = Req 'GET' "$gw/api/users/" $ut $null $null
Check 'readonly GET /users 200' ($r.http -eq 200) "http=$($r.http) $($r.body)"
$r = Req 'POST' "$gw/api/users/" $ut $null $tester
Check 'readonly POST /users 403' ($r.http -eq 403) "http=$($r.http) $($r.body)"
$r = Req 'PUT' "$gw/api/users/1/toggle" $ut $null $null
Check 'readonly PUT toggle 403' ($r.http -eq 403) "http=$($r.http) $($r.body)"
$r = Req 'DELETE' "$gw/api/users/1" $ut $null $null
Check 'readonly DELETE 403' ($r.http -eq 403) "http=$($r.http) $($r.body)"

Write-Host '== 5. forged X-User-Role admin header stripped' -ForegroundColor Cyan
$r = Req 'POST' "$gw/api/users/" $ut @('X-User-Role: admin','X-User-Name: admin') $tester
Check 'forged role header still 403' ($r.http -eq 403) "http=$($r.http) $($r.body)"

Write-Host '== 6. anonymous portal' -ForegroundColor Cyan
$r = Req 'GET' "$gw/api/portal/config" $null $null $null
Check 'anon GET portal/config 200' ($r.http -eq 200) "http=$($r.http)"
$cfg = @{ portalTitle='pwned' }
$r = Req 'PUT' "$gw/api/portal/config" $null $null $cfg
Check 'anon PUT portal/config 401' ($r.http -eq 401) "http=$($r.http) $($r.body)"
$r = Req 'PUT' "$gw/api/portal/config" $ut $null $cfg
Check 'readonly PUT portal/config 403' ($r.http -eq 403) "http=$($r.http) $($r.body)"

Write-Host '== 7. direct service access bypasses gateway (nac-user :9003)' -ForegroundColor Cyan
$r = Req 'GET' 'http://192.168.80.36:9003/api/users/' $ut $null $null
Check 'direct:9003 valid token no sign -> 403' ($r.http -eq 403) "http=$($r.http) $($r.body)"
$r = Req 'GET' 'http://192.168.80.36:9003/api/users/' $null $null $null
Check 'direct:9003 no token no sign -> 403' ($r.http -eq 403) "http=$($r.http) $($r.body)"

Write-Host '== 8. no token write via gateway -> 401' -ForegroundColor Cyan
$r = Req 'POST' "$gw/api/users/" $null $null $tester
Check 'anon POST /users 401' ($r.http -eq 401) "http=$($r.http) $($r.body)"

Write-Host '== 9. internal aggregation nac-user->nac-auth (online field)' -ForegroundColor Cyan
$r = Req 'GET' "$gw/api/users/" $at $null $null
Check 'admin GET /users aggregation 200' ($r.http -eq 200 -and $r.body -match 'online') "http=$($r.http)"

Write-Host '== 9b. sensitive GET portal-phone/reveal requires admin' -ForegroundColor Cyan
$r = Req 'GET' "$gw/api/auth/portal-phone/reveal?phone=1380000%2A%2A%2A%2A0000" $ut $null $null
Check 'readonly GET phone/reveal 403' ($r.http -eq 403) "http=$($r.http) $($r.body)"
$r = Req 'GET' "$gw/api/auth/portal-phone/reveal?phone=1380000%2A%2A%2A%2A0000" $at $null $null
Check 'admin GET phone/reveal not 403 (passes role matrix)' ($r.http -ne 403) "http=$($r.http) $($r.body)"

Write-Host '== 9c. self-service logout allowed for readonly role' -ForegroundColor Cyan
$r = Req 'POST' "$gw/api/auth/logout" $ut $null $null
Check 'readonly POST logout not 403 (write exception)' ($r.http -ne 403) "http=$($r.http) $($r.body)"

Write-Host '== 10. cleanup: admin delete test users' -ForegroundColor Cyan
$r = Req 'DELETE' "$gw/api/auth/radius-users/sec_readonly" $at $null $null
Check 'admin delete radius sec_readonly 200' ($r.http -eq 200) "http=$($r.http) $($r.body)"
$list = (Req 'GET' "$gw/api/users/" $at $null $null).body
$id = (($list | ConvertFrom-Json).data | Where-Object { $_.username -eq 'sec_readonly' } | Select-Object -First 1).id
if ($id) {
    $r = Req 'DELETE' "$gw/api/users/$id" $at $null $null
    Check "admin delete sys_user sec_readonly id=$id 200" ($r.http -eq 200) "http=$($r.http) $($r.body)"
} else { Check 'found sys_user sec_readonly id' $false 'not found' }

Remove-SSHSession -SSHSession $ssh | Out-Null
Write-Host "`nRESULT: pass=$pass fail=$fail" -ForegroundColor Yellow
if ($fail -gt 0) { exit 1 }
