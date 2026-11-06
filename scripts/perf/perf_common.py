"""Portable perf inputs. No author workspace, encrypted credential store or external account."""
from pathlib import Path
import sys,os,json,time,subprocess,hashlib,base64,hmac,secrets
Q=Path(__file__).resolve().parents[2];D=Path(os.environ.get('LEASEGUARD_PERF_EVIDENCE_DIR',str(Q/'docs/evidence/perf-reproduce'))).resolve();L=Q/'.local/perf'
assert D.is_relative_to(Q),'Evidence stays in this project';D.mkdir(parents=True,exist_ok=True);L.mkdir(parents=True,exist_ok=True)
sys.path.insert(0,str(Q/'scripts/portable'));from common import sql
def db(statement,root=False,migration=False):
 assert not migration,'Use formal Flyway migrations, not this read/data-fixture adapter'
 return sql(statement,None if root else 'leaseguard_perf','root' if root else os.environ['LEASEGUARD_DB_USER'],os.environ['LEASEGUARD_ROOT_PASSWORD'] if root else os.environ['LEASEGUARD_DB_PASSWORD'])
def token(role):
 assert os.environ['LEASEGUARD_DB_URL'].startswith('jdbc:mysql://127.0.0.1:13308/leaseguard_perf?')
 b64=lambda x:base64.urlsafe_b64encode(x).rstrip(b'=');uid={'admin':101,'user':1001,'tech':102}[role]
 payload={'userId':uid,'sub':'G5 synthetic','role':{'admin':1,'user':0,'tech':2}[role],'credentialStamp':hashlib.sha256(b'credential:!G5_SYNTHETIC_NON_LOGIN!').hexdigest(),'iat':int(time.time()),'exp':int(time.time())+7200}
 body=b64(b'{"alg":"HS256"}')+b'.'+b64(json.dumps(payload).encode());return (body+b'.'+b64(hmac.new(os.environ['LEASEGUARD_JWT_SECRET'].encode(),body,hashlib.sha256).digest())).decode()
