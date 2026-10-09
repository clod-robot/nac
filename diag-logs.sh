#!/bin/bash
echo "===== 1. via nginx -> gateway ====="
curl -s -m 8 -o /tmp/logs-via-nginx.txt -w 'http=%{http_code} time=%{time_total}s size=%{size_download}\n' http://localhost/api/logs/
head -c 300 /tmp/logs-via-nginx.txt; echo
echo
echo "===== 2. direct nac-log 9004 ====="
curl -s -m 5 -w '\nhttp=%{http_code}\n' http://localhost:9004/api/logs/ | head -c 400
echo
echo "===== 3. gateway 9000 -> logs ====="
curl -s -m 5 -w '\nhttp=%{http_code}\n' http://localhost:9000/api/logs/ | head -c 400
echo
echo "===== 4. nac-log registered in nacos? ====="
curl -s -m 5 "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=nac-log" | python3 -c '
import sys, json
d = json.load(sys.stdin)
hosts = d.get("hosts", [])
print("instances:", len(hosts))
for h in hosts:
    print(" ", h.get("ip"), h.get("port"), "healthy=", h.get("healthy"))
'
echo "===== 5. nac-log process / port ====="
ss -tln | grep -E '9004' || echo "9004 NOT LISTENING"
