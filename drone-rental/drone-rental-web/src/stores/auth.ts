import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api, request } from '../api/leaseguard'
import type { Credentials, Identity, Register } from '../types/leaseguard'
export const useAuthStore=defineStore('auth',()=>{
 const token=ref(localStorage.getItem('token') || '');const user=ref<Identity|null>(null);const isAdmin=ref(localStorage.getItem('isAdmin')==='true')
 const isLoggedIn=computed(()=>!!token.value)
 const isTechnician=computed(()=>user.value?.role===2)
 // Qualification is server-authoritative; account status alone does not establish certification.
 const verified=ref(false);const isVerified=computed(()=>verified.value);const canRent=computed(()=>isLoggedIn.value && verified.value)
 const setToken=(value:string,admin=false)=>{token.value=value;isAdmin.value=admin;localStorage.setItem('token',value);localStorage.setItem('isAdmin',String(admin))}
 const setUser=(value:Identity)=>{user.value=value;isAdmin.value=value.role===1;localStorage.setItem('isAdmin',String(isAdmin.value))}
 const logout=()=>{verified.value=false;token.value='';user.value=null;isAdmin.value=false;localStorage.removeItem('token');localStorage.removeItem('isAdmin')}
 window.addEventListener('leaseguard:unauthorized',logout)
 const fetchUserInfo=async()=>{if(!token.value)return null;try{const r=await api.me();setUser(r.data);if(r.data.role===2){verified.value=false;return user.value}const q=await request<{auditStatus:number;validEndDate:string}|null>('/user/qualification');verified.value=!!q.data && q.data.auditStatus===1 && q.data.validEndDate>=new Date().toLocaleDateString('en-CA');return user.value}catch{logout();return null}}
 const signIn=async(c:Credentials,admin=false)=>{const r=await api.login(c,admin);setToken(r.data.token,r.data.role===1);setUser(r.data);await fetchUserInfo();return r}
 const userLogin=(c:Credentials)=>signIn(c);const adminUserLogin=(c:Credentials)=>signIn(c,true)
 const userRegister=async(data:Register)=>{await api.register(data);return signIn(data)}
 const updateUserInfo=(info:Partial<Identity>)=>{if(user.value)user.value={...user.value,...info}}
 const initAuth=fetchUserInfo
 return {token,user,isAdmin,isTechnician,isLoggedIn,isVerified,canRent,setToken,setUser,userLogin,adminUserLogin,userRegister,fetchUserInfo,updateUserInfo,logout,initAuth}
})
