package vip.xiaonuo.srm.performance.api;
import java.util.Map;
public interface SrmQualityIngressApi {
 record Origin(String connectorId,String orgId,String system,String mode,String operatorId){}
 Map<String,Object> apply(Origin origin,String resource,String businessKey,long version,Map<String,Object> data);
}
