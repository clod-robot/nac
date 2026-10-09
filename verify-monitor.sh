#!/bin/bash
curl -s -m 5 http://localhost:9006/api/monitor/system | python3 -c '
import sys, json
d = json.load(sys.stdin)["data"]
print("hostname =", d.get("hostname"))
print("managementIp =", d.get("managementIp"))
print("systemUptimeSeconds =", d.get("systemUptimeSeconds"))
up = d.get("systemUptimeSeconds", 0)
print("uptimeDays = %.2f" % (up / 86400))
print("os =", d.get("osName"), d.get("osVersion"))
print("cpuModel =", d.get("cpuModel"))
print("physicalCores =", d.get("physicalCores"))
print("memTotalGB = %.1f" % (d.get("memTotalBytes", 0) / 1073741824))
print("cpuUsage = %.1f%%" % d.get("cpuUsagePercent", 0))
print("memUsage = %.1f%%" % d.get("memUsagePercent", 0))
print("diskUsage = %.1f%%" % d.get("diskUsagePercent", 0))
'
