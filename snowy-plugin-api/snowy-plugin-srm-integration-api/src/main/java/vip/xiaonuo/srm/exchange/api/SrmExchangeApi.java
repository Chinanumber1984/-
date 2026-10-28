package vip.xiaonuo.srm.exchange.api;
import java.util.*;
/** Business authorization is the caller's responsibility; connector selection is server-side. */
public interface SrmExchangeApi {
 String enqueue(String orgId,String system,String mode,String businessType,String businessId,long version,Map<String,Object> payload,String actorId);
 Map<String,Object> delivery(String orgId,String id);
 List<Map<String,Object>> targets(String orgId,String system,String mode);
 void requireTarget(String connectorId,String orgId,String system,String mode);
}
