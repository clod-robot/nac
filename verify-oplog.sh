#!/bin/bash
BASE=http://127.0.0.1
echo "===== 1) system log paged (size=3) ====="
curl -s "$BASE/api/logs/page?page=1&size=3" | head -c 900
echo; echo
echo "===== 2) operation log paged (empty before ops) ====="
curl -s "$BASE/api/operation-logs/page?page=1&size=3" | head -c 600
echo; echo
echo "===== 3) login (audit should capture username/ip) ====="
LOGIN=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}')
echo "$LOGIN" | head -c 300
echo; echo
sleep 2
echo "===== 4) operation log after login ====="
curl -s "$BASE/api/operation-logs/page?page=1&size=5" | head -c 1500
echo; echo
echo "===== 5) wrong-password login (FAIL audit) ====="
curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d '{"username":"admin","password":"WRONG"}' | head -c 200
echo; sleep 2
echo "===== 6) FAIL rows ====="
curl -s "$BASE/api/operation-logs/page?page=1&size=5&result=FAIL" | head -c 1200
echo; echo
echo "===== 7) DELETE must not exist on log APIs (expect 404/405) ====="
curl -s -o /dev/null -w "DELETE /api/logs/1 -> %{http_code}\n" -X DELETE "$BASE/api/logs/1"
curl -s -o /dev/null -w "DELETE /api/operation-logs/1 -> %{http_code}\n" -X DELETE "$BASE/api/operation-logs/1"
