"""Verify the actual downloadable bundle, its public entry point, login and workflow."""
from pathlib import Path
import json,time,http.client
from playwright.sync_api import sync_playwright,expect
root=Path(__file__).resolve().parents[1];bundle=root/'artifacts/SRM-local-review'
accounts={a['account']:a for a in json.loads((bundle/'runtime/accounts.json').read_text())};checks=[]
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
 page=browser.new_page(viewport={'width':1500,'height':1000});errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
 page.goto('http://localhost:18080/');expect(page.get_by_label('登录账号',exact=True)).to_be_visible(timeout=15000)
 checks.append('downloadable_bundle_entry_page_loads')
 for path in ['/runtime/accounts.json','/backend.jar','/scripts/serve-review.mjs','/%2e%2e/runtime/accounts.json']:
  conn=http.client.HTTPConnection('localhost',18080);conn.request('GET',path);r=conn.getresponse();assert r.status==404,(path,r.status);r.read();conn.close()
 checks.append('private_files_and_source_are_not_served')
 conn=http.client.HTTPConnection('localhost',18080);conn.request('GET','/',headers={'Host':'untrusted.invalid'});r=conn.getresponse();assert r.status==403;r.read();conn.close();checks.append('untrusted_host_rejected')
 r=page.request.get('http://localhost:18080/api/srm/v1/suppliers');assert r.json()['code']==401;checks.append('anonymous_business_access_denied')
 def login(name):
  a=accounts[name];page.get_by_label('登录账号',exact=True).fill(a['account']);page.get_by_label('登录密码',exact=True).fill(a['password']);page.get_by_role('button',name='登录工作台 →',exact=True).click();expect(page.get_by_role('heading',name='供应商档案',exact=True)).to_be_visible(timeout=20000)
 def logout():
  if page.get_by_label('关闭表单',exact=True).count():page.get_by_label('关闭表单',exact=True).last.click()
  if page.get_by_label('关闭档案',exact=True).count():page.get_by_label('关闭档案',exact=True).click()
  page.get_by_role('button',name='退出登录',exact=True).first.click();expect(page.get_by_label('登录账号',exact=True)).to_be_visible()
 name='本地验收包验证-'+str(int(time.time()))
 def supplier():
  page.get_by_label('搜索供应商',exact=True).fill(name);page.get_by_role('button',name='查询',exact=True).click();page.locator('tbody tr').filter(has_text=name).get_by_role('button',name='查看档案 ›',exact=True).click()
 login('srm.owner');checks.append('generated_owner_account_logs_in_via_gateway')
 assert page.get_by_label('品类筛选',exact=True).locator('option[value=MACHINING]').inner_text()=='精密加工';checks.append('fresh_database_chinese_category_encoding')
 page.get_by_role('button',name='＋ 新建供应商',exact=True).click();page.get_by_label('供应商名称',exact=True).fill(name)
 chars='0123456789ABCDEFGHJKLMNPQRTUWXY';weights=[1,3,9,27,19,26,16,17,20,29,25,13,8,24,10,30,28];prefix='9111000'+str(int(time.time()))[-10:];credit=prefix+chars[(31-sum(chars.index(c)*weights[i] for i,c in enumerate(prefix))%31)%31]
 page.get_by_label('信用代码',exact=True).fill(credit);page.get_by_role('button',name='保存到服务器',exact=True).click();expect(page.get_by_label('关闭档案',exact=True)).to_be_visible();checks.append('supplier_persists_to_packaged_mysql')
 page.get_by_role('button',name='联系人',exact=True).click();page.get_by_role('button',name='＋ 新增联系人',exact=True).click();page.get_by_label('联系人姓名',exact=True).fill('本机测试管理员');page.get_by_label('联系人手机号',exact=True).fill('13800138000');page.get_by_role('button',name='保存到服务器',exact=True).click();expect(page.locator('.drawer-content')).to_contain_text('本机测试管理员')
 page.get_by_role('button',name='资质证书',exact=True).click()
 for kind in ['BUSINESS_LICENSE','ISO9001']:
  page.get_by_role('button',name='＋ 录入资质',exact=True).click();page.get_by_label('资质类型',exact=True).select_option(kind);page.get_by_label('证书编号',exact=True).fill('LOCAL-'+kind);page.get_by_label('生效日期',exact=True).fill('2026-01-01');page.get_by_label('到期日期',exact=True).fill('2030-01-01');page.get_by_label('资质附件',exact=True).set_input_files({'name':'local-review.pdf','mimeType':'application/pdf','buffer':b'%PDF-1.4\n%%EOF'});page.get_by_role('button',name='保存到服务器',exact=True).click();expect(page.locator('.drawer-content')).to_contain_text('LOCAL-'+kind)
 checks.append('real_upload_to_private_local_volume')
 page.get_by_role('button',name='准入与审批',exact=True).click();page.get_by_role('button',name='提交准入',exact=True).click();page.get_by_role('button',name='确认提交',exact=True).click();expect(page.get_by_role('dialog',name='准入申请 · 待审核',exact=True)).to_be_visible();checks.append('flowable_submission_from_packaged_frontend');logout()
 for account in ['srm.sqe','srm.manager']:
  login(account);page.get_by_role('button',name='✓　审批工作台',exact=True).click();page.locator('tbody tr').filter(has_text=name).get_by_role('button',name='查看并处理',exact=True).click();page.get_by_role('button',name='同意',exact=True).click();page.get_by_label('审批意见',exact=True).fill('本地验收包真实流程验证通过');page.get_by_role('button',name='确认同意',exact=True).click();expect(page.get_by_role('dialog',name='同意',exact=True)).to_have_count(0);checks.append(account+'_completes_real_task');logout()
 login('srm.owner');supplier();expect(page.locator('.drawer-badges')).to_contain_text('合格供应商');checks.append('approved_status_visible_after_account_switch')
 page.screenshot(path=str(root/'workspace/本地验收包实测.png'),full_page=True);page.reload();expect(page.get_by_role('heading',name='供应商档案',exact=True)).to_be_visible();supplier();expect(page.locator('.drawer-badges')).to_contain_text('合格供应商');checks.append('database_state_survives_page_reload')
 assert not errors,errors
 browser.close()
(root/'docs/review').mkdir(exist_ok=True)
(root/'docs/review/verification.json').write_text(json.dumps({'result':'passed','testedEntry':'user-computer localhost:18080 via the actual Docker bundle','environment':'Linux Docker containers and Chromium; Windows/macOS launchers not executed here','checks':checks,'runtimeErrors':errors},ensure_ascii=False,indent=2)+'\n')
print(f'PASS: {len(checks)} packaged-entry checks; real login, private upload, two-level approval, persistence; zero browser runtime errors.')
