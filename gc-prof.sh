#!/bin/bash
exec > /tmp/gc-prof.txt 2>&1
APID=$(pgrep -f "app/nac-auth" | head -1)
LPID=$(pgrep -f "app/nac-log" | head -1)
echo "auth=$APID log=$LPID"
echo "===== jstat gcutil auth 1s x 12 ====="
~/software/jdk1.8.0_202/bin/jstat -gcutil "$APID" 1000 12
echo "===== jstat gccause (last) ====="
~/software/jdk1.8.0_202/bin/jstat -gccause "$APID"
echo "===== machine top ====="
top -b -n 1 | head -14
echo "===== auth busy threads ====="
top -b -n 1 -H -p "$APID" | awk 'NR>=8 && $9>8 {print $1,$9,$12}'
echo "===== nac-log busy threads ====="
top -b -n 1 -H -p "$LPID" | awk 'NR>=8 && $9>8 {print $1,$9,$12}'
echo "===== kafka consumer lag ====="
/opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group nac-log-consumer 2>/dev/null | head -8 || \
  ~/software/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group nac-log-consumer 2>/dev/null | head -8
echo "===== mysql slow/process ====="
mysql -uroot -pMAO5201314sm -e "SHOW PROCESSLIST;" 2>/dev/null | head -12
