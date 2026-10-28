package vip.xiaonuo.srm.collaboration.api;
import java.util.*;
/** P7 risk consumers must additionally authorize caller scope. This API never changes supplier lifecycle. */
public interface SrmCollaborationRiskApi {List<Map<String,Object>> pending(String trustedOrgId,String supplierId);}
