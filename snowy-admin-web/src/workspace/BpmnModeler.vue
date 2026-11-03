<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import BpmnModelerEngine from 'bpmn-js/lib/Modeler'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'

const props = defineProps({ xml: { type: String, required: true }, busy: Boolean })
const emit = defineEmits(['save', 'close'])
const canvas = ref(null)
const error = ref('')
let modeler

onMounted(async () => {
  modeler = new BpmnModelerEngine({ container: canvas.value })
  try {
    await modeler.importXML(props.xml)
    modeler.get('canvas').zoom('fit-viewport')
  } catch (e) {
    error.value = `流程图打开失败：${e.message || 'BPMN 文件格式无效'}`
  }
})

onBeforeUnmount(() => modeler?.destroy())

async function save() {
  error.value = ''
  try {
    const { xml } = await modeler.saveXML({ format: true })
    emit('save', xml)
  } catch (e) {
    error.value = `流程图导出失败：${e.message || '未知错误'}`
  }
}
</script>

<template>
  <div class="bpmn-editor" role="dialog" aria-modal="true" aria-label="BPMN 流程设计器">
    <header class="bpmn-editor__header">
      <div>
        <h2>流程图设计</h2>
        <p>拖动节点可调整布局，双击节点可修改名称；节点类型、审批角色及业务顺序由系统模板控制。</p>
      </div>
      <button class="bpmn-editor__close" type="button" aria-label="关闭" @click="emit('close')">×</button>
    </header>
    <div ref="canvas" class="bpmn-editor__canvas" />
    <p v-if="error" class="bpmn-editor__error" role="alert">{{ error }}</p>
    <footer class="bpmn-editor__footer">
      <span>保存时将由服务端再次校验受控节点、候选角色与流程结构，并发布为新版本。</span>
      <div><button class="btn" type="button" @click="emit('close')">取消</button><button class="btn primary" type="button" :disabled="busy || !modeler" @click="save">校验并发布新版本</button></div>
    </footer>
  </div>
</template>

<style>
.bpmn-editor{position:fixed;inset:3vh 3vw;z-index:1200;display:flex;flex-direction:column;background:#fff;border:1px solid #d9e2dc;border-radius:12px;box-shadow:0 20px 70px #10251a40;overflow:hidden}.bpmn-editor__header,.bpmn-editor__footer{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:12px 18px}.bpmn-editor__header{border-bottom:1px solid #e5ebe7}.bpmn-editor__header h2{margin:0;font-size:18px}.bpmn-editor__header p{margin:5px 0 0;color:#68776e;font-size:13px}.bpmn-editor__close{border:0;background:transparent;font-size:28px;cursor:pointer}.bpmn-editor__canvas{min-height:0;flex:1}.bpmn-editor__footer{border-top:1px solid #e5ebe7;color:#68776e;font-size:12px}.bpmn-editor__footer>div{display:flex;gap:8px;flex-shrink:0}.bpmn-editor__error{margin:0;padding:8px 18px;color:#a32626;background:#fff1f0}
</style>
