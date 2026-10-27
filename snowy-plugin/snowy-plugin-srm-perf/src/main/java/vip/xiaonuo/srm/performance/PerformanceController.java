package vip.xiaonuo.srm.performance;
import java.util.*;
import java.io.*;
import java.time.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import vip.xiaonuo.common.pojo.CommonResult;
import vip.xiaonuo.common.annotation.CommonLog;
import vip.xiaonuo.srm.api.SrmSupplierAccessApi;
import static vip.xiaonuo.srm.performance.PerformanceInputs.*;
import static vip.xiaonuo.srm.performance.PerformanceRules.*;
@RestController @RequiredArgsConstructor
@RequestMapping({"/srm/v1/performance","/client/c/srm/v1/performance"})
public class PerformanceController {
 private final SrmSupplierAccessApi access;private final PerformanceService service;
 @GetMapping("/templates") public CommonResult<?> templates(){return CommonResult.data(service.templates(access.current()));}
 @PostMapping("/templates") @CommonLog(value="发布统一绩效模板",safe=true) public CommonResult<?> template(@Valid @RequestBody Template p){return CommonResult.data(service.publish(access.current(),p));}
 @PostMapping("/imports") @CommonLog(value="导入绩效历史来源",safe=true) public CommonResult<?> imports(@Valid @RequestBody Import p){return CommonResult.data(service.importRows(access.current(),p));}
 @PostMapping("/calculate") @CommonLog(value="计算月度绩效",safe=true) public CommonResult<?> calculate(@Valid @RequestBody Calculate p){return CommonResult.data(service.calculate(access.current(),p.supplierId(),p.period()));}
 @GetMapping("/scores") public CommonResult<?> scores(@RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String period,@RequestParam(defaultValue="") String category,@RequestParam(defaultValue="") String grade){return CommonResult.data(service.page(access.current(),current,size,period,category,grade));}
 @GetMapping("/scores/{id}") public CommonResult<?> score(@PathVariable String id){return CommonResult.data(service.detail(access.current(),id));}
 @PostMapping("/scores/{id}/appeals") @CommonLog(value="供应商绩效申诉",safe=true) public CommonResult<?> appeal(@PathVariable String id,@Valid @RequestBody Appeal p){return CommonResult.data(service.appeal(access.current(),id,p));}
 @PostMapping("/appeals/{id}/review") @CommonLog(value="SQE绩效复核",safe=true) public CommonResult<?> review(@PathVariable String id,@Valid @RequestBody Review p){return CommonResult.data(service.review(access.current(),id,p));}
 @GetMapping("/suppliers/{id}/summary") public CommonResult<?> summary(@PathVariable String id,@RequestParam(required=false) String endPeriod,@RequestParam(defaultValue="12") int months){return CommonResult.data(service.scopedSummary(access.current(),id,endPeriod==null?YearMonth.now(ZoneId.of("Asia/Shanghai")).minusMonths(1).toString():endPeriod,months));}
 @GetMapping("/export") public ResponseEntity<byte[]> export(@RequestParam(defaultValue="")String period,@RequestParam(defaultValue="")String category,@RequestParam(defaultValue="")String grade)throws IOException {
  var a=access.current();var first=service.page(a,1,100,period,category,grade);long total=((Number)first.get("total")).longValue();require(total<=10000,400,"导出最多10000行，请缩小范围");
  try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
   var sheet=book.createSheet("绩效分级");String[] keys={"supplierName","category","period","deliverScore","qualityScore","costScore","serviceScore","total","grade"};String[] names={"供应商","品类","月份","交付","质量","成本","服务","总分","绩效等级"};var h=sheet.createRow(0);for(int i=0;i<names.length;i++)h.createCell(i).setCellValue(names[i]);int index=1;
   for(int page=1;page<=Math.max(1,(total+99)/100);page++){var data=page==1?first:service.page(a,page,100,period,category,grade);for(var row:(List<Map<String,Object>>)data.get("records")){var r=sheet.createRow(index++);for(int i=0;i<keys.length;i++)r.createCell(i).setCellValue(String.valueOf(row.get(keys[i])));}}
   book.write(out);return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=performance.xlsx").body(out.toByteArray());
  }
 }
 @ExceptionHandler(MethodArgumentNotValidException.class) public CommonResult<?> invalid(){return CommonResult.get(400,"绩效字段校验失败，请检查分值、月份和必填项",null);}
 @ExceptionHandler(HttpMessageNotReadableException.class) public CommonResult<?> unreadable(){return CommonResult.get(400,"绩效请求格式或字段类型不正确",null);}
}
