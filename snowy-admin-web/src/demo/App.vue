<script setup>
import { computed, reactive, ref, watch, h, nextTick } from 'vue'
import { seedState, roles, statuses, organizations, visibleSuppliers, businessToday, createSupplier, completeDemoCertificates, submitOnboarding, reviewOnboarding } from './model.mjs'
const paths = {
 grid: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
 people: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M16 3a4 4 0 0 1 0 8 M22 21v-2a4 4 0 0 0-3-3.87 M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
 check: 'M9 12l2 2 4-4 M12 3l9 4v6c0 5-9 9-9 9s-9-4-9-9V7z',
 chart: 'M3 3v18h18 M7 14l4-4 4 3 5-7',
 box: 'M3 7l9-4 9 4-9 4z M3 7v10l9 4 9-4V7 M12 11v10',
 settings: 'M4 7h16 M4 17h16 M8 4v6 M16 14v6',
 search: 'M21 21l-5-5 M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
 bell: 'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9 M10 21h4',
 arrow: 'M5 12h14 M13 6l6 6-6 6', close: 'M6 6l12 12 M6 18L18 6', plus: 'M12 5v14 M5 12h14',
 clock: 'M12 8v4l3 2 M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0',
 file: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6 M8 13h8 M8 17h5',
 leaf: 'M5 20C2 9 10 3 21 3c0 11-6 19-16 17 M5 20L16 9',
 logout: 'M10 17l5-5-5-5 M15 12H2 M14 3h5a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-5',
 chevron: 'M9 5l7 7-7 7'
}
const Icon = (props) => h('svg', { viewBox: '0 0 24 24', width: 19, height: 19, fill: 'none', stroke: 'currentColor', 'stroke-width': 1.65, 'stroke-linecap': 'round', 'stroke-linejoin': 'round', 'aria-hidden': true }, [h('path', { d: paths[props.name] || paths.grid })])
const key = 'srm-foundation-demo-v1'
function restore() {
 try {
  const x = JSON.parse(localStorage.getItem(key))
  if (x?.version === 1 && Array.isArray(x.suppliers) && x.suppliers.every(s => s.id && s.name && Array.isArray(s.certificates) && Array.isArray(s.contacts) && statuses[s.status]) && Array.isArray(x.events)) return x
 } catch {}
 return seedState()
}
const state = reactive(restore())
watch(state, () => { try { localStorage.setItem(key, JSON.stringify(state)) } catch {} }, { deep: true })
const ctx = reactive({ orgId: 'east', role: 'SUP_ADMIN', supplierId: 's1' })
const view = ref('overview'), filter = ref('all'), query = ref(''), category = ref('all'), selectedId = ref(null), detailTab = ref('profile')
const newModal = ref(false), resetModal = ref(false), notice = ref(''), formError = ref(''), reviewComment = ref(''), nameInput = ref(null)
const form = reactive({ name: '', category: '精密加工', address: '' })
const today = businessToday()
const deadline = new Date(today + 'T12:00:00+08:00'); deadline.setDate(deadline.getDate() + 30)
const cutoff = deadline.toISOString().slice(0, 10)
const scoped = computed(() => visibleSuppliers(state, ctx))
const expiring = s => s.certificates.some(c => c.until >= today && c.until <= cutoff)
const expired = s => s.certificates.some(c => c.until < today)
const waiting = computed(() => scoped.value.filter(s => s.status === 'REVIEWING'))
const count = computed(() => ({ total: scoped.value.length, qualified: scoped.value.filter(s => s.status === 'QUALIFIED').length, pending: waiting.value.length, expiry: scoped.value.filter(expiring).length }))
const selected = computed(() => scoped.value.find(s => s.id === selectedId.value))
const rows = computed(() => scoped.value.filter(s => {
 if ((view.value === 'approvals' || filter.value === 'pending') && s.status !== 'REVIEWING') return false
 if (filter.value === 'expiry' && !expiring(s)) return false
 if (category.value !== 'all' && category.value !== s.category) return false
 return !query.value || (s.name + s.code).toLowerCase().includes(query.value.toLowerCase())
}))
const events = computed(() => state.events.filter(e => scoped.value.some(s => s.id === e.supplierId)).slice(0, 5))
const title = computed(() => ({ overview: '供应商工作台', suppliers: '供应商档案', approvals: '准入审批中心', portal: '我的供应商工作台', design: '基础设计交付' }[view.value]))
const myTask = computed(() => selected.value?.status === 'REVIEWING' && (selected.value.step === 'SQE' ? ctx.role === 'SQE' : ctx.role === 'CAT_MGR'))
watch(() => [ctx.orgId, ctx.role], () => { selectedId.value = null; query.value = ''; filter.value = 'all'; if (ctx.role === 'PORTAL_ADMIN') { ctx.orgId = 'east'; view.value = 'portal' } else if (view.value === 'portal') view.value = 'overview' })
let timer
function toast(text) { notice.value = text; clearTimeout(timer); timer = setTimeout(() => { notice.value = '' }, 4500) }
function act(fn, success) { try { fn(); toast(success) } catch (e) { toast(e.message) } }
function openSupplier(s) { selectedId.value = s.id; detailTab.value = 'profile'; reviewComment.value = '' }
function navigate(v) { view.value = v; filter.value = 'all'; query.value = '' }
async function openCreate() { form.name = ''; form.category = '精密加工'; form.address = ''; formError.value = ''; newModal.value = true; await nextTick(); nameInput.value?.focus() }
function saveSupplier() { try { const s = createSupplier(state, ctx, form); newModal.value = false; navigate('suppliers'); openSupplier(s); toast('供应商草稿已创建') } catch (e) { formError.value = e.message } }
function reset() { const x = seedState(); state.suppliers = x.suppliers; state.events = x.events; resetModal.value = false; selectedId.value = null; ctx.orgId = 'east'; ctx.role = 'SUP_ADMIN'; navigate('overview'); toast('已恢复初始演示数据') }
function keyboard(e) { if (e.key === 'Escape') { selectedId.value = null; newModal.value = false; resetModal.value = false } }
const categories = ['精密加工', '金属材料', '设备与服务', '电子元器件', '包装辅料']
const designItems = [
 ['01', '数据模型与迁移', '供应商、资质、联系人及审批关联 / 事件发件箱；迁移脚本与升级验证。', '数据设计'],
 ['02', '身份与权限边界', '组织范围、供应商边界、角色并集及停用账号策略；服务端策略内核测试。', '权限矩阵'],
 ['03', '接口与业务规则', 'API 契约、错误码、审批状态机、字段授权及待评审决策。', '接口契约'],
 ['04', '安全与集成基线', 'SM4 随机 IV、完整性校验与检索指纹；集成契约、CI 与测试基线。', '基础组件']
]
</script>

