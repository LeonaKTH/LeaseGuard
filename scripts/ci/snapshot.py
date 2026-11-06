"""Read-only CI oracle through the ephemeral service's DML account."""
from pathlib import Path
import sys,os,subprocess
assert os.environ.get('GITHUB_ACTIONS')=='true'
q=Path(__file__).resolve().parents[2];sys.path.insert(0,str(q/'scripts/g4'))
from gold import snapshot
def db(statement):
    env=os.environ.copy();env['MYSQL_PWD']=os.environ['LEASEGUARD_DB_PASSWORD']
    return subprocess.check_output(['docker','exec','-i','-e','MYSQL_PWD',os.environ['MYSQL_CONTAINER'],'mysql','--protocol=TCP','-h127.0.0.1','-uleaseguard_dev','--batch','--skip-column-names','--raw','leaseguard_dev'],input=statement.encode(),env=env).decode().strip()
snapshot(db,Path(os.environ['LEASEGUARD_G4_BROWSER_EVIDENCE']),sys.argv[2])
