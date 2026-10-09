#!/usr/bin/env python3
# Verify RADIUS Access-Accept signatures captured at 09:17:53.698
import hmac, hashlib, struct

SECRET = b'testing123'
REQ_AUTH = bytes.fromhex('bfad3f2b80b161fbad1495d993b139de')
RESP_AUTH_GOT = bytes.fromhex('b8c0ba8e1d53346342fa927f940e7d29')
MSGAUTH_GOT = bytes.fromhex('c30b704b81d9e9a8b55219e046061860')

attrs = b''
attrs += bytes.fromhex('4f0603020004')
attrs += bytes([0x50,0x12]) + MSGAUTH_GOT
s = b'S=49EDC3F69518413190A0D86A2C9D9A5B7502D2A5 M=Welcome to NAC RADIUS'
attrs += bytes([0x1a, 6+2+len(s)]) + struct.pack('>I',311) + bytes([26, len(s)]) + s
attrs += bytes.fromhex('1a2a000001371024b14b65fe33dde14860b96e107d3ae69c0d37faf092faeb5473f813cd62ad80873db1')
attrs += bytes.fromhex('1a2a000001371124b58e613bc4f1e6f763cb1756b14e3527d161646ec4912cfe7c85c22ba1c410a6f5a0')
attrs += bytes.fromhex('060600000002')
attrs += bytes.fromhex('40060000000d')
attrs += bytes.fromhex('410600000006')
attrs += bytes.fromhex('51040031')
attrs += bytes.fromhex('1b0600000e10')

hdr = bytes([0x02,0xf2]) + struct.pack('>H', 20+len(attrs))
pkt_with_resp = hdr + RESP_AUTH_GOT + attrs
print('packet length =', len(pkt_with_resp), '(expect 231)')

# 1) Response Authenticator: MD5(Code+ID+Len+RequestAuth+Attrs+Secret)
calc_resp = hashlib.md5(hdr + REQ_AUTH + attrs + SECRET).digest()
print('ResponseAuth calc =', calc_resp.hex())
print('ResponseAuth got  =', RESP_AUTH_GOT.hex())
print('ResponseAuth MATCH:', calc_resp == RESP_AUTH_GOT)

# 2) Message-Authenticator: HMAC-MD5 over full packet with attr80 value zeroed
def zero_msgauth(p):
    i = 20
    while i < len(p):
        t, l = p[i], p[i+1]
        if t == 80:
            return p[:i+2] + b'\x00'*(l-2) + p[i+l:]
        i += l
    raise Exception('no msgauth')

z = zero_msgauth(pkt_with_resp)
# variant A: response-auth slot holds final Response Authenticator
vA = hmac.new(SECRET, z, hashlib.md5).digest()
# variant B: response-auth slot holds Request Authenticator (pre-sign state)
zB = z[:4] + REQ_AUTH + z[20:]
vB = hmac.new(SECRET, zB, hashlib.md5).digest()
# variant C: response-auth zeroed
zC = z[:4] + b'\x00'*16 + z[20:]
vC = hmac.new(SECRET, zC, hashlib.md5).digest()
print('MsgAuth got  =', MSGAUTH_GOT.hex())
print('HMAC A (final respAuth in slot):', vA.hex(), vA == MSGAUTH_GOT)
print('HMAC B (reqAuth in slot)       :', vB.hex(), vB == MSGAUTH_GOT)
print('HMAC C (zeroed slot)           :', vC.hex(), vC == MSGAUTH_GOT)
