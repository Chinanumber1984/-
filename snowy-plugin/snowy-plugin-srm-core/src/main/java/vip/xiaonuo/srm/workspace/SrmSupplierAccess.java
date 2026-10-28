package vip.xiaonuo.srm.workspace;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vip.xiaonuo.srm.api.SrmSupplierAccessApi;
import vip.xiaonuo.common.exception.CommonException;
@Service @RequiredArgsConstructor
public class SrmSupplierAccess implements SrmSupplierAccessApi {
 private final SrmIdentity identity;private final SrmWorkspaceService suppliers;private final JdbcTemplate db;
 @jakarta.annotation.Resource(name="loginUserApi") private vip.xiaonuo.auth.api.SaBaseLoginUserApi authUsers;
 SrmActor actor(Context a){return new SrmActor(a.userId(),a.orgId(),a.supplierId(),a.roles(),a.allOrganizations(),a.organizationIds(),a.ownOnly(),false,a.portal());}
 public Context current(){var a=identity.current();return new Context(a.userId(),a.orgId(),a.supplierId(),a.roles(),a.allOrganizations(),a.organizationIds(),a.ownOnly(),a.portal());}
 public boolean canViewUnitPrice(Context a){return !a.portal()&&(a.roles().contains("superAdmin")||cn.dev33.satoken.stp.StpUtil.hasPermission("/srm/fields/unit-price"));}
 public Context socketContext(String token,boolean portal){
  Object login=portal?vip.xiaonuo.auth.core.util.StpClientUtil.getLoginIdByToken(token):cn.dev33.satoken.stp.StpUtil.getLoginIdByToken(token);
  if(login==null)throw new CommonException(401,"登录已失效，请重新连接");String uid=String.valueOf(login);
  if(portal){var rows=db.queryForList("SELECT c.SUPPLIER_ID,c.ROLES,s.ORG_ID FROM SRM_SUPPLIER_CONTACT c JOIN SRM_SUPPLIER s ON s.ID=c.SUPPLIER_ID WHERE c.ACCOUNT_ID=? AND c.ENABLED=true AND c.DELETE_FLAG='NOT_DELETE' AND s.DELETE_FLAG='NOT_DELETE'",uid);if(rows.size()!=1)throw new CommonException(403,"联系人已停用或解除绑定");var r=rows.get(0);return new Context(uid,String.valueOf(r.get("ORG_ID")),String.valueOf(r.get("SUPPLIER_ID")),new HashSet<>(cn.hutool.json.JSONUtil.toList(String.valueOf(r.get("ROLES")),String.class)),false,Set.of(),false,true);}
  var rows=db.queryForList("SELECT ORG_ID FROM SYS_USER WHERE ID=? AND USER_STATUS='ENABLE' AND DELETE_FLAG='NOT_DELETE'",uid);if(rows.size()!=1)throw new CommonException(403,"用户已停用");String org=String.valueOf(rows.get(0).get("ORG_ID"));var roles=new HashSet<String>();var ids=new ArrayList<String>();ids.add(uid);for(var r:authUsers.getRoleListByUserId(uid)){roles.add(r.getStr("code"));ids.add(r.getStr("id"));}
  boolean all=roles.contains("superAdmin"),allowed=all;var orgs=new HashSet<String>();
  for(var scope:authUsers.getPermissionListByUserIdAndRoleIdList(ids,org)){String url=scope.getStr("apiUrl");if(url!=null&&new org.springframework.util.AntPathMatcher().match(url,"/srm/v1/sourcing/socket-ticket")){allowed=true;all|=Boolean.TRUE.equals(scope.getBool("scopeAll"));var granted=scope.getBeanList("dataScope",String.class);if(granted!=null)orgs.addAll(granted);}}
  if(!allowed)throw new CommonException(403,"寻源权限已撤销");return new Context(uid,org,null,roles,all,orgs,!all&&orgs.isEmpty(),false);
 }
 public List<Map<String,Object>> sourcingReviewers(String org,String requester,String creator){var result=new ArrayList<Map<String,Object>>();for(var u:db.queryForList("SELECT DISTINCT u.ID,u.NAME FROM SYS_USER u JOIN SYS_RELATION rel ON rel.OBJECT_ID=u.ID AND rel.CATEGORY='SYS_USER_HAS_ROLE' JOIN SYS_ROLE r ON r.ID=rel.TARGET_ID WHERE u.ORG_ID=? AND u.USER_STATUS='ENABLE' AND u.DELETE_FLAG='NOT_DELETE' AND r.DELETE_FLAG='NOT_DELETE' AND r.CODE='CAT-MGR' AND u.ID<>?",org,requester)){String uid=String.valueOf(u.get("ID"));var ids=new ArrayList<String>();ids.add(uid);for(var role:authUsers.getRoleListByUserId(uid))ids.add(role.getStr("id"));boolean allowed=false;for(var scope:authUsers.getPermissionListByUserIdAndRoleIdList(ids,org)){String path=scope.getStr("apiUrl");if(path==null||!new org.springframework.util.AntPathMatcher().match(path,"/srm/v1/sourcing/tasks"))continue;var orgs=scope.getBeanList("dataScope",String.class);if(Boolean.TRUE.equals(scope.getBool("scopeAll"))||(orgs!=null&&orgs.contains(org))||((orgs==null||orgs.isEmpty())&&uid.equals(creator)))allowed=true;}if(allowed)result.add(Map.of("id",uid,"name",String.valueOf(u.get("NAME"))));}return result;}
 public Supplier requireSupplier(Context a,String sid,boolean lock){var s=suppliers.supplier(actor(a),sid,lock);return new Supplier(sid,String.valueOf(s.get("ORG_ID")),String.valueOf(s.get("NAME")),String.valueOf(s.get("CATEGORY")),String.valueOf(s.get("STATUS")),String.valueOf(s.get("TIER")));}
 public void recordEvent(Context a,String sid,String subject,String action,Map<String,Object> metadata){suppliers.audit(actor(a),sid,subject,action,metadata);}
 @Transactional public void applyPerformanceTier(Context a,String sid,String tier){
  if(a.portal()||Collections.disjoint(a.roles(),Set.of("SQE","CAT-MGR","superAdmin")))throw new CommonException(403,"无绩效定级权限");
  if(!Set.of("STRATEGIC","PREFERRED","QUALIFIED","WATCH").contains(tier))throw new CommonException(400,"绩效不得直接淘汰供应商");
  var s=requireSupplier(a,sid,true);if(!s.status().equals("QUALIFIED")||tier.equals(s.tier()))return;
  if(db.queryForObject("SELECT COUNT(*) FROM SRM_APPROVAL_LINK WHERE SUPPLIER_ID=? AND STATUS='PENDING'",Long.class,sid)>0)return;
  // Only classification changes here. Lifecycle STATUS remains governed by approvals.
  db.update("UPDATE SRM_SUPPLIER SET TIER=?,ROW_VERSION=ROW_VERSION+1,UPDATE_USER=?,UPDATE_TIME=NOW() WHERE ID=?",tier,a.userId(),sid);
  recordEvent(a,sid,sid,"PERFORMANCE_TIER_UPDATED",Map.of("before",s.tier(),"after",tier));
 }
}
