"""Closed-loop real HTTP, fixed duration, all rounds retained. No command mutation."""
from perf_common import *
from query import cases
from concurrent.futures import ThreadPoolExecutor
import http.client,threading,statistics,gzip,csv,collections,ctypes
phase=sys.argv[1];warm=float(os.environ.get('G5_WARM_SECONDS','30'));duration=float(os.environ.get('G5_MEASURE_SECONDS','60'));levels=[int(x) for x in os.environ.get('G5_CONCURRENCY','1,10,30').split(',')]
work=cases(20)
if phase.startswith('worker'):work=[x for x in work if x[0] in ['user_renting','admin_renting','tech_tickets']]
tokens={r:token(r) for r in ['user','admin','tech']}
def quantile(values,p):
 s=sorted(values);return s[min(len(s)-1,int((len(s)-1)*p))] if s else None
class Memory(ctypes.Structure):_fields_=[('length',ctypes.c_ulong),('load',ctypes.c_ulong)]+[(v,ctypes.c_ulonglong) for v in ['total','available','page','freePage','virtual','freeVirtual','extended']]
def memory():
 m=Memory();m.length=ctypes.sizeof(m);ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(m));return {'available_physical_bytes':m.available,'system_memory_load_pct':m.load}
def process_cpu(pid):
 k=ctypes.windll.kernel32;k.OpenProcess.restype=ctypes.c_void_p;h=k.OpenProcess(0x1000,False,pid)
 if not h:return None
 values=[ctypes.c_ulonglong() for _ in range(4)];ok=k.GetProcessTimes(ctypes.c_void_p(h),*[ctypes.byref(v) for v in values]);k.CloseHandle(ctypes.c_void_p(h));return (values[2].value+values[3].value)/1e7 if ok else None
def status():return db("SHOW GLOBAL STATUS WHERE Variable_name IN ('Innodb_deadlocks','Innodb_row_lock_waits','Innodb_row_lock_time','Threads_connected','Threads_running')")+'\nlock_deadlocks\t'+db("SELECT count FROM information_schema.innodb_metrics WHERE name='lock_deadlocks'",root=True)+('\n'+db("SELECT status,COUNT(*) FROM job_task WHERE id>10000 GROUP BY status") if phase.startswith('worker') else '')
backend_pid=int(os.environ['LEASEGUARD_PERF_JAVA_PID']);mysql_pid=int(os.environ['LEASEGUARD_PERF_MYSQL_PID'])
results=[]
for concurrent in levels:
 for round_no in [0,1,2,3]:
  seconds=warm if round_no==0 else duration;rows=[];lock=threading.Lock();began=time.perf_counter();deadline=began+seconds;before=status();cpu_before={'backend':process_cpu(backend_pid),'mysql':process_cpu(mysql_pid)};resources=[memory()]
  def worker(index):
   connection=http.client.HTTPConnection('127.0.0.1',18084,timeout=30);local=[];seq=index
   while time.perf_counter()<deadline:
    name,role,path,_=work[seq%len(work)];seq+=1;start=time.perf_counter_ns();code=0;sql=0;jdbc=assembly=checkout=0;waiting=0
    try:
     connection.request('GET','/api'+path,headers={'Authorization':'Bearer '+tokens[role],'X-G3-Measure':'true'});r=connection.getresponse();raw=r.read();code=r.status
     if code==200:
      data=json.loads(raw)['data'];assert isinstance(data,dict)
      if role=='tech':assert all(t['assigneeId']=='102' for t in data['records'])
      if name.startswith('user_') and 'records' in data:assert all(int(o['userId'])==1001 for o in data['records'])
     sql=int(r.getheader('X-G3-SQL-Count','0'));jdbc=int(r.getheader('X-G3-DB-Nanos','0'))/1e6;assembly=int(r.getheader('X-G5-Assembly-Nanos','0'))/1e6;checkout=int(r.getheader('X-G5-Checkout-Nanos','0'))/1e6;waiting=int(r.getheader('X-G5-Pool-Awaiting','0'))
    except Exception:
     code=0;connection.close();connection=http.client.HTTPConnection('127.0.0.1',18084,timeout=30)
    local.append((name,code,(time.perf_counter_ns()-start)/1e6,sql,jdbc,assembly,checkout,waiting))
   connection.close()
   with lock:rows.extend(local)
  with ThreadPoolExecutor(max_workers=concurrent) as pool:
   futures=[pool.submit(worker,i) for i in range(concurrent)]
   for future in futures:future.result()
  elapsed=time.perf_counter()-began;after=status();resources.append(memory());cpus={}
  for key,pid in [('backend',backend_pid),('mysql',mysql_pid)]:
   value=process_cpu(pid);cpus[key+'_one_core_pct']=None if value is None or cpu_before[key] is None else (value-cpu_before[key])/elapsed*100
  if round_no==0:print(phase,'warm',concurrent,round(elapsed,2),'seconds',len(rows),'requests',flush=True);continue
  lat=[r[2] for r in rows];codes=collections.Counter(r[1] for r in rows);record={'phase':phase,'concurrency':concurrent,'round':round_no,'warmup_seconds':warm,'target_seconds':seconds,'actual_seconds':elapsed,'requests':len(rows),'rps':len(rows)/elapsed,'p50_ms':quantile(lat,.5),'p95_ms':quantile(lat,.95),'p99_ms':quantile(lat,.99) if len(lat)>=1000 else None,'p99_minimum_samples':1000,'status_counts':dict(codes),'success_rate':codes[200]/len(rows),'unexpected_500':sum(n for c,n in codes.items() if c>=500),'expected_conflicts':codes[409],'pool_awaiting_response_snapshot_max':max(r[7] for r in rows),'checkout_p95_ms':quantile([r[6] for r in rows],.95),'mysql_status_before':before,'mysql_status_after':after,'cpu':cpus,'memory':resources,'source_sha':subprocess.check_output(['git','-C',str(Q),'rev-parse','HEAD'],text=True).strip()}
  with gzip.open(D/f'load-{phase}-{concurrent}-{round_no}.csv.gz','wt',newline='') as f:w=csv.writer(f);w.writerow(['case','status','http_ms','sql_count','jdbc_ms','assembly_ms','checkout_ms','awaiting_snapshot']);w.writerows(rows)
  results.append(record);(D/('load-'+phase+'.json')).write_text(json.dumps(results,indent=2));print(phase,concurrent,round_no,'RPS',round(record['rps'],2),'p95',round(record['p95_ms'],2),'success',record['success_rate'],flush=True)
