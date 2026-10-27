package vip.xiaonuo.srm.workspace;
import java.util.*;
import java.io.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import lombok.RequiredArgsConstructor;
import vip.xiaonuo.common.pojo.CommonResult;
import vip.xiaonuo.common.annotation.CommonLog;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import static vip.xiaonuo.srm.workspace.SrmInputs.*;

@RestController @RequiredArgsConstructor
@RequestMapping({"/srm/v1","/client/c/srm/v1"})
public class SrmWorkspaceController {
 private final SrmIdentity identity;private final SrmWorkspaceService service;private final SrmAttachments files;private final SrmCertificateWarnings warnings;
 @GetMapping("/session/context") public CommonResult<?> context(){var data=new LinkedHashMap<String,Object>(service.context(identity.current()));data.putAll(identity.labels());return CommonResult.data(data);}
 @GetMapping("/suppliers") public CommonResult<?> page(@RequestParam(defaultValue="1")int current,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String searchKey,@RequestParam(defaultValue="")String category,@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="")String tier,@RequestParam(defaultValue="")String orgId,@RequestParam(defaultValue="createTime")String sortBy,@RequestParam(defaultValue="DESC")String sortOrder){return CommonResult.data(service.page(identity.current(),current,size,searchKey,category,status,tier,orgId,sortBy,sortOrder));}
 @GetMapping("/suppliers/{sid}") public CommonResult<?> detail(@PathVariable String sid){return CommonResult.data(service.detail(identity.current(),sid));}
 @PostMapping("/suppliers") @CommonLog(value="SRM新建供应商",safe=true) public CommonResult<?> create(@Valid @RequestBody SupplierCreate p){return CommonResult.data(service.create(identity.current(),p));}
 @PostMapping("/suppliers/{sid}/edit") @CommonLog(value="SRM编辑供应商",safe=true) public CommonResult<?> edit(@PathVariable String sid,@Valid @RequestBody SupplierEdit p){return CommonResult.data(service.edit(identity.current(),sid,p));}
 @PostMapping("/suppliers/{sid}/tier") @CommonLog(value="SRM调整等级",safe=true) public CommonResult<?> tier(@PathVariable String sid,@Valid @RequestBody TierInput p){service.tier(identity.current(),sid,p);return CommonResult.ok();}
 @GetMapping("/categories") public CommonResult<?> categories(){identity.current();return CommonResult.data(service.categories());}
 @PostMapping("/categories") @CommonLog(value="SRM维护品类",safe=true) public CommonResult<?> category(@Valid @RequestBody CategoryInput p){service.saveCategory(identity.current(),p);return CommonResult.ok();}
 @PostMapping("/suppliers/{sid}/certificates") @CommonLog(value="SRM保存资质",safe=true) public CommonResult<?> cert(@PathVariable String sid,@Valid @RequestBody CertificateInput p){service.addCertificate(identity.current(),sid,p);return CommonResult.ok();}
 @PostMapping("/suppliers/{sid}/contacts") @CommonLog(value="SRM新增联系人",safe=true) public CommonResult<?> contact(@PathVariable String sid,@Valid @RequestBody ContactInput p){service.addContact(identity.current(),sid,p);return CommonResult.ok();}
 @PostMapping("/suppliers/{sid}/contacts/{cid}") @CommonLog(value="SRM编辑联系人",safe=true) public CommonResult<?> contactEdit(@PathVariable String sid,@PathVariable String cid,@Valid @RequestBody ContactInput p){service.editContact(identity.current(),sid,cid,p);return CommonResult.ok();}
 @PostMapping("/suppliers/{sid}/contacts/{cid}/access") @CommonLog(value="SRM调整联系人权限",safe=true) public CommonResult<?> access(@PathVariable String sid,@PathVariable String cid,@Valid @RequestBody ContactAccess p){service.contactAccess(identity.current(),sid,cid,p);return CommonResult.ok();}
 @PostMapping("/suppliers/{sid}/contacts/{cid}/account") @CommonLog(value="SRM开通门户账号",safe=true) public CommonResult<?> bind(@PathVariable String sid,@PathVariable String cid,@Valid @RequestBody AccountInput p){service.bindAccount(identity.current(),sid,cid,p);return CommonResult.ok();}
 @PostMapping("/suppliers/{sid}/attachments") @CommonLog(value="SRM上传私有附件",safe=true) public CommonResult<?> upload(@PathVariable String sid,@RequestParam MultipartFile file)throws Exception{return CommonResult.data(files.upload(identity.current(),sid,file));}
 @GetMapping("/attachments/{fid}") public ResponseEntity<byte[]> download(@PathVariable String fid)throws Exception{byte[] content=files.download(identity.current(),fid);boolean pdf=content.length>=5&&content[0]=='%';boolean png=content.length>=8&&content[0]==(byte)137;String ext=pdf?"pdf":png?"png":"jpg";MediaType mime=pdf?MediaType.APPLICATION_PDF:png?MediaType.IMAGE_PNG:MediaType.IMAGE_JPEG;return ResponseEntity.ok().contentType(mime).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=certificate-"+fid+"."+ext).body(content);}
 @PostMapping("/maintenance/certificate-warnings") @CommonLog(value="SRM扫描资质预警",safe=true) public CommonResult<?> scan(){var a=identity.current();SrmWorkspaceService.require(!a.portal()&&a.superUser(),403,"仅系统管理员可手工执行全局扫描");warnings.scan();return CommonResult.ok();}
 @GetMapping("/alerts") public CommonResult<?> alerts(){return CommonResult.data(service.alerts(identity.current()));}
 @GetMapping("/suppliers-export") public ResponseEntity<byte[]> export(@RequestParam(defaultValue="")String searchKey,@RequestParam(defaultValue="")String category,@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="")String tier,@RequestParam(defaultValue="")String orgId,@RequestParam(defaultValue="createTime")String sortBy,@RequestParam(defaultValue="DESC")String sortOrder)throws Exception{
  var actor=identity.current();var first=service.page(actor,1,100,searchKey,category,status,tier,orgId,sortBy,sortOrder);long total=((Number)first.get("total")).longValue();SrmWorkspaceService.require(total<=10000,400,"导出最多10000条，请缩小筛选范围");
  try(var workbook=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
   var sheet=workbook.createSheet("Suppliers");String[] keys={"supplierCode","name","category","tier","status","creditCode","bankAccount"};String[] labels={"供应商编码","名称","品类","等级","状态","信用代码（脱敏）","银行账号（脱敏）"};var header=sheet.createRow(0);for(int i=0;i<keys.length;i++)header.createCell(i).setCellValue(labels[i]);int index=1;
   for(int page=1;page<=Math.max(1,(total+99)/100);page++){
    var data=page==1?first:service.page(actor,page,100,searchKey,category,status,tier,orgId,sortBy,sortOrder);
    for(var s:(List<Map<String,Object>>)data.get("records")){var row=sheet.createRow(index++);for(int i=0;i<keys.length;i++)row.createCell(i).setCellValue(String.valueOf(s.getOrDefault(keys[i],"")));}
   }
   workbook.write(out);return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=suppliers.xlsx").body(out.toByteArray());
  }
 }
}
