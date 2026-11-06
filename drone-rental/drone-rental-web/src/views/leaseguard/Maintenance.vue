<script setup lang="ts">
import {ref,reactive,onMounted} from 'vue'
import {useRoute} from 'vue-router'
import {api,ApiError} from '../../api/leaseguard'
import {safeId} from '../../api/contracts'
import {repairLabels} from '../../types/leaseguard'
import type {Fault,Ticket,Order} from '../../types/leaseguard'
import Timeline from './Timeline.vue'
const route=useRoute(),faults=ref<Fault[]>([]),orders=ref<Order[]>([]),progress=ref<Ticket|null>(null),busy=ref(false),loading=ref(false),error=ref(''),message=ref('')
const form=reactive({orderId:'',faultType:'设备故障',faultDescription:''})
async function load(){loading.value=true;try{faults.value=(await api.faults()).data.records;orders.value=(await api.orders(false,1,3,100)).data.records;if(route.query.orderId){const id=safeId(route.query.orderId);if(!orders.value.some(o=>o.id===id)){const o=(await api.order(id)).data;if(o.orderStatus===3)orders.value.push(o)}form.orderId=id}}catch(e){error.value=e instanceof Error?e.message:'加载失败'}finally{loading.value=false}}
async function act(fn:()=>Promise<unknown>){if(busy.value)return;busy.value=true;error.value='';message.value='';try{await fn();await load();message.value='操作已保存'}catch(e){error.value=e instanceof Error?e.message:'操作失败';if(e instanceof ApiError&&e.status===409)await load()}finally{busy.value=false}}
async function submit(){const order=orders.value.find(o=>o.id===form.orderId);if(!order){error.value='请选择本人租赁中订单';return}await act(()=>api.report({...form,droneId:order.droneId}))}
onMounted(load)
</script>
<template><section v-loading="loading" class="lease-panel"><h1>设备故障报告</h1><el-alert v-if="error" :title="error" type="error" :closable="false"/><el-alert v-if="message" :title="message" type="success" :closable="false"/><el-button @click="load" :disabled="busy">刷新</el-button><form @submit.prevent="submit"><label>本人租赁订单 <select v-model="form.orderId" required><option value="" disabled>请选择订单</option><option v-for="o in orders" :key="o.id" :value="o.id">{{o.orderNo}} · {{o.droneModel}}</option></select></label><label>故障类型 <input v-model="form.faultType" required maxlength="50"/></label><label>故障描述 <textarea v-model="form.faultDescription" required maxlength="2000"/></label><el-button native-type="submit" :disabled="busy||!form.orderId">提交故障</el-button></form><h2>本人故障与维修进度</h2><el-empty v-if="!loading&&!error&&!faults.length" description="暂无故障记录"/><el-table :data="faults" row-key="id"><el-table-column prop="reportNo" label="故障编号"/><el-table-column prop="faultDescription" label="描述"/><el-table-column label="审核"><template #default="{row}">{{['待审核','已确认','已驳回'][row.auditStatus]}}</template></el-table-column><el-table-column label="操作"><template #default="{row}"><el-button :disabled="busy" @click="act(async()=>{progress=(await api.progress(row.id)).data})">查看维修进度</el-button></template></el-table-column></el-table><el-card v-if="progress"><h2>维修进度：{{repairLabels[progress.status]}}</h2><p v-if="progress.acceptedAt">验收时间 {{progress.acceptedAt}}</p><Timeline kind="tickets" :resource-id="progress.id"/></el-card><p>可从指定订单详情直接报告故障；选择器展示最近100条租赁中记录。</p></section></template>
<style scoped>.lease-panel{padding:20px}form{padding:16px;border:1px solid #eee;margin:16px 0}label{display:block;margin:10px}input,select,textarea{padding:8px}textarea{width:90%}</style>
