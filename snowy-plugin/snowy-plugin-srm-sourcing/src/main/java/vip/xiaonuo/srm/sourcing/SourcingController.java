package vip.xiaonuo.srm.sourcing;
import java.util.*;
import java.io.*;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import vip.xiaonuo.common.pojo.CommonResult;
import vip.xiaonuo.common.annotation.CommonLog;
import vip.xiaonuo.srm.api.SrmSupplierAccessApi;
import static vip.xiaonuo.srm.sourcing.SourcingInputs.*;
@RestController @RequiredArgsConstructor @RequestMapping({"/srm/v1/sourcing","/client/c/srm/v1/sourcing"})
public class SourcingController {
 private final SrmSupplierAccessApi access;private final SourcingRfqService rfqs;private final SourcingAuctionService auctions;private final SourcingAwardService awards;private final SourcingComparison comparison;private final SourcingFiles files;private final SourcingSocketHub sockets;
 @GetMapping("/rfqs") public CommonResult<?> page(@RequestParam(defaultValue="1")int current,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String searchKey){return CommonResult.data(rfqs.page(access.current(),current,size,searchKey));}
 @GetMapping("/candidates") public CommonResult<?> candidates(@RequestParam String category,@RequestParam(defaultValue="")String searchKey){return CommonResult.data(rfqs.candidates(access.current(),category,searchKey));}
 @PostMapping("/rfqs") @CommonLog(value="创建询价草稿",safe=true) public CommonResult<?> create(@Valid @RequestBody Rfq p){return CommonResult.data(rfqs.create(access.current(),p));}
 @GetMapping("/rfqs/{rid}") public CommonResult<?> detail(@PathVariable String rid){return CommonResult.data(rfqs.detail(access.current(),rid));}
 @PostMapping("/rfqs/{rid}/edit") @CommonLog(value="编辑询价草稿",safe=true) public CommonResult<?> edit(@PathVariable String rid,@Valid @RequestBody Rfq p){return CommonResult.data(rfqs.edit(access.current(),rid,p));}
 @PostMapping("/rfqs/{rid}/publish") @CommonLog(value="发布受邀询价",safe=true) public CommonResult<?> publish(@PathVariable String rid,@Valid @RequestBody Version p){return CommonResult.data(rfqs.publish(access.current(),rid,p));}
 @PostMapping("/rfqs/{rid}/extend") @CommonLog(value="延期询价",safe=true) public CommonResult<?> extend(@PathVariable String rid,@Valid @RequestBody Extend p){return CommonResult.data(rfqs.extend(access.current(),rid,p));}
 @PostMapping("/rfqs/{rid}/withdraw") @CommonLog(value="撤回未报价询价",safe=true) public CommonResult<?> withdraw(@PathVariable String rid,@Valid @RequestBody Version p){return CommonResult.data(rfqs.withdraw(access.current(),rid,p));}
 @PostMapping("/rfqs/{rid}/quotes") @CommonLog(value="提交加密报价轮次",safe=true) public CommonResult<?> quote(@PathVariable String rid,@Valid @RequestBody Quote p){return CommonResult.data(rfqs.quote(access.current(),rid,p));}
 @GetMapping("/rfqs/{rid}/quotes") public CommonResult<?> quotes(@PathVariable String rid){return CommonResult.data(rfqs.quotes(access.current(),rid));}
 @GetMapping("/rfqs/{rid}/comparison") public CommonResult<?> compare(@PathVariable String rid){return CommonResult.data(comparison.compare(access.current(),rid));}
 @GetMapping("/rfqs/{rid}/comparison-export") public ResponseEntity<byte[]> export(@PathVariable String rid)throws Exception{
  var data=comparison.compare(access.current(),rid);try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()){var sheet=book.createSheet("比价看板");var h=sheet.createRow(0);String[] labels={"排序","供应商","到厂总价","成本分","综合分","有效绩效月数","风险"};for(int i=0;i<labels.length;i++)h.createCell(i).setCellValue(labels[i]);int index=1;for(var c:(List<Map<String,Object>>)data.get("candidates")){var r=sheet.createRow(index++);var perf=new com.fasterxml.jackson.databind.ObjectMapper().convertValue(c.get("performance"),Map.class);Object[] values={c.get("rank"),c.get("supplierName"),c.get("total"),c.get("costScore"),c.get("weightedScore"),perf.get("validMonths"),((Map<?,?>)c.get("risk")).get("level")};for(int i=0;i<values.length;i++)r.createCell(i).setCellValue(values[i]==null?"无历史，待例外审批":String.valueOf(values[i]));}book.write(out);return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=comparison.xlsx").body(out.toByteArray());}
 }
 @PostMapping("/rfqs/{rid}/questions") @CommonLog(value="提交寻源答疑",safe=true) public CommonResult<?> question(@PathVariable String rid,@Valid @RequestBody Question p){return CommonResult.data(rfqs.question(access.current(),rid,p));}
 @GetMapping("/rfqs/{rid}/questions") public CommonResult<?> questions(@PathVariable String rid){return CommonResult.data(rfqs.questions(access.current(),rid));}
 @PostMapping("/rfqs/{rid}/questions/{qid}/answer") @CommonLog(value="回复寻源答疑",safe=true) public CommonResult<?> answer(@PathVariable String rid,@PathVariable String qid,@Valid @RequestBody Answer p){rfqs.answer(access.current(),rid,qid,p);return CommonResult.ok();}
 @PostMapping("/rfqs/{rid}/files") @CommonLog(value="上传寻源私有规格",safe=true) public CommonResult<?> upload(@PathVariable String rid,@RequestParam MultipartFile file)throws Exception{return CommonResult.data(files.upload(access.current(),rid,file));}
 @GetMapping("/rfqs/{rid}/files/{fid}") public ResponseEntity<byte[]> download(@PathVariable String rid,@PathVariable String fid){var file=files.download(access.current(),rid,fid);return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mime())).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=spec-"+file.id()+"."+(file.mime().contains("pdf")?"pdf":file.mime().contains("png")?"png":"jpg")).body(file.bytes());}
 @GetMapping("/notices") public CommonResult<?> notices(){return CommonResult.data(rfqs.notices(access.current()));}
 @GetMapping("/suppliers/{sid}/risk") public CommonResult<?> risk(@PathVariable String sid){return CommonResult.data(rfqs.risk(access.current(),sid));}
 @PostMapping("/suppliers/{sid}/risk") @CommonLog(value="登记有依据的人工风险",safe=true) public CommonResult<?> risk(@PathVariable String sid,@Valid @RequestBody Risk p){rfqs.risk(access.current(),sid,p);return CommonResult.ok();}
 @GetMapping("/rfqs/{rid}/auction-state") public CommonResult<?> state(@PathVariable String rid){return CommonResult.data(auctions.state(access.current(),rid));}
 @GetMapping("/rfqs/{rid}/bid-history") public CommonResult<?> history(@PathVariable String rid){return CommonResult.data(auctions.history(access.current(),rid));}
 @GetMapping("/rfqs/{rid}/receipts/{key}") public CommonResult<?> receipt(@PathVariable String rid,@PathVariable String key){return CommonResult.data(auctions.receipt(access.current(),rid,key));}
 @PostMapping("/rfqs/{rid}/recover") @CommonLog(value="按持久记录恢复竞价缓存",safe=true) public CommonResult<?> recover(@PathVariable String rid){return CommonResult.data(auctions.recover(access.current(),rid));}
 @PostMapping("/socket-ticket") @CommonLog(value="签发单次实时连接票据",safe=true) public CommonResult<?> ticket(@Valid @RequestBody Ticket p,HttpServletRequest request){return CommonResult.data(sockets.ticket(access.current(),p.rfqId(),request.getHeader("token")));}
 @PostMapping("/rfqs/{rid}/awards") @CommonLog(value="提交定点与例外审批",safe=true) public CommonResult<?> award(@PathVariable String rid,@Valid @RequestBody Award p){return CommonResult.data(awards.submit(access.current(),rid,p));}
 @GetMapping("/rfqs/{rid}/awards") public CommonResult<?> awards(@PathVariable String rid){return CommonResult.data(awards.list(access.current(),rid));}
 @GetMapping("/awards/{aid}") public CommonResult<?> award(@PathVariable String aid){return CommonResult.data(awards.detail(access.current(),aid));}
 @GetMapping("/tasks") public CommonResult<?> tasks(){return CommonResult.data(awards.tasks(access.current()));}
 @PostMapping("/awards/{aid}/tasks/{tid}") @CommonLog(value="处理定点审批",safe=true) public CommonResult<?> decide(@PathVariable String aid,@PathVariable String tid,@Valid @RequestBody Decision p){return CommonResult.data(awards.decide(access.current(),aid,tid,p));}
 @PostMapping("/awards/{aid}/publish") @CommonLog(value="发布定点及落选通知",safe=true) public CommonResult<?> publish(@PathVariable String aid){return CommonResult.data(awards.publish(access.current(),aid));}
 @PostMapping("/awards/{aid}/withdraw") @CommonLog(value="撤回定点申请",safe=true) public CommonResult<?> withdrawAward(@PathVariable String aid){awards.withdraw(access.current(),aid);return CommonResult.ok();}
 @GetMapping("/rfqs/{rid}/result") public CommonResult<?> result(@PathVariable String rid){return CommonResult.data(awards.result(access.current(),rid));}
 @ExceptionHandler(MethodArgumentNotValidException.class)public CommonResult<?> invalid(){return CommonResult.get(400,"请检查寻源必填项、金额精度和字段范围",null);}
 @ExceptionHandler(HttpMessageNotReadableException.class)public CommonResult<?> malformed(){return CommonResult.get(400,"寻源请求格式或字段类型错误",null);}
}
