<script setup lang="ts">
import {ref,reactive,onMounted} from 'vue'
import {api} from '../../api/leaseguard'
import type {Qualification} from '../../types/leaseguard'
import {useAuthStore} from '../../stores/auth'
const auth=useAuthStore(),qualification=ref<Qualification|null>(null),busy=ref(false),error=ref(''),message=ref(''),form=reactive({certificateNo:'',certificateType:'操作资质',validStartDate:'',validEndDate:''})
async function load(){qualification.value=(await api.qualification()).data}
async function submit(){if(busy.value)return;if(form.validEndDate<form.validStartDate){error.value='资质结束日期不得早于开始';return}busy.value=true;error.value='';try{await api.submitQualification(form);await load();await auth.fetchUserInfo();message.value='资质已提交，等待管理员审核'}catch(e){error.value=e instanceof Error?e.message:'提交失败'}finally{busy.value=false}}
onMounted(()=>load().catch(e=>{error.value=e.message}))
</script>
<template><section class="lease-panel"><h1>个人身份与资质</h1><p>{{auth.user?.username}} · {{auth.isAdmin?'管理员':'普通用户'}}</p><el-alert v-if="error" :title="error" type="error" :closable="false"/><el-alert v-if="message" :title="message" type="success" :closable="false"/><p>资质状态：{{qualification?['待审核','已通过','已驳回'][qualification.auditStatus]:'未提交'}}</p><p v-if="qualification">有效期 {{qualification.validStartDate}} 至 {{qualification.validEndDate}}；{{qualification.auditRemark}}</p><form @submit.prevent="submit"><label>证件编号 <input v-model="form.certificateNo" required maxlength="50"/></label><label>资质类型 <input v-model="form.certificateType" required maxlength="50"/></label><label>有效开始 <input type="date" v-model="form.validStartDate" required/></label><label>有效结束 <input type="date" v-model="form.validEndDate" :min="form.validStartDate" required/></label><el-button native-type="submit" :disabled="busy" :loading="busy">提交资质</el-button></form></section></template>
<style scoped>.lease-panel{padding:20px}label{display:block;margin:12px}input{padding:8px}</style>
