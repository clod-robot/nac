# RADIUS PAP + EAP-MD5 冒烟脚本（纯 .NET，无需 Python）
# 用法: .\radius-smoke.ps1
param(
    [string]$Server = '127.0.0.1',
    [int]$Port = 1812,
    [string]$Secret = 'testing123',
    [string]$User = 'wangfang',
    [string]$Pass = 'Wf$secure99'
)

$ErrorActionPreference = 'Stop'
function MD5([byte[]]$b) {
    $m = [System.Security.Cryptography.MD5]::Create()
    try { return $m.ComputeHash($b) } finally { $m.Dispose() }
}
function HMACMD5([byte[]]$key, [byte[]]$b) {
    $h = New-Object System.Security.Cryptography.HMACMD5
    $h.Key = $key
    try { return $m = $h.ComputeHash($b) } finally { $h.Dispose() }
}
function Attr([byte]$type, [byte[]]$val) {
    $o = New-Object System.Collections.Generic.List[byte]
    $o.Add($type); $o.Add([byte]($val.Length + 2)); $o.AddRange($val)
    return ,$o.ToArray()
}
function MergeAttrs($items) {
    $o = New-Object System.Collections.Generic.List[byte]
    foreach ($a in $items) { $o.AddRange([byte[]]$a) }
    return ,$o.ToArray()
}
function RadiusPacket([byte]$code, [byte]$id, [byte[]]$auth, [byte[]]$attrs) {
    $len = 20 + $attrs.Length
    $o = New-Object System.Collections.Generic.List[byte]
    $o.Add($code); $o.Add($id); $o.Add([byte]($len -shr 8)); $o.Add([byte]($len -band 255))
    $o.AddRange($auth); $o.AddRange($attrs)
    return ,$o.ToArray()
}
function SendRadius($udp, [byte[]]$pkt) {
    $ep = New-Object System.Net.IPEndPoint([System.Net.IPAddress]::Parse($Server), $Port)
    [void]$udp.Send($pkt, $pkt.Length, $ep)
    $remote = New-Object System.Net.IPEndPoint([System.Net.IPAddress]::Any, 0)
    $async = $udp.BeginReceive($null, $null)
    if (-not $async.AsyncWaitHandle.WaitOne(5000)) { throw "RADIUS response timeout" }
    $resp = $udp.EndReceive($async, [ref]$remote)
    return $resp
}
function EapPacket([byte]$code, [byte]$id, [byte]$type, [byte[]]$data) {
    # code(1) id(1) len(2) [type(1) data]
    $len = 4 + $(if ($type -ne 0) { 1 + $data.Length } else { $data.Length })
    $o = New-Object System.Collections.Generic.List[byte]
    $o.Add($code); $o.Add($id); $o.Add([byte]($len -shr 8)); $o.Add([byte]($len -band 255))
    if ($type -ne 0) { $o.Add($type) }
    $o.AddRange($data)
    return ,$o.ToArray()
}

$udp = New-Object System.Net.Sockets.UdpClient
$udp.Client.ReceiveTimeout = 5000

