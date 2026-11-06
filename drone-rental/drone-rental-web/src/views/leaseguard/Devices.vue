<script setup lang="ts">
import {ref,watch} from 'vue'
import {useRoute} from 'vue-router'
import {api} from '../../api/leaseguard'
import {useAuthStore} from '../../stores/auth'
import {safeId} from '../../api/contracts'
import type {Drone} from '../../types/leaseguard'
const route=useRoute(),auth=useAuthStore(),devices=ref<Drone[]>([]),selected=ref<Drone|null>(null),loading=ref(false),error=ref(''),page=ref(1),total=ref(0)
async function load(){loading.value=true;error.value='';try{const r=await api.devices(page.value,20);devices.value=r.data.records;total.value=r.data.total;selected.value=null;if(route.params.id)selected.value=(await api.device(safeId(route.params.id))).data}catch(e){error.value=e instanceof Error?e.message:'设备加载失败'}finally{loading.value=false}}
watch(()=>route.params.id,()=>{void load()},{immediate:true})
</script>
<template><section class="lease-panel" v-loading="loading"><h1>设备与可用库存</h1><el-alert v-if="error" :title="error" type="error" :closable="false"/><el-button @click="load" :disabled="loading">刷新</el-button><el-empty v-if="!loading&&!error&&!devices.length" description="暂无设备"/><el-table :data="devices" row-key="id"><el-table-column prop="model" label="型号"/><el-table-column prop="pricePerDay" label="服务端日租金额"/><el-table-column prop="stock" label="可用库存"/><el-table-column label="状态"><template #default="{row}">{{['缺货','可租','维护中'][row.status]}}</template></el-table-column><el-table-column label="操作"><template #default="{row}"><router-link :to="`/drones/${row.id}`">设备详情</router-link></template></el-table-column></el-table><el-pagination v-model:current-page="page" :total="total" :page-size="20" layout="prev,pager,next" @current-change="load"/>
<el-card v-if="selected"><h2>{{selected.model}}</h2><p>日租金额 ¥{{selected.pricePerDay}}，可用数量 {{selected.stock}}。维护状态不代表释放库存。</p><router-link v-if="!auth.isTechnician&&selected.status===1&&selected.stock>0" :to="{path:'/orders',query:{droneId:selected.id}}">租赁此型号</router-link><p v-else>当前身份或设备状态不可创建租赁。</p></el-card></section></template>
<style scoped>.lease-panel{padding:20px}.el-card{margin-top:20px}</style>
