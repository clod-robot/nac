#!/bin/bash
echo "===== Nacos registered instances ====="
for svc in nac-gateway nac-auth nac-device nac-user nac-log nac-config; do
  cnt=$(curl -s "http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=$svc" | python3 -c "import sys,json;d=json.load(sys.stdin);print(len(d.get('hosts',[])))" 2>/dev/null)
  ips=$(curl -s "http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=$svc" | python3 -c "import sys,json;d=json.load(sys.stdin);print(','.join(h['ip']+':'+str(h['port'])+('/'+h['healthy'].__str__()) for h in d.get('hosts',[])))" 2>/dev/null)
  printf "%-12s instances=%s  %s\n" "$svc" "$cnt" "$ips"
done
echo "===== Started markers / errors in logs ====="
for f in ~/nac/logs/nac-*.out; do
  name=$(basename "$f" .out)
  started=$(grep -c "Started .*Application in" "$f" 2>/dev/null)
  errs=$(grep -cE "APPLICATION FAILED TO START|ERROR" "$f" 2>/dev/null)
  printf "%-12s started=%s errorLines=%s\n" "$name" "$started" "$errs"
done
echo "===== nac-auth RADIUS + EAPOL lines ====="
grep -E "RADIUS Server started|Auth  port|EAPOL Authenticator disabled|Started AuthApplication" ~/nac/logs/nac-auth.out | tail -6
