package vip.xiaonuo.srm.collaboration;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import vip.xiaonuo.common.pojo.CommonResult;
import vip.xiaonuo.common.annotation.CommonLog;
import vip.xiaonuo.srm.api.SrmSupplierAccessApi;
import static vip.xiaonuo.srm.collaboration.CollaborationInputs.*;
@RestController @RequiredArgsConstructor @RequestMapping({"/srm/v1/collaboration","/client/c/srm/v1/collaboration"})
public class CollaborationController {
 final SrmSupplierAccessApi access;final OrdersService orders;final SettlementService settlements;final InvoiceService invoices;final CollaborationCases cases;final CollaborationSandbox sandbox;
 @GetMapping("/orders") public CommonResult<?> get0(@RequestParam(defaultValue="1")int current,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String searchKey,@RequestParam(defaultValue="")String status){return CommonResult.data(orders.page(access.current(),current,size,searchKey,status));}
 @GetMapping("/orders/{id}") public CommonResult<?> get1(@PathVariable String id){return CommonResult.data(orders.detail(access.current(),id));}
 @GetMapping("/asns/{id}") public CommonResult<?> get2(@PathVariable String id){return CommonResult.data(orders.asn(access.current(),id));}
 @GetMapping("/receipts") public CommonResult<?> get3(@RequestParam(defaultValue="")String supplierId){return CommonResult.data(orders.receipts(access.current(),supplierId));}
 @GetMapping("/dashboard") public CommonResult<?> get4(){return CommonResult.data(orders.dashboard(access.current()));}
 @GetMapping("/notices") public CommonResult<?> get5(){return CommonResult.data(orders.notices(access.current()));}
 @GetMapping("/policy") public CommonResult<?> get6(){return CommonResult.data(orders.policy(access.current()));}
 @GetMapping("/mappings") public CommonResult<?> get7(){return CommonResult.data(orders.mappings(access.current()));}
 @GetMapping("/reconciliations") public CommonResult<?> get8(@RequestParam(defaultValue="1")int current,@RequestParam(defaultValue="20")int size){return CommonResult.data(settlements.page(access.current(),current,size));}
 @GetMapping("/reconciliations/{id}") public CommonResult<?> get9(@PathVariable String id){return CommonResult.data(settlements.detail(access.current(),id));}
 @GetMapping("/invoices/{id}") public CommonResult<?> get10(@PathVariable String id){return CommonResult.data(invoices.detail(access.current(),id));}
 @GetMapping("/cases/{id}") public CommonResult<?> get11(@PathVariable String id){return CommonResult.data(cases.detail(access.current(),id));}
 @GetMapping("/tasks") public CommonResult<?> get12(){return CommonResult.data(cases.tasks(access.current()));}
 @PostMapping("/orders/{id}/confirm") @CommonLog(value="采购协同操作",safe=true) public CommonResult<?> post0(@PathVariable String id,@Valid @RequestBody Confirmation p){return CommonResult.data(orders.confirm(access.current(),id,p));}
 @PostMapping("/orders/{id}/change") @CommonLog(value="采购协同操作",safe=true) public CommonResult<?> post1(@PathVariable String id,@Valid @RequestBody Change p){return CommonResult.data(orders.change(access.current(),id,p));}
 @PostMapping("/orders/{id}/asns") @CommonLog(value="采购协同操作",safe=true) public CommonResult<?> post2(@PathVariable String id,@Valid @RequestBody Shipment p){return CommonResult.data(orders.shipment(access.current(),id,p));}
 @PostMapping("/asns/{id}/tracking") @CommonLog(value="采购协同操作",safe=true) public CommonResult<?> post3(@PathVariable String id,@Valid @RequestBody Tracking p){return CommonResult.data(orders.tracking(access.current(),id,p));}
 @PostMapping("/asns/{id}/receive") @CommonLog(value="采购协同操作",safe=true) public CommonResult<?> post4(@PathVariable String id,@Valid @RequestBody Receipt p){return CommonResult.data(orders.receive(access.current(),id,p));}
 @PostMapping("/policy") @CommonLog(value="采购协同操作",safe=true) public CommonResult<?> post5(@Valid @RequestBody Policy p){return CommonResult.data(orders.policy(access.current(),p));}
 @PostMapping("/reconciliations") @CommonLog(value="采购协同操作",safe=true) public CommonResult<?> post6(@Valid @RequestBody Reconciliation p){return CommonResult.data(settlements.create(access.current(),p));}
 @PostMapping("/reconciliations/{id}/confirm") @CommonLog(value="采购协同操作",safe=true) public CommonResult<?> post7(@PathVariable String id,@Valid @RequestBody Confirmation p){return CommonResult.data(settlements.confirm(access.current(),id,p));}
 @PostMapping("/reconciliations/{id}/difference") @CommonLog(value="采购协同操作",safe=true) public CommonResult<?> post8(@PathVariable String id,@Valid @RequestBody Opinion p){return CommonResult.data(settlements.difference(access.current(),id,p));}
 @PostMapping("/reconciliations/{id}/discuss") @CommonLog(value="采购协同操作",safe=true) public CommonResult<?> post9(@PathVariable String id,@Valid @RequestBody Opinion p){return CommonResult.data(settlements.discuss(access.current(),id,p));}
 @PostMapping("/reconciliations/{id}/resolve") @CommonLog(value="采购协同操作",safe=true) public CommonResult<?> post10(@PathVariable String id,@Valid @RequestBody Opinion p){return CommonResult.data(settlements.resolve(access.current(),id,p));}
 @PostMapping("/reconciliations/{id}/dispute") @CommonLog(value="采购协同操作",safe=true) public CommonResult<?> post11(@PathVariable String id,@Valid @RequestBody Opinion p){return CommonResult.data(cases.submit(access.current(),"RECON_DISPUTE",id,p));}
 @PostMapping("/receipts/{id}/reversal") @CommonLog(value="采购协同操作",safe=true) public CommonResult<?> post12(@PathVariable String id,@Valid @RequestBody Opinion p){return CommonResult.data(cases.submit(access.current(),"RECEIPT_REVERSAL",id,p));}
 @PostMapping("/reconciliations/{id}/invoices") @CommonLog(value="采购协同操作",safe=true) public CommonResult<?> post13(@PathVariable String id,@Valid @RequestBody Invoice p){return CommonResult.data(invoices.register(access.current(),id,p));}
 @PostMapping("/invoices/{id}/revise") @CommonLog(value="采购协同操作",safe=true) public CommonResult<?> post14(@PathVariable String id,@Valid @RequestBody Revise p){return CommonResult.data(invoices.revise(access.current(),id,p));}
 @PostMapping("/invoices/{id}/push") @CommonLog(value="采购协同操作",safe=true) public CommonResult<?> post15(@PathVariable String id,@Valid @RequestBody Version p){return CommonResult.data(invoices.push(access.current(),id,p));}
 @PostMapping("/sandbox/orders") @CommonLog(value="采购协同操作",safe=true) public CommonResult<?> post16(@Valid @RequestBody Seed p){return CommonResult.data(sandbox.seed(access.current(),p));}
 @PostMapping("/sandbox/commands") @CommonLog(value="采购协同操作",safe=true) public CommonResult<?> post17(@Valid @RequestBody SandboxCommand p){return CommonResult.data(sandbox.command(access.current(),p));}
 @PostMapping("/invoices/{id}/payment-request") @CommonLog(value="提交财务付款申请",safe=true) public CommonResult<?> paymentRequest(@PathVariable String id,@Valid @RequestBody Version p){return CommonResult.data(invoices.requestPayment(access.current(),id,p));}
 @PostMapping("/mappings") @CommonLog(value="配置ERP供方映射",safe=true) public CommonResult<?> mapping(@Valid @RequestBody CollaborationInputs.Mapping p){orders.map(access.current(),p);return CommonResult.ok();}
 @PostMapping("/reconciliations/{id}/withdraw") @CommonLog(value="撤回对账",safe=true) public CommonResult<?> withdraw(@PathVariable String id,@Valid @RequestBody Opinion p){settlements.withdraw(access.current(),id,p);return CommonResult.ok();}
 @PostMapping("/cases/{id}/tasks/{tid}") @CommonLog(value="经理处理协同争议",safe=true) public CommonResult<?> decide(@PathVariable String id,@PathVariable String tid,@Valid @RequestBody CaseDecision p){return CommonResult.data(cases.decide(access.current(),id,tid,p));}
 @PostMapping("/cases/{id}/withdraw") @CommonLog(value="撤回协同争议",safe=true) public CommonResult<?> withdrawCase(@PathVariable String id){cases.withdraw(access.current(),id);return CommonResult.ok();}
 @PostMapping("/reconciliations/{id}/files") @CommonLog(value="上传私有发票附件",safe=true) public CommonResult<?> upload(@PathVariable String id,@RequestParam MultipartFile file){return CommonResult.data(invoices.upload(access.current(),id,file));}
 @GetMapping("/files/{id}") public ResponseEntity<byte[]> download(@PathVariable String id){var f=invoices.download(access.current(),id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.mime())).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=invoice-"+id).header("X-Content-Type-Options","nosniff").body(f.bytes());}
 @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class}) public CommonResult<?> invalid(){return CommonResult.get(400,"请检查采购协同必填项、数值精度及字段范围",null);}
}
