package vip.xiaonuo.srm.workspace;
import java.util.*;
import java.security.SecureRandom;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import cn.hutool.crypto.digest.DigestUtil;
import vip.xiaonuo.auth.api.AuthApi;
import vip.xiaonuo.common.pojo.CommonResult;
import vip.xiaonuo.common.annotation.CommonLog;
import static vip.xiaonuo.srm.workspace.SrmWorkspaceService.*;
@Service @RequiredArgsConstructor
public class SrmRegistration {
 private final JdbcTemplate db;private final SrmWorkspaceService service;private final AuthApi auth;
 public record Register(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}")String invitation,@NotNull @Valid SrmInputs.SupplierCreate supplier,@NotNull @Valid SrmInputs.ContactInput contact,@NotNull @Valid SrmInputs.AccountInput account,@NotBlank @Size(max=10)String validCode,@NotBlank @Pattern(regexp="[0-9]{1,20}")String validCodeReqNo){}
 @Transactional public Map<String,Object> invite(SrmActor a){require(!a.portal()&&a.canManage(),403,"仅内部管理员可创建注册邀请");byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);db.update("INSERT INTO SRM_REGISTRATION_INVITE(ID,TOKEN_HASH,ORG_ID,CREATED_BY,EXPIRES_AT,CREATED_AT) VALUES(?,?,?,?,DATE_ADD(NOW(3),INTERVAL 7 DAY),NOW(3))",id(),DigestUtil.sha256Hex(token),a.orgId(),a.userId());return Map.of("invitation",token,"expiresInDays",7,"notice","单次使用，归属当前组织，请通过受控渠道交付供应商");}
 @Transactional public Map<String,Object> register(Register p){
  auth.verifyRegistrationCaptcha(p.validCode(),p.validCodeReqNo());var rows=db.queryForList("SELECT * FROM SRM_REGISTRATION_INVITE WHERE TOKEN_HASH=? AND USED_AT IS NULL AND EXPIRES_AT>NOW(3) FOR UPDATE",DigestUtil.sha256Hex(p.invitation()));require(rows.size()==1,400,"注册邀请无效、已使用或已过期");var invite=rows.get(0);String org=str(invite,"ORG_ID");
  require(p.contact().enabled()&&p.contact().roles().equals(Set.of("ADMIN")),400,"注册联系人必须是启用的管理员");var system=new SrmActor(str(invite,"CREATED_BY"),org,null,Set.of("SUP-ADMIN"),false,Set.of(org),false,false,false);
  var supplier=service.create(system,p.supplier());String sid=String.valueOf(supplier.get("id"));service.addContact(system,sid,p.contact());var contacts=service.contacts(service.supplier(system,sid,false));String cid=String.valueOf(contacts.get(0).get("id"));service.bindAccount(system,sid,cid,p.account());
  db.update("UPDATE SRM_REGISTRATION_INVITE SET USED_AT=NOW(3),SUPPLIER_ID=? WHERE ID=?",sid,invite.get("ID"));service.audit(system,sid,sid,"SUPPLIER_REGISTERED",Map.of("entry","INVITATION"));return Map.of("supplierCode",supplier.get("supplierCode"),"status","POTENTIAL","nextStep","登录供应商门户，补齐品类要求的资质后提交准入；注册不跳过资质门槛");
 }
 @RestController @RequiredArgsConstructor public static class Controller {
  private final SrmRegistration service;private final SrmIdentity identity;
  @PostMapping("/srm/v1/registration-invitations") @CommonLog(value="SRM注册邀请",safe=true) public CommonResult<?> invite(){return CommonResult.data(service.invite(identity.current()));}
  @GetMapping("/srm/public/categories") public CommonResult<?> categories(){return CommonResult.data(service.service.categories().stream().filter(c->Boolean.TRUE.equals(c.get("enabled"))).map(c->Map.of("code",c.get("code"),"name",c.get("name"))).toList());}
  @PostMapping("/srm/public/register") @CommonLog(value="SRM供应商注册",safe=true) public CommonResult<?> register(@Valid @RequestBody Register p){return CommonResult.data(service.register(p));}
 }
}
