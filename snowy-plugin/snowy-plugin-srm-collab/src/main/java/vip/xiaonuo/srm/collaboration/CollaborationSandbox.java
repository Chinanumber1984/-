package vip.xiaonuo.srm.collaboration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.xiaonuo.srm.api.SrmSupplierAccessApi.Context;
import static vip.xiaonuo.srm.collaboration.CollaborationStore.*;
import static vip.xiaonuo.srm.collaboration.CollaborationRules.*;
import static vip.xiaonuo.srm.collaboration.CollaborationInputs.*;
@Service @RequiredArgsConstructor @Transactional
public class CollaborationSandbox {
 final CollaborationStore x;
 public Map<String,Object> seed(Context a,Seed p){x.buyer(a);var supplier=x.access.requireSupplier(a,p.supplierId(),true);x.eligibility.requireEligibleForNewBusiness(p.supplierId(),supplier.orgId());require(supplier.orgId().equals(a.orgId()),403,"只能生成当前组织沙箱订单");var targets=x.exchange.targets(a.orgId(),"ERP","SANDBOX");require(targets.size()==1,503,"ERP沙箱连接未配置");var maps=x.db.queryForList("SELECT EXTERNAL_KEY FROM SRM_COL_SUPPLIER_MAP WHERE CONNECTOR_ID=? AND SUPPLIER_ID=?",targets.get(0).get("id"),p.supplierId());require(maps.size()==1,409,"请管理员先配置本供方唯一沙箱ERP映射");require(p.dueDate().getYear()>=2000&&p.dueDate().getYear()<=2100,400,"交期范围无效");line(p.quantity(),p.unitPrice());String out=x.exchange.enqueue(a.orgId(),"ERP","SANDBOX","SANDBOX_GENERATE_PO",p.requestKey(),1,Map.of("supplierKey",maps.get(0).get("EXTERNAL_KEY"),"quantity",p.quantity(),"unitPrice",p.unitPrice(),"dueDate",p.dueDate()),a.userId());return Map.of("outboxId",out,"mode","SANDBOX");}
 public Map<String,Object> command(Context a,SandboxCommand p){x.buyer(a);String system="ERP";Map<String,Object> parent;if(p.type().equals("PAYMENT")){x.finance(a);parent=x.aggregate(a,"SRM_COL_INVOICE",p.businessId(),false);var rec=x.recon(a,s(parent,"RECON_ID"),false);require(s(rec,"MODE").equals("SANDBOX"),403,"只能模拟沙箱账单付款");system="FINANCE";}else if(p.type().equals("RECEIPT")){parent=x.aggregate(a,"SRM_COL_ASN",p.businessId(),false);var po=x.po(a,s(parent,"PO_ID"),false);require(s(po,"MODE").equals("SANDBOX"),403,"只能模拟沙箱发运实收");}else{parent=x.po(a,p.businessId(),false);require(s(parent,"MODE").equals("SANDBOX"),403,"只能操作沙箱订单");if(p.type().equals("MES_PRIORITY"))system="MES";if(p.type().equals("FAIL_NEXT")){require(x.has(a,"SYS-ADMIN","superAdmin"),403,"仅管理员可注入沙箱故障");system="FINANCE";}}require(s(parent,"ORG_ID").equals(a.orgId()),403,"只能操作当前组织沙箱");String out=x.exchange.enqueue(a.orgId(),system,"SANDBOX","SANDBOX_"+p.type(),p.requestKey(),1,Map.of("businessId",p.businessId(),"data",p.data()),a.userId());return Map.of("outboxId",out,"mode","SANDBOX");}
}
