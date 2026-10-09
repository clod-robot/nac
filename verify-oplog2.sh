#!/bin/bash
BASE=http://127.0.0.1
echo "===== login success ====="
LOGIN=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d '{"username":"admin","password":"Admin@123"}')
echo "$LOGIN" | head -c 300
TOKEN=$(echo "$LOGIN" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
echo; echo "token=${TOKEN:0:24}..."
echo
echo "===== create device (audit via token user) ====="
curl -s -X POST "$BASE/api/devices/" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"id":"DEV-OPLOG-TEST","name":"审计测试设备","ip":"10.0.0.99","type":"switch","status":"offline"}'
echo
echo "===== delete it ====="
curl -s -X DELETE "$BASE/api/devices/DEV-OPLOG-TEST" -H "Authorization: Bearer $TOKEN"
echo
sleep 2
echo "===== recent op logs ====="
curl -s "$BASE/api/operation-logs/page?page=1&size=6" | python3 -m json.tool 2>/dev/null | grep -E '"(username|module|action|bizType|method|url|ip|result|errorMsg|costMs|eventTime)"'
