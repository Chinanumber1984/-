package vip.xiaonuo.srm.workspace;

import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import vip.xiaonuo.flow.api.SrmFlowApi;
import static vip.xiaonuo.srm.workspace.SrmWorkspaceService.require;
import static vip.xiaonuo.srm.workspace.SrmWorkflowCategoryInputs.Update;

@Service @RequiredArgsConstructor
public class SrmWorkflowCategoryService {
 static final Set<String> BUSINESS_TYPES=Set.of("ONBOARD","CHANGE","FREEZE","UNFREEZE","ELIMINATE","AWARD","COL_REVIEW","QUALITY_REVIEW");
 private final JdbcTemplate db;
 private final SrmFlowApi flow;

 public List<Map<String,Object>> list(SrmActor actor){
  require(!actor.portal(),403,"供应商门户不能读取工作流分类映射");
  var rows=db.queryForList("SELECT BUSINESS_TYPE AS businessType,CATEGORY_CODE AS categoryCode,CATEGORY_NAME AS categoryName,ROW_VERSION AS rowVersion FROM SRM_WORKFLOW_CATEGORY_MAP ORDER BY BUSINESS_TYPE");
  require(rows.size()==BUSINESS_TYPES.size()&&rows.stream().map(r->String.valueOf(r.get("businessType"))).collect(java.util.stream.Collectors.toSet()).equals(BUSINESS_TYPES),500,"工作流分类映射未完整初始化");
  return rows;
 }
 @Transactional public Map<String,Object> update(SrmActor actor,String businessType,Update p){
  require(!actor.portal()&&(actor.superUser()||actor.roles().contains("SYS-ADMIN")),403,"仅流程管理员可维护分类映射");
  require(BUSINESS_TYPES.contains(businessType),400,"不支持的SRM业务类型");
  String code=p.categoryCode().trim(),name=p.categoryName().trim();
  require(code.matches("[A-Z][A-Z0-9_-]{1,39}"),400,"分类编码格式无效");require(!name.isBlank()&&name.length()<=80,400,"分类名称长度无效");
  var old=db.queryForList("SELECT * FROM SRM_WORKFLOW_CATEGORY_MAP WHERE BUSINESS_TYPE=? FOR UPDATE",businessType);require(old.size()==1,404,"业务类型映射不存在");
  var row=old.get(0);require(((Number)row.get("ROW_VERSION")).longValue()==p.expectedVersion(),409,"分类映射已被其他管理员修改");
  require(db.queryForObject("SELECT COUNT(*) FROM SRM_WORKFLOW_CATEGORY_MAP WHERE BUSINESS_TYPE<>? AND (CATEGORY_CODE=? OR CATEGORY_NAME=?)",Long.class,businessType,code,name)==0,409,"分类编码或名称已映射到其他业务类型");
  try{db.update("UPDATE SRM_WORKFLOW_CATEGORY_MAP SET CATEGORY_CODE=?,CATEGORY_NAME=?,ROW_VERSION=ROW_VERSION+1,UPDATED_BY=?,UPDATED_AT=NOW(3) WHERE BUSINESS_TYPE=? AND ROW_VERSION=?",code,name,actor.userId(),businessType,p.expectedVersion());}
  catch(DuplicateKeyException ex){throw new vip.xiaonuo.common.exception.CommonException(409,"分类编码或名称已映射到其他业务类型");}
  flow.updateCategory(businessType,code);
  return Map.of("businessType",businessType,"categoryCode",code,"categoryName",name,"rowVersion",p.expectedVersion()+1);
 }
}
