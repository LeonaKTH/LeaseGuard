export function safeId(value:unknown):string {
 if(typeof value==='number'){if(!Number.isSafeInteger(value)||value<=0)throw new Error('旧接口ID超出安全整数范围，已阻止不精确操作');return String(value)}
 if(typeof value==='string'&&/^[1-9]\d{0,18}$/.test(value)&&BigInt(value)<=9223372036854775807n)return value
 throw new Error('服务端ID契约无效')
}
export function validMoney(value:string):boolean{return /^(0|[1-9]\d{0,7})(\.\d{1,2})?$/.test(value)}
