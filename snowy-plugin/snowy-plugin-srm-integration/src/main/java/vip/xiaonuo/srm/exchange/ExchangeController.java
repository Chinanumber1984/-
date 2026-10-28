package vip.xiaonuo.srm.exchange;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import vip.xiaonuo.common.annotation.CommonLog;
import vip.xiaonuo.common.pojo.CommonResult;
import vip.xiaonuo.srm.api.SrmSupplierAccessApi;
import vip.xiaonuo.srm.collaboration.api.SrmCollaborationIngressApi;

@RestController @RequiredArgsConstructor
public class ExchangeController {
 private final ExchangeStore x;private final ExchangeService service;private final ExchangeEngine engine;
 private final SrmSupplierAccessApi access;private final SrmCollaborationIngressApi business;
 @GetMapping("/srm/v1/integration/connectors") public CommonResult<?> connectors(){return CommonResult.data(service.connectors(access.current()));}
 @PostMapping("/srm/v1/integration/connectors") @CommonLog(value="配置集成连接",safe=true) public CommonResult<?> create(@Valid @RequestBody ExchangeService.ConnectorInput p){return CommonResult.data(service.create(access.current(),p));}
 @PostMapping("/srm/v1/integration/connectors/{id}/enabled") @CommonLog(value="切换集成连接",safe=true) public CommonResult<?> enabled(@PathVariable String id,@RequestBody ExchangeService.Toggle p){return CommonResult.data(service.toggle(access.current(),id,p.enabled()));}
 @GetMapping("/srm/v1/integration/monitor") public CommonResult<?> monitor(@RequestParam(defaultValue="1")int current,@RequestParam(defaultValue="20")int size){return CommonResult.data(service.monitor(access.current(),current,size));}
 @PostMapping("/srm/v1/integration/{kind}/{id}/retry") @CommonLog(value="重试原集成事件",safe=true) public CommonResult<?> retry(@PathVariable String kind,@PathVariable String id){service.retry(access.current(),kind,id);return CommonResult.ok();}
 @PostMapping("/srm-integration/v1/ingress/{id}") @CommonLog(value="接收签名业务事件",safe=true) public CommonResult<?> ingress(@PathVariable String id,@RequestBody String raw,HttpServletRequest request){var c=x.authenticate(id,"POST",request.getRequestURI(),request.getHeader("X-SRM-Timestamp"),request.getHeader("X-SRM-Nonce"),request.getHeader("X-SRM-Signature"),raw);return CommonResult.data(engine.receive(c,raw));}
 @GetMapping("/srm-integration/v1/invoices/{id}/revisions/{revision}/file") public ResponseEntity<byte[]> file(@PathVariable String id,@PathVariable long revision,HttpServletRequest request){var c=x.authenticate(request.getHeader("X-SRM-Connector"),"GET",request.getRequestURI(),request.getHeader("X-SRM-Timestamp"),request.getHeader("X-SRM-Nonce"),request.getHeader("X-SRM-Signature"),"");var f=business.invoiceFile(x.origin(c),id,revision);return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.mime())).header(HttpHeaders.CACHE_CONTROL,"no-store").header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=invoice-"+id+".bin").body(f.bytes());}
 @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class}) public CommonResult<?> invalid(){return CommonResult.get(400,"集成字段格式或取值无效",null);}
}
