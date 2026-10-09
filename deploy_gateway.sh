#!/bin/bash
set -e
JAVAC=/home/mao/software/jdk1.8.0_202/bin/javac
JARFILE=/home/mao/nac/app/nac-gateway-1.0-SNAPSHOT.jar
TMP=/tmp/gw-compile
rm -rf $TMP; mkdir -p $TMP/out
python3 << 'PYEOF'
import zipfile
with zipfile.ZipFile('/home/mao/nac/app/nac-gateway-1.0-SNAPSHOT.jar','r') as z:
    for n in z.namelist():
        if n.startswith('BOOT-INF/classes/') and not n.endswith('/'):
            z.extract(n, '/tmp/gw-compile')
        elif n.startswith('BOOT-INF/lib/') and n.endswith('.jar'):
            z.extract(n, '/tmp/gw-compile')
PYEOF
CP="$TMP/BOOT-INF/classes"
for f in $TMP/BOOT-INF/lib/*.jar; do CP="$CP:$f"; done
echo 'Compiling AuthGatewayFilter...'
$JAVAC -encoding UTF-8 -cp "$CP" -d $TMP/out /tmp/AuthGatewayFilter.java
echo 'Compile OK'
cp $JARFILE ${JARFILE}.bak.$(date +%s)
cp /tmp/gateway-application.yml $TMP/application.yml
export TMP JARFILE
python3 << 'PYEOF2'
import zipfile, shutil, os
jar=os.environ['JARFILE']; tmp=os.environ['TMP']
entry='BOOT-INF/classes/com/nac/gateway/AuthGatewayFilter.class'
cls=tmp+'/out/com/nac/gateway/AuthGatewayFilter.class'
yml=tmp+'/application.yml'
out=jar+'.tmp'
with zipfile.ZipFile(jar,'r') as zin, zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as zout:
    for it in zin.infolist():
        if it.filename==entry or it.filename=='BOOT-INF/classes/application.yml':
            continue
        zout.writestr(it, zin.read(it.filename))
    zout.writestr(entry, open(cls,'rb').read())
    zout.writestr('BOOT-INF/classes/application.yml', open(yml,'rb').read())
shutil.move(out, jar)
print('jar updated')
PYEOF2
echo 'Restarting gateway...'
bash /home/mao/nac/nac.sh stop nac-gateway 2>/dev/null || true
sleep 2
bash /home/mao/nac/nac.sh start nac-gateway
sleep 6
tail -3 /home/mao/nac/logs/nac-gateway.out
