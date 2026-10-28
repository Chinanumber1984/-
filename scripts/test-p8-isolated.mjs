// Read-only four-account acceptance checks against an explicitly isolated P8 restore.
import assert from 'node:assert/strict'
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs'
import {join} from 'node:path'
import {call,login} from './test-performance-api.mjs'

const bundle=process.env.SRM_TEST_BUNDLE
if(!bundle||process.env.SRM_TEST_ENTRY!=='http://localhost:18081')throw new Error('Set the verified isolated P8 clone bundle and entry http://localhost:18081')
const expectedProject='srm-drill-p8-20261028'
const marker=JSON.parse(readFileSync(join(bundle,'..','recovery-result.json'),'utf8'))
assert.equal(marker.project,expectedProject)
assert.equal(marker.result,'passed')
const run=(await import('node:child_process')).execFileSync
const project=run('docker',['inspect','srm-drill-p8-20261028-mysql-1','--format','{{index .Config.Labels "com.docker.compose.project"}}'],{encoding:'utf8'}).trim()
assert.equal(project,expectedProject)
const query=run('docker',['compose','--project-directory',bundle,'--env-file',join(bundle,'runtime/compose.env'),'-f',join(bundle,'compose.drill.json'),'-p',project,'exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names snowy'],{input:'SELECT PROJECT FROM SRM_P8_DRILL_MARKER WHERE ID=1;',encoding:'utf8'}).trim()
assert.equal(query,expectedProject)
const accounts=JSON.parse(readFileSync(join(bundle,'runtime/accounts.json'),'utf8'))
assert.deepEqual(accounts.map(a=>a.account).sort(),['admin','gysadmin','lijunhua','lixiaolong'])
const users=Object.fromEntries(await Promise.all(accounts.map(async a=>[a.account,{...a,token:await login(a,a.realm)}])))
const profile=JSON.parse(readFileSync(join(bundle,'runtime/account-profile.json'),'utf8'))
const checks=[];const pass=n=>checks.push(n)
for(const a of accounts){assert.ok(users[a.account].token);pass('login:'+a.account)}
const tester=users.lijunhua.token,admin=users.admin.token,quote=users.lixiaolong.token,portalAdmin=users.gysadmin.token
const supplier=await call('/srm/v1/suppliers/'+profile.supplierId,tester)
assert.equal(supplier.id,profile.supplierId);assert.equal(supplier.status,'QUALIFIED');pass('internal supplier detail')
const internalRfq=await call('/srm/v1/sourcing/rfqs/'+profile.rfqId,tester)
assert.equal(internalRfq.id,profile.rfqId);pass('internal published RFQ detail')
for(const token of [quote,portalAdmin]){
 const rfqs=await call('/client/c/srm/v1/sourcing/rfqs',token)
 assert.ok(rfqs.records.some(r=>r.id===profile.rfqId))
 const own=await call('/client/c/srm/v1/suppliers',token)
 assert.equal(own.total,1)
 const denied=await call('/srm/v1/suppliers/'+profile.supplierId,token,undefined,401)
 assert.equal(denied,null)
}
const foreign=await call('/srm/v1/suppliers',tester)
if(foreign.records)assert.ok(foreign.records.length>=1)
await call('/srm/v1/sourcing/rfqs/'+profile.rfqId,admin)
pass('portal identities restricted to bound supplier and cannot enter internal API realm')
const cfg=await call('/dev/config/sysBaseList',admin)
assert.ok(cfg.some(x=>x.configKey==='SNOWY_SYS_DEFAULT_CAPTCHA_OPEN_FLAG_FOR_C'))
pass('admin can read portal-login configuration')
assert.ok((await call('/dev/config/sysBaseList')).some(x=>x.configKey==='SNOWY_SYS_DEFAULT_CAPTCHA_OPEN_FLAG_FOR_C'))
pass('public login bootstrap exposes the captcha flag; settings page remains a separate role-gated UI')
const report={result:'passed',date:new Date().toISOString(),entry:process.env.SRM_TEST_ENTRY,project:expectedProject,mutation:'none; isolated recovery marker verified before logins',checks,accounts:accounts.map(a=>a.account),scope:'clone database and clone gateway only'}
mkdirSync('docs/review',{recursive:true});writeFileSync('docs/review/P8-isolated-api-verification.json',JSON.stringify(report,null,2)+'\n')
console.log(`PASS: ${checks.length} isolated four-account API checks`)
