from perf_common import *
import urllib.request,statistics
PHASE=sys.argv[1] if len(sys.argv)>1 else 'before'
def request(path,role='admin'):
 req=urllib.request.Request('http://127.0.0.1:18084/api'+path,headers={'Authorization':'Bearer '+token(role),'X-G3-Measure':'true'})
 t=time.perf_counter_ns()
 with urllib.request.urlopen(req,timeout=60) as r:data=json.load(r);headers=dict(r.headers)
 assert data['code']==200
 return data['data'],{'http_ms':(time.perf_counter_ns()-t)/1e6,'jdbc_ms':int(headers.get('X-G3-DB-Nanos','0'))/1e6,'assembly_ms':int(headers.get('X-G5-Assembly-Nanos','0'))/1e6,'sql_count':int(headers.get('X-G3-SQL-Count','0')),'pool_waiting':int(headers.get('X-G5-Pool-Awaiting','0')),'checkout_ms':int(headers.get('X-G5-Checkout-Nanos','0'))/1e6}
def cases(size=20):return [
 ('user_orders','user',f'/user/orders?pageSize={size}',"SELECT id FROM rental_order WHERE user_id=1001 AND deleted=0 ORDER BY created_time DESC,id DESC LIMIT "+str(size)),
 ('user_renting','user',f'/user/orders?pageSize={size}&orderStatus=3',"SELECT id FROM rental_order WHERE user_id=1001 AND deleted=0 AND order_status=3 ORDER BY created_time DESC,id DESC LIMIT "+str(size)),
 ('admin_orders','admin',f'/admin/order/list?pageSize={size}',"SELECT id FROM rental_order WHERE deleted=0 ORDER BY created_time DESC,id DESC LIMIT "+str(size)),
 ('admin_renting','admin',f'/admin/order/list?pageSize={size}&orderStatus=3',"SELECT id FROM rental_order WHERE deleted=0 AND order_status=3 ORDER BY created_time DESC,id DESC LIMIT "+str(size)),
 ('tech_tickets','tech',f'/workbench/tickets?pageSize={size}',"SELECT id FROM maintenance_ticket WHERE deleted=0 AND assignee_id=102 ORDER BY created_time DESC,id DESC LIMIT "+str(size)),
 ('tech_pending','tech',f'/workbench/tickets?pageSize={size}&status=0',"SELECT id FROM maintenance_ticket WHERE deleted=0 AND assignee_id=102 AND status=0 ORDER BY created_time DESC,id DESC LIMIT "+str(size)),
 ('admin_repairs','admin',f'/admin/maintenance/ticket/list?pageSize={size}',"SELECT id FROM maintenance_ticket WHERE deleted=0 ORDER BY created_time DESC,id DESC LIMIT "+str(size)),
 ('order_detail','user','/order/3',None),('order_timeline','user',f'/audit/orders/3?pageSize={size}',None),('tasks_done','admin',f'/admin/tasks?pageSize={size}&status=DONE',None)]
if __name__=='__main__':
 output=[]
 for size in [10,50,100]:
  for name,role,path,oracle in cases(size):
   for _ in range(2):request(path,role)
   samples=[]
   for _ in range(5):
    body,sample=request(path,role);samples.append(sample)
    if oracle:
     assert [int(r['id']) for r in body['records']]==[int(n) for n in db(oracle).splitlines()],name
     if role=='tech':assert all(r['assigneeId']=='102' for r in body['records'])
   output.append({'case':name,'page_size':size,'path':path,'role':role,'samples':samples,'result_sha256':hashlib.sha256(json.dumps(body,sort_keys=True).encode()).hexdigest(),'oracle_passed':True})
   (D/('queries-'+PHASE+'.json')).write_text(json.dumps(output,indent=2));print(PHASE,name,size,'http median',round(statistics.median(s['http_ms'] for s in samples),2),'sql',samples[-1]['sql_count'],flush=True)
 plans={name:db('EXPLAIN ANALYZE '+oracle.replace('SELECT id FROM','SELECT * FROM')) for name,role,path,oracle in cases(100) if oracle}
 (D/('explain-'+PHASE+'.json')).write_text(json.dumps(plans,indent=2))
 (D/('indexes-'+PHASE+'.txt')).write_text(db("SELECT table_name,index_name,GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema='leaseguard_perf' GROUP BY table_name,index_name ORDER BY table_name,index_name"))
