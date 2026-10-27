"""Browser smoke for the exported, offline HTML. Requires Playwright and Chromium."""
from pathlib import Path
import json, os, threading, functools
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from urllib.parse import quote
from playwright.sync_api import sync_playwright, expect

root = Path(__file__).resolve().parents[1]
class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, *args): pass
server = ThreadingHTTPServer(('127.0.0.1', 0), functools.partial(QuietHandler, directory=str(root / 'demo')))
threading.Thread(target=server.serve_forever, daemon=True).start()
origin = f'http://127.0.0.1:{server.server_port}'
with sync_playwright() as p:
    executable = os.environ.get('SRM_CHROMIUM_PATH', '/usr/bin/chromium')
    launch = {'headless': True, 'args': ['--no-sandbox', '--disable-dev-shm-usage']}
    if Path(executable).exists(): launch['executable_path'] = executable
    browser = p.chromium.launch(**launch)
    page = browser.new_page(viewport={'width': 1600, 'height': 1040}, device_scale_factor=1)
    errors, requests = [], []
    page.on('pageerror', lambda error: errors.append(str(error)))
    def no_network(route):
        if route.request.is_navigation_request() and route.request.url.startswith(origin + '/'):
            route.continue_()
        elif route.request.url == origin + '/favicon.ico':
            route.fulfill(status=204, body='')
        else:
            requests.append(route.request.url)
            route.abort()
    page.route('http://**/*', no_network)
    page.route('https://**/*', no_network)
    page.goto(origin + '/' + quote('SRM演示.html'))
    page.evaluate('localStorage.clear()')
    page.reload()
    expect(page.get_by_role('heading', name='供应商工作台')).to_be_visible()
    expect(page.locator('tbody tr')).to_have_count(6)
    page.screenshot(path=str(root / 'demo/工作台预览.png'), full_page=True)
    page.get_by_role('textbox', name='搜索供应商').fill('华辰')
    expect(page.locator('tbody tr')).to_have_count(1)
    page.get_by_role('textbox', name='搜索供应商').fill('')
    page.get_by_label('演示组织').select_option('south')
    expect(page.locator('tbody tr')).to_have_count(2)
    expect(page.locator('tbody')).not_to_contain_text('华辰')
    page.get_by_label('演示组织').select_option('east')
    page.get_by_role('button', name='新建供应商', exact=True).click()
    page.get_by_role('textbox', name='供应商名称', exact=True).fill('云启演示精密制造有限公司')
    page.get_by_role('textbox', name='注册地址').fill('演示园区 1 号')
    page.get_by_role('button', name='保存供应商草稿').click()
    expect(page.get_by_role('dialog', name='云启演示精密制造有限公司')).to_be_visible()
    page.get_by_test_id('submit-onboarding').click()
    expect(page.get_by_role('status')).to_contain_text('请补齐有效资质')
    page.get_by_role('button', name='资质证书', exact=True).click()
    page.get_by_test_id('complete-certs').click()
    page.get_by_test_id('submit-onboarding').click()
    expect(page.get_by_role('dialog')).to_contain_text('SQE 资质审核')
    page.get_by_label('关闭档案').click()
    page.get_by_label('演示角色').select_option('SQE')
    row = page.locator('tbody tr').filter(has_text='云启演示精密制造有限公司')
    row.get_by_role('button', name='查看档案').click()
    page.get_by_label('审核意见').fill('演示资质齐全，审核通过')
    page.get_by_test_id('approve').click()
    expect(page.get_by_role('dialog')).to_contain_text('采购经理准入审批')
    expect(page.get_by_test_id('approve')).to_have_count(0)
    page.get_by_label('关闭档案').click()
    page.get_by_label('演示角色').select_option('CAT_MGR')
    row.get_by_role('button', name='查看档案').click()
    page.get_by_label('审核意见').fill('同意纳入合格供应商名录')
    page.get_by_test_id('approve').click()
    expect(page.locator('.drawer-badges .status')).to_have_text('合格供应商')
    page.get_by_role('button', name='审批记录', exact=True).click()
    expect(page.locator('.drawer-content')).to_contain_text('准入审批通过')
    page.screenshot(path=str(root / 'demo/准入审批预览.png'), full_page=True)
    page.get_by_label('关闭档案').click()
    page.reload()
    expect(page.locator('tbody')).to_contain_text('云启演示精密制造有限公司')
    page.get_by_label('演示角色').select_option('PORTAL_ADMIN')
    expect(page.locator('tbody tr')).to_have_count(1)
    expect(page.locator('tbody')).to_contain_text('华辰')
    expect(page.get_by_label('演示组织')).to_be_disabled()
    expect(page.get_by_role('button', name='新建供应商', exact=True)).to_have_count(0)
    page.get_by_label('演示角色').select_option('SUP_ADMIN')
    page.get_by_role('button', name='重置演示数据').click()
    page.get_by_role('button', name='确认重置').click()
    expect(page.locator('tbody tr')).to_have_count(6)
    page.set_viewport_size({'width': 390, 'height': 844})
    expect(page.get_by_role('heading', name='供应商工作台')).to_be_visible()
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), 'Page overflows mobile viewport'
    page.screenshot(path=str(root / 'demo/移动视图预览.png'), full_page=True)
    assert not errors, errors
    assert not requests, requests
    (root / 'demo/browser-validation.json').write_text(json.dumps({
        'result': 'passed', 'checks': ['offline_no_requests', 'search', 'organization_scope', 'create_draft', 'certificate_gate', 'sqe_then_manager', 'role_gate', 'history', 'reload_persistence', 'portal_scope', 'reset', 'mobile_no_page_overflow'],
        'console_errors': errors, 'external_requests': requests
    }, ensure_ascii=False, indent=2) + '\n')
    print('PASS: 12 browser checks; no runtime errors or network requests.')
    browser.close()
server.shutdown()
