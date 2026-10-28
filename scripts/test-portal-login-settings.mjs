import {call,response,login,encrypt,sql} from './test-performance-api.mjs'
import {readFileSync,writeFileSync} from 'node:fs'
import {createHash} from 'node:crypto'
import assert from 'node:assert/strict'
const runtime='artifacts/SRM-local-review/runtime/',users=JSON.parse(readFileSync(runtime+'accounts.json')),key='SNOWY_SYS_DEFAULT_CAPTCHA_OPEN_FLAG_FOR_C',checks=[],pass=s=>{checks.push(s);console.log('PASS:',s)},sha=v=>createHash('sha256').update(v).digest('hex')
const cfg=await call('/dev/config/sysBaseList'),bFlag=cfg.find(c=>c.configKey.endsWith('CAPTCHA_OPEN_FLAG_FOR_B')).configValue
const tester=await login(users.find(u=>u.account==='lijunhua')),portal=await login(users.find(u=>u.account==='gysadmin'),'c')
assert.equal((await response('/dev/config/editBatch',tester,[{configKey:key,configValue:'true'}])).code,403)
assert.equal((await response('/dev/config/editBatch',portal,[{configKey:key,configValue:'true'}])).code,401)
pass('internal_tester_and_portal_admin_cannot_change_global_login_captcha')
const admin=await login(users.find(u=>u.account==='admin'))
try{await call('/dev/config/editBatch',admin,[{configKey:key,configValue:'true'}]);const actor=users.find(u=>u.account==='lixiaolong'),denied=await response('/auth/c/doLogin',null,{account:actor.account,password:encrypt(actor.password),device:'PC'});assert.notEqual(denied.code,200);assert.match(denied.msg,/验证码/);pass('enabled_captcha_is_enforced_by_server_even_when_client_omits_fields')}finally{await call('/dev/config/editBatch',admin,[{configKey:key,configValue:'false'}])}
const result=await call('/dev/config/sysBaseList');assert.equal(result.find(c=>c.configKey===key).configValue,'false');assert.equal(result.find(c=>c.configKey.endsWith('CAPTCHA_OPEN_FLAG_FOR_B')).configValue,bFlag)
const before=JSON.parse(readFileSync(readFileSync('.runtime/portal-login-settings/latest-backup.txt','utf8').trim()+'/credential-hashes.json'));for(const [name,hash]of Object.entries(before))assert.equal(sha(readFileSync(runtime+name)),hash)
const old=JSON.parse(readFileSync('.runtime/collaboration/preservation.json'));assert.equal(sha(sql("SELECT ID,ACCOUNT,PASSWORD,USER_STATUS FROM SYS_USER WHERE ACCOUNT IN ('admin','lijunhua') ORDER BY ACCOUNT")+'\n'+sql("SELECT ID,ACCOUNT,PASSWORD,USER_STATUS FROM CLIENT_USER WHERE ACCOUNT IN ('gysadmin','lixiaolong') ORDER BY ACCOUNT")),old.userHashes)
pass('portal_captcha_disabled_internal_flag_passwords_and_cryptographic_keys_preserved')
writeFileSync('docs/review/portal-login-settings-api.json',JSON.stringify({result:'passed',date:new Date().toISOString(),checks,finalPortalCaptcha:false,internalConfigurationUnchanged:true,credentialsChanged:false},null,2)+'\n')
