import telnetlib, time
HOST="192.168.100.1"
def pump(tn, wait=3.0):
    data=b""; end=time.time()+wait
    while time.time()<end:
        try: chunk=tn.read_very_eager()
        except Exception: chunk=b""
        if chunk: data+=chunk; end=time.time()+1.5
        else: time.sleep(0.2)
    return data.decode("utf-8","ignore")
tn=telnetlib.Telnet(HOST,23,timeout=10)
time.sleep(1.5); pump(tn,1.0)
tn.write(b"enable\r\n"); pump(tn,1.0)
tn.write(b"Maipu@1993\r\n"); pump(tn,1.5)
tn.write(b"write\r\n"); pump(tn,2.0)
tn.write(b"Yes\r\n")
print(pump(tn,6.0))
tn.write(b"exit\r\n")
