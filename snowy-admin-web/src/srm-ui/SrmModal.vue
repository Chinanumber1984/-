<script setup>
defineProps({title:{type:String,required:true}})
defineEmits(['close'])
</script>
<template><div class="modal-backdrop" @keydown.esc="$emit('close')"><section class="modal" role="dialog" aria-modal="true" :aria-label="title"><div class="modal-heading"><div><span class="eyebrow">SUPPLIER MANAGEMENT</span><h2>{{title}}</h2></div><button class="icon-button" aria-label="关闭表单" @click="$emit('close')">✕</button></div><slot /></section></div></template>
