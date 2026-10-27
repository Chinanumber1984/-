package vip.xiaonuo.srm.api;
import java.util.*;
/** Trusted, server-side identity and supplier boundary for SRM plugins. Never bind Context from HTTP. */
public interface SrmSupplierAccessApi {
 record Context(String userId,String orgId,String supplierId,Set<String> roles,boolean allOrganizations,Set<String> organizationIds,boolean ownOnly,boolean portal) {
  public Context {roles=Set.copyOf(roles);organizationIds=Set.copyOf(organizationIds);}
 }
 record Supplier(String id,String orgId,String name,String category,String status,String tier) {}
 Context current();
 Supplier requireSupplier(Context actor,String supplierId,boolean lock);
 void recordEvent(Context actor,String supplierId,String subjectId,String action,Map<String,Object> metadata);
 void applyPerformanceTier(Context actor,String supplierId,String tier);
}
