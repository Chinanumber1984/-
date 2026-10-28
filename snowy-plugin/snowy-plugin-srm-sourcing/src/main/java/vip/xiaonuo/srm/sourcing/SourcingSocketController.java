package vip.xiaonuo.srm.sourcing;
import java.security.Principal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.messaging.handler.annotation.*;
import jakarta.validation.Valid;
import vip.xiaonuo.common.exception.CommonException;
@Controller @RequiredArgsConstructor
public class SourcingSocketController {
 private final SourcingSocketHub hub;private final SourcingAuctionService auctions;
 @MessageMapping("/bid/submit") public void bid(@Valid SourcingInputs.Bid p,Principal principal){var c=(SourcingSocketHub.Connection)principal;try{SourcingRules.require(c.rfqId.equals(p.rfqId()),403,"票据不属于当前场次");var a=hub.actor(c);var receipt=auctions.bid(a,p);hub.ack(c,Map.of("code",200,"requestKey",p.requestKey(),"receipt",receipt));}catch(CommonException e){hub.ack(c,Map.of("code",e.getCode(),"requestKey",p.requestKey(),"message",e.getMsg()));}catch(Exception e){hub.ack(c,Map.of("code",503,"requestKey",p.requestKey(),"message","提交暂未确认，请用原请求键查询收据后重试"));}}
 @MessageMapping("/bid/snapshot") public void snapshot(Principal principal){hub.snapshot((SourcingSocketHub.Connection)principal);}
 @MessageExceptionHandler(Exception.class) public void invalid(Principal principal){if(principal instanceof SourcingSocketHub.Connection c)hub.ack(c,Map.of("code",400,"message","实时请求无效，请检查字段后重试"));}
}
