package vip.xiaonuo.srm.workspace;
import java.util.*;
import vip.xiaonuo.common.exception.CommonException;
public record SrmActor(String userId,String orgId,String supplierId,Set<String> roles,boolean allOrganizations,Set<String> organizationIds,boolean ownOnly,boolean sensitive,boolean portal) {
 public SrmActor { roles=Set.copyOf(roles);organizationIds=Set.copyOf(organizationIds); }
 public boolean superUser(){return !portal && roles.contains("superAdmin");}
 public boolean canManage(){return portal ? roles.contains("ADMIN") : superUser()||roles.contains("SUP-ADMIN");}
 public boolean canClassify(){return !portal && (superUser()||roles.contains("CAT-MGR"));}
 public void requireManage(){if(!canManage())throw new CommonException(403,"没有资料维护权限");}
 public boolean canRead(Map<String,Object> row){
  if(portal)return orgId.equals(row.get("ORG_ID"))&&supplierId.equals(row.get("ID"));
  if(allOrganizations)return true;
  if(ownOnly)return orgId.equals(row.get("ORG_ID"))&&userId.equals(row.get("CREATE_USER"));
  return organizationIds.contains(String.valueOf(row.get("ORG_ID")));
 }
}
