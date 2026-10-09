$ErrorActionPreference = "Continue"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# ---------------------------------------------------------------------------
# NVD 规则刷新脚本 (v2 - 支持分页，目标 10000+ 条规则)
# 用途：从 NVD REST API 2.0 拉取 65 类产品的 CVE，提取版本范围，
#       生成 VulnScanner 外部规则文件 build/bin/vuln_rules.json
# 用法：powershell -ExecutionPolicy Bypass -File fetch_nvd.ps1
# 速率：NVD 无 key 限制 5 请求/30秒，每请求间隔 6 秒
# ---------------------------------------------------------------------------

$scriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$productsFile = Join-Path $scriptDir "products.json"
$outPath    = Join-Path $scriptDir "build\bin\vuln_rules.json"

if (-not (Test-Path $productsFile)) {
    Write-Host "ERROR: products.json not found at $productsFile" -ForegroundColor Red
    exit 1
}

$products = Get-Content $productsFile -Raw -Encoding UTF8 | ConvertFrom-Json
$allRules = @()
$seen = @{}
$idx = 0
$totalReqs = 0

foreach ($p in $products) {
    $idx++
    $startIdx = 0
    $pageRules = 0
    $page = 0
    $cpeProduct = ($p.cpe -split ':')[4]

    do {
        $page++
        $totalReqs++
        $pageLabel = if ($page -gt 1) { " (page $page)" } else { "" }
        Write-Host "[$idx/$($products.Count)] Fetching: $($p.prod)$pageLabel ..." -NoNewline

        $encoded = [uri]::EscapeDataString($p.cpe)
        $url = "https://services.nvd.nist.gov/rest/json/cves/2.0?virtualMatchString=$encoded&resultsPerPage=2000&startIndex=$startIdx"

        $retryCount = 0
        $resp = $null
        while ($retryCount -lt 3) {
            try {
                $resp = Invoke-RestMethod -Uri $url -Method Get -TimeoutSec 90 -Headers @{ "User-Agent" = "VulnScanner/1.0" }
                break
            } catch {
                $retryCount++
                Write-Host " retry$retryCount..." -NoNewline
                if ($retryCount -ge 3) { Write-Host " ERROR: $($_.Exception.Message)" }
                Start-Sleep -Seconds 12
            }
        }
        if (-not $resp) { Start-Sleep -Seconds 6; continue }

        $totalResults = $resp.totalResults
        $vulns = $resp.vulnerabilities
        $count = 0
        if ($vulns) {
            foreach ($v in $vulns) {
                $cve = $v.cve
                $id = $cve.id
                if ($seen.ContainsKey($id)) { continue }
                $seen[$id] = $true

                $desc = ""
                foreach ($d in $cve.descriptions) { if ($d.lang -eq "en") { $desc = $d.value; break } }
                if ($desc.Length -lt 30) { continue }

                $sev = ""; $score = 0
                if ($cve.metrics.cvssMetricV31) {
                    $sev = $cve.metrics.cvssMetricV31[0].cvssData.baseSeverity
                    $score = $cve.metrics.cvssMetricV31[0].cvssData.baseScore
                } elseif ($cve.metrics.cvssMetricV30) {
                    $sev = $cve.metrics.cvssMetricV30[0].cvssData.baseSeverity
                    $score = $cve.metrics.cvssMetricV30[0].cvssData.baseScore
                } elseif ($cve.metrics.cvssMetricV2) {
                    $sev = $cve.metrics.cvssMetricV2[0].baseSeverity
                    $score = $cve.metrics.cvssMetricV2[0].cvssData.baseScore
                }
                $sevLower = ([string]$sev).ToLower()
                $ruleSev = ""
                if ($sevLower -eq "critical") { $ruleSev = "critical" }
                elseif ($sevLower -eq "high") { $ruleSev = "high" }
                elseif ($sevLower -eq "medium") { $ruleSev = "medium" }
                else { continue }

                $fixVersion = $null
                $op = "lt"
                if ($cve.configurations) {
                    foreach ($cfg in $cve.configurations) {
                        if ($cfg.nodes) {
                            foreach ($node in $cfg.nodes) {
                                if ($node.cpeMatch) {
                                    foreach ($cpeM in $node.cpeMatch) {
                                        $crit = $cpeM.criteria
                                        if (-not $crit) { continue }
                                        $parts = $crit -split ':'
                                        $match = $false
                                        if ($parts.Length -ge 5 -and $parts[4] -eq $cpeProduct) { $match = $true }
                                        if (-not $match) { continue }
                                        if ($cpeM.versionEndExcluding) {
                                            $fixVersion = $cpeM.versionEndExcluding; $op = "lt"; break
                                        } elseif ($cpeM.versionEndIncluding) {
                                            $fixVersion = $cpeM.versionEndIncluding; $op = "le"; break
                                        }
                                    }
                                }
                            }
                        }
                        if ($fixVersion) { break }
                    }
                }
                if (-not $fixVersion) { continue }

                $title = $desc -replace '\s+', ' '
                if ($title.Length -gt 150) { $title = $title.Substring(0, 147) + "..." }

                $rule = [ordered]@{
                    id           = $id
                    product      = $p.prod
                    severity     = $ruleSev
                    service      = $p.svc
                    source       = $p.src
                    regex        = $p.rx
                    versionGroup = $p.vg
                    op           = $op
                    version      = $fixVersion
                    title        = $title
                    description  = "CVSS $score ($sev). $desc"
                    solution     = "Upgrade to $fixVersion or later."
                    reference    = "https://nvd.nist.gov/vuln/detail/$id"
                }
                $allRules += $rule
                $count++
            }
        }
        $pageRules += $count
        Write-Host " $count rules (total: $($allRules.Count))"

        $startIdx += 2000
        if ($startIdx -lt $totalResults) { Start-Sleep -Seconds 6 }
    } while ($startIdx -lt $totalResults)

    if ($page -gt 1) { Write-Host "  -> $($p.prod): $pageRules rules from $page pages (NVD total: $totalResults)" }
    Start-Sleep -Seconds 6
}

# 写入输出
$output = [ordered]@{
    meta = [ordered]@{
        version     = 2
        notice      = "Extended rules auto-generated from NVD. For authorized assessment only."
        generatedAt = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
        products    = $products.Count
        apiRequests = $totalReqs
    }
    fingerprints = @()
    rules        = $allRules
}

$outDir = Split-Path -Parent $outPath
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

$json = $output | ConvertTo-Json -Depth 10
[System.IO.File]::WriteAllText($outPath, $json, [System.Text.Encoding]::UTF8)

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "=== Done: $($allRules.Count) rules written ===" -ForegroundColor Green
Write-Host "=== Output: $outPath" -ForegroundColor Green
Write-Host "=== API requests: $totalReqs ===" -ForegroundColor Green
Write-Host "=== Products: $($products.Count) ===" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
$bySev = $allRules | Group-Object severity | Sort-Object Name
foreach ($g in $bySev) { Write-Host "  $($g.Name): $($g.Count)" }

$byProd = $allRules | Group-Object product | Sort-Object Count -Descending | Select-Object -First 15
Write-Host ""
Write-Host "Top 15 products:"
foreach ($g in $byProd) { Write-Host "  $($g.Name): $($g.Count)" }
