package vip.xiaonuo.srm.workspace;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import lombok.RequiredArgsConstructor;
import vip.xiaonuo.dev.api.DevMessageApi;
import vip.xiaonuo.sys.api.SysUserApi;

@Component @EnableScheduling @RequiredArgsConstructor
public class SrmCertificateWarnings {
 private final JdbcTemplate db;private final DevMessageApi messages;private final SysUserApi users;@jakarta.annotation.Resource(name="loginUserApi") private vip.xiaonuo.auth.api.SaBaseLoginUserApi authUsers;
 @Value("${srm.cert.warning-days:30}")private int days;
 @Scheduled(cron="${srm.cert.warning-cron:0 0 8 * * *}",zone="Asia/Shanghai") @Transactional
 public void scan(){
  if(days<0||days>365)throw new IllegalStateException("资质预警天数须在0–365之间");
  var date=SrmWorkspaceService.today();
  for(var c:db.queryForList("SELECT c.* FROM SRM_SUPPLIER_CERT c JOIN SRM_SUPPLIER s ON s.ID=c.SUPPLIER_ID WHERE c.DELETE_FLAG='NOT_DELETE' AND c.STATUS<>'VOID' AND s.DELETE_FLAG='NOT_DELETE' AND c.VALID_TO<=?",date.plusDays(days))){
   String kind=java.time.LocalDate.parse(String.valueOf(c.get("VALID_TO"))).isBefore(date)?"EXPIRED":"EXPIRING";
   db.update("INSERT IGNORE INTO SRM_CERT_ALERT(ID,ORG_ID,SUPPLIER_ID,CERT_ID,VALID_TO,KIND,CREATED_AT) VALUES(?,?,?,?,?,?,NOW(3))",SrmWorkspaceService.id(),c.get("ORG_ID"),c.get("SUPPLIER_ID"),c.get("ID"),c.get("VALID_TO"),kind);
  }
  for(var alert:db.queryForList("SELECT * FROM SRM_CERT_ALERT WHERE STATUS='PENDING' FOR UPDATE")){
   String org=String.valueOf(alert.get("ORG_ID")),supplier=String.valueOf(alert.get("SUPPLIER_ID"));
   var targets=new LinkedHashSet<String>();
   String creator=db.queryForObject("SELECT CREATE_USER FROM SRM_SUPPLIER WHERE ID=?",String.class,supplier);
   for(String uid:users.userIdsByOrgAndRole(org,"SUP-ADMIN")){
    var ids=new ArrayList<String>();ids.add(uid);for(var role:authUsers.getRoleListByUserId(uid))ids.add(role.getStr("id"));
    boolean allowed=false;
    for(var scope:authUsers.getPermissionListByUserIdAndRoleIdList(ids,org)){
     String path=scope.getStr("apiUrl");if(path==null||!new org.springframework.util.AntPathMatcher().match(path,"/srm/v1/alerts"))continue;
     var orgs=scope.getBeanList("dataScope",String.class);
     if(Boolean.TRUE.equals(scope.getBool("scopeAll"))||(orgs!=null&&orgs.contains(org))||((orgs==null||orgs.isEmpty())&&uid.equals(creator)))allowed=true;
    }
    if(allowed)targets.add(uid);
   }
   targets.addAll(db.queryForList("SELECT ACCOUNT_ID FROM SRM_SUPPLIER_CONTACT WHERE SUPPLIER_ID=? AND ORG_ID=? AND ENABLED=true AND ACCOUNT_ID IS NOT NULL AND DELETE_FLAG='NOT_DELETE' AND JSON_CONTAINS(ROLES,'\"ADMIN\"')",String.class,supplier,org));
   if(!targets.isEmpty()){messages.sendMessageWithContent(new ArrayList<>(targets),"供应商资质预警","供应商ID："+supplier+"，证书ID："+alert.get("CERT_ID")+"，有效期至："+alert.get("VALID_TO"));db.update("UPDATE SRM_CERT_ALERT SET STATUS='SENT' WHERE ID=?",alert.get("ID"));}
  }
 }
}
