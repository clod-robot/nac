import telnetlib, time
HOST="192.168.100.1"
def pump(tn, wait=2.0):
    data=b""; end=time.time()+wait
    while time.time()<end:
        try: chunk=tn.read_very_eager()
        except Exception: chunk=b""
        if chunk: data+=chunk; end=time.time()+1.0
        else: time.sleep(0.2)
    return data.decode("utf-8","ignore")
def send(tn, cmd, wait=2.0):
    tn.write(cmd.encode()+b"\r\n"); return pump(tn, wait)
tn=telnetlib.Telnet(HOST,23,timeout=10)
time.sleep(1.5); pump(tn,1.0)
send(tn,"enable",1.5)
tn.write(b"Maipu@1993\r\n"); pump(tn,2.0)
for c in ["no debug aaa all","no debug dot1x all","no debug all"]:
    print("===",c,"===")
    print(send(tn,c,2.0))
print("=== show debugging ===")
print(send(tn,"show debugging",2.5))
tn.write(b"exit\r\n")
