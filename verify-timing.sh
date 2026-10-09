#!/bin/bash
echo "== warning filter timing =="
curl -s -o /tmp/w.json -w 'http=%{http_code} time=%{time_total}s\n' 'http://127.0.0.1/api/logs/page?page=1&size=3&level=warning'
python3 -c "import json;d=json.load(open('/tmp/w.json'))['data'];print('total=',d['total'],' levels=',set(r['level'] for r in d['rows']))"
echo "== op-log FAIL filter timing =="
curl -s -o /tmp/f.json -w 'http=%{http_code} time=%{time_total}s\n' 'http://127.0.0.1/api/operation-logs/page?page=1&size=5&result=FAIL'
python3 -c "import json;d=json.load(open('/tmp/f.json'))['data'];print('total=',d['total'],' results=',set(r['result'] for r in d['rows']))"
echo "== nac-log memory / OOM check =="
PID=$(pgrep -f 'app/nac-log-1.0-SNAPSHOT.jar' | head -1)
echo "pid=$PID rss_kb=$(awk '/VmRSS/{print $2}' /proc/$PID/status)"
echo "OOM_count_in_current_log=$(grep -c OutOfMemory ~/nac/logs/nac-log.out)"
