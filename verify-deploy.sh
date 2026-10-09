#!/bin/bash
echo "===== 1. Nacos registered services ====="
curl -s -m 5 "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=nac-auth" | head -c 300
echo
echo "===== 2. nac-auth startup markers ====="
grep -aE 'Started AuthApplication|RadiusServer|radius-biz|ERROR|APPLICATION FAILED' ~/nac/logs/nac-auth.out | tail -8
echo "===== 3. gateway login (wangfang) ====="
curl -s -m 8 -X POST http://localhost:9000/api/auth/login -H 'Content-Type: application/json' -d '{"username":"wangfang","password":"Wf$secure99"}' | head -c 300
echo
echo "===== 4. monitor snapshot ====="
curl -s -m 8 http://localhost:9006/api/monitor/snapshot | head -c 400
echo
echo "===== 5. fatal errors in any service (last run) ====="
for f in ~/nac/logs/nac-*.out; do
  n=$(grep -ac 'APPLICATION FAILED' "$f" 2>/dev/null)
  [ "$n" != "0" ] && echo "$f FAILED=$n"
done
echo "CHECK_DONE"
