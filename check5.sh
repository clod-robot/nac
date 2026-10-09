#!/bin/bash
grep -aE 'PEAP|Access-Accept|认证成功|EAP Identity|NAK' /home/mao/nac/logs/nac-auth.out | tail -20
echo ---SESSION---
mysql -uroot -pNceRadius@2026 -D nac -e "SELECT id,username,auth_method,status,framed_ip_address FROM sys_radius_session ORDER BY id DESC LIMIT 3" 2>/dev/null
echo ---PCAP---
python3 /tmp/radpeap.py /tmp/peap5.pcap 2>&1 | tail -14
