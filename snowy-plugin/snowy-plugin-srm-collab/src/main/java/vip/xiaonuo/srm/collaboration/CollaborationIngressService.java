package vip.xiaonuo.srm.collaboration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vip.xiaonuo.srm.collaboration.api.*;
import static vip.xiaonuo.srm.collaboration.CollaborationStore.*;
import static vip.xiaonuo.srm.collaboration.CollaborationRules.*;
import static vip.xiaonuo.srm.collaboration.CollaborationInputs.*;
@Service @RequiredArgsConstructor
public class CollaborationIngressService implements SrmCollaborationIngressApi,SrmCollaborationRiskApi {
 final CollaborationStore x;final OrdersService orders;final InvoiceService invoices;
 public Map<String,Object> apply(Origin o,Command c){return switch(c.resource()){
 case "PO"->orders.sourcePo(o,c);case "PO_CHANGE_RESULT"->orders.changeResult(o,c);case "RECEIPT"->orders.sourceReceipt(o,c);case "FIN_ACK"->invoices.acknowledgement(o,c);case "PAYMENT"->invoices.payment(o,c);
 case "SUPPLIER_MASTER"->{var maps=x.db.queryForList("SELECT SUPPLIER_ID FROM SRM_COL_SUPPLIER_MAP WHERE CONNECTOR_ID=? AND EXTERNAL_KEY=? AND ORG_ID=?",o.connectorId(),c.businessKey(),o.orgId());if(maps.isEmpty())throw new PendingDependency("SUPPLIER_MAPPING_REQUIRED");x.access.requireSupplier(x.system(o),s(maps.get(0),"SUPPLIER_ID"),true);x.db.update("INSERT INTO SRM_COL_EXTERNAL_MASTER(CONNECTOR_ID,EXTERNAL_KEY,ORG_ID,SOURCE_VERSION,PAYLOAD_CIPHER,UPDATED_AT) VALUES(?,?,?,?,?,NOW(3)) ON DUPLICATE KEY UPDATE SOURCE_VERSION=VALUES(SOURCE_VERSION),PAYLOAD_CIPHER=VALUES(PAYLOAD_CIPHER),UPDATED_AT=NOW(3)",o.connectorId(),c.businessKey(),o.orgId(),c.version(),x.crypto.encrypt(x.json(c.data())));yield Map.of("staged",true);}
 case "MES_PRIORITY"->{var p=x.input(c.data(),SourceMes.class);require(c.businessKey().equals(p.poNo()),400,"MES订单标识不一致");var rows=x.db.queryForList("SELECT ID FROM SRM_COL_PO WHERE ORG_ID=? AND PO_NO=? AND MODE=?",o.orgId(),p.poNo(),o.mode());if(rows.isEmpty())throw new PendingDependency("PO_NOT_FOUND");var po=x.lockPo(x.system(o),s(rows.get(0),"ID"));x.db.update("INSERT INTO SRM_COL_MES(CONNECTOR_ID,PO_ID,SOURCE_VERSION,PRIORITY,SOURCE_REF,PAYLOAD_CIPHER,UPDATED_AT) VALUES(?,?,?,?,?,?,NOW(3)) ON DUPLICATE KEY UPDATE SOURCE_VERSION=VALUES(SOURCE_VERSION),PRIORITY=VALUES(PRIORITY),SOURCE_REF=VALUES(SOURCE_REF),PAYLOAD_CIPHER=VALUES(PAYLOAD_CIPHER),UPDATED_AT=NOW(3)",o.connectorId(),po.get("ID"),c.version(),p.priority(),p.sourceRef(),x.crypto.encrypt(x.json(p)));x.audit(x.system(o),s(po,"SUPPLIER_ID"),s(po,"ID"),"MES_PRIORITY_RECEIVED",Map.of("version",c.version(),"priority",p.priority()));yield Map.of("applied",true);}
 default->throw new vip.xiaonuo.common.exception.CommonException(400,"不支持的业务资源");};}
 public PrivateFile invoiceFile(Origin o,String id,long rev){return invoices.invoiceFile(o,id,rev);}
 public List<Map<String,Object>> pending(String org,String supplier){var out=new ArrayList<Map<String,Object>>();for(var r:x.db.queryForList("SELECT * FROM SRM_COL_RISK_EVENT WHERE ORG_ID=? AND SUPPLIER_ID=? AND STATUS='PENDING_ASSESSMENT' ORDER BY CREATED_AT",org,supplier)){var item=x.basic(r,new String[][]{{"id","ID"},{"kind","KIND"},{"poId","PO_ID"},{"createdAt","CREATED_AT"}});item.put("basis",x.object(x.crypto.decrypt(s(r,"PAYLOAD_CIPHER"))));out.add(item);}return out;}
}
