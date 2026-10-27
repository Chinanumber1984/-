<template><Workspace :initial-token="token" :initial-view="route.query.view==='approvals'?'approvals':'suppliers'" embedded /></template>
<script setup>
import {useRoute} from 'vue-router'
const route=useRoute()
import Workspace from '@/workspace/App.vue'
import '@/srm-ui/base.css'
import '@/workspace/style.css'
import tool from '@/utils/tool'
const token = tool.data.get('TOKEN') || ''
</script>
