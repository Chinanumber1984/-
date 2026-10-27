import test from 'node:test'
import assert from 'node:assert/strict'
import { seedState, visibleSuppliers, createSupplier, completeDemoCertificates, submitOnboarding, reviewOnboarding } from './model.mjs'
const admin = () => ({ orgId: 'east', role: 'SUP_ADMIN', supplierId: 's1' })
test('demo scope separates organizations and portal supplier', () => {
 const s = seedState(); assert.equal(visibleSuppliers(s, admin()).length, 6)
 assert.equal(visibleSuppliers(s, { ...admin(), orgId: 'south' }).length, 2)
 assert.deepEqual(visibleSuppliers(s, { ...admin(), role: 'PORTAL_ADMIN' }).map(x => x.id), ['s1'])
})
test('missing certificates block submission and two distinct review roles qualify a supplier', () => {
 const state=seedState(), ctx=admin(), s=createSupplier(state,ctx,{name:'测试演示供应商', category:'精密加工', address:''})
 assert.throws(()=>submitOnboarding(state,ctx,s.id), /有效资质/)
 completeDemoCertificates(state,ctx,s.id); submitOnboarding(state,ctx,s.id)
 assert.equal(s.step,'SQE')
 assert.throws(()=>reviewOnboarding(state,ctx,s.id,'APPROVE','同意'),/质量工程师/)
 reviewOnboarding(state,{...ctx,role:'SQE'},s.id,'APPROVE','资质有效')
 assert.equal(s.step,'CAT_MGR');assert.equal(s.status,'REVIEWING')
 assert.throws(()=>reviewOnboarding(state,{...ctx,role:'SQE'},s.id,'APPROVE','同意'),/采购经理/)
 reviewOnboarding(state,{...ctx,role:'CAT_MGR'},s.id,'APPROVE','同意准入')
 assert.equal(s.status,'QUALIFIED');assert.equal(state.events[0].action,'准入审批通过')
})
test('review comments are required and return allows correction/resubmission', () => {
 const state=seedState(), ctx=admin()
 assert.throws(()=>reviewOnboarding(state,{...ctx,role:'SQE'},'s2','APPROVE',''),/审核意见/)
 reviewOnboarding(state,{...ctx,role:'SQE'},'s2','RETURN','请补充资料')
 assert.equal(state.suppliers.find(x=>x.id==='s2').status,'POTENTIAL')
 submitOnboarding(state,ctx,'s2'); assert.equal(state.suppliers.find(x=>x.id==='s2').step,'SQE')
})
test('direct model calls cannot review another organization or create from portal role', () => {
 const state=seedState(), ctx=admin()
 assert.throws(()=>reviewOnboarding(state,{...ctx,role:'SQE'},'s8','APPROVE','同意'),/无权访问/)
 assert.throws(()=>createSupplier(state,{...ctx,role:'PORTAL_ADMIN'},{name:'测试',category:'金属材料',address:''}),/仅主数据/)
})
test('repeat submission and unsupported review actions cannot skip workflow', () => {
 const state=seedState(), ctx=admin()
 assert.throws(()=>submitOnboarding(state,ctx,'s2'),/仅潜在/)
 assert.throws(()=>reviewOnboarding(state,{...ctx,role:'SQE'},'s2','FORCE_QUALIFY','同意'),/无可处理/)
})