<template>
 <div class="app-shell" @keydown="keyboard">
  <aside class="sidebar">
   <a class="brand" href="#" @click.prevent="navigate('overview')"><span class="brand-mark"><Icon name="leaf" /></span><span>知源 <b>SRM</b><small>供应商协同管理平台</small></span></a>
   <div class="workspace-label">工作空间 <span>WORKSPACE</span></div>
   <nav aria-label="主导航">
    <button v-for="item in (ctx.role === 'PORTAL_ADMIN' ? [['portal','grid','我的工作台'], ['suppliers','people','企业档案']] : [['overview','grid','工作台总览'], ['suppliers','people','供应商档案'], ['approvals','check','准入审批']])" :key="item[0]" :class="['nav-item', { active: view === item[0] }]" @click="navigate(item[0])"><Icon :name="item[1]" /><span>{{ item[2] }}</span><span v-if="item[0] === 'approvals' && waiting.length" class="nav-count">{{ waiting.length }}</span></button>
    <div class="nav-divider"></div><button class="nav-item unavailable" disabled title="二期开发范围"><Icon name="chart" /><span>寻源中心</span><span class="soon">规划中</span></button><button class="nav-item unavailable" disabled title="二期开发范围"><Icon name="box" /><span>采购协同</span><span class="soon">规划中</span></button>
    <div class="workspace-label second-label">管理与设置</div><button :class="['nav-item', { active: view === 'design' }]" @click="navigate('design')"><Icon name="settings" /><span>基础设计交付</span></button>
   </nav>
   <div class="sidebar-bottom"><span class="status-dot"></span>交互演示环境<span class="env-tag">DEMO</span><p>浏览器内模拟 · 不连接业务后台</p><button @click="resetModal = true"><Icon name="logout" /> 重置演示数据</button></div>
  </aside>
  <div class="main-shell">
   <header class="topbar"><div class="breadcrumb">供应链管理 <span>/</span> <strong>{{ title }}</strong></div><div class="topbar-actions"><span class="org-icon">▥</span><select aria-label="演示组织" v-model="ctx.orgId" :disabled="ctx.role === 'PORTAL_ADMIN'"><option v-for="org in organizations" :value="org.id" :key="org.id">{{ org.name }}</option></select><span class="header-separator"></span><button class="icon-button notification" title="查看准入待办" @click="navigate(ctx.role === 'PORTAL_ADMIN' ? 'portal' : 'approvals')"><Icon name="bell" /><i v-if="waiting.length"></i></button><div class="avatar">{{ ctx.role === 'SQE' ? '质' : ctx.role === 'CAT_MGR' ? '审' : ctx.role === 'PORTAL_ADMIN' ? '供' : '林' }}</div><select aria-label="演示角色" v-model="ctx.role"><option v-for="(label, id) in roles" :value="id" :key="id">{{ label }}</option></select></div></header>
   <main>
    <div class="page-heading"><div><div class="eyebrow">SUPPLIER RELATIONSHIP MANAGEMENT</div><h1>{{ title }}<span class="demo-label">交互演示</span></h1><p>{{ view === 'approvals' ? '每一次准入都经过审核，让合作从信任开始。' : view === 'design' ? '从清晰的规则，到可验证的工程基础。' : '连接优质伙伴，让供应链协同更有秩序。' }}</p></div><div class="heading-actions"><span class="today"><Icon name="clock" />{{ today.replaceAll('-', '.') }}</span><button v-if="ctx.role === 'SUP_ADMIN' && view !== 'design'" class="btn primary" @click="openCreate"><Icon name="plus" />新建供应商</button></div></div>
    <div class="demo-strip"><span class="pulse"></span><strong>你正在体验 SRM 演示版</strong><span>数据均为虚构，可切换组织与角色体验流程；修改仅保存在当前浏览器。</span><button @click="toast('推荐路径：新建供应商 → 补齐演示资质 → 提交准入 → 切换质量工程师与采购经理审核')">操作指引 <Icon name="arrow" /></button></div>
    <template v-if="view !== 'design'">
     <section class="metrics" aria-label="供应商统计">
      <div class="metric"><div><span>供应商总数</span><strong>{{ count.total }}<small>家</small></strong><p><i class="tiny-dot green"></i>当前{{ ctx.role === 'PORTAL_ADMIN' ? '供应商' : '组织' }}可见档案</p></div><div class="metric-icon green-icon"><Icon name="people" /></div><div class="mini-bars"><i v-for="n in 10" :key="n" :style="{ height: (10 + (n * 13) % 29) + 'px' }"></i></div></div>
      <div class="metric"><div><span>合格供应商</span><strong>{{ count.qualified }}<small>家</small></strong><p><i class="tiny-dot green"></i>准入通过 · 可开展合作</p></div><div class="metric-icon blue-icon"><Icon name="check" /></div><div class="metric-ratio">{{ count.total ? Math.round(count.qualified / count.total * 100) : 0 }}<span>%</span><small>合格占比</small></div></div>
      <button class="metric metric-button" @click="navigate('approvals')"><div><span>待处理准入</span><strong>{{ count.pending }}<small>项</small></strong><p><i class="tiny-dot amber"></i>等待质量审核或经理审批</p></div><div class="metric-icon amber-icon"><Icon name="clock" /></div><Icon class="metric-arrow" name="arrow" /></button>
      <button class="metric metric-button" @click="navigate('suppliers'); filter = 'expiry'"><div><span>资质即将到期</span><strong>{{ count.expiry }}<small>家</small></strong><p><i class="tiny-dot coral"></i>未来 30 天内到期</p></div><div class="metric-icon coral-icon"><Icon name="file" /></div><Icon class="metric-arrow" name="arrow" /></button>
     </section>
     <div class="content-grid">
      <section class="panel supplier-panel">
       <div class="panel-heading"><div><h2>{{ view === 'approvals' ? '准入审核任务' : ctx.role === 'PORTAL_ADMIN' ? '我的企业档案' : '供应商概览' }}</h2><p>{{ view === 'approvals' ? '按当前节点校验处理角色' : '合作伙伴的每一步，都清晰可见' }}</p></div><span class="scope-chip"><span class="tiny-dot green"></span>{{ ctx.role === 'PORTAL_ADMIN' ? '仅当前供应商' : '本组织范围' }}</span></div>
       <div v-if="view !== 'approvals'" class="filter-tabs"><button :class="{ selected: filter === 'all' }" @click="filter = 'all'">全部供应商 <span>{{ count.total }}</span></button><button :class="{ selected: filter === 'pending' }" @click="filter = 'pending'">待准入 <span>{{ count.pending }}</span></button><button :class="{ selected: filter === 'expiry' }" @click="filter = 'expiry'">资质到期提醒 <span>{{ count.expiry }}</span></button></div>
       <div class="table-toolbar"><label class="search-field"><Icon name="search" /><input aria-label="搜索供应商" v-model="query" placeholder="搜索供应商名称或编码…" /></label><select aria-label="供应商品类" v-model="category"><option value="all">全部品类</option><option v-for="c in categories" :key="c">{{ c }}</option></select><span class="result-count">共 {{ rows.length }} 条</span></div>
       <div class="table-scroll"><table><thead><tr><th>供应商名称</th><th>品类 / 等级</th><th>准入状态</th><th>资质情况</th><th>操作</th></tr></thead><tbody><tr v-for="s in rows" :key="s.id"><td><button class="supplier-cell" @click="openSupplier(s)"><span class="supplier-avatar" :style="{ background: s.color }">{{ s.initials }}</span><span><strong>{{ s.name }}</strong><small>{{ s.code }}</small></span></button></td><td><div class="category-text">{{ s.category }}</div><span class="tier">{{ s.tier }}</span></td><td><span :class="['status', s.status.toLowerCase()]"><i></i>{{ statuses[s.status] }}</span><small v-if="s.status === 'REVIEWING'" class="step-note">{{ s.step === 'SQE' ? 'SQE 资质审核' : '采购经理审批' }}</small></td><td><span :class="['cert-status', { warning: expiring(s) || expired(s), muted: s.certificates.length < 2 }]">{{ expired(s) ? '存在过期' : expiring(s) ? '即将到期' : s.certificates.length < 2 ? '待补充' : '资料完备' }}</span></td><td><button class="text-btn" @click="openSupplier(s)">{{ view === 'approvals' ? '查看任务' : '查看档案' }}<Icon name="chevron" /></button></td></tr></tbody></table><div v-if="!rows.length" class="empty-state"><Icon name="search" /><h3>暂无符合条件的供应商</h3><p>试试其他名称、品类，或新建一份供应商档案。</p><button class="btn" @click="query = ''; category = 'all'; filter = 'all'">清除筛选</button></div></div>
       <div class="table-footer"><span>已显示 {{ rows.length }} 条 · 演示数据</span><div><button disabled>‹</button><button class="current-page">1</button><button disabled>›</button></div></div>
      </section>
      <aside class="right-column">
       <section class="flow-card"><span class="flow-kicker">ONBOARDING JOURNEY</span><h2>从了解，到信赖</h2><p>供应商准入的每一步都有迹可循</p><div class="journey"><div v-for="(step, i) in ['建立供应商档案', '提交资质与联系人', 'SQE 资质审核', '采购经理准入审批']" :key="step"><span :class="{ done: i < 2 }">{{ i < 2 ? '✓' : i + 1 }}</span><p>{{ step }}<small>{{ ['完整的基础信息', '有效资质 · 管理员联系人', '核验真实性与有效期', '审核通过后进入合格名录'][i] }}</small></p></div></div><button class="flow-link" @click="navigate(ctx.role === 'PORTAL_ADMIN' ? 'suppliers' : 'approvals')">{{ ctx.role === 'PORTAL_ADMIN' ? '查看企业档案' : '进入准入审批' }}<Icon name="arrow" /></button></section>
       <section class="panel activity-panel"><div class="activity-title"><h2>最近动态</h2><span>演示记录</span></div><div v-for="e in events" :key="e.id" class="activity"><span class="activity-dot"></span><div><strong>{{ e.action }}</strong><p>{{ e.name }}</p><small>{{ e.actor }} · {{ e.time }}</small></div></div><div v-if="!events.length" class="muted small-copy">暂无当前范围内的操作记录</div><div class="activity-foot">完整流程签名审计将在业务阶段接入</div></section>
      </aside>
     </div>
    </template>
    <section v-else class="design-grid"><article class="panel design-card" v-for="d in designItems" :key="d[0]"><span class="design-number">{{ d[0] }}</span><h2>{{ d[1] }}</h2><p>{{ d[2] }}</p><span class="scope-chip">{{ d[3] }}</span></article><div class="panel design-explainer"><Icon name="check" /><div><h2>可交互原型与真实业务能力分别验收</h2><p>当前页面在浏览器内模拟身份与审批，用于确认交互。真实服务端权限、加密和数据库迁移由独立组件及测试验证；生产密钥、业务决策评审与外部沙箱仍需要实际配置。</p><p>完整交付目录：docs/foundation/README.md</p></div></div></section>
    <footer class="page-footer"><span>知源 SRM <i>·</i> 让供应协同更简单</span><span>基础设计阶段 / W1–2 <span class="version-chip">DEMO v0.1</span></span></footer>
   </main>
  </div>
  <div v-if="notice" class="toast" role="status"><span>●</span>{{ notice }}</div>
  <template v-if="selected">
   <div class="backdrop" @click="selectedId = null"></div><section class="drawer" role="dialog" aria-modal="true" :aria-label="selected.name"><div class="drawer-top"><span class="eyebrow">SUPPLIER PROFILE</span><button class="icon-button" aria-label="关闭档案" @click="selectedId = null"><Icon name="close" /></button></div><div class="drawer-heading"><span class="supplier-avatar large" :style="{ background: selected.color }">{{ selected.initials }}</span><div><h2>{{ selected.name }}</h2><p>{{ selected.code }}</p></div></div><div class="drawer-badges"><span :class="['status', selected.status.toLowerCase()]"><i></i>{{ statuses[selected.status] }}</span><span class="scope-chip">{{ selected.category }} · {{ selected.tier }}</span></div><div class="detail-tabs"><button v-for="(label,id) in { profile: '基础资料', certificates: '资质证书', contacts: '联系人', history: '审批记录' }" :key="id" :class="{ selected: detailTab === id }" @click="detailTab = id">{{ label }}</button></div>
    <div class="drawer-content">
     <template v-if="detailTab === 'profile'"><h3 class="section-title">企业基本信息</h3><dl class="profile-grid"><div><dt>供应商名称</dt><dd>{{ selected.name }}</dd></div><div><dt>归属组织</dt><dd>{{ organizations.find(o => o.id === selected.orgId)?.name }}</dd></div><div><dt>供应商编码</dt><dd>{{ selected.code }}</dd></div><div><dt>合作等级</dt><dd>{{ selected.tier }}</dd></div><div><dt>信用代码（演示掩码）</dt><dd>{{ selected.credit }}</dd></div><div><dt>银行账号（演示掩码）</dt><dd>{{ selected.bank }}</dd></div><div class="full"><dt>注册地址</dt><dd>{{ selected.address || '暂未填写' }}</dd></div></dl><div class="info-note">演示不采集真实信用代码或银行账号。请勿在演示页面输入敏感信息。</div><div v-if="selected.status === 'POTENTIAL'" class="next-step"><h3>下一步：完善资料并发起准入</h3><p>需有效营业执照、ISO9001 与至少一位启用的管理员联系人。</p><button class="btn" @click="detailTab = 'certificates'">查看资质资料<Icon name="arrow" /></button></div></template>
     <template v-if="detailTab === 'certificates'"><h3 class="section-title">资质证书 <span>{{ selected.certificates.length }} 份</span></h3><article v-for="c in selected.certificates" :key="c.type" class="certificate"><span class="certificate-icon"><Icon name="file" /></span><div><strong>{{ c.type }}</strong><small>{{ c.number }}</small><p>{{ c.from }} — {{ c.until }}</p></div><span :class="['status', c.until < today ? 'frozen' : c.until <= cutoff ? 'reviewing' : 'qualified']">{{ c.until < today ? '已过期' : c.until <= cutoff ? '即将到期' : '有效' }}</span></article><div v-if="!selected.certificates.length" class="empty-state small">尚未录入资质，请补齐后提交准入。</div><button v-if="selected.status === 'POTENTIAL' && ['SUP_ADMIN','PORTAL_ADMIN'].includes(ctx.role)" class="btn" data-testid="complete-certs" @click="act(() => completeDemoCertificates(state, ctx, selected.id), '已补齐两份有效演示资质')"><Icon name="plus" />补齐演示资质</button><p class="muted small-copy">此操作仅模拟资质录入，不上传或保存真实文件。</p></template>
     <template v-if="detailTab === 'contacts'"><h3 class="section-title">联系人与门户角色</h3><article class="contact-card" v-for="c in selected.contacts" :key="c.name"><span class="avatar">{{ c.name.slice(0,1) }}</span><div><strong>{{ c.name }}</strong><p>{{ c.phone }}</p><small>{{ c.email }}</small></div><span class="scope-chip">{{ c.roles.includes('ADMIN') ? '管理员' : c.roles.join(' / ') }}</span></article><div class="info-note">一个供应商支持多个联系人与角色。正式账号停用及最后一名管理员保护在业务阶段实现。</div></template>
     <template v-if="detailTab === 'history'"><h3 class="section-title">本次演示操作记录</h3><article v-for="e in state.events.filter(x => x.supplierId === selected.id)" :key="e.id" class="history-item"><strong>{{ e.action }}<small>{{ e.time }}</small></strong><p>{{ e.detail }}</p><small>{{ e.actor }}</small></article><p v-if="!state.events.some(x => x.supplierId === selected.id)" class="muted">尚无本次演示记录。</p></template>
     <div v-if="selected.status === 'REVIEWING'" class="review-box"><span class="eyebrow">CURRENT TASK</span><h3>{{ selected.step === 'SQE' ? 'SQE 资质审核' : '采购经理准入审批' }}</h3><template v-if="myTask"><label for="review-comment">审核意见</label><textarea id="review-comment" v-model="reviewComment" placeholder="请填写审核意见…" rows="3" maxlength="512"></textarea><div class="review-actions"><button class="btn" @click="act(() => reviewOnboarding(state,ctx,selected.id,'RETURN',reviewComment),'已退回补正')">退回补正</button><button class="btn primary" data-testid="approve" @click="act(() => reviewOnboarding(state,ctx,selected.id,'APPROVE',reviewComment),'审核操作已完成')">{{ selected.step === 'SQE' ? '审核通过，转经理审批' : '审批通过，准入合格' }}</button></div></template><p v-else>请在右上角切换为{{ selected.step === 'SQE' ? '质量工程师' : '采购经理' }}视角处理。</p></div>
    </div>
    <div class="drawer-footer"><span>浏览器内流程模拟</span><button v-if="selected.status === 'POTENTIAL' && ['SUP_ADMIN','PORTAL_ADMIN'].includes(ctx.role)" class="btn primary" data-testid="submit-onboarding" @click="act(() => submitOnboarding(state,ctx,selected.id),'准入已提交，等待 SQE 审核')">提交准入申请<Icon name="arrow" /></button><button v-else class="btn" @click="selectedId = null">关闭档案</button></div>
   </section>
  </template>
  <div v-if="newModal" class="modal-backdrop" @click.self="newModal = false"><section class="modal" role="dialog" aria-modal="true" aria-labelledby="new-title"><div class="modal-heading"><div><span class="eyebrow">NEW PARTNER</span><h2 id="new-title">新建供应商档案</h2></div><button class="icon-button" aria-label="关闭新建" @click="newModal = false"><Icon name="close" /></button></div><p class="modal-copy">先建立潜在供应商档案，再完善资质并申请准入。</p><form @submit.prevent="saveSupplier"><label>供应商名称 <span>*</span><input ref="nameInput" aria-label="供应商名称" v-model="form.name" placeholder="请输入演示企业名称" required minlength="2" maxlength="128" /></label><label>物料品类 <span>*</span><select aria-label="新建品类" v-model="form.category"><option v-for="c in categories" :key="c">{{ c }}</option></select></label><label>归属组织<input :value="organizations.find(o => o.id === ctx.orgId)?.name" disabled /></label><label>注册地址<input aria-label="注册地址" v-model="form.address" maxlength="255" placeholder="选填，请使用虚构地址" /></label><div v-if="formError" class="form-error" role="alert">{{ formError }}</div><div class="info-note">仅创建本地演示记录，不会写入真实业务数据库。</div><div class="modal-actions"><button type="button" class="btn" @click="newModal = false">取消</button><button class="btn primary" type="submit">保存供应商草稿<Icon name="arrow" /></button></div></form></section></div>
  <div v-if="resetModal" class="modal-backdrop" @click.self="resetModal = false"><section class="modal small-modal" role="dialog" aria-modal="true" aria-label="重置演示数据"><h2>恢复初始演示数据？</h2><p class="modal-copy">你在此演示中新建的档案和审批操作会被清除。真实业务数据不受影响。</p><div class="modal-actions"><button class="btn" @click="resetModal = false">取消</button><button class="btn primary" @click="reset">确认重置</button></div></section></div>
 </div>
</template>
