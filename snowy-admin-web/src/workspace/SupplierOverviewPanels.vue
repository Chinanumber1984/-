<script setup>
import {ref,watch} from 'vue'
const props=defineProps({api:Function,section:String,supplier:Object})
const rows=ref([]),total=ref(0),summary=ref(null),scoreDetail=ref(null),busy=ref(false),error=ref('')
const grades={STRATEGIC:'战略',PREFERRED:'优选',QUALIFIED:'合格',WATCH:'观察',ELIMINATION_SUGGESTED:'淘汰建议（需审批）'}
const riskTypes={DELIVERY_EXCEPTION:'履约异常',CERTIFICATE_EXPIRED:'资质过期',COMPLIANCE_BLACKLIST:'合规风险',SCAR_OVERDUE:'8D超期',REPEATED_DEFECT:'重复缺陷',SEVERE_QUALITY:'严重质量问题'}
const riskStates={PENDING:'待评估',ASSESSED:'已评估',WARNING:'预警跟进',DISPOSED:'已归档'}
const orderStates={OPEN:'待确认',CONFIRMED:'已确认',CHANGE_PENDING:'变更待确认',PARTIALLY_RECEIVED:'部分收货',RECEIVED:'已收货',CLOSED:'已关闭',CANCELLED:'已取消'}
async function load(){
 busy.value=true;error.value='';rows.value=[];summary.value=null;scoreDetail.value=null
 try{
  const supplierId=encodeURIComponent(props.supplier.id),query=new URLSearchParams({current:'1',size:'100',supplierId})
  if(props.section==='performance'){
   const [page,trend]=await Promise.all([props.api('/performance/scores?'+query),props.api('/performance/suppliers/'+supplierId+'/summary?months=12')])
   rows.value=page.records;total.value=page.total;summary.value=trend
  }else if(props.section==='risks'){
   const page=await props.api('/quality/risks?'+query);rows.value=page.records;total.value=page.total
  }else if(props.section==='orders'){
   const page=await props.api('/collaboration/orders?'+new URLSearchParams({current:'1',size:'100',supplierId}));rows.value=page.records;total.value=page.total
  }
 }catch(e){error.value=e.message||'暂时无法加载该供应商的关联记录'}finally{busy.value=false}
}
async function inspectScore(row){try{scoreDetail.value=await props.api('/performance/scores/'+row.id)}catch(e){error.value=e.message}}
watch(()=>[props.section,props.supplier?.id],load,{immediate:true})
</script>
<template>
 <section class="supplier-linked-records" aria-live="polite">
  <div v-if="busy" class="info-note">正在加载{{section==='performance'?'绩效':section==='risks'?'风险':'采购订单'}}记录…</div>
  <p v-if="error" class="form-error" role="alert">{{error}}</p>
  <template v-if="section==='performance'&&!busy&&!error">
   <article v-if="summary" class="info-note">近{{summary.requestedMonths}}个月：有效评分 {{summary.validMonths}} 期；{{summary.insufficientHistory?'历史不完整':'历史完整'}}。缺少月份 {{summary.missingPeriods?.join('、')||'无'}}。平均总分 {{summary.averageTotal??'暂无'}}。来源不足时不会补造分数。</article>
   <div class="table-scroll"><table><thead><tr><th>月份</th><th>交付</th><th>质量</th><th>成本</th><th>服务</th><th>总分</th><th>等级</th><th>来源与历史</th></tr></thead><tbody>
    <tr v-for="r in rows" :key="r.id"><td>{{r.period}}</td><td>{{r.deliverScore}}</td><td>{{r.qualityScore}}</td><td>{{r.costScore}}</td><td>{{r.serviceScore}}</td><td>{{r.total}}</td><td>{{grades[r.grade]||r.grade}}</td><td><button class="text-btn" @click="inspectScore(r)">查看来源版本</button></td></tr>
   </tbody></table></div>
   <article v-if="scoreDetail" class="linked-detail"><div class="inline-actions"><strong>{{scoreDetail.period}} 绩效来源版本</strong><button class="text-btn" @click="scoreDetail=null">收起</button></div><div v-for="v in scoreDetail.history" :key="v.revision" class="history-item"><strong>第{{v.revision}}版 · {{v.snapshot?.sourceType||'未知来源'}}</strong><p>来源引用：{{v.snapshot?.sourceRef||'无'}}</p><p>更新时间：{{v.createdAt}}</p></div><p v-if="!scoreDetail.history?.length" class="muted">暂无评分版本快照</p></article>
   <div v-if="!rows.length" class="empty-state">该供应商暂无月度绩效记录。</div>
  </template>
  <template v-else-if="section==='risks'&&!busy&&!error">
   <div class="table-scroll"><table><thead><tr><th>风险事件</th><th>来源</th><th>状态</th><th>等级</th><th>冻结建议</th></tr></thead><tbody><tr v-for="r in rows" :key="r.id"><td>{{riskTypes[r.riskType]||r.riskType}}</td><td>{{r.source==='INTERNAL'?'内部记录':r.source}}</td><td>{{riskStates[r.status]||r.status}}</td><td>{{({HIGH:'高',MEDIUM:'中',LOW:'低'}[r.level]||'待评估')}}</td><td>{{r.freezeSuggested?'已生成审批建议':'无'}}</td></tr></tbody></table></div>
   <div v-if="!rows.length" class="empty-state">该供应商暂无未归档风险事件。仍需按业务流程核对风险依据。</div>
  </template>
  <template v-else-if="section==='orders'&&!busy&&!error">
   <div class="table-scroll"><table><thead><tr><th>采购订单</th><th>状态</th><th>数据来源</th><th>币种</th><th>来源版本</th><th>内部版本</th></tr></thead><tbody><tr v-for="r in rows" :key="r.id"><td>{{r.poNo}}</td><td>{{orderStates[r.status]||r.status}}</td><td>{{r.mode==='SANDBOX'?'本机沙箱':'业务来源'}}</td><td>{{r.currency}}</td><td>{{r.sourceVersion}}</td><td>{{r.rowVersion}}</td></tr></tbody></table></div>
   <div v-if="!rows.length" class="empty-state">该供应商暂无采购订单。</div>
  </template>
  <p v-if="!busy&&!error&&total>rows.length" class="muted">仅展示最近{{rows.length}}条，共{{total}}条；请转到对应业务模块查看完整记录。</p>
 </section>
</template>
