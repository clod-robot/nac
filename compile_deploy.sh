#!/bin/bash
set -e
JAVAC=/home/mao/software/jdk1.8.0_202/bin/javac
JARFILE=/home/mao/nac/app/nac-auth-1.0-SNAPSHOT.jar
SRC=$1
CLASS=$2
export CLASS
TMP=/tmp/eap-compile
rm -rf $TMP
mkdir -p $TMP/out
python3 << 'PYEOF'
import zipfile, os
jar_path = '/home/mao/nac/app/nac-auth-1.0-SNAPSHOT.jar'
tmp = '/tmp/eap-compile'
with zipfile.ZipFile(jar_path, 'r') as z:
    for name in z.namelist():
        if name.startswith('BOOT-INF/classes/') and not name.endswith('/'):
            z.extract(name, tmp)
        elif name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
            z.extract(name, tmp)
PYEOF
CP="$TMP/BOOT-INF/classes"
for f in $TMP/BOOT-INF/lib/*.jar; do CP="$CP:$f"; done
echo "=== Compiling $SRC ==="
$JAVAC -encoding UTF-8 -cp "$CP" -d $TMP/out $SRC
echo "Compile OK"
echo "=== Updating jar with $CLASS ==="
cp $JARFILE ${JARFILE}.bak.$(date +%s)
python3 << 'PYEOF2'
import zipfile, shutil, os
jar_path = '/home/mao/nac/app/nac-auth-1.0-SNAPSHOT.jar'
cls = os.environ['CLASS']
class_file = '/tmp/eap-compile/out/' + cls
entry = 'BOOT-INF/classes/' + cls
tmp_path = jar_path + '.tmp'
with zipfile.ZipFile(jar_path, 'r') as zin:
    with zipfile.ZipFile(tmp_path, 'w', zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            if item.filename == entry:
                continue
            zout.writestr(item, zin.read(item.filename))
        with open(class_file, 'rb') as f:
            zout.writestr(entry, f.read())
shutil.move(tmp_path, jar_path)
print('jar updated')
PYEOF2
echo "=== Restart ==="
bash /home/mao/nac/nac.sh stop nac-auth
sleep 2
bash /home/mao/nac/nac.sh start nac-auth
sleep 8
grep 'Started Auth' /home/mao/nac/logs/nac-auth.out | tail -1
