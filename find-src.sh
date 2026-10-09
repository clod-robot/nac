#!/bin/bash
echo '=== /home/mao tree (depth 2) ==='
find /home/mao -maxdepth 2 -type d 2>/dev/null | grep -v -E 'logs|certs' | head -50
echo '=== find RadiusAuthService.java ==='
find /home/mao /opt /usr/local /root -name 'RadiusAuthService.java' -o -name 'RadiusSignatures.java' 2>/dev/null | head
echo '=== find pom.xml nac ==='
find /home/mao /opt -maxdepth 4 -name 'pom.xml' 2>/dev/null | head -20
echo '=== nac dir ==='
ls -la /home/mao/nac/
