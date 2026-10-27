<script setup>
defineProps({tone:{type:String,default:'potential'}})
</script>
<template><span :class="['status',tone]"><i></i><slot /></span></template>
