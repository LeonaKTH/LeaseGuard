<script setup lang="ts">
import {ref,reactive,onMounted} from 'vue'
import {useRoute} from 'vue-router'
import {api,ApiError} from '../../api/leaseguard'
import {useAuthStore} from '../../stores/auth'
import Timeline from './Timeline.vue'
import {safeId} from '../../api/contracts'
import {orderLabels,returnLabels} from '../../types/leaseguard'
import type {Order,Drone,Id,OrderStatus} from '../../types/leaseguard'
const auth=useAuthStore(),route=useRoute(),orders=ref<Order[]>([]),devices=ref<Drone[]>([]),selected=ref<Order|null>(null)
const busy=ref(false),loading=ref(false),error=ref(''),message=ref(''),page=ref(1),total=ref(0)
const filter=ref<OrderStatus|''>('')
const form=reactive({droneId:'',startDate:'',endDate:''}),delivery=ref(''),company=ref(''),tracking=ref(''),remark=ref('')
async function reload(){loading.value=true;try{const r=await api.orders(auth.isAdmin,page.value,filter.value);orders.value=r.data.records;total.value=r.data.total;if(selected.value)selected.value=(await api.order(selected.value.id)).data}catch(e){error.value=e instanceof Error?e.message:'加载失败'}finally{loading.value=false}}
async function act(fn:()=>Promise<unknown>){if(busy.value)return;busy.value=true;error.value='';message.value='';try{await fn();await reload();message.value='操作已保存'}catch(e){error.value=e instanceof Error?e.message:'操作失败';if(e instanceof ApiError && e.status===409)await reload()}finally{busy.value=false}}
async function detail(id:Id){await act(async()=>{selected.value=(await api.order(id)).data})}
async function create(){await act(async()=>{selected.value=(await api.create(form)).data})}
onMounted(async()=>{await reload();try{devices.value=(await api.devices()).data.records.filter(d=>d.status===1&&d.stock>0);if(route.query.droneId)form.droneId=safeId(route.query.droneId);if(route.params.id)await detail(safeId(route.params.id))}catch(e){error.value=e instanceof Error?e.message:'设备加载失败'}})
</script>
<template><section v-loading="loading" class="lease-panel"><h1>{{auth.isAdmin?'订单交付与归还验收':'我的租赁'}}</h1>
<el-alert v-if="error" :title="error" type="error" :closable="false"/><el-alert v-if="message" :title="message" type="success" :closable="false"/>
<el-button @click="reload" :disabled="busy">刷新</el-button><label>订单筛选 <select v-model="filter" @change="page=1;reload()"><option value="">全部状态</option><option v-for="(label,key) in orderLabels" :key="key" :value="Number(key)">{{label}}</option></select></label>
<el-card v-if="!auth.isAdmin" class="box"><h2>新建租赁</h2><p>需先在个人中心提交资质并由管理员审核。金额由服务器计算。</p>
<form @submit.prevent="create"><label>设备 <select v-model="form.droneId" required><option value="" disabled>选择可租设备</option><option v-for="d in devices" :key="d.id" :value="d.id">{{d.model}} · ¥{{d.pricePerDay}}/天</option></select></label>
<label>开始 <input type="date" v-model="form.startDate" required/></label><label>结束 <input type="date" v-model="form.endDate" :min="form.startDate" required/></label><el-button native-type="submit" :disabled="busy || !form.droneId" :loading="busy">创建订单</el-button></form></el-card>
<el-empty v-if="!loading && !orders.length" description="暂无订单"/>
<el-table v-else :data="orders" row-key="id"><el-table-column prop="orderNo" label="订单编号"/><el-table-column prop="droneModel" label="设备"/>
<el-table-column label="状态"><template #default="{row}">{{orderLabels[(row as Order).orderStatus]}}</template></el-table-column>
<el-table-column label="归还申请"><template #default="{row}">{{(row as Order).returnRequest?returnLabels[(row as Order).returnRequest!.status]:'未提交'}}</template></el-table-column>
<el-table-column label="操作"><template #default="{row}"><el-button :data-testid="`order-${row.id}-detail`" @click="detail(row.id)" :disabled="busy">详情</el-button></template></el-table-column></el-table>
<el-pagination v-model:current-page="page" :page-size="20" :total="total" layout="prev,pager,next" @current-change="reload"/>
<el-card v-if="selected" class="box" data-testid="order-detail"><h2>{{selected.orderNo}}</h2><p>订单状态：<strong>{{orderLabels[selected.orderStatus]}}</strong> · 服务端总额 ¥{{selected.totalAmount}}</p><p>{{selected.droneModel}} · {{selected.rentalDays}}天</p>
<div v-if="selected.returnRequest" data-testid="return-status"><p>归还申请：{{returnLabels[selected.returnRequest.status]}} · 提交 {{selected.returnRequest.submittedAt}}</p><p v-if="selected.returnRequest.reviewedAt">审核时间 {{selected.returnRequest.reviewedAt}} · {{selected.returnRequest.reviewRemark}}</p></div>
<div v-if="!auth.isAdmin">
<template v-if="selected.orderStatus===0"><label>模拟收货地址 <input v-model="delivery" placeholder="模拟收货地址"/></label><el-button @click="act(()=>api.pay(selected!.id,delivery))" :disabled="busy">模拟结算</el-button><el-button @click="act(()=>api.cancel(selected!.id))" :disabled="busy">取消订单</el-button></template>
<el-button v-if="selected.orderStatus===2" @click="act(()=>api.receive(selected!.id))" :disabled="busy">确认接收</el-button>
<el-button data-testid="apply-return" v-if="selected.orderStatus===3 && (!selected.returnRequest || selected.returnRequest.status===2)" type="primary" @click="act(()=>api.applyReturn(selected!.id))" :disabled="busy">提交归还申请</el-button>
<router-link v-if="selected.orderStatus===3" :to="{path:'/fault-report',query:{orderId:selected.id}}">报告故障</router-link></div>
<div v-else>
<template v-if="selected.orderStatus===1"><label>快递公司 <input v-model="company" placeholder="快递公司"/></label><label>单号 <input v-model="tracking" placeholder="单号"/></label><el-button @click="act(()=>api.ship(selected!.id,company,tracking))" :disabled="busy || !company || !tracking">交付</el-button><el-button @click="act(()=>api.refund(selected!.id))" :disabled="busy">退款</el-button></template>
<template v-if="selected.orderStatus===3 && selected.returnRequest?.status===0"><input v-model="remark" maxlength="500" placeholder="验收意见"/><el-button data-testid="approve-return" type="primary" @click="act(()=>api.reviewReturn(selected!.id,true,remark))" :disabled="busy">验收归还</el-button><el-button @click="act(()=>api.reviewReturn(selected!.id,false,remark))" :disabled="busy">驳回申请</el-button></template></div>
<Timeline :key="selected.id+'-'+selected.orderStatus+'-'+selected.returnRequest?.status" kind="orders" :resource-id="selected.id"/>
</el-card></section></template>
<style scoped>.lease-panel{padding:20px}.box{margin:18px 0}label,input,select{margin:6px;padding:6px}h1{margin-bottom:18px}</style>
