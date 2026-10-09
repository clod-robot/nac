# 通过原生 RESP 协议向 Redis 预置 RADIUS 用户缓存（无需 redis-cli）
# 用法: .\redis-seed-users.ps1
param(
    [string]$Host_ = '192.168.80.36',
    [int]$Port = 6379,
    [string]$Password = 'MAO5201314sm'
)
$ErrorActionPreference = 'Stop'

function Send-Resp($stream, [string[]]$args) {
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append("*$($args.Count)`r`n")
    foreach ($a in $args) {
        $b = [System.Text.Encoding]::UTF8.GetBytes($a)
        [void]$sb.Append("`$$($b.Length)`r`n").Append($a).Append("`r`n")
    }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($sb.ToString())
    $stream.Write($bytes, 0, $bytes.Length)
    $stream.Flush()
}
function Recv-Line($reader) {
    return $reader.ReadLine()
}

$client = New-Object System.Net.Sockets.TcpClient
$client.Connect($Host_, $Port)
$stream = $client.GetStream()
$reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)

Send-Resp $stream @('AUTH', $Password)
$authResp = Recv-Line $reader
if ($authResp -notlike '+OK*') { throw "Redis AUTH failed: $authResp" }
Write-Output "AUTH ok"

# 与 com.nac.auth.store.RadiusUser 的 Gson 字段严格一致
$users = @(
    @{ name='wangfang'; json='{"username":"wangfang","password":"Wf$secure99","authType":"EAP-MD5","role":null,"enabled":true}' },
    @{ name='admin';    json='{"username":"admin","password":"Admin@123","authType":"PAP","role":null,"enabled":true}' }
)
foreach ($u in $users) {
    $key = "nac:radius:user:$($u.name)"
    Send-Resp $stream @('SET', $key, $u.json)
    $resp = Recv-Line $reader
    Write-Output "SET $key -> $resp"
}
Send-Resp $stream @('GET', 'nac:radius:user:wangfang')
$bulkLen = Recv-Line $reader
if ($bulkLen -like '$-1') { throw 'verify GET returned nil' }
$len = [int]$bulkLen.Substring(1)
$buf = New-Object char[] $len
$read = 0
while ($read -lt $len) { $read += $reader.Read($buf, $read, $len - $read) }
[void]$reader.ReadLine()
Write-Output ("VERIFY wangfang = " + (-join $buf))

$reader.Close(); $stream.Close(); $client.Close()
Write-Output 'SEED DONE'
