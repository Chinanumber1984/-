package vip.xiaonuo.srm.api;
/** Server-side scope must already be authorized by caller. Never pass client-provided org scope. */
public interface SrmSupplierEligibilityApi {void requireEligibleForNewBusiness(String supplierId,String trustedOrgId);void requireEligibleForAward(String supplierId,String trustedOrgId);}
