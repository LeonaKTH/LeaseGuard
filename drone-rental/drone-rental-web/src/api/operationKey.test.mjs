import test from 'node:test'
import assert from 'node:assert/strict'
import {OperationKeys,isBusinessCommand} from './operationKey.ts'
const store=()=>{const map=new Map();return {getItem:k=>map.get(k)??null,setItem:(k,v)=>map.set(k,v),removeItem:k=>map.delete(k)}}
test('same intent retains key across failed transport and store recreation',async()=>{const storage=store();const first=new OperationKeys(storage,()=> 'intent-001');const a=await first.begin('POST','/order/create',{device:1,days:2});const retry=new OperationKeys(storage,()=> 'intent-002');const b=await retry.begin('POST','/order/create',{days:2,device:1});assert.equal(a.key,b.key);retry.complete(b);assert.equal((await retry.begin('POST','/order/create',{days:2,device:1})).key,'intent-002')})
test('different effective parameters get different pending operations',async()=>{let n=0;const keys=new OperationKeys(store(),()=> 'intent-'+(++n));assert.notEqual((await keys.begin('POST','/order/create',{device:1})).key,(await keys.begin('POST','/order/create',{device:2})).key)})
test('scope excludes authentication credentials',()=>{assert.equal(isBusinessCommand('POST','/auth/login'),false);assert.equal(isBusinessCommand('PUT','/user/password'),false);assert.equal(isBusinessCommand('GET','/order/1'),false);assert.equal(isBusinessCommand('POST','/order/1/cancel'),true)})
