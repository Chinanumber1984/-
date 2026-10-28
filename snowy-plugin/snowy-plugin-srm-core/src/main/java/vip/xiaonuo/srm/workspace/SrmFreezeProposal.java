package vip.xiaonuo.srm.workspace;
import java.util.*;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import vip.xiaonuo.srm.api.*;
@Service @RequiredArgsConstructor public class SrmFreezeProposal implements SrmLifecycleProposalApi {
 final SrmApprovals approvals;final SrmSupplierAccess access;final org.springframework.jdbc.core.JdbcTemplate db;
 public Map<String,Object> proposeFreeze(SrmSupplierAccessApi.Context a,String sid,String key,String reason){access.requireSupplier(a,sid,true);long v=db.queryForObject("SELECT ROW_VERSION FROM SRM_SUPPLIER WHERE ID=?",Long.class,sid);return approvals.submit(access.actor(a),sid,new SrmApprovalInputs.Submit(key,"FREEZE",v,reason,null));}
}
