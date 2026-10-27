// Local development only. Preserve existing credentials and never print passwords.
import {existsSync,readFileSync,writeFileSync} from 'node:fs'
import crypto from 'node:crypto'
import {createRequire} from 'node:module'
import {sql} from './test-workspace-api.mjs'
const require=createRequire(new URL('../snowy-admin-web/package.json',import.meta.url)),{sm3}=require('sm-crypto')
const path='.runtime/approval-access.json';if(existsSync(path)){console.log('Existing private approval access retained.');process.exit(0)}
const owner=JSON.parse(readFileSync('.runtime/workspace-access.json','utf8')),accounts=[]
const id=()=>String(BigInt(Date.now())*1000000n+BigInt(crypto.randomInt(1,999999)))
for(const [account,name,role] of [['srm.sqe','本地SQE审核员','SQE'],['srm.manager','本地采购经理审批员','CAT-MGR'],['srm.flowadmin','本地流程管理员','SYS-ADMIN']]){
 if(sql(`SELECT COUNT(*) FROM SYS_USER WHERE ACCOUNT='${account}';`)!=='0')throw new Error('Existing development account retained; no password reset performed')
 const uid=id(),password=crypto.randomBytes(9).toString('base64url')+'A9!';let rid=sql(`SELECT ID FROM SYS_ROLE WHERE CODE='${role}' AND DELETE_FLAG='NOT_DELETE' LIMIT 1;`)
 if(!rid){rid=id();sql(`INSERT INTO SYS_ROLE(ID,NAME,CODE,CATEGORY,DELETE_FLAG,CREATE_TIME) VALUES('${rid}','${role}','${role}','GLOBAL','NOT_DELETE',NOW());`)}
 sql(`INSERT INTO SYS_USER(ID,ACCOUNT,PASSWORD,NAME,USER_STATUS,ORG_ID,DELETE_FLAG,CREATE_TIME) VALUES('${uid}','${account}','${sm3(password)}','${name}','ENABLE','${owner.orgId}','NOT_DELETE',NOW());INSERT INTO SYS_RELATION(ID,OBJECT_ID,TARGET_ID,CATEGORY) VALUES('${id()}','${uid}','${rid}','SYS_USER_HAS_ROLE');`)
 for(const api of ['/srm/v1/**','/srm/fields/sensitive'])sql(`INSERT INTO SYS_RELATION(ID,OBJECT_ID,TARGET_ID,CATEGORY,EXT_JSON) VALUES('${id()}','${uid}','${api}','SYS_USER_HAS_PERMISSION','{"scopeCategory":"SCOPE_ORG"}');`)
 accounts.push({account,password,uid,orgId:owner.orgId,name,role})
}
writeFileSync(path,JSON.stringify(accounts,null,2),{mode:0o600});writeFileSync('.runtime/approval-access.md','# W7–9 本机审批验收账号\n\n仅用于本地开发；不要提交或用于生产。发起人使用 workspace-access.md 中的账号，审批使用独立人员。\n\n'+accounts.map(a=>`- ${a.name}（${a.role}）：${a.account}，初始密码：${a.password}`).join('\n')+'\n\n三个账号归属发起人的同一组织，仅该组织数据权限。\n',{mode:0o600});console.log('Private development approval accounts prepared; credentials were not printed.')
