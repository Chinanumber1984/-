"""Build an exact-ID P8 sign-off matrix without changing the backlog or UAT books."""
from pathlib import Path
import csv,collections,datetime,sys

root=Path(__file__).resolve().parents[1]
out=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else root/'docs/pilot'
csv_path=out/'P8-114-acceptance-matrix.csv';md_path=out/'P8-114-acceptance-matrix.md'
if csv_path.exists() or md_path.exists():raise SystemExit('P8 matrix already exists; choose another output directory to preserve user results.')
backlog=list(csv.DictReader((root/'docs/backlog.csv').open(encoding='utf-8-sig',newline='')))
assert len(backlog)==114 and len({r['requirement_id'] for r in backlog})==114
evidence={
 'CORE':'docs/review/four-accounts-verification.json; docs/review/manual-acceptance/W12-15-手工验收用例.xlsx; docs/review/manual-acceptance/W16-19-采购协同验收用例.xlsx',
 'SRC':'docs/sourcing/api-verification.json; docs/sourcing/load-verification.json; docs/sourcing/fault-verification.json',
 'COL':'docs/review/W16-19-api-verification.json; docs/collaboration/README.md',
 'PER':'docs/performance/api-verification.json; docs/review/W20-22-api-verification.json; docs/review/W20-22-java-verification.json',
 'POR':'docs/review/P8-browser-verification.json; docs/review/manual-acceptance/W20-22-质量风险验收用例.xlsx',
 'FLOW':'docs/review/W20-22-java-verification.json; docs/review/W20-22-api-verification.json',
 'INT':'docs/quality/INTEGRATION.md; docs/review/W20-22-api-verification.json'
}
headers=['需求编号','需求标题','当前实现状态','P8业务验收状态','已有阶段证据（不代表该编号全部AC通过）','待补证据/签收']
rows=[]
for r in backlog:
 group=r['requirement_id'].split('-')[1]
 status='部分实现' if r['implementation_status']=='PARTIAL' else '已实现（未等于业务验收）'
 follow='补齐该编号PARTIAL内容；验证PRD全部特异AC及G1–G6；业务负责人记录实际结果并签收。' if r['implementation_status']=='PARTIAL' else '按PRD逐项验证全部特异AC及G1–G6；业务负责人记录实际结果并签收。'
 rows.append([r['requirement_id'],r['title'],status,'待业务逐项验收',evidence[group],follow])
out.mkdir(parents=True,exist_ok=True)
with csv_path.open('w',encoding='utf-8-sig',newline='') as f:
 w=csv.writer(f,lineterminator='\n');w.writerow(headers);w.writerows(rows)
counts=collections.Counter(r['implementation_status'] for r in backlog)
lines=['# P8：114项需求总体验收与业务签收矩阵','',f'生成日期：{datetime.date.today().isoformat()}。编号与标题直接取自 `docs/backlog.csv`；本表不修改原状态和既有手工验收记录。','','## 状态口径','',f'- 共114个唯一L3编号：{counts["IMPLEMENTED"]}项当前标记已实现，{counts["PARTIAL"]}项部分实现。实现状态不是业务验收结论。','- 114项的P8业务验收状态均为“待业务逐项验收”。已有证据列只给出模块级回归材料，不能替代该编号全部PRD特异AC和G1–G6的实际结果。','- 30项PARTIAL需要补齐实现并复测；所有项目都需要业务负责人填写结果、缺陷号和签收人。','- 本阶段已单独验证恢复、浏览器角色、10k列表性能和隔离故障恢复；证据见 `docs/review/P8-*-verification.json`。这些验证不会自动关闭任一L3编号。','','## 各模块状态','','| 模块 | 编号数 | 已实现 | 部分实现 |','| --- | ---: | ---: | ---: |']
for group in ['CORE','SRC','COL','PER','POR','FLOW','INT']:
 items=[r for r in backlog if r['requirement_id'].split('-')[1]==group]
 lines.append(f'| {group} | {len(items)} | {sum(r["implementation_status"]=="IMPLEMENTED" for r in items)} | {sum(r["implementation_status"]=="PARTIAL" for r in items)} |')
lines+=['','逐项填写文件：`P8-114-acceptance-matrix.csv`。每项都要补上具体测试人、日期、实际结果、缺陷/证据位置和业务签收人后，才能关闭验收。','']
md_path.write_text('\n'.join(lines),encoding='utf-8')
print(f'Generated {len(rows)} rows: {counts["IMPLEMENTED"]} implemented, {counts["PARTIAL"]} partial; none marked business-accepted.')
