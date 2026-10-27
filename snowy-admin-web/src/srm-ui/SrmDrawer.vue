<script setup>
defineProps({label:{type:String,required:true}})
defineEmits(['close'])
</script>
<template><div class="backdrop" @click="$emit('close')"></div><section class="drawer" role="dialog" aria-modal="true" :aria-label="label" @keydown.esc="$emit('close')"><slot /></section></template>
