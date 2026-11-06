"""Portable native MySQL client adapter; passwords only in child environment."""
import os,subprocess
def sql(statement,schema='leaseguard_dev',user=None,password=None):
 assert schema in ['leaseguard_dev','leaseguard_test','leaseguard_test_g2_fresh','leaseguard_test_g4_fresh','leaseguard_perf',None]
 env=os.environ.copy();env['MYSQL_PWD']=password if password is not None else env['LEASEGUARD_DB_PASSWORD']
 command=[env.get('MYSQL_CLIENT','mysql'),'--no-defaults','--protocol=TCP','-h127.0.0.1','-P13308','-u'+(user or env['LEASEGUARD_DB_USER']),'--batch','--skip-column-names','--raw','--default-character-set=utf8mb4']
 if schema:command.append(schema)
 r=subprocess.run(command,input=statement.encode(),env=env,capture_output=True)
 if r.returncode:raise RuntimeError('MySQL command failed; no SQL/password/driver error is echoed')
 return r.stdout.decode('utf-8').strip()
