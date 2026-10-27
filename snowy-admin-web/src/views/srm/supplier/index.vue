<template><Workspace :initial-token="token" embedded /></template>
<script setup>
import Workspace from '@/workspace/App.vue'
import '@/srm-ui/base.css'
import '@/workspace/style.css'
import tool from '@/utils/tool'
const token = tool.data.get('TOKEN') || ''
</script>
