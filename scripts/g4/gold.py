"""Portable read-only MySQL oracle shared by local browser and CI."""
import json
def snapshot(db,out,stage):
    def rows(q):return [json.loads(v) for v in db(q).splitlines() if v]
    f=json.loads((out/'fixture.json').read_text());r=f['repair'];c=f['scheduled'];d=f['returnDevice']
    # Locate fresh UI-created order by a device that no prior fixture uses.
    order=db(f'SELECT id FROM rental_order WHERE drone_id={int(d)}');t=db(f'SELECT id FROM maintenance_ticket WHERE fault_report_id={int(r["fault"])}')
    ids=[int(r['id']),int(c['id']),int(f['failed']['id']),int(f['otherRepair']['id'])]+([int(order)] if order else []);ds=[int(d),int(r['device']),int(c['device']),int(f['failed']['device']),int(f['otherRepair']['device'])]
    truth={'stage':stage,'orders':rows("SELECT JSON_OBJECT('id',id,'status',order_status,'held',inventory_held) FROM rental_order WHERE id IN("+','.join(map(str,ids))+')'),
      'devices':rows("SELECT JSON_OBJECT('id',id,'stock',stock,'status',status) FROM drone WHERE id IN("+','.join(map(str,ds))+')'),
      'returns':rows(f"SELECT JSON_OBJECT('order',order_id,'status',status,'reviewer',reviewer_id,'at',reviewed_at) FROM return_request WHERE order_id={int(order) if order else 0}"),
      'ticket':rows(f"SELECT JSON_OBJECT('id',id,'status',status,'assignee',assignee_id,'acceptedBy',accepted_by,'acceptedAt',accepted_at,'version',version) FROM maintenance_ticket WHERE id={int(t) if t else 0}"),
      'logs':rows("SELECT JSON_OBJECT('device',drone_id,'order',related_order_id,'effect',inventory_effect,'delta',change_amount,'before',before_stock,'after',after_stock,'command',command_id) FROM drone_stock_log WHERE drone_id IN("+','.join(map(str,ds))+') ORDER BY id'),
      'events':rows("SELECT JSON_OBJECT('id',b.id,'command',command_id,'event',event_type,'aggregate',aggregate_type,'resource',aggregate_id,'actor',actor_id,'from',from_status,'to',to_status,'details',details) FROM business_event b WHERE command_id IN(SELECT command_id FROM business_event WHERE aggregate_type='ORDER' AND aggregate_id IN("+','.join(map(str,ids))+")) OR(aggregate_type='TICKET' AND aggregate_id="+str(int(t) if t else 0)+') ORDER BY b.id'),
      'commands':rows("SELECT JSON_OBJECT('id',id,'actor',actor_id,'operation',operation,'resource',resource_id) FROM command_dedup WHERE actor_id IN("+','.join(str(int(f[x])) for x in ['adminId','userId','techAId','techBId'])+") OR(operation='TIMEOUT_CANCEL' AND resource_id="+str(int(c['id']))+')'),
      'tasks':rows(f"SELECT JSON_OBJECT('order',order_id,'status',status,'attempts',attempts,'result',result,'error',last_error) FROM job_task WHERE order_id IN({int(c['id'])},{int(f['failed']['id'])})")}
    dev={str(x['id']):x for x in truth['devices']};orders={str(x['id']):x for x in truth['orders']}
    approved=stage in ('return-approved','repair-started','repair-submitted','repair-accepted','final')
    if order:
      assert (orders[order]['status'],orders[order]['held'],dev[d]['stock'])==((4,0,1) if approved else (3,1,0))
      assert len(truth['returns'])==1 and truth['returns'][0]['status']==(1 if approved else 0)
      assert sum(x['effect']=='RELEASE' for x in truth['logs'] if str(x['order'])==order)==int(approved)
    if stage.startswith('repair-') or stage=='final':
      expected={'repair-started':1,'repair-submitted':4,'repair-accepted':2,'final':2}[stage];ticket=truth['ticket'][0];assert ticket['status']==expected and str(ticket['assignee'])==f['techAId']
      assert dev[r['device']]['stock']==1
      active=int(db(f"SELECT COUNT(*) FROM maintenance_ticket WHERE drone_id={int(r['device'])} AND deleted=0 AND status IN(0,1,4)"));assert dev[r['device']]['status']==(2 if active else 1)
      if expected==2:assert str(ticket['acceptedBy'])==f['adminId'] and ticket['acceptedAt']
    if stage=='final':
      assert orders[c['id']]['status']==5 and orders[c['id']]['held']==0 and dev[c['device']]['stock']==1
      assert any(x['order']==int(c['id']) and x['status']=='DONE' and x['result']=='CANCELLED' for x in truth['tasks'])
      assert any(x['order']==int(f['failed']['id']) and x['status']=='FAILED' for x in truth['tasks'])
      assert (orders[f['failed']['id']]['status'],orders[f['failed']['id']]['held'],dev[f['failed']['device']]['stock'])==(0,1,0),'FAILED is not an inventory release'
      assert sum(x['effect']=='RELEASE' for x in truth['logs'] if x['order']==int(f['failed']['id']))==0
      other=db(f"SELECT status,assignee_id FROM maintenance_ticket WHERE id={int(f['otherRepair']['ticket'])}")
      assert other=='0\t'+f['techBId'],'Other technician ticket must remain unchanged'
      assert dev[f['otherRepair']['device']]['stock']==1 and dev[f['otherRepair']['device']]['status']==2
      system=int(db("SELECT id FROM user WHERE principal_kind='SYSTEM' AND status=0 AND role=0"));assert any(x['operation']=='TIMEOUT_CANCEL' and x['actor']==system and x['resource']==int(c['id']) for x in truth['commands'])
      assert sum(x['effect']=='RELEASE' for x in truth['logs'] if x['order']==int(c['id']))==1
      assert [x['to'] for x in truth['events'] if x['aggregate']=='TICKET' and x['event'] in ('START_REPAIR','SUBMIT_REPAIR','ACCEPT_REPAIR')]==[1,4,2]
    for device in truth['devices']:
      logs=[x for x in truth['logs'] if x['device']==device['id']];assert all(x['before']+x['delta']==x['after'] for x in logs);assert all(a['after']==b['before'] for a,b in zip(logs,logs[1:]));assert logs[-1]['after']==device['stock']
    truth['gold_passed']=True;(out/('db-'+stage+'.json')).write_text(json.dumps(truth,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print('G4 MySQL Gold:',stage)
