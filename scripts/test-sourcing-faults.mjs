import {call,response,login,sql,credit,encrypt} from './test-performance-api.mjs'
import {socket} from './sourcing-test-client.mjs'
import {frame,parseFrame} from '../snowy-admin-web/src/workspace/sourcingRealtime.mjs'
import {readFileSync,writeFileSync} from 'node:fs'
import {execFileSync} from 'node:child_process'
import {createRequire} from 'node:module'
import crypto from 'node:crypto'
import assert from 'node:assert/strict'
const f=JSON.parse(readFileSync('.runtime/sourcing/fixture.json')),p='/srm/v1/sourcing',cp='/client/c/srm/v1/sourcing',sleep=ms=>new Promise(r=>setTimeout(r,ms)),key=()=>crypto.randomUUID(),checks=[],pass=n=>{checks.push(n);console.log('PASS:',n)},a=await login(f.owner),m=await login(f.manager),pt=await login(f.portal,'c'),pt2=await login(f.otherPortal,'c')
function redis(...args){return execFileSync('docker',['compose','--env-file','runtime/compose.env','exec','-T','redis','redis-cli','-n','1','--raw',...args],{cwd:'artifacts/SRM-local-review',encoding:'utf8'}).trim()}
const request={requestKey:key(),rowVersion:0,title:'W12–15故障验收-'+Date.now(),category:'MACHINING',strategy:'AUCTION',currency:'CNY',deadline:Date.now()+600000,startAt:Date.now()-1000,supplierIds:[f.supplier,f.secondSupplier],lines:[{id:'L1',material:'测试',quantity:1,delivery:'7天',fileIds:[]}],startingPrice:1000,minDecrement:10,antiSnipeMinutes:0}
let r=await call(p+'/rfqs',a,request);r=await call(p+'/rfqs/'+r.id+'/publish',a,{rowVersion:r.rowVersion});await call(p+'/rfqs/'+r.id+'/recover',a,{});let c=await socket(pt,r.id)
try{
 const before=redis('GET','srm:sourcing:auction:'+r.id);sql(`DELIMITER $$\nCREATE TRIGGER srm_src_test_failure BEFORE INSERT ON SRM_SRC_BID FOR EACH ROW BEGIN IF NEW.RFQ_ID='${r.id}' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated sourcing fixture fault'; END IF; END$$\nDELIMITER ;\n`)
 try{const b={rfqId:r.id,requestKey:key(),expectedVersion:r.rowVersion,amount:990};c.send(b);assert.equal((await c.wait(x=>x.requestKey===b.requestKey)).code,503);assert.equal(sql(`SELECT COUNT(*) FROM SRM_SRC_BID WHERE RFQ_ID='${r.id}'`),'0');assert.equal(redis('GET','srm:sourcing:auction:'+r.id),before);await call(cp+'/rfqs/'+r.id+'/receipts/'+b.requestKey,pt,undefined,404)}finally{sql('DROP TRIGGER IF EXISTS srm_src_test_failure;')}
 pass('database_insert_failure_rolls_back_bid_version_and_cache')
 let b={rfqId:r.id,requestKey:key(),expectedVersion:r.rowVersion,amount:995};c.send(b);assert.equal((await c.wait(x=>x.requestKey===b.requestKey)).code,409);b={...b,requestKey:key(),amount:990.001};c.send(b);assert.equal((await c.wait(x=>x.code===400)).code,400);pass('minimum_decrease_and_precision_rejected_over_stomp')
 // Explicitly unauthorized broker destination closes this connection.
 c.ws.send(frame('SUBSCRIBE',{id:'evil',destination:'/topic/all-bids'}));await sleep(500);assert.ok(c.messages.some(x=>x.error)||c.ws.readyState!==1);pass('arbitrary_stomp_topic_denied')
}finally{c.close()}
// No bid => actual scheduled FAILED transition. Advance only isolated fixture deadline.
sql(`UPDATE SRM_SRC_RFQ SET DEADLINE_MS=1 WHERE ID='${r.id}' AND TITLE LIKE 'W12–15故障验收-%';`);for(let i=0;i<30;i++){r=await call(p+'/rfqs/'+r.id,a);if(r.status==='FAILED')break;await sleep(500)}assert.equal(r.status,'FAILED');pass('no_bid_scheduled_close_is_failed')
// Basis drift must keep the real manager task pending.
let q=await call(p+'/rfqs',a,{...request,strategy:'COMPARE',requestKey:key(),title:'W12–15审批漂移-'+Date.now(),deadline:Date.now()+600000});q=await call(p+'/rfqs/'+q.id+'/publish',a,{rowVersion:q.rowVersion});await call(cp+'/rfqs/'+q.id+'/quotes',pt2,{requestKey:key(),expectedRound:0,lines:[{lineId:'L1',unitPrice:100,leadDays:7}],freight:0});sql(`UPDATE SRM_SRC_RFQ SET DEADLINE_MS=1 WHERE ID='${q.id}' AND TITLE LIKE 'W12–15审批漂移-%';`);for(let i=0;i<30;i++){q=await call(p+'/rfqs/'+q.id,a);if(q.status==='CLOSED')break;await sleep(500)}let award=await call(p+'/rfqs/'+q.id+'/awards',a,{requestKey:key(),supplierId:f.secondSupplier,reason:'验证审批依据漂移',noHistoryReason:'测试新供方例外',discloseLoss:false});await call(p+'/suppliers/'+f.secondSupplier+'/risk',a,{level:'HIGH',evidenceRef:'TEST-DRIFT-'+Date.now(),note:'测试期人工评估更新'});await call(p+'/awards/'+award.id+'/tasks/'+award.tasks[0].id,m,{action:'APPROVE',comment:'应被漂移阻断',approveNoHistory:true,acknowledgeRisk:true},409);assert.equal((await call(p+'/awards/'+award.id,a)).tasks.length,1);await call(p+'/awards/'+award.id+'/withdraw',a,{});pass('risk_basis_drift_preserves_task_and_requester_withdraws')
writeFileSync('docs/sourcing/fault-verification.json',JSON.stringify({result:'passed',date:new Date().toISOString(),checks},null,2)+'\n')
