"""Fresh-instance verification. Never uses DPAPI, original workspace scripts or development Git."""
from pathlib import Path
import sys,os,argparse,secrets,subprocess,json,time,socket,getpass,hashlib,xml.etree.ElementTree as ET
from common import sql
p=argparse.ArgumentParser();p.add_argument('--mysql',default='mysql');p.add_argument('--expected-uuid',required=True);p.add_argument('--maven',default='mvn.cmd' if os.name=='nt' else 'mvn');p.add_argument('--node',default='node');p.add_argument('--npm',default='npm.cmd' if os.name=='nt' else 'npm');p.add_argument('--repo');p.add_argument('--browser',action='store_true');p.add_argument('--chrome');p.add_argument('--source-sha',default='candidate-snapshot');args=p.parse_args()
q=Path(__file__).resolve().parents[2];private=q/'.local/portable';private.mkdir(parents=True,exist_ok=True);evidence=q/'docs/evidence/reproduce';evidence.mkdir(parents=True,exist_ok=True)
os.environ['MYSQL_CLIENT']=str(args.mysql)
root=os.environ.get('LEASEGUARD_ROOT_PASSWORD') or getpass.getpass('Password of the NEW isolated local MySQL root: ')
assert root,'Root password required; never initialize an exposed passwordless instance here'
root_sql=lambda text:sql(text,None,'root',root)
assert root_sql('SELECT @@port')=='13308'
identity=root_sql('SELECT @@server_uuid');assert identity==args.expected_uuid,'Dedicated instance UUID mismatch'
assert root_sql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name IN('leaseguard_dev','leaseguard_test','leaseguard_test_g2_fresh','leaseguard_test_g4_fresh')")=='0','Only a fresh isolated instance, never reset a developer catalog'
passwords={}
for suffix in ['dev','test']:
 schema='leaseguard_'+suffix;passwords[suffix]=secrets.token_hex(24);passwords['migrate_'+suffix]=secrets.token_hex(24)
 root_sql(f"CREATE DATABASE {schema} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;CREATE USER '{schema}'@'localhost' IDENTIFIED BY '{passwords[suffix]}';GRANT SELECT,INSERT,UPDATE,DELETE ON {schema}.* TO '{schema}'@'localhost';CREATE USER 'leaseguard_migrate_{suffix}'@'localhost' IDENTIFIED BY '{passwords['migrate_'+suffix]}';GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES ON {schema}.* TO 'leaseguard_migrate_{suffix}'@'localhost'")
for generation in ['g2','g4']:
 schema='leaseguard_test_'+generation+'_fresh';user='leaseguard_'+generation+'_fresh';passwords[generation]=secrets.token_hex(24)
 root_sql(f"CREATE DATABASE {schema} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;CREATE USER '{user}'@'localhost' IDENTIFIED BY '{passwords[generation]}';GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES ON {schema}.* TO '{user}'@'localhost'")
env=os.environ.copy();env['NPM_CONFIG_CACHE']=os.environ.get('NPM_CONFIG_CACHE',str(private/'npm-cache'));env.update(LEASEGUARD_DB_URL='jdbc:mysql://127.0.0.1:13308/leaseguard_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai',LEASEGUARD_DB_USER='leaseguard_test',LEASEGUARD_DB_PASSWORD=passwords['test'],LEASEGUARD_MIGRATION_USER='leaseguard_migrate_test',LEASEGUARD_MIGRATION_PASSWORD=passwords['migrate_test'],LEASEGUARD_DEV_MIGRATION_PASSWORD=passwords['migrate_dev'],LEASEGUARD_FRESH_PASSWORD=passwords['g2'],LEASEGUARD_G4_FRESH_PASSWORD=passwords['g4'],LEASEGUARD_JWT_SECRET=secrets.token_hex(24),LEASEGUARD_EVIDENCE_DIR=str(evidence),LEASEGUARD_TEST_SHA=args.source_sha,LEASEGUARD_SETUP_NEW_CATALOGS='YES',LEASEGUARD_SQL_FIXTURE_MODE='generated',LEASEGUARD_UPLOAD_PATH=str(private/'uploads')+os.sep,LEASEGUARD_JOBS_ENABLED='false',MAVEN_OPTS='-Xmx512m -Dfile.encoding=UTF-8')
def run(command,cwd,name):
 with (private/(name+'.log')).open('wb') as log:r=subprocess.run(command,cwd=cwd,env=env,stdout=log,stderr=subprocess.STDOUT)
 if r.returncode:raise RuntimeError(name+' failed; private local log retained')
 print('PASS',name,flush=True)
mvn=[str(args.maven),'-B','-ntp']+(['-Dmaven.repo.local='+str(Path(args.repo).resolve())] if args.repo else [])
run(mvn+['test-compile','dependency:build-classpath','-Dmdep.outputFile=target/portable-classpath.txt','-DskipTests'],q/'drone-rental','compile')
entries=(q/'drone-rental/target/portable-classpath.txt').read_text().strip().split(os.pathsep)
if args.repo:
 cache=Path(args.repo).resolve();marker=cache.name+os.sep
 entries=[v if Path(v).is_file() else str(cache/v.rsplit(marker,1)[1]) for v in entries]
