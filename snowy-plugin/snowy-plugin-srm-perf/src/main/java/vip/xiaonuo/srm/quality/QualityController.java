package vip.xiaonuo.srm.quality;
import lombok.RequiredArgsConstructor;import jakarta.validation.Valid;import org.springframework.web.bind.annotation.*;import org.springframework.web.multipart.MultipartFile;import org.springframework.http.*;import vip.xiaonuo.common.pojo.CommonResult;import vip.xiaonuo.common.annotation.CommonLog;import vip.xiaonuo.srm.api.SrmSupplierAccessApi;import static vip.xiaonuo.srm.quality.QualityInputs.*;
@RestController @RequiredArgsConstructor @RequestMapping({"/srm/v1/quality","/client/c/srm/v1/quality"}) public class QualityController {
 final SrmSupplierAccessApi access;final QualityCases cases;final QualityRisks risks;final QualitySources sources;
 @GetMapping("/cases") public CommonResult<?> page(@RequestParam(defaultValue="PPAP")String kind,@RequestParam(defaultValue="1")int current,@RequestParam(defaultValue="20")int size){return CommonResult.data(cases.page(access.current(),kind,current,size));}
 @GetMapping("/cases/{id}")public CommonResult<?> detail(@PathVariable String id){return CommonResult.data(cases.detail(access.current(),id));}
 @PostMapping("/ppaps")@CommonLog(value="创建PPAP草稿",safe=true)public CommonResult<?> draft(@Valid @RequestBody Draft p){return CommonResult.data(cases.create(access.current(),"PPAP",p));}
 @PostMapping("/scars")@CommonLog(value="发起SCAR",safe=true)public CommonResult<?> scar(@Valid @RequestBody Scar p){return CommonResult.data(cases.create(access.current(),"SCAR",p));}
 @PostMapping("/cases/{id}/ppap-submit")@CommonLog(value="提交PPAP审核",safe=true)public CommonResult<?> submit(@PathVariable String id,@Valid @RequestBody Ppap p){return CommonResult.data(cases.submitPpap(access.current(),id,p));}
 @PostMapping("/cases/{id}/8d")@CommonLog(value="提交结构化8D",safe=true)public CommonResult<?> reply(@PathVariable String id,@Valid @RequestBody EightD p){return CommonResult.data(cases.reply(access.current(),id,p));}
 @PostMapping("/cases/{id}/tasks/{tid}")@CommonLog(value="SQE质量审核",safe=true)public CommonResult<?> review(@PathVariable String id,@PathVariable String tid,@Valid @RequestBody Review p){return CommonResult.data(cases.review(access.current(),id,tid,p));}
 @PostMapping("/cases/{id}/close")@CommonLog(value="确认8D有效性并闭环",safe=true)public CommonResult<?> close(@PathVariable String id,@Valid @RequestBody Close p){return CommonResult.data(cases.close(access.current(),id,p));}
 @GetMapping("/tasks")public CommonResult<?> tasks(){return CommonResult.data(cases.tasks(access.current()));}
 @PostMapping("/cases/{id}/files")@CommonLog(value="上传质量私有附件",safe=true)public CommonResult<?> upload(@PathVariable String id,@RequestParam MultipartFile file){return CommonResult.data(cases.upload(access.current(),id,file));}
 @GetMapping("/files/{id}")public ResponseEntity<byte[]> file(@PathVariable String id){var f=cases.download(access.current(),id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.mime())).header(HttpHeaders.CACHE_CONTROL,"no-store").header("X-Content-Type-Options","nosniff").header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=quality-"+id).body(f.bytes());}
 @GetMapping("/risks")public CommonResult<?> riskPage(@RequestParam(defaultValue="1")int current,@RequestParam(defaultValue="20")int size){return CommonResult.data(risks.page(access.current(),current,size));}
 @GetMapping("/risks/{id}")public CommonResult<?> riskDetail(@PathVariable String id){return CommonResult.data(risks.detail(access.current(),id));}
 @PostMapping("/risks")@CommonLog(value="登记有据内部合规风险",safe=true)public CommonResult<?> risk(@Valid @RequestBody Risk p){return CommonResult.data(risks.create(access.current(),p));}
 @PostMapping("/risks/{id}/assess")@CommonLog(value="评估内部风险等级",safe=true)public CommonResult<?> assess(@PathVariable String id,@Valid @RequestBody Assess p){return CommonResult.data(risks.assess(access.current(),id,p));}
 @PostMapping("/risks/{id}/freeze")@CommonLog(value="将高风险冻结建议提交原审批流程",safe=true)public CommonResult<?> freeze(@PathVariable String id,@Valid @RequestBody Assess p){return CommonResult.data(risks.freeze(access.current(),id,p));}
 @PostMapping("/risks/{id}/dispose")@CommonLog(value="归档内部风险处置",safe=true)public CommonResult<?> dispose(@PathVariable String id,@Valid @RequestBody Dispose p){return CommonResult.data(risks.dispose(access.current(),id,p));}
 @GetMapping("/policy")public CommonResult<?> policy(){return CommonResult.data(risks.policy(access.current()));}
 @PostMapping("/policy")@CommonLog(value="发布质量规则版本",safe=true)public CommonResult<?> policy(@Valid @RequestBody Policy p){return CommonResult.data(risks.publishPolicy(access.current(),p));}
 @GetMapping("/notices")public CommonResult<?> notices(){return CommonResult.data(risks.notices(access.current()));}
 @GetMapping("/source-mappings")public CommonResult<?> mappings(){return CommonResult.data(sources.mappings(access.current()));}
 @PostMapping("/source-mappings")@CommonLog(value="配置QMS供方映射",safe=true)public CommonResult<?> mapping(@Valid @RequestBody QualityInputs.Mapping p){sources.map(access.current(),p);return CommonResult.ok();}
 @GetMapping("/source-preview")public CommonResult<?> preview(@RequestParam String supplierId,@RequestParam String period,@RequestParam(defaultValue="SANDBOX")String mode){return CommonResult.data(sources.preview(access.current(),supplierId,period,mode));}
 @PostMapping("/source-publish")@CommonLog(value="按ERP及QMS可追溯来源重算绩效",safe=true)public CommonResult<?> publish(@Valid @RequestBody Publish p){return CommonResult.data(sources.publish(access.current(),p));}
 @PostMapping("/sandbox/source")@CommonLog(value="请求本机沙箱质量来源事件",safe=true)public CommonResult<?> demo(@Valid @RequestBody Demo p){return CommonResult.data(sources.demo(access.current(),p));}
 @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class}) public CommonResult<?> invalid(){return CommonResult.get(400,"请检查质量风险必填项、数值精度与字段范围",null);}
}
