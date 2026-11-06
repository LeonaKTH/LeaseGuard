type Store = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>
export interface PendingOperation { fingerprint:string; key:string }
function canonical(value:unknown):string {
 if(Array.isArray(value))return '['+value.map(canonical).join(',')+']'
 if(value!==null && typeof value==='object'){const object=value as Record<string,unknown>;return '{'+Object.keys(object).sort().map(k=>JSON.stringify(k)+':'+canonical(object[k])).join(',')+'}'}
 return JSON.stringify(value) ?? 'null'
}
/** Stores only SHA-256 fingerprints and random IDs, never credentials or request bodies. */
export class OperationKeys {
 private storage:Store
 private randomId:()=>string
 constructor(storage:Store,randomId:()=>string=()=>crypto.randomUUID()){this.storage=storage;this.randomId=randomId}
 async begin(method:string,path:string,body:unknown):Promise<PendingOperation>{
  const bytes=new TextEncoder().encode(method.toUpperCase()+' '+path+' '+canonical(body))
  const digest=await crypto.subtle.digest('SHA-256',bytes)
  const fingerprint='leaseguard-pending:'+Array.from(new Uint8Array(digest)).map(b=>b.toString(16).padStart(2,'0')).join('')
  let key=this.storage.getItem(fingerprint)
  if(!key){key=this.randomId();this.storage.setItem(fingerprint,key)}
  return {fingerprint,key}
 }
 complete(operation:PendingOperation):void { if(this.storage.getItem(operation.fingerprint)===operation.key)this.storage.removeItem(operation.fingerprint) }
}
export function isBusinessCommand(method:string,path:string):boolean {
 return ['POST','PUT','DELETE'].includes(method.toUpperCase()) && /^\/(order\/|admin\/order\/|fault\/report|workbench\/tickets\/|admin\/maintenance\/|admin\/drone)/.test(path)
}
