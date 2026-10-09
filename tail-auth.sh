#!/bin/bash
echo '=== auth log since 10:01 ==='
grep -E '10:0[1-9]' /home/mao/nac/logs/nac-auth.out | tail -60
echo '=== ERROR/Exception recent ==='
grep -n -E 'ERROR|Exception|失败' /home/mao/nac/logs/nac-auth.out | tail -20
