#!/bin/bash
echo "=== /online/count (was 38s, now expect <10ms) ==="
curl -s -m 10 -o /tmp/cnt.json -w 'http=%{http_code} time=%{time_total}s\n' http://localhost:9001/api/auth/online/count
cat /tmp/cnt.json
echo
echo "=== /online?page=0&size=20 (paged) ==="
curl -s -m 10 -o /tmp/pg.json -w 'http=%{http_code} time=%{time_total}s size=%{size_download}\n' 'http://localhost:9001/api/auth/online?page=0&size=20'
python3 -c 'import json; d=json.load(open("/tmp/pg.json")); print("total=%d rows=%d page=%d" % (d["data"]["total"], len(d["data"]["rows"]), d["data"]["page"]))'
echo
echo "=== /online?page=0&size=100 ==="
curl -s -m 10 -o /tmp/pg2.json -w 'http=%{http_code} time=%{time_total}s size=%{size_download}\n' 'http://localhost:9001/api/auth/online?page=0&size=100'
python3 -c 'import json; d=json.load(open("/tmp/pg2.json")); print("total=%d rows=%d page=%d" % (d["data"]["total"], len(d["data"]["rows"]), d["data"]["page"]))'
