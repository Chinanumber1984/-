<script setup>
import {ref,onMounted} from 'vue'
import {useRouter} from 'vue-router'
import tool from '@/utils/tool'
const router=useRouter(),count=ref(null)
onMounted(async()=>{try{const token=tool.data.get('TOKEN');if(!token)return;const r=await fetch('/api/srm/v1/approvals/tasks',{headers:{token}}).then(r=>r.json());if(r.code===200)count.value=r.data.length}catch{}})
</script>
<template><a-card v-if="count!==null" title="SRM 审批待办" class="mb-2"><p>当前有 <strong>{{count}}</strong> 项待处理审批</p><p>准入、关键资料变更、冻结与淘汰</p><a-button type="primary" @click="router.push('/srm/workspace?view=approvals')">进入审批工作台</a-button></a-card></template>
