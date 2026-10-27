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
 private SrmActor actor(Context a){return new SrmActor(a.userId(),a.orgId(),a.supplierId(),a.roles(),a.allOrganizations(),a.organizationIds(),a.ownOnly(),false,a.portal());}
 public Context current(){var a=identity.current();return new Context(a.userId(),a.orgId(),a.supplierId(),a.roles(),a.allOrganizations(),a.organizationIds(),a.ownOnly(),a.portal());}
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
