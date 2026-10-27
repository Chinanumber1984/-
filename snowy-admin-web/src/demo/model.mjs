export const roles = { SUP_ADMIN: '主数据管理员', SQE: '质量工程师', CAT_MGR: '采购经理', PORTAL_ADMIN: '供应商管理员' }
export const statuses = { POTENTIAL: '潜在供应商', REVIEWING: '准入审核中', QUALIFIED: '合格供应商', FROZEN: '已冻结' }
export const organizations = [{ id: 'east', name: '华东制造事业部' }, { id: 'south', name: '华南制造事业部' }]
export function businessToday() { return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date()) }
function day(offset) { const d = new Date(businessToday() + 'T12:00:00+08:00'); d.setUTCDate(d.getUTCDate() + offset); return d.toISOString().slice(0, 10) }
const cert = (type, end = 365) => ({ type, number: `DEMO-${type}-2026`, from: day(-90), until: day(end) })
export function seedState() {
  const specs = [
    ['s1', 'east', '华辰精密制造有限公司', '精密加工', 'QUALIFIED', '优选', 92.4, 'HC', '#eaf1fe'],
    ['s2', 'east', '瑞丰新材料科技有限公司', '金属材料', 'REVIEWING', '临时', null, 'RF', '#fff2e2'],
    ['s3', 'east', '远山工业自动化有限公司', '设备与服务', 'QUALIFIED', '战略', 96.1, 'YS', '#eeeaff'],
    ['s4', 'east', '蓝川电子科技有限公司', '电子元器件', 'POTENTIAL', '临时', null, 'LC', '#e5f3f1'],
    ['s5', 'east', '恒达包装制品有限公司', '包装辅料', 'QUALIFIED', '合格', 86.7, 'HD', '#fff0f1'],
    ['s6', 'east', '森禾环保技术有限公司', '设备与服务', 'FROZEN', '观察', 71.2, 'SH', '#edf0f4'],
    ['s7', 'south', '南粤先进材料有限公司', '金属材料', 'QUALIFIED', '优选', 91.0, 'NY', '#fff2e2'],
    ['s8', 'south', '岭南精工有限公司', '精密加工', 'REVIEWING', '临时', null, 'LN', '#eeeaff']
  ]
  return { version: 1, suppliers: specs.map(([id, orgId, name, category, status, tier, score, initials, color], i) => ({
    id, orgId, name, category, status, tier, score, initials, color, code: `SUP2026${String(i + 1).padStart(4, '0')}`,
    address: orgId === 'east' ? '江苏省苏州市工业园区 · 演示地址' : '广东省佛山市 · 演示地址',
    credit: 'DEMO**********001', bank: 'DEMO********6789', updated: day(-i), step: status === 'REVIEWING' ? 'SQE' : null,
    certificates: id === 's4' ? [cert('营业执照')] : [cert('营业执照'), cert('ISO9001', id === 's5' ? 19 : id === 's6' ? -3 : 365)],
    contacts: [{ name: '林经理', phone: '138****0628', email: 'demo@example.invalid', roles: ['ADMIN'], enabled: true }]
  })), events: [{ id: 'seed', orgId: 'east', supplierId: 's2', name: '瑞丰新材料科技有限公司', action: '提交准入申请', actor: '演示供应商', time: '09:42', detail: '已进入 SQE 资质审核' }] }
}
export function visibleSuppliers(state, ctx) {
  return state.suppliers.filter(s => s.orgId === ctx.orgId && (ctx.role !== 'PORTAL_ADMIN' || s.id === ctx.supplierId))
}
function getScoped(state, ctx, id) { const s = visibleSuppliers(state, ctx).find(x => x.id === id); if (!s) throw new Error('当前演示视角无权访问该供应商'); return s }
function log(state, ctx, s, action, detail = '') {
  state.events.unshift({ id: crypto.randomUUID(), orgId: s.orgId, supplierId: s.id, name: s.name, action, detail, actor: roles[ctx.role], time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }) })
  s.updated = businessToday()
}
export function createSupplier(state, ctx, input) {
  if (ctx.role !== 'SUP_ADMIN') throw new Error('仅主数据管理员可新建供应商')
  if (!organizations.some(o => o.id === ctx.orgId)) throw new Error('请选择有效组织')
  const name = input.name.trim()
  if (name.length < 2 || name.length > 128) throw new Error('名称应为 2–128 个字符')
  if (state.suppliers.some(s => s.orgId === ctx.orgId && s.name === name)) throw new Error('当前组织已存在同名演示供应商')
  if (!['精密加工', '金属材料', '设备与服务', '电子元器件', '包装辅料'].includes(input.category)) throw new Error('请选择品类')
  const s = { id: crypto.randomUUID(), orgId: ctx.orgId, name, category: input.category, status: 'POTENTIAL', tier: '临时', score: null,
    initials: 'NEW', color: '#e5f3f1', code: `DEMO-${Date.now()}`, address: input.address.trim(), credit: '演示未采集', bank: '演示未采集',
    updated: businessToday(), step: null, certificates: [], contacts: [{ name: '演示管理员', phone: '未采集', email: '', roles: ['ADMIN'], enabled: true }] }
  state.suppliers.unshift(s); log(state, ctx, s, '新建供应商', '保存为潜在供应商'); return s
}
export function completeDemoCertificates(state, ctx, id) {
  const s = getScoped(state, ctx, id)
  if (!['SUP_ADMIN', 'PORTAL_ADMIN'].includes(ctx.role) || s.status !== 'POTENTIAL') throw new Error('仅资料维护角色可为潜在供应商补齐资质')
  s.certificates = [cert('营业执照'), cert('ISO9001')]; log(state, ctx, s, '补齐演示资质', '模拟录入两份有效资质，不上传真实附件')
}
export function submitOnboarding(state, ctx, id) {
  const s = getScoped(state, ctx, id)
  if (!['SUP_ADMIN', 'PORTAL_ADMIN'].includes(ctx.role)) throw new Error('当前角色不能提交准入')
  if (s.status !== 'POTENTIAL') throw new Error('仅潜在供应商可提交准入')
  const missing = ['营业执照', 'ISO9001'].filter(type => !s.certificates.some(c => c.type === type && c.from <= businessToday() && c.until >= businessToday()))
  if (missing.length) throw new Error(`请补齐有效资质：${missing.join('、')}`)
  if (!s.contacts.some(c => c.enabled && c.roles.includes('ADMIN'))) throw new Error('至少需要一位启用的供应商管理员')
  s.status = 'REVIEWING'; s.step = 'SQE'; log(state, ctx, s, '提交准入申请', '进入 SQE 审核节点')
}
export function reviewOnboarding(state, ctx, id, action, comment) {
  const s = getScoped(state, ctx, id)
  if (s.status !== 'REVIEWING' || !['SQE', 'CAT_MGR'].includes(s.step) || !['APPROVE', 'RETURN'].includes(action)) throw new Error('当前无可处理的审核任务')
  const expected = s.step === 'SQE' ? 'SQE' : 'CAT_MGR'
  if (ctx.role !== expected) throw new Error(`该任务需要${roles[expected]}处理`)
  if (!comment || !comment.trim()) throw new Error('请填写审核意见')
  if (action === 'RETURN') { s.status = 'POTENTIAL'; s.step = null; log(state, ctx, s, '退回补正', comment.trim()); return }
  if (s.step === 'SQE') { s.step = 'CAT_MGR'; log(state, ctx, s, '资质审核通过', comment.trim()) }
  else { s.status = 'QUALIFIED'; s.step = null; s.tier = '合格'; log(state, ctx, s, '准入审批通过', comment.trim()) }
}
