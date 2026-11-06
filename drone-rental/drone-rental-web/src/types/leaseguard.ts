export type Id = string
export type Role = 0 | 1 | 2
export type OrderStatus = 0 | 1 | 2 | 3 | 4 | 5 | 6
// Historical completed=2, cancelled=3 are preserved. New acceptance state=4.
export type RepairStatus = 0 | 1 | 2 | 3 | 4
export interface Envelope<T> { code: number; message: string; data: T }
export interface Page<T> { records: T[]; total: number; current: number; size: number }
export interface Identity { userId?: Id; id?: Id; username: string; nickname?: string; role: Role; avatar?: string; status?: number }
export interface Login extends Identity { token: string }
export interface Credentials { username: string; password: string }
export interface Register extends Credentials { nickname?: string; phone?: string; email?: string }
export interface ReturnRequest { id: Id; orderId: Id; status: 0|1|2; submittedAt: string; reviewedAt: string|null; reviewerId: Id|null; reviewRemark: string|null }
export interface Order { id: Id; orderNo: string; userId: Id; droneId: Id; droneModel: string; orderStatus: OrderStatus; rentalDays: number; rentalStartTime: string; rentalEndTime: string; totalAmount: number|string; returnRequest: ReturnRequest|null }
export interface Drone { id:Id;model:string;stock:number;status:number;pricePerDay:number|string }
export interface Fault { id:Id;orderId:Id;droneId:Id;reportNo:string;faultDescription:string;auditStatus:0|1|2 }
export interface Ticket { id:Id;ticketNo:string;droneId:Id;droneModel?:string;status:RepairStatus;completeTime:string|null;acceptedAt:string|null;acceptedBy:Id|null;acceptanceRemark:string|null }
export const orderLabels: Record<OrderStatus,string> = {0:'待结算',1:'待交付',2:'待接收',3:'租赁中',4:'已归还',5:'已取消',6:'已退款'}
export const repairLabels: Record<RepairStatus,string> = {0:'待维修',1:'维修中',2:'已验收完成',3:'已取消',4:'待验收'}
export const returnLabels = {0:'待验收',1:'验收通过',2:'已驳回'} as const

export interface WorkTicket {id:Id;ticketNo:string;droneId:Id;droneModel:string|null;faultReportId:Id;status:RepairStatus;version:number;assigneeId:Id|null;assigneeName:string|null;assignedAt:string|null;acceptedAt:string|null;description:string|null;faultType:string|null;actualCost:string|null;progressNotes:string|null}
export interface Technician {id:Id;username:string;nickname:string|null}
export interface BusinessEvent {id:Id;aggregateType:string;resourceId:Id;eventType:string;fromStatus:number|null;toStatus:number|null;actorId:Id;actorName:string;actorKind:'USER'|'SYSTEM';createdAt:string;previousAssigneeId:Id|null;assigneeId:Id|null}
export type TaskStatus='PENDING'|'CLAIMED'|'DONE'|'FAILED'
export interface Task {id:Id;orderId:Id;type:string;status:TaskStatus;attempts:number;maxAttempts:number;lastError:string|null;result:'CANCELLED'|'NO_ACTION'|null;dueAt:string;createdAt:string;updatedAt:string}
export interface Qualification {id:Id;certificateNo:string;certificateType:string|null;validStartDate:string;validEndDate:string;auditStatus:0|1|2;auditRemark:string|null}
