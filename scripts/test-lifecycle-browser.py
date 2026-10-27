from pathlib import Path
import json,subprocess,os
from playwright.sync_api import sync_playwright,expect
root=Path(__file__).resolve().parents[1]
subprocess.run(['node','scripts/prepare-lifecycle-browser.mjs'],cwd=root,check=True,capture_output=True)
f=json.loads((root/'.runtime/w36-fixture.json').read_text());s=json.loads((root/'.runtime/lifecycle-browser-fixture.json').read_text());checks=[]
mgr=next(a for a in reversed(f['w79Actors']) if 'w79manager_' in a['account'])
with sync_playwright() as p:
 launch={'headless':True,'args':['--no-sandbox','--disable-dev-shm-usage']}
 executable=os.environ.get('SRM_CHROMIUM_PATH','/usr/bin/chromium')
 if Path(executable).exists():launch['executable_path']=executable
 browser=p.chromium.launch(**launch)
 page=browser.new_page(viewport={'width':1500,'height':1000});errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
 page.goto('http://127.0.0.1:5175/')
 def login(actor):
  page.get_by_label('登录账号',exact=True).fill(actor['account']);page.get_by_label('登录密码',exact=True).fill(actor['password']);page.get_by_role('button',name='登录工作台 →',exact=True).click();expect(page.get_by_role('heading',name='供应商档案',exact=True)).to_be_visible(timeout=20000)
 def logout():
  if page.get_by_label('关闭表单',exact=True).count():page.get_by_label('关闭表单',exact=True).last.click()
  if page.get_by_label('关闭档案',exact=True).count():page.get_by_label('关闭档案',exact=True).click()
  page.get_by_role('button',name='退出登录',exact=True).first.click();expect(page.get_by_label('登录账号',exact=True)).to_be_visible()
 def open_supplier():
  page.get_by_label('搜索供应商',exact=True).fill(s['name']);page.get_by_role('button',name='查询',exact=True).click();row=page.locator('tbody tr').filter(has_text=s['name']);expect(row).to_have_count(1);row.get_by_role('button',name='查看档案 ›',exact=True).click();page.get_by_role('button',name='准入与审批',exact=True).click()
 def review(action,comment):
  page.get_by_role('button',name=action,exact=True).click();page.get_by_label('审批意见',exact=True).fill(comment);page.get_by_role('button',name='确认'+action,exact=True).click();expect(page.get_by_role('dialog',name=action,exact=True)).to_have_count(0)
 login(f['a']);open_supplier();page.get_by_role('button',name='提交准入',exact=True).click();page.get_by_label('申请原因',exact=True).fill('真实浏览器准入验收');page.get_by_role('button',name='确认提交',exact=True).click();expect(page.get_by_role('dialog',name='准入申请 · 待审核',exact=True)).to_be_visible();checks.append('submit_real_onboarding_from_profile')
 expect(page.get_by_role('button',name='同意',exact=True)).to_have_count(0);checks.append('requester_has_no_self_approval_action');logout()
 login(f['reader']);page.get_by_role('button',name='✓　审批工作台',exact=True).click();row=page.locator('tbody tr').filter(has_text=s['name']).first;row.get_by_role('button',name='查看并处理',exact=True).click();expect(page.get_by_role('button',name='退回补正',exact=True)).to_be_visible();checks.append('sqe_inbox_and_actions_visible')
 page.get_by_role('button',name='查看供应商档案与资质',exact=True).click();expect(page.get_by_label('关闭档案',exact=True)).to_be_visible();page.get_by_role('button',name='资质证书',exact=True).click();expect(page.locator('.drawer-content')).to_contain_text('ISO9001');checks.append('sqe_reviews_real_certificates_before_decision');page.get_by_role('button',name='准入与审批',exact=True).click();page.get_by_role('button',name='准入申请 · 待审核',exact=True).click()
 page.screenshot(path=str(root/'workspace/真实SQE审批预览.png'),full_page=True)
 review('退回补正','请补充材料说明后重提');expect(page.get_by_role('dialog',name='准入申请 · 已退回补正',exact=True)).to_be_visible();checks.append('return_for_correction_ui');logout()
 login(f['a']);open_supplier();expect(page.locator('.drawer-badges')).to_contain_text('补正中');page.get_by_role('button',name='提交准入',exact=True).click();page.get_by_role('button',name='确认提交',exact=True).click();expect(page.get_by_role('dialog',name='准入申请 · 待审核',exact=True)).to_be_visible();checks.append('correction_resubmission_ui');logout()
 login(f['reader']);page.get_by_role('button',name='✓　审批工作台',exact=True).click();page.locator('tbody tr').filter(has_text=s['name']).first.get_by_role('button',name='查看并处理',exact=True).click();review('同意','资质有效，提交采购经理审批');checks.append('sqe_approval_routes_to_manager');logout()
 login(mgr);page.get_by_role('button',name='✓　审批工作台',exact=True).click();row=page.locator('tbody tr').filter(has_text=s['name']).first;row.get_by_role('button',name='查看并处理',exact=True).click();review('同意','确认准入');expect(page.get_by_role('dialog',name='准入申请 · 已通过',exact=True)).to_be_visible();checks.append('manager_approval_completes_real_workflow');logout()
 login(f['a']);open_supplier();expect(page.locator('.drawer-badges')).to_contain_text('合格供应商');expect(page.get_by_role('button',name='申请关键资料变更',exact=True)).to_be_visible();checks.append('qualified_status_persisted_after_login')
 page.screenshot(path=str(root/'workspace/真实准入完成预览.png'),full_page=True)
 page.get_by_role('button',name='申请关键资料变更',exact=True).click();page.get_by_label('变更银行账号',exact=True).fill('6222999988887777');page.get_by_label('申请原因',exact=True).fill('银行账号变更验收');page.get_by_role('button',name='确认提交',exact=True).click();expect(page.get_by_role('dialog',name='关键资料变更 · 待审核',exact=True)).to_be_visible();checks.append('critical_change_request_ui');logout()
 login(mgr);page.get_by_role('button',name='✓　审批工作台',exact=True).click();page.locator('tbody tr').filter(has_text=s['name']).first.get_by_role('button',name='查看并处理',exact=True).click();review('同意','银行变更核实通过');expect(page.get_by_role('dialog',name='关键资料变更 · 已通过',exact=True)).to_be_visible();checks.append('critical_change_approved_ui');logout()
 login(f['a']);open_supplier();page.get_by_role('button',name='基础资料',exact=True).click();expect(page.locator('.drawer-content')).to_contain_text('7777');checks.append('approved_change_persisted_in_profile')
 assert not errors,errors
 browser.close()
(root/'docs/lifecycle/browser-verification.json').write_text(json.dumps({'result':'passed','checks':checks,'runtimeErrors':errors},ensure_ascii=False,indent=2)+'\n')
print(f'PASS: {len(checks)} lifecycle browser checks, zero runtime errors.')