# ---------- PAP ----------
$rng = New-Object System.Security.Cryptography.RNGCryptoServiceProvider
$reqAuth = New-Object byte[] 16; $rng.GetBytes($reqAuth)
$secretB = [System.Text.Encoding]::UTF8.GetBytes($Secret)
$userB = [System.Text.Encoding]::UTF8.GetBytes($User)
$passB = [System.Text.Encoding]::UTF8.GetBytes($Pass)
# PAP 加密: 16 字节对齐
$padded = New-Object byte[] ([math]::Ceiling($passB.Length / 16.0) * 16)
[Array]::Copy($passB, $padded, $passB.Length)
$enc = New-Object byte[] $padded.Length
$prev = $reqAuth
for ($i = 0; $i -lt $padded.Length; $i += 16) {
    $h = New-Object System.Collections.Generic.List[byte]
    $h.AddRange($secretB); $h.AddRange($prev)
    $hash = MD5 $h.ToArray()
    $block = New-Object byte[] 16
    for ($j = 0; $j -lt 16; $j++) { $block[$j] = $padded[$i + $j] -bxor $hash[$j] }
    [Array]::Copy($block, 0, $enc, $i, 16)
    $prev = $block
}
$attrs = @(
    (Attr 1 $userB),
    (Attr 2 $enc),
    (Attr 4 ([byte[]](127,0,0,1)))
) | ForEach-Object { $_ }
$pkt = RadiusPacket 1 1 $reqAuth (MergeAttrs $attrs)
$resp = SendRadius $udp $pkt
$codeName = @{2='Access-Accept';3='Access-Reject';11='Access-Challenge'}[[int]$resp[0]]
Write-Output ("PAP   -> code={0} ({1})" -f [int]$resp[0], $codeName)
if ($resp[0] -ne 2) { throw 'PAP expected Access-Accept' }

# ---------- EAP-MD5 round 1: Identity ----------
$reqAuth2 = New-Object byte[] 16; $rng.GetBytes($reqAuth2)
$eapIdentity = EapPacket 2 10 1 $userB
$attrs2 = @(
    (Attr 1 $userB),
    (Attr 79 $eapIdentity),
    (Attr 80 (New-Object byte[] 16)),
    (Attr 4 ([byte[]](127,0,0,1)))
)
$pkt2 = RadiusPacket 1 2 $reqAuth2 (MergeAttrs $attrs2)
$resp2 = SendRadius $udp $pkt2
Write-Output ("EAP#1 -> code={0} ({1})" -f [int]$resp2[0], $codeName[[int]$resp2[0]])
if ($resp2[0] -ne 11) { throw 'EAP#1 expected Access-Challenge' }
# 提取 EAP-Message 属性(79)，取 MD5 challenge：EAP body 第5字节起 type=4, 后16字节
$pos = 20; $challenge = $null; $serverEapId = 0
while ($pos + 2 -le $resp2.Length) {
    $t = $resp2[$pos]; $l = $resp2[$pos + 1]
    if ($t -eq 79) {
        $v = New-Object byte[] ($l - 2)
        [Array]::Copy($resp2, $pos + 2, $v, 0, $l - 2)
        if ($v[4] -eq 4) {
            $serverEapId = $v[1]
            $challenge = New-Object byte[] 16
            [Array]::Copy($v, 5, $challenge, 0, 16)
        }
    }
    $pos += $l
}
if ($null -eq $challenge) { throw 'no MD5 challenge in EAP#1 response' }

# ---------- EAP-MD5 round 2: MD5 response ----------
# value = MD5(EAP-Identifier + password + challenge)
$h3 = New-Object System.Collections.Generic.List[byte]
$h3.Add($serverEapId); $h3.AddRange($passB); $h3.AddRange($challenge)
$md5val = MD5 $h3.ToArray()
$td = New-Object byte[] (1 + 16); $td[0] = 16; [Array]::Copy($md5val, 0, $td, 1, 16)
$eapResp = EapPacket 2 $serverEapId 4 $td
$reqAuth3 = New-Object byte[] 16; $rng.GetBytes($reqAuth3)
$attrs3 = @(
    (Attr 1 $userB),
    (Attr 79 $eapResp),
    (Attr 80 (New-Object byte[] 16)),
    (Attr 4 ([byte[]](127,0,0,1)))
)
$pkt3 = RadiusPacket 1 3 $reqAuth3 (MergeAttrs $attrs3)
$resp3 = SendRadius $udp $pkt3
Write-Output ("EAP#2 -> code={0} ({1})" -f [int]$resp3[0], $codeName[[int]$resp3[0]])
if ($resp3[0] -ne 2) { throw 'EAP#2 expected Access-Accept' }

$udp.Close()
Write-Output 'SMOKE PASS: PAP + EAP-MD5 both Access-Accept'
