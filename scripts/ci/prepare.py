"""Only a fresh GitHub Actions MySQL service; no host install or existing database."""
import os,secrets,subprocess
assert os.environ.get('GITHUB_ACTIONS')=='true','CI service only'
root=os.environ['MYSQL_ROOT_PASSWORD'];container=os.environ['MYSQL_CONTAINER']
assert root and container
def sql(statement):
    env=os.environ.copy();env['MYSQL_PWD']=root
    return subprocess.check_output(['docker','exec','-i','-e','MYSQL_PWD',container,'mysql','--protocol=TCP','-h127.0.0.1','-uroot','--batch','--skip-column-names'],input=statement.encode(),env=env).decode().strip()
assert sql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name LIKE 'leaseguard_%'")=='0','Never reset a preexisting catalog'
values={}
def secret(key):
    value=secrets.token_hex(24);values[key]=value;print('::add-mask::'+value);return value
for suffix in ['dev','test']:
    schema='leaseguard_'+suffix;sql('CREATE DATABASE '+schema+' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
    app=secret('LEASEGUARD_'+suffix.upper()+'_PASSWORD');migration=secret('LEASEGUARD_'+suffix.upper()+'_MIGRATION_PASSWORD')
    sql(f"CREATE USER '{schema}'@'%' IDENTIFIED BY '{app}'; GRANT SELECT,INSERT,UPDATE,DELETE ON {schema}.* TO '{schema}'@'%'; CREATE USER 'leaseguard_migrate_{suffix}'@'%' IDENTIFIED BY '{migration}'; GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES ON {schema}.* TO 'leaseguard_migrate_{suffix}'@'%'")
for generation,key in [('g2','LEASEGUARD_FRESH_PASSWORD'),('g4','LEASEGUARD_G4_FRESH_PASSWORD')]:
    schema='leaseguard_test_'+generation+'_fresh';user='leaseguard_'+generation+'_fresh';pw=secret(key)
    sql(f"CREATE DATABASE {schema} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER '{user}'@'%' IDENTIFIED BY '{pw}'; GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES ON {schema}.* TO '{user}'@'%'")
values.update(LEASEGUARD_DB_URL='jdbc:mysql://127.0.0.1:13308/leaseguard_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai',LEASEGUARD_DB_USER='leaseguard_test',LEASEGUARD_DB_PASSWORD=values['LEASEGUARD_TEST_PASSWORD'],LEASEGUARD_MIGRATION_USER='leaseguard_migrate_test',LEASEGUARD_MIGRATION_PASSWORD=values['LEASEGUARD_TEST_MIGRATION_PASSWORD'],LEASEGUARD_BOOTSTRAP_USER='ci_admin',LEASEGUARD_BOOTSTRAP_PASSWORD=secret('CI_BOOTSTRAP_PASSWORD'),LEASEGUARD_JWT_SECRET=secret('CI_JWT_SECRET'),LEASEGUARD_E2E_PASSWORD=secret('CI_E2E_PASSWORD'))
with open(os.environ['GITHUB_ENV'],'a') as out:
    for key,value in values.items():out.write(key+'='+value+'\n')
print('Four fresh catalogs; application DML and migration DDL accounts separated')
