package vip.xiaonuo.srm.sourcing;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import cn.hutool.crypto.digest.DigestUtil;
import vip.xiaonuo.srm.api.SrmSupplierAccessApi.Context;
import static vip.xiaonuo.srm.sourcing.SourcingRules.*;
import static vip.xiaonuo.srm.sourcing.SourcingStore.*;
@Service @RequiredArgsConstructor @Transactional(isolation=Isolation.READ_COMMITTED)
public class SourcingFiles {
 private final SourcingStore x;
 public Map<String,Object> upload(Context a,String rid,MultipartFile file)throws Exception{x.writer(a);var r=x.rfq(a,rid,true);require(s(r,"STATUS").equals("DRAFT"),409,"规格附件只能在发布前维护");require(file.getSize()>0&&file.getSize()<=10*1024*1024,400,"附件须为10MB以内PDF/PNG/JPEG");byte[] bytes=file.getBytes();String original=Optional.ofNullable(file.getOriginalFilename()).orElse("spec.pdf").replaceAll("[\\r\\n\\\"\\\\/]","_");require(original.length()<=128,400,"文件名过长");String lower=original.toLowerCase(Locale.ROOT);boolean pdf=lower.endsWith(".pdf")&&bytes.length>=5&&new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-");boolean png=lower.endsWith(".png")&&bytes.length>=8&&Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10});boolean jpeg=(lower.endsWith(".jpg")||lower.endsWith(".jpeg"))&&bytes.length>=3&&bytes[0]==(byte)255&&bytes[1]==(byte)216&&bytes[2]==(byte)255;require(pdf||png||jpeg,400,"附件内容须与PDF/PNG/JPEG格式一致");String fid=id(),engine=x.files.put(fid,bytes),sha=DigestUtil.sha256Hex(bytes),mime=pdf?"application/pdf":png?"image/png":"image/jpeg";
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{x.files.delete(fid,engine);}catch(Exception ignored){}}});x.db.update("INSERT INTO SRM_SRC_FILE VALUES(?,?,?,?,?,?,?, ?,NOW(3))",fid,rid,r.get("ORG_ID"),original,mime,sha,engine,a.userId());x.auditRfq(a,r,"RFQ_SPEC_UPLOADED",Map.of("fileId",fid,"sha256",sha));return Map.of("id",fid,"name",original,"mime",mime);
 }
 public record Download(byte[] bytes,String mime,String id){}
 public Download download(Context a,String rid,String fid){x.rfq(a,rid,false);var rows=x.db.queryForList("SELECT * FROM SRM_SRC_FILE WHERE ID=? AND RFQ_ID=?",fid,rid);require(rows.size()==1,404,"规格附件不存在或无权访问");var f=rows.get(0);byte[] bytes=x.files.read(fid,s(f,"STORAGE_ENGINE"));require(DigestUtil.sha256Hex(bytes).equals(f.get("CONTENT_SHA256")),409,"规格附件完整性校验失败");return new Download(bytes,s(f,"MIME"),fid);}
}
