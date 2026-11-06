<script setup lang="ts">
import {useAuthStore} from '../stores/auth'
import {useRouter} from 'vue-router'
const auth=useAuthStore(),router=useRouter()
function logout(){auth.logout();void router.push('/login')}
</script>
<template><div class="tech-shell"><header><strong>LeaseGuard · 维修工作台</strong><span>{{auth.user?.username}}</span><el-button @click="logout">退出登录</el-button></header><main><router-view/></main></div></template>
<style scoped>.tech-shell{max-width:1400px;margin:auto}header{display:flex;gap:24px;align-items:center;padding:20px;border-bottom:1px solid #ddd}header span{margin-left:auto}main{padding:20px}</style>
