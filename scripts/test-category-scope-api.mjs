import assert from 'node:assert/strict'
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs'
import {randomUUID} from 'node:crypto'
import {login,call} from './test-performance-api.mjs'

const bundle=process.env.SRM_TEST_BUNDLE||'artifacts/SRM-local-review'
const users=JSON.parse(readFileSync(bundle+'/runtime/accounts.json','utf8'))
const admin=await login(users.find(x=>x.account==='admin'))
const portal=await login(users.find(x=>x.account==='lixiaolong'),'c')
const checks=[],pass=s=>{checks.push(s);console.log('PASS:',s)},stamp=Date.now()
const categories='/srm/v1/categories',candidates='/srm/v1/sourcing/candidates?category='
const code='W29_VERIFY_'+stamp.toString(36).toUpperCase()
const inactive='W29_OFF_'+stamp.toString(36).toUpperCase()
const body=(value)=>({code:value,name:'W29隔离验证-'+value,requiredCerts:['BUSINESS_LICENSE'],enabled:true,rowVersion:0})
const categoryPage=async token=>call(categories,token)

await call(categories,admin,body(code));let rows=await categoryPage(admin);let active=rows.find(x=>x.code===code);assert.ok(active&&active.enabled);assert.deepEqual(JSON.parse(active.requiredCerts),['BUSINESS_LICENSE']);pass('authorized category create and read')
await call(categories,admin,{...body(code),name:'W29隔离验证-已修改-'+code,rowVersion:active.rowVersion});rows=await categoryPage(admin);active=rows.find(x=>x.code===code);assert.match(active.name,/已修改/);assert.equal(active.rowVersion,1);pass('category update increments row version')
await call(categories,admin,{...body(code),rowVersion:0},409);pass('stale category version rejected')
await call(categories,admin,body(inactive));let off=(await categoryPage(admin)).find(x=>x.code===inactive);await call(categories,admin,{...body(inactive),enabled:false,rowVersion:off.rowVersion});
await call(candidates+inactive,admin,undefined,400);const publicRows=await call('/srm/public/categories');assert.ok(!publicRows.some(x=>x.code===inactive));const portalCategories=await call('/client/c/srm/v1/categories',portal);assert.ok(!portalCategories.some(x=>x.code===inactive));pass('disabled category is absent from public registration and portal lists and rejected by sourcing candidates')
const scoped=await call(candidates+code,admin);assert.ok(scoped.every(x=>x.category===code));pass('active candidates remain restricted to the requested exact category')
await call('/client/c/srm/v1/categories',portal,{...body('W29_PORTAL_'+stamp.toString(36).toUpperCase())},403);pass('supplier portal cannot create or maintain category dictionary')
const report={result:'passed',entry:process.env.SRM_TEST_ENTRY||'http://localhost:18080',date:new Date().toISOString(),checks,retainedFixtures:{activeCategory:code,disabledCategory:inactive},mutation:'W29-labeled isolated category rows only; no supplier or sourcing business rows changed'}
mkdirSync('docs/review',{recursive:true});writeFileSync('docs/review/W29-category-scope-api-verification.json',JSON.stringify(report,null,2)+'\n')
