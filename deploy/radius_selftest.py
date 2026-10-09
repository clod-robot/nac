import socket, os, hashlib, struct

secret = b'testing123'
auth = os.urandom(16)
user = b'testuser'

def pap(pwd, auth, secret):
    if len(pwd) % 16:
        pwd = pwd + b'\x00' * (16 - len(pwd) % 16)
    out = b''
    prev = auth
    for i in range(0, len(pwd), 16):
        m = hashlib.md5(secret + prev).digest()
        blk = bytes(a ^ b for a, b in zip(pwd[i:i+16], m))
        out += blk
        prev = blk
    return out

attrs = b''
attrs += bytes([1, 2 + len(user)]) + user                     # User-Name
pe = pap(user, auth, secret)
attrs += bytes([2, 2 + len(pe)]) + pe                          # User-Password (PAP)
attrs += bytes([4, 6]) + socket.inet_aton('192.168.100.1')     # NAS-IP-Address
nid = b'nac-selftest'
attrs += bytes([32, 2 + len(nid)]) + nid                       # NAS-Identifier

length = 20 + len(attrs)
pkt = bytes([1, 99]) + struct.pack('>H', length) + auth + attrs  # code=1 Access-Request

s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.settimeout(4)
s.sendto(pkt, ('127.0.0.1', 1812))
try:
    data, addr = s.recvfrom(4096)
    print('RESPONSE code=%d from %s  (2=Accept 3=Reject)' % (data[0], addr))
except socket.timeout:
    print('TIMEOUT: no response from RADIUS server')
