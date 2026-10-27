// Additional regression for scheduler-era and lifecycle approval boundaries, using retained fixtures.
import {readFileSync,writeFileSync} from 'node:fs'
import crypto from 'node:crypto'
import{execFileSync}from'node:child_process'
import assert from 'node:assert/strict'
import {call,response,login,credit,encrypt} from './test-performance-api.mjs'
const f=JSON.parse(readFileSync('.runtime/performance/fixture.json','utf8')),a=await login(f.owner),sqe=await login(f.sqe),portal=await login(f.portal,'c'),base='/srm/v1/performance',cbase='/client/c/srm/v1/performance',checks=[]
const pass=x=>{checks.push(x);console.log('PASS:',x)}
await call(base+'/imports',a,{requestKey:crypto.randomUUID(),rows:[null]},400);pass('null_import_row_is_validation_error')
let score=await call(base+'/scores/'+f.score,a)
const appeal=await call(cbase+'/scores/'+score.id+'/appeals',portal,{scoreVersion:score.rowVersion,reason:'检查复核决定必填'})
await call(base+'/appeals/'+appeal.id+'/review',sqe,{scoreVersion:score.rowVersion,comment:'未选择决定'},400)
assert.ok((await call(base+'/scores/'+score.id,a)).appeals.some(x=>x.id===appeal.id&&x.status==='PENDING'))
await call(base+'/appeals/'+appeal.id+'/review',sqe,{scoreVersion:score.rowVersion,approve:false,comment:'验证结束，保留原分数'});pass('missing_review_decision_cannot_silently_reject')
const current=await call('/srm/v1/suppliers/'+f.supplier,a)
const freeze=await call('/srm/v1/suppliers/'+f.supplier+'/approvals',a,{requestKey:crypto.randomUUID(),kind:'FREEZE',rowVersion:current.rowVersion,reason:'绩效与生命周期并行保护测试'})
await call(base+'/imports',a,{requestKey:crypto.randomUUID(),rows:[{supplierId:f.supplier,period:f.period,sourceType:'SANDBOX',sourceRef:'BOUNDARY-TEST',scores:[95,95,95,95]}]})
score=await call(base+'/calculate',a,{supplierId:f.supplier,period:f.period});assert.equal(score.grade,'STRATEGIC');assert.equal((await call('/srm/v1/suppliers/'+f.supplier,a)).tier,current.tier)
await call('/srm/v1/approvals/'+freeze.id+'/withdraw',a,{comment:'边界验证结束，撤回测试申请'})
const repeat=await call(base+'/calculate',a,{supplierId:f.supplier,period:f.period});assert.equal(repeat.rowVersion,score.rowVersion);assert.equal((await call('/srm/v1/suppliers/'+f.supplier,a)).tier,'STRATEGIC');pass('pending_lifecycle_locks_tier_then_same_input_recompute_applies_deferred_tier')
const latest=(await call(base+'/templates',a))[0]
const config={expectedVersion:latest.version,effectivePeriod:latest.effectivePeriod,weights:latest.config.weights,thresholds:latest.config.thresholds}
const concurrent=await Promise.all([1,2].map(()=>response(base+'/templates',sqe,config)));assert.deepEqual(concurrent.map(x=>x.code).sort(),[200,409]);pass('concurrent_global_template_publish_has_one_winner')
await call(base+'/imports',a,{requestKey:crypto.randomUUID(),rows:[{supplierId:f.supplier,period:f.period,sourceType:'HISTORICAL',sourceRef:'BOUNDARY-TEST-RESTORED-SAMPLE',scores:[88,90,85,94]}]});await call(base+'/calculate',a,{supplierId:f.supplier,period:f.period})
const challenges=[];for(let i=0;i<5;i++){const challenge=await call('/auth/c/getPicCaptcha');assert.match(challenge.validCodeReqNo,/^[0-9a-f]{32}$/);challenges.push(challenge.validCodeReqNo)}assert.equal(new Set(challenges).size,5);pass('captcha_uses_distinct_uuid_request_nonces')
const invite=await call('/srm/v1/registration-invitations',a,{}),challenge=await call('/auth/c/getPicCaptcha')
const raw=execFileSync('docker',['compose','--env-file','runtime/compose.env','exec','-T','redis','redis-cli','-n','1','--raw','GET','Cache:auth-validCode:'+challenge.validCodeReqNo],{cwd:'artifacts/SRM-local-review',encoding:'utf8'}).trim();let code;try{code=JSON.parse(raw)}catch{code=raw}
const registrationPortal={account:'w1011_nonce_'+crypto.randomBytes(4).toString('hex'),password:crypto.randomBytes(6).toString('hex')+'Aa9!'}
const registered=await call('/srm/public/register',null,{invitation:invite.invitation,supplier:{name:'W10–11验证码注册兼容测试-'+Date.now(),category:'MACHINING',creditCode:credit()},contact:{name:'验证码注册测试管理员',phone:'13800138000',roles:['ADMIN'],enabled:true,rowVersion:0},account:{account:registrationPortal.account,passwordCipher:encrypt(registrationPortal.password)},validCode:String(code),validCodeReqNo:challenge.validCodeReqNo,orgId:f.orgB})
assert.equal(registered.status,'POTENTIAL');const rt=await login(registrationPortal,'c');assert.equal((await call('/client/c/srm/v1/session/context',rt)).orgId,f.orgA);f.registrationPortal=registrationPortal;writeFileSync('.runtime/performance/fixture.json',JSON.stringify(f,null,2),{mode:0o600});pass('uuid_captcha_registration_retains_trusted_invitation_org')
writeFileSync('docs/performance/boundary-verification.json',JSON.stringify({result:'passed',entry:'http://localhost:18080/',checks},null,2)+'\n');console.log(`PASS: ${checks.length} additional boundary checks.`)
