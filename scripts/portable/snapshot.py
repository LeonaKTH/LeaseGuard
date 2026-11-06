from pathlib import Path
import sys,os
from common import sql
q=Path(__file__).resolve().parents[2];sys.path.insert(0,str(q/'scripts/g4'))
from gold import snapshot
snapshot(lambda statement:sql(statement),Path(os.environ['LEASEGUARD_G4_BROWSER_EVIDENCE']),sys.argv[2])
