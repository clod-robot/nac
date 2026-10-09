#!/bin/bash
echo '=== warehouse com/nac ==='
ls -R /home/mao/software/warehouse/com/nac 2>/dev/null | head -30
echo '=== maven settings ==='
grep -E 'localRepository|aliyun' /home/mao/software/apache-maven-3.9.16/conf/settings.xml | grep -v '<!--'
echo '=== find any nac source/pom on disk ==='
find /home/mao /tmp /opt -maxdepth 5 \( -name 'nac-parent' -o -name 'pom.xml' \) 2>/dev/null | grep -iv m2 | head -20
echo '=== java ==='
/home/mao/software/jdk1.8.0_202/bin/java -version 2>&1
echo '=== nac.sh ==='
cat /home/mao/nac/nac.sh
