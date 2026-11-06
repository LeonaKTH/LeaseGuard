import {safeId} from './contracts'
import {OperationKeys,isBusinessCommand} from './operationKey'
const commandKeys=new OperationKeys(sessionStorage)
import type { Envelope, Page, Credentials, Register, Login, Identity, Order, Drone, Fault, Ticket,Id,WorkTicket,Technician,BusinessEvent,Task,Qualification,OrderStatus } from '../types/leaseguard'
export class ApiError extends Error { constructor(public status:number,message:string){super(message)} }
export async function request<T>(path:string,method='GET',body?:unknown):Promise<Envelope<T>>{
 const token=localStorage.getItem('token');const headers:Record<string,string>={'Content-Type':'application/json'}
 if(token && !['/auth/login','/auth/admin/login','/auth/register'].includes(path)) headers.Authorization=`Bearer ${token}`
 const pending=isBusinessCommand(method,path)?await commandKeys.begin(method,path,body):undefined
 if(pending)headers['Idempotency-Key']=pending.key
 const abort=new AbortController(),timer=setTimeout(()=>abort.abort(),15000)
 let response:Response
 try{response=await fetch((import.meta.env.VITE_API_BASE_URL || '/api')+path,{method,headers,signal:abort.signal,body:body===undefined?undefined:JSON.stringify(body)})}catch{clearTimeout(timer);throw new ApiError(0,abort.signal.aborted?'请求超时，结果未知，请使用相同操作重试':'网络连接失败，请重试')}
 let result:Envelope<T>
 try{result=await response.json() as Envelope<T>}catch{throw new ApiError(response.status,'响应未完整接收，请使用相同操作重试')}finally{clearTimeout(timer)}
 if(pending && (response.ok || [400,401,403,404,409].includes(response.status)))commandKeys.complete(pending)
 if(!response.ok || result.code!==200){
  if(response.status===401){localStorage.removeItem('token');localStorage.removeItem('isAdmin');window.dispatchEvent(new Event('leaseguard:unauthorized'))}
  const fallback:Record<number,string>={401:'请重新登录',403:'没有操作权限',404:'资源不存在或不可见',409:'业务状态已变化，请刷新'}
  throw new ApiError(response.status,result.message || fallback[response.status] || '请求失败')
 }
 return result
}
function identity<T extends Identity>(v:T):T{return {...v,...(v.id===undefined?{}:{id:safeId(v.id)}),...(v.userId===undefined?{}:{userId:safeId(v.userId)})}}
function order(v:Order):Order{return {...v,id:safeId(v.id),userId:safeId(v.userId),droneId:safeId(v.droneId),returnRequest:v.returnRequest?{...v.returnRequest,id:safeId(v.returnRequest.id),orderId:safeId(v.returnRequest.orderId),reviewerId:v.returnRequest.reviewerId===null?null:safeId(v.returnRequest.reviewerId)}:null}}
function fault(v:Fault):Fault{return {...v,id:safeId(v.id),orderId:safeId(v.orderId),droneId:safeId(v.droneId)}}
function ticket(v:Ticket):Ticket{return {...v,id:safeId(v.id),droneId:safeId(v.droneId),acceptedBy:v.acceptedBy==null?null:safeId(v.acceptedBy)}}
function pageMap<T>(e:Envelope<Page<T>>,map:(v:T)=>T):Envelope<Page<T>>{return {...e,data:{...e.data,records:e.data.records.map(map)}}}
export const api={
 login:(body:Credentials,admin=false)=>request<Login>(admin?'/auth/admin/login':'/auth/login','POST',body).then(e=>({...e,data:identity(e.data)})),
 register:(body:Register)=>request<void>('/auth/register','POST',body),
 me:()=>request<Identity>('/user/info').then(e=>({...e,data:identity(e.data)})),
 orders:(admin=false,page=1,status:OrderStatus|''='',size=20)=>request<Page<Order>>((admin?`/admin/order/list?page=${page}&pageSize=${size}`:`/user/orders?pageNum=${page}&pageSize=${size}`)+(status===''?'':`&orderStatus=${status}`)).then(e=>pageMap(e,order)),
 order:(id:Id)=>request<Order>(`/order/${id}`).then(e=>({...e,data:order(e.data)})),
 devices:(page=1,size=100)=>request<Page<Drone>>(`/drone/list?pageNum=${page}&pageSize=${size}`).then(e=>pageMap(e,v=>({...v,id:safeId(v.id)}))),
 device:(id:Id)=>request<Drone>(`/drone/detail/${id}`).then(e=>({...e,data:{...e.data,id:safeId(e.data.id)}})),
 create:(body:{droneId:Id;startDate:string;endDate:string})=>request<Order>('/order/create','POST',body).then(e=>({...e,data:order(e.data)})),
 pay:(id:Id,address:string)=>request<void>(`/order/${id}/pay`,'POST',{deliveryAddress:address}),
 cancel:(id:Id)=>request<void>(`/order/${id}/cancel`,'POST',{reason:'用户取消'}),
 receive:(id:Id)=>request<void>(`/order/${id}/receive`,'POST'),
 applyReturn:(id:Id)=>request<void>(`/order/${id}/return`,'POST'),
 ship:(id:Id,company:string,no:string)=>request<void>(`/admin/order/${id}/ship?expressCompany=${encodeURIComponent(company)}&expressNo=${encodeURIComponent(no)}`,'POST'),
 refund:(id:Id)=>request<void>(`/admin/order/${id}/refund`,'POST',{reason:'管理员退款'}),
 reviewReturn:(id:Id,approved:boolean,remark:string)=>request<void>(`/admin/order/${id}/return-review`,'POST',{approved,remark}),
 faults:(admin=false,page=1)=>request<Page<Fault>>(admin?`/admin/maintenance/fault/list?pageNum=${page}&pageSize=100`:`/fault/my?pageNum=${page}&pageSize=100`).then(e=>pageMap(e,fault)),
 report:(body:{orderId:Id;droneId:Id;faultType:string;faultDescription:string})=>request<void>('/fault/report','POST',body),
 audit:(id:Id,approved:boolean)=>request<void>(`/admin/maintenance/fault/${id}/audit`,'PUT',{auditStatus:approved?1:2,auditRemark:'管理员审核'}),
 tickets:(page=1,status:number|''='')=>request<Page<WorkTicket>>(`/workbench/tickets?page=${page}&pageSize=20${status===''?'':`&status=${status}`}`),
 ticket:(id:Id)=>request<WorkTicket>(`/workbench/tickets/${id}`),
 progress:(id:Id)=>request<Ticket|null>(`/fault/${id}/maintenance`).then(e=>({...e,data:e.data?ticket(e.data):null})),
 start:(id:Id)=>request<void>(`/workbench/tickets/${id}/start`,'POST'),
 complete:(id:Id,actualCost:string)=>request<void>(`/workbench/tickets/${id}/complete`,'POST',{actualCost}),
 accept:(id:Id,remark:string)=>request<void>(`/admin/maintenance/ticket/${id}/accept`,'POST',{remark}),
 assign:(id:Id,assigneeId:Id,version:number)=>request<void>(`/admin/maintenance/ticket/${id}/assignment`,'POST',{assigneeId,version}),
 technicians:()=>request<Technician[]>('/admin/technicians'),
 createTechnician:(body:Credentials)=>request<Technician>('/admin/technicians','POST',body),
 events:(kind:'orders'|'tickets',id:Id,page=1)=>request<Page<BusinessEvent>>(`/audit/${kind}/${id}?page=${page}&pageSize=20`),
 tasks:(page=1,status='')=>request<Page<Task>>(`/admin/tasks?page=${page}&pageSize=20${status?'&status='+status:''}`),
 qualification:()=>request<Qualification|null>('/user/qualification').then(e=>({...e,data:e.data?{...e.data,id:safeId(e.data.id)}:null})),
 submitQualification:(body:{certificateNo:string;certificateType:string;validStartDate:string;validEndDate:string})=>request<void>('/user/qualification','POST',body)
}
