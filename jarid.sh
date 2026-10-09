#!/bin/bash
echo '=== running process ==='
ps -eo pid,lstart,cmd | grep 'app/nac-auth' | grep -v grep
echo '=== app dir ==='
ls -la /home/mao/nac/app/ | grep nac-auth
echo '=== hashes ==='
md5sum /home/mao/nac/app/nac-auth-1.0-SNAPSHOT.jar
echo '=== class inside jar ==='
unzip -l /home/mao/nac/app/nac-auth-1.0-SNAPSHOT.jar | grep -E 'RadiusAuthService\.class|RadiusSignatures'
