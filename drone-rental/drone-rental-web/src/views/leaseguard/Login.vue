<script setup lang="ts">
import { reactive,ref,computed } from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {useAuthStore} from '../../stores/auth'
const route=useRoute(),router=useRouter(),auth=useAuthStore()
const admin=computed(()=>route.path==='/admin/login'),register=computed(()=>route.path==='/register')
const form=reactive({username:'',password:''}),busy=ref(false),error=ref('')
async function submit(){if(busy.value)return;busy.value=true;error.value='';try{if(register.value)await auth.userRegister(form);else if(admin.value)await auth.adminUserLogin(form);else await auth.userLogin(form);await router.push(auth.isAdmin?'/admin/orders':auth.isTechnician?'/technician':'/orders')}catch(e){error.value=e instanceof Error?e.message:'登录失败'}finally{busy.value=false}}
</script>
<template><main class="auth-shell"><el-card><h1>{{register?'注册':admin?'管理员登录':'用户登录'}} · LeaseGuard</h1>
<el-alert v-if="error" :title="error" type="error" :closable="false"/>
<el-form @submit.prevent="submit" label-position="top"><el-form-item label="用户名" for="leaseguard-username"><input id="leaseguard-username" data-testid="username" v-model="form.username" autocomplete="username" required minlength="4" maxlength="20" /></el-form-item>
<el-form-item label="密码" for="leaseguard-password"><input id="leaseguard-password" data-testid="password" type="password" v-model="form.password" :autocomplete="register?'new-password':'current-password'" required :minlength="register?12:1" maxlength="64"/></el-form-item>
<p v-if="register">密码12至64字符，UTF-8不超过72字节。</p><el-button data-testid="login-submit" native-type="submit" type="primary" :loading="busy">{{register?'注册并登录':'登录'}}</el-button></el-form>
<p><router-link to="/login">用户/技师登录</router-link> · <router-link to="/admin/login">管理员登录</router-link> · <router-link to="/register">注册</router-link></p></el-card></main></template>
<style scoped>.auth-shell{max-width:520px;margin:10vh auto}input{width:100%;padding:10px;border:1px solid #bbb;border-radius:6px}</style>
