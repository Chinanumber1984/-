package vip.xiaonuo.srm.workspace;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.io.*;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
import vip.xiaonuo.common.exception.CommonException;

@Service @RequiredArgsConstructor
public class SrmAttachments {
 private final JdbcTemplate db; private final SrmWorkspaceService service;private final SrmPrivateStorage storage;
 @Value("${srm.storage.directory:.runtime/srm-files}") private String directory;
 @Transactional public Map<String,Object> upload(SrmActor a,String sid,MultipartFile file) throws Exception {
  a.requireManage();var s=service.supplier(a,sid,true);
  SrmWorkspaceService.require(file.getSize()>0&&file.getSize()<=10*1024*1024,400,"文件须为10MB以内的PDF/PNG/JPEG");
  byte[] bytes=file.getBytes();String original=Optional.ofNullable(file.getOriginalFilename()).orElse("certificate");
  String suffix=original.contains(".")?original.substring(original.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";
  boolean pdf=suffix.equals("pdf")&&bytes.length>=5&&new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-");
  boolean png=suffix.equals("png")&&bytes.length>=8&&Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10});
  boolean jpeg=Set.of("jpg","jpeg").contains(suffix)&&bytes.length>=3&&bytes[0]==(byte)255&&bytes[1]==(byte)216&&bytes[2]==(byte)255;
  SrmWorkspaceService.require(pdf||png||jpeg,400,"文件内容与允许的PDF/PNG/JPEG格式不符");
  String fid=SrmWorkspaceService.id();String engine=storage.engine();storage.put(fid,bytes);
  org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{storage.delete(fid,engine);}catch(Exception ignored){}}});
  String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  db.update("INSERT INTO SRM_ATTACHMENT_SCOPE(ID,FILE_ID,ORG_ID,SUPPLIER_ID,OWNER_USER_ID,CONTENT_SHA256,VISIBILITY,CREATED_AT,STORAGE_ENGINE) VALUES(?,?,?,?,?,?,'PRIVATE',NOW(3),?)",fid,fid,s.get("ORG_ID"),sid,a.userId(),sha,engine);
  service.audit(a,sid,fid,"ATTACHMENT_UPLOADED",Map.of("fileId",fid,"sha256",sha));return Map.of("fileId",fid,"size",bytes.length);
 }
 public byte[] download(SrmActor a,String fid) throws Exception{
  SrmWorkspaceService.require(fid.matches("[0-9]{1,20}"),404,"附件不存在");var rows=db.queryForList("SELECT * FROM SRM_ATTACHMENT_SCOPE WHERE FILE_ID=?",fid);SrmWorkspaceService.require(rows.size()==1,404,"附件不存在");
  var file=rows.get(0);service.supplier(a,String.valueOf(file.get("SUPPLIER_ID")),false);
  byte[] bytes;try{bytes=storage.get(fid,String.valueOf(file.get("STORAGE_ENGINE")));}catch(Exception ex){throw new CommonException(404,"附件内容不存在或存储不可用");}
  String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));SrmWorkspaceService.require(hash.equals(file.get("CONTENT_SHA256")),409,"附件完整性校验失败");return bytes;
 }
}
