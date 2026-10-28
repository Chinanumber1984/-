import {sql} from './test-performance-api.mjs'
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs'
import {createHash} from 'node:crypto'
import assert from 'node:assert/strict'
const dir='.runtime/collaboration',path=dir+'/preservation.json',bundle='artifacts/SRM-local-review/',sha=b=>createHash('sha256').update(b).digest('hex')
const hashes={};for(const f of ['accounts.json','account-profile.json','backend.env','compose.env','99-accounts.sql'])hashes[f]=sha(readFileSync(bundle+'runtime/'+f))
const userHashes=sha(sql("SELECT ID,ACCOUNT,PASSWORD,USER_STATUS FROM SYS_USER WHERE ACCOUNT IN ('admin','lijunhua') ORDER BY ACCOUNT")+'\n'+sql("SELECT ID,ACCOUNT,PASSWORD,USER_STATUS FROM CLIENT_USER WHERE ACCOUNT IN ('gysadmin','lixiaolong') ORDER BY ACCOUNT"))
const checksums=sql('CHECKSUM TABLE SRM_PERF_TEMPLATE,SRM_PERF_IMPORT,SRM_PERF_INPUT,SRM_SCORECARD,SRM_PERF_SCORE_VERSION,SRM_SRC_RFQ,SRM_SRC_QUOTE,SRM_SRC_AWARD')
const supplier=JSON.parse(readFileSync(bundle+'runtime/account-profile.json')).supplierId,supplierState=sql(`SELECT ID,STATUS,ROW_VERSION FROM SRM_SUPPLIER WHERE ID='${supplier}'`),now={hashes,userHashes,checksums,supplierState}
if(process.argv[2]==='capture'){mkdirSync(dir,{recursive:true});writeFileSync(path,JSON.stringify(now,null,2),{flag:'wx',mode:0o600});console.log('PASS: current four-account credentials, key environment, source sourcing/performance and target supplier snapshot captured privately.')}else{assert.deepEqual(now,JSON.parse(readFileSync(path)));assert.equal(sql("SELECT GROUP_CONCAT(ACCOUNT ORDER BY ACCOUNT) FROM SYS_USER WHERE USER_STATUS='ENABLE' AND DELETE_FLAG='NOT_DELETE'"),'admin,lijunhua');assert.equal(sql("SELECT GROUP_CONCAT(ACCOUNT ORDER BY ACCOUNT) FROM CLIENT_USER WHERE USER_STATUS='ENABLE' AND DELETE_FLAG='NOT_DELETE'"),'gysadmin,lixiaolong');console.log('PASS: existing credentials, keys, sourcing/performance data, target supplier lifecycle and exactly four active accounts preserved.')}
