package vip.xiaonuo.srm.collaboration.api;
import java.util.*;
/** Called only after connector authentication. All identity dimensions are server-derived. */
public interface SrmCollaborationIngressApi {
 record Origin(String connectorId,String orgId,String system,String mode,String operatorId){}
 record Command(String eventId,String resource,String businessKey,long version,long occurredAt,Map<String,Object> data){}
 class PendingDependency extends RuntimeException {public PendingDependency(String code){super(code);}}
 record PrivateFile(byte[] bytes,String mime,String sha256){}
 Map<String,Object> apply(Origin origin,Command command);
 PrivateFile invoiceFile(Origin origin,String invoiceId,long revision);
}
