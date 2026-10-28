package vip.xiaonuo.srm.api;
import java.util.Map;
public interface SrmLifecycleProposalApi {Map<String,Object> proposeFreeze(SrmSupplierAccessApi.Context actor,String supplierId,String requestKey,String reason);}
