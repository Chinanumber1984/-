from pathlib import Path
import json,subprocess,os
from playwright.sync_api import sync_playwright,expect
root=Path(__file__).resolve().parents[1]
f=json.loads((root/'.runtime/w36-fixture.json').read_text())
import time
browser_suffix=str(int(time.time()))[-5:]
report=[]
with sync_playwright() as p:
 launch={'headless':True,'args':['--no-sandbox','--disable-dev-shm-usage']}
 executable=os.environ.get('SRM_CHROMIUM_PATH','/usr/bin/chromium')
 if Path(executable).exists():launch['executable_path']=executable
 browser=p.chromium.launch(**launch)
 page=browser.new_page(viewport={'width':1500,'height':1000})
 errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
 page.goto('http://127.0.0.1:5175/')
 def login(actor):
  page.get_by_role('textbox',name='登录账号',exact=True).fill(actor['account'])
  page.get_by_role('textbox',name='登录密码',exact=True).fill(actor['password'])
  page.get_by_role('button',name='登录工作台 →',exact=True).click()
  expect(page.get_by_role('heading',name='供应商档案',exact=True)).to_be_visible(timeout=20000)
 login(f['a']);expect(page.locator('tbody')).to_contain_text('W36真实档案测试有限公司');report.append('real_login_and_data')
 page.screenshot(path=str(root/'workspace/真实工作台预览.png'),full_page=True)
 page.get_by_role('button',name='＋ 新建供应商',exact=True).click()
 page.get_by_role('textbox',name='供应商名称',exact=True).fill('W36浏览器持久化有限公司'+browser_suffix)
 cc=subprocess.check_output(['node','--input-type=module','-e',"import{credit}from'./scripts/test-workspace-api.mjs';process.stdout.write(credit('91110000ABCDE'+String(Date.now()).slice(-4)))"],cwd=root,text=True)
 page.get_by_role('textbox',name='信用代码',exact=True).fill(cc)
 page.get_by_role('textbox',name='银行账号',exact=True).fill('6222999912345678')
 page.get_by_role('button',name='保存到服务器',exact=True).click()
 expect(page.get_by_role('dialog',name='W36浏览器持久化有限公司'+browser_suffix,exact=True)).to_be_visible(timeout=15000)
 expect(page.locator('.drawer-content')).not_to_contain_text('6222999912345678');report.append('create_and_field_masking')
 page.get_by_label('关闭档案',exact=True).click();page.reload()
 expect(page.locator('tbody')).to_contain_text('W36浏览器持久化有限公司'+browser_suffix,timeout=15000);report.append('refresh_persistence')
 row=page.locator('tbody tr').filter(has_text='W36浏览器持久化有限公司'+browser_suffix);row.get_by_role('button',name='查看档案 ›',exact=True).click()
 page.get_by_role('button',name='编辑档案',exact=True).click();page.get_by_role('textbox',name='注册地址',exact=True).fill('浏览器验证地址')
 page.get_by_role('button',name='保存到服务器',exact=True).click();expect(page.locator('.drawer-content')).to_contain_text('浏览器验证地址');report.append('edit_real_record')
 page.get_by_role('button',name='资质证书',exact=True).click();page.get_by_role('button',name='＋ 录入资质',exact=True).click()
 page.get_by_role('textbox',name='证书编号',exact=True).fill('BROWSER-CERT-01')
 page.get_by_label('生效日期',exact=True).fill('2026-01-01');page.get_by_label('到期日期',exact=True).fill('2027-12-31')
 page.get_by_label('资质附件',exact=True).set_input_files({'name':'browser-test.pdf','mimeType':'application/pdf','buffer':b'%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF'})
 page.get_by_role('button',name='保存到服务器',exact=True).click()
 expect(page.locator('.drawer-content')).to_contain_text('BROWSER-CERT-01');report.append('certificate_upload_to_private_storage')
 page.get_by_role('button',name='预览附件',exact=True).click();expect(page.get_by_role('dialog',name='资质附件预览',exact=True)).to_be_visible();page.get_by_label('关闭表单',exact=True).click();report.append('authorized_certificate_preview')
 page.get_by_role('button',name='续期',exact=True).click();page.get_by_role('textbox',name='证书编号',exact=True).fill('BROWSER-CERT-02')
 page.get_by_label('到期日期',exact=True).fill('2028-12-31')
 page.get_by_label('资质附件',exact=True).set_input_files({'name':'browser-renewal.pdf','mimeType':'application/pdf','buffer':b'%PDF-1.4\n% renewal\n%%EOF'})
 page.get_by_role('button',name='保存到服务器',exact=True).click()
 expect(page.locator('.drawer-content')).to_contain_text('BROWSER-CERT-02');expect(page.locator('.drawer-content')).to_contain_text('已被续期');report.append('certificate_renewal_history_ui')
 page.get_by_role('button',name='联系人',exact=True).click();page.get_by_role('button',name='＋ 新增联系人',exact=True).click()
 page.get_by_role('textbox',name='联系人姓名',exact=True).fill('浏览器测试联系人');page.get_by_role('textbox',name='联系人手机号',exact=True).fill('13700137000')
 page.get_by_role('textbox',name='联系人邮箱',exact=True).fill('browser@example.invalid')
 page.get_by_role('button',name='保存到服务器',exact=True).click();expect(page.locator('.drawer-content')).to_contain_text('浏览器测试联系人');report.append('contact_real_record')
 page.screenshot(path=str(root/'workspace/真实联系人预览.png'),full_page=True)
 page.get_by_label('关闭档案',exact=True).click()
 page.get_by_role('textbox',name='搜索供应商',exact=True).fill('W36浏览器持久化有限公司'+browser_suffix)
 page.get_by_role('button',name='查询',exact=True).click();expect(page.locator('tbody tr')).to_have_count(1);report.append('server_search')
 page.get_by_role('button',name='退出登录',exact=True).first.click()
 login(f['b']);expect(page.locator('tbody')).not_to_contain_text('W36浏览器');expect(page.locator('tbody')).to_contain_text('W36另一组织');report.append('other_organization_isolated')
 page.get_by_role('button',name='退出登录',exact=True).first.click()
 login(f['reader']);expect(page.get_by_role('button',name='＋ 新建供应商',exact=True)).to_have_count(0);report.append('read_only_ui')
 page.get_by_role('button',name='退出登录',exact=True).first.click()
 # Portal retains the real captcha switch. Read only the freshly issued test captcha from the local test Redis.
 last_captcha={}
 def observe(response):
  if '/auth/c/getPicCaptcha' in response.url:
   try:last_captcha.update(response.json()['data'])
   except:pass
 page.on('response',observe)
 page.get_by_role('button',name='供应商门户',exact=True).click()
 expect(page.get_by_role('textbox',name='图片验证码',exact=True)).to_be_visible(timeout=15000)
 page.wait_for_function("document.querySelector('.captcha-row img')?.src?.startsWith('data:')")
 request_id=last_captcha['validCodeReqNo']
 assert request_id.isdigit()
 raw=subprocess.check_output(['docker','compose','--env-file','.runtime/local.env','-f','deploy/compose.yml','exec','-T','redis','redis-cli','-n','1','--raw','GET','Cache:auth-validCode:'+request_id],cwd=root,text=True).strip()
 code=json.loads(raw)
 page.get_by_role('textbox',name='图片验证码',exact=True).fill(str(code))
 login(f['portal']);expect(page.locator('tbody tr')).to_have_count(1);expect(page.locator('tbody')).to_contain_text('W36真实档案');report.append('real_portal_login')
 expect(page.get_by_role('button',name='＋ 新建供应商',exact=True)).to_have_count(0)
 page.set_viewport_size({'width':390,'height':844})
 assert page.evaluate('document.documentElement.scrollWidth<=innerWidth'), 'Mobile page overflow'
 report.append('mobile_layout');assert not errors,errors
 browser.close()
(root/'docs/workspace/browser-verification.json').write_text(json.dumps({'result':'passed','checks':report,'runtime_errors':errors,'data_source':'real_http_and_mysql_test_fixtures'},ensure_ascii=False,indent=2)+'\n')
print('PASS:',len(report),'real browser checks, zero runtime errors.')
