package vip.xiaonuo.srm.workspace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import vip.xiaonuo.srm.api.SrmSupplierEligibilityApi;
@Service @RequiredArgsConstructor
public class SrmSupplierEligibility implements SrmSupplierEligibilityApi {
 private final JdbcTemplate db;
 public void requireEligibleForNewBusiness(String sid,String org){SrmWorkspaceService.require(org!=null&&!org.isBlank(),403,"缺少可信组织");var r=db.queryForList("SELECT STATUS FROM SRM_SUPPLIER WHERE ID=? AND ORG_ID=? AND DELETE_FLAG='NOT_DELETE'",sid,org);SrmWorkspaceService.require(r.size()==1,404,"供应商不存在或不属于授权组织");SrmWorkspaceService.require("QUALIFIED".equals(r.get(0).get("STATUS")),409,"仅合格供应商可参与新增业务，潜在、补正、审核、冻结和淘汰均禁止");}
}
