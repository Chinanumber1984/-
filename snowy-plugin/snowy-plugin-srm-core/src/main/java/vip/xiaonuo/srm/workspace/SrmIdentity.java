package vip.xiaonuo.srm.workspace;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.AntPathMatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import vip.xiaonuo.auth.core.util.*;
import vip.xiaonuo.common.exception.CommonException;
@Component @RequiredArgsConstructor
public class SrmIdentity {
 private final JdbcTemplate jdbc; private final HttpServletRequest request;
 public SrmActor current(){
  if(request.getServletPath().startsWith("/client/c/srm/")){
   StpClientUtil.checkLogin();
   var rows=jdbc.queryForList("SELECT c.*,s.ORG_ID AS SUPPLIER_ORG FROM SRM_SUPPLIER_CONTACT c JOIN SRM_SUPPLIER s ON s.ID=c.SUPPLIER_ID WHERE c.ACCOUNT_ID=? AND c.ENABLED=true AND c.DELETE_FLAG='NOT_DELETE' AND s.DELETE_FLAG='NOT_DELETE'",StpClientUtil.getLoginIdAsString());
   if(rows.size()!=1)throw new CommonException(403,"联系人未绑定或已停用");
   var c=rows.get(0); return new SrmActor(StpClientUtil.getLoginIdAsString(),String.valueOf(c.get("SUPPLIER_ORG")),String.valueOf(c.get("SUPPLIER_ID")),new HashSet<>(JSONUtil.toList(String.valueOf(c.get("ROLES")),String.class)),false,Set.of(),false,JSONUtil.toList(String.valueOf(c.get("ROLES")),String.class).stream().anyMatch(r->Set.of("ADMIN","FINANCE").contains(r)),true);
  }
  StpUtil.checkLogin();if(!request.getServletPath().endsWith("/context")&&!StpUtil.hasRole("superAdmin"))StpUtil.checkPermission(request.getServletPath());var u=StpLoginUserUtil.getLoginUser();if(u==null||u.getOrgId()==null)throw new CommonException(403,"账号未绑定组织");
  var roles=new HashSet<String>(u.getRoleCodeList()==null?List.of():u.getRoleCodeList());
  if(Collections.disjoint(roles,Set.of("superAdmin","SYS-ADMIN","SUP-ADMIN","CAT-MGR","SQE","SRC-SPE","BUYER","FIN")))throw new CommonException(403,"未授予SRM角色");
  boolean all=roles.contains("superAdmin");var orgs=new HashSet<String>();boolean matched=false;
  if(u.getDataScopeList()!=null)for(var scope:u.getDataScopeList()){
   if(scope.getApiUrl()!=null && new AntPathMatcher().match(scope.getApiUrl(),request.getServletPath())){
    matched=true;all|=scope.isScopeAll();if(scope.getDataScope()!=null)orgs.addAll(scope.getDataScope());
   }
  }
  boolean own=!all&&orgs.isEmpty();
  return new SrmActor(u.getId(),u.getOrgId(),null,roles,all,orgs,own,roles.contains("superAdmin")||StpUtil.hasPermission("/srm/fields/sensitive"),false);
 }
 public Map<String,String> labels(){
  if(request.getServletPath().startsWith("/client/c/srm/")){
   var rows=jdbc.queryForList("SELECT c.NAME,s.NAME AS SUPPLIER_NAME FROM SRM_SUPPLIER_CONTACT c JOIN SRM_SUPPLIER s ON s.ID=c.SUPPLIER_ID WHERE c.ACCOUNT_ID=?",StpClientUtil.getLoginIdAsString());
   if(rows.size()==1)return Map.of("displayName",String.valueOf(rows.get(0).get("NAME")),"orgName",String.valueOf(rows.get(0).get("SUPPLIER_NAME")));
   return Map.of();
  }
  var u=StpLoginUserUtil.getLoginUser();return Map.of("displayName",u.getName()==null?u.getId():u.getName(),"orgName",u.getOrgName()==null?u.getOrgId():u.getOrgName());
 }
}