assert all(Path(v).is_file() for v in entries)
cp=os.pathsep.join([str(q/'drone-rental/target/test-classes'),str(q/'drone-rental/target/classes'),*entries]);java=str(Path(env['JAVA_HOME'])/'bin'/('java.exe' if os.name=='nt' else 'java')) if env.get('JAVA_HOME') else 'java'
run([java,'-Xmx384m','-cp',cp,'com.drone.rental.PortableDatabaseSetup'],q,'fresh-migration-setup')
run(mvn+['verify'],q/'drone-rental','java-mysql-verify')
front=q/'drone-rental/drone-rental-web';env['NODE_OPTIONS']='--max-old-space-size=1024';env['PATH']=str(Path(args.node).resolve().parent)+os.pathsep+env['PATH']
for name,command in [('npm-ci',['ci','--ignore-scripts','--registry=https://registry.npmjs.org']),('frontend-unit',['run','test:commands']),('frontend-types',['run','typecheck']),('frontend-build',['run','build'])]:run([args.npm,*command],front,name)
summary={'new_instance_uuid':identity,'private_development_files_used':False,'source_sha':args.source_sha,'java_suites':[],'frontend_clean_install':True,'browser_run':False}
for group in ['surefire-reports','failsafe-reports']:
 for file in (q/'drone-rental/target'/group).glob('TEST-*.xml'):
  r=ET.parse(file).getroot();row={k:int(r.get(k,'0')) for k in ['tests','failures','errors','skipped']};row['suite']=r.get('name');assert not any(row[k] for k in ['failures','errors','skipped']);summary['java_suites'].append(row)
if args.browser:
 (q/'.local/g4-browser').mkdir(parents=True,exist_ok=True)
 assert args.chrome and Path(args.chrome).is_file(),'Reuse an existing controlled browser; never download one locally'
 for port in [18084,15174]:
  with socket.socket() as check:assert check.connect_ex(('127.0.0.1',port))!=0
 env.update(LEASEGUARD_DB_URL=env['LEASEGUARD_DB_URL'].replace('/leaseguard_test?','/leaseguard_dev?'),LEASEGUARD_DB_USER='leaseguard_dev',LEASEGUARD_DB_PASSWORD=passwords['dev'],LEASEGUARD_MIGRATION_USER='leaseguard_migrate_dev',LEASEGUARD_MIGRATION_PASSWORD=passwords['migrate_dev'],LEASEGUARD_EVIDENCE_DIR=str(evidence),LEASEGUARD_E2E_PASSWORD=secrets.token_hex(24),LEASEGUARD_CHROME=args.chrome,LEASEGUARD_G4_BROWSER_EVIDENCE=str(evidence/'browser'),LEASEGUARD_G4_SNAPSHOT_TOOL=str(q/'scripts/portable/snapshot.py'),LEASEGUARD_PYTHON=sys.executable)
 run([java,'-Xmx384m','-cp',cp,'com.drone.rental.BrowserFixtureG4Setup'],q,'browser-fixture')
 env['LEASEGUARD_JOBS_ENABLED']='true';processes=[];handles=[]
 try:
  for name,command,cwd in [('backend',[java,'-Xmx384m','-jar',str(q/'drone-rental/target/drone-rental-1.0.0.jar')],private),('frontend',[args.node,str(front/'node_modules/vite/bin/vite.js'),'preview','--host','127.0.0.1','--port','15174','--strictPort'],front)]:
   log=(private/(name+'.log')).open('wb');handles.append(log);processes.append(subprocess.Popen(command,cwd=cwd,env=env,stdout=log,stderr=subprocess.STDOUT,**({'creationflags':subprocess.CREATE_NO_WINDOW} if os.name=='nt' else {})))
  import urllib.request
  for url in ['http://127.0.0.1:18084/api/drone/list','http://127.0.0.1:15174']:
   deadline=time.monotonic()+60
   while True:
    try:urllib.request.urlopen(url,timeout=2).close();break
    except Exception:
     if time.monotonic()>deadline:raise
     time.sleep(.25)
  run([args.npm,'ci','--ignore-scripts','--registry=https://registry.npmjs.org'],q/'browser-tests','browser-dependency')
  run([args.node,str(q/'browser-tests/g4.mjs')],q,'browser-three-chains');summary['browser_run']=True
  with urllib.request.urlopen('http://127.0.0.1:18084/api/v3/api-docs',timeout=10) as response:api_document=json.load(response)
  assert '/common/upload' not in api_document['paths']
  (evidence/'openapi.json').write_text(json.dumps(api_document,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
 finally:
  for process in processes:
   if process.poll() is None:
    if os.name=='nt':subprocess.run(['taskkill','/PID',str(process.pid),'/T','/F'],capture_output=True,check=True)
    else:process.terminate()
    process.wait(timeout=30)
  for handle in handles:handle.close()
summary['java_tests']=sum(t['tests'] for t in summary['java_suites']);summary['passed']=True
(evidence/'candidate-verification.json').write_text(json.dumps(summary,indent=2)+'\n');print('Independent verification complete:',summary['java_tests'],'Java tests')
