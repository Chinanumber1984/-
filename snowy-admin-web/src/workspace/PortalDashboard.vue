<script setup>
import {computed,onMounted,ref} from 'vue'
const props=defineProps({api:Function,context:Object,alerts:{type:Array,default:()=>[]}})
const emit=defineEmits(['navigate'])
const busy=ref(false),error=ref(''),rfqs=ref([]),reconciliations=ref([]),scars=ref([]),scores=ref([]),notices=ref([]),loaded=ref(false)
const roles=props.context.roles||[]
const permissions={sourcing:roles.some(r=>['ADMIN','QUOTE'].includes(r)),collaboration:roles.some(r=>['ADMIN','SHIP','FINANCE'].includes(r)),quality:roles.some(r=>['ADMIN','QUALITY'].includes(r)),performance:roles.some(r=>['ADMIN','QUOTE','QUALITY','FINANCE'].includes(r))}
const activeRfqs=computed(()=>rfqs.value.filter(r=>r.status==='PUBLISHED'))
const openAuctions=computed(()=>activeRfqs.value.filter(r=>r.strategy==='AUCTION'))
const quoteRfqs=computed(()=>activeRfqs.value.filter(r=>r.strategy!=='AUCTION'))
const needRecon=computed(()=>reconciliations.value.filter(r=>r.status==='WAITING_CONFIRMATION'&&!r.supplierConfirmed))
const disputedRecon=computed(()=>reconciliations.value.filter(r=>['DIFFERENCE','DISPUTED'].includes(r.status)))
const openScars=computed(()=>scars.value.filter(r=>!['CLOSED','WITHDRAWN','REJECTED'].includes(r.status)))
const latestScore=computed(()=>scores.value.slice().sort((a,b)=>String(b.period||'').localeCompare(String(a.period||'')))[0]||null)
const recentNotices=computed(()=>[
 ...props.alerts.map(a=>({key:'alert:'+a.id,time:a.createdAt||a.validTo,title:a.kind==='EXPIRED'?'资质已过期':'资质即将到期',body:`证书 ${a.certId||''} · ${a.validTo||''}`,target:'alerts'})),
 ...notices.value.map((n,i)=>({key:n.id?'notice:'+n.id:'notice:'+i,time:n.createdAt,title:'业务通知',body:n.message||n.title||'收到一条新通知',target:n.target==='quality'?'quality':n.target==='collaboration'?'collaboration':'sourcing'}))
].sort((a,b)=>String(b.time||'').localeCompare(String(a.time||''))).slice(0,8))
async function load(){
 if(busy.value)return
 busy.value=true;error.value='';rfqs.value=[];reconciliations.value=[];scars.value=[];scores.value=[];notices.value=[]
 const jobs=[]
 if(permissions.sourcing){jobs.push(['rfqs',()=>props.api('/sourcing/rfqs?current=1&size=100')]);jobs.push(['sourceNotices',()=>props.api('/sourcing/notices')])}
 if(permissions.collaboration){jobs.push(['reconciliations',()=>props.api('/collaboration/reconciliations?current=1&size=100')]);jobs.push(['collabNotices',()=>props.api('/collaboration/notices')])}
 if(permissions.quality){jobs.push(['scars',()=>props.api('/quality/cases?kind=SCAR&current=1&size=100')]);jobs.push(['qualityNotices',()=>props.api('/quality/notices')])}
 if(permissions.performance)jobs.push(['scores',()=>props.api('/performance/scores?current=1&size=100')])
 const results=await Promise.allSettled(jobs.map(([,fn])=>fn())),failed=[]
 results.forEach((result,i)=>{if(result.status!=='fulfilled'){failed.push(jobs[i][0]);return}const [name]=jobs[i],data=result.value;if(name==='rfqs')rfqs.value=data.records||[];else if(name==='reconciliations')reconciliations.value=data.records||[];else if(name==='scars')scars.value=data.records||[];else if(name==='scores')scores.value=data.records||[];else if(name.endsWith('Notices'))notices.value.push(...(Array.isArray(data)?data:[]))})
 if(failed.length)error.value='部分工作台数据暂不可用，请刷新或进入对应业务模块查看。'
 loaded.value=true;busy.value=false
}
onMounted(load)
</script>
<template>
 <section class="portal-dashboard" :aria-busy="busy">
  <div class="panel-heading"><div><h2>我的供应商工作台</h2><p>只汇总当前联系人可访问的本方待办、提醒和绩效；点卡片进入对应业务页面处理。</p></div><button class="btn" :disabled="busy" @click="load">{{busy?'正在刷新…':'刷新工作台'}}</button></div>
  <p v-if="error" role="status" class="info-note">{{error}}</p>
  <div class="portal-dashboard-grid">
   <article class="portal-summary-card"><span>待处理询价</span><strong>{{permissions.sourcing?quoteRfqs.length:'—'}}</strong><small>已发布并邀请本供应商</small><button class="text-btn" :disabled="!permissions.sourcing" @click="emit('navigate','sourcing')">查看询价</button></article>
   <article class="portal-summary-card"><span>进行中竞价</span><strong>{{permissions.sourcing?openAuctions.length:'—'}}</strong><small>仅统计本供应商受邀场次</small><button class="text-btn" :disabled="!permissions.sourcing" @click="emit('navigate','sourcing')">进入竞价</button></article>
   <article class="portal-summary-card"><span>待我确认的对账</span><strong>{{permissions.collaboration?needRecon.length:'—'}}</strong><small>{{permissions.collaboration?disputedRecon.length:'—'}} 笔存在差异或待协商</small><button class="text-btn" :disabled="!permissions.collaboration" @click="emit('navigate','collaboration')">查看对账</button></article>
   <article class="portal-summary-card"><span>未关闭 SCAR</span><strong>{{permissions.quality?openScars.length:'—'}}</strong><small>质量整改与 8D 跟进</small><button class="text-btn" :disabled="!permissions.quality" @click="emit('navigate','quality')">查看质量事项</button></article>
  </div>
  <div class="portal-dashboard-lower">
   <section class="portal-dashboard-card"><div class="panel-heading"><div><h3>最近提醒</h3><p>资质到期和业务通知</p></div><button class="text-btn" @click="emit('navigate','alerts')">全部提醒</button></div><ul v-if="recentNotices.length" class="portal-notice-list"><li v-for="n in recentNotices" :key="n.key"><button @click="emit('navigate',n.target)"><strong>{{n.title}}</strong><span>{{n.body}}</span></button><small>{{n.time||'—'}}</small></li></ul><p v-else class="empty-state">{{loaded?'暂无待处理提醒':'正在读取…'}}</p></section>
   <section class="portal-dashboard-card"><div class="panel-heading"><div><h3>绩效概览</h3><p>只展示当前绑定供应商的评分</p></div><button class="text-btn" :disabled="!permissions.performance" @click="emit('navigate','performance')">查看绩效</button></div><div v-if="latestScore" class="portal-score-summary"><strong>{{latestScore.total}}</strong><span>{{latestScore.period}} · {{latestScore.grade}}</span></div><p v-else class="empty-state">{{permissions.performance?(loaded?'暂无绩效记录':'正在读取…'):'当前联系人无绩效查看权限'}}</p></section>
  </div>
  <p class="portal-dashboard-footnote">汇总内容受服务器供应商范围和联系人角色控制。金额、对手信息与内部风险评估不会在门户总览展示。</p>
 </section>
