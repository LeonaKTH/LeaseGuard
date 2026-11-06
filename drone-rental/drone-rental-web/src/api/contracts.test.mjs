import test from 'node:test'
import assert from 'node:assert/strict'
import {safeId,validMoney} from './contracts.ts'
test('string IDs preserve Java Long max and legacy safe integers',()=>{assert.equal(safeId('9223372036854775807'),'9223372036854775807');assert.equal(safeId(42),'42');assert.throws(()=>safeId(9007199254740992));assert.throws(()=>safeId('9223372036854775808'));assert.throws(()=>safeId('1e4'));assert.throws(()=>safeId('-1'))})
test('money input remains decimal text, no float settlement',()=>{assert.equal(validMoney('12.50'),true);for(const value of ['-1','1e3','0.001','NaN',''])assert.equal(validMoney(value),false)})