</template>
<style scoped>
.portal-dashboard-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin:20px 0}.portal-summary-card,.portal-dashboard-card{border:1px solid #dce7e0;border-radius:12px;background:#fff;padding:18px}.portal-summary-card{display:flex;flex-direction:column;gap:7px;min-height:145px}.portal-summary-card>span{color:#62746a;font-size:13px}.portal-summary-card strong{font-size:30px;color:#183d2b}.portal-summary-card small{color:#78877e}.portal-summary-card .text-btn{align-self:flex-start;margin-top:auto}.portal-dashboard-lower{display:grid;grid-template-columns:1.3fr 1fr;gap:16px}.portal-dashboard-card h3{margin:0}.portal-notice-list{list-style:none;padding:0;margin:0}.portal-notice-list li{display:flex;justify-content:space-between;gap:12px;border-bottom:1px solid #eef2ef;padding:11px 0}.portal-notice-list li button{border:0;background:none;text-align:left;display:flex;flex-direction:column;gap:4px;cursor:pointer;color:inherit}.portal-notice-list li button span,.portal-notice-list li small{font-size:12px;color:#718076}.portal-score-summary{display:flex;align-items:baseline;gap:12px;padding:12px 4px}.portal-score-summary strong{font-size:34px;color:#183d2b}.portal-score-summary span{color:#62746a}.portal-dashboard-footnote{margin-top:18px;color:#738077;font-size:12px}.portal-dashboard .text-btn:disabled{opacity:.45;cursor:not-allowed}@media(max-width:900px){.portal-dashboard-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.portal-dashboard-lower{grid-template-columns:1fr}}@media(max-width:480px){.portal-dashboard-grid{grid-template-columns:1fr}}
</style>
