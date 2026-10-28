package vip.xiaonuo.srm.quality;
import java.time.*;import lombok.RequiredArgsConstructor;import lombok.extern.slf4j.Slf4j;import org.springframework.stereotype.Component;import org.springframework.scheduling.annotation.Scheduled;import static vip.xiaonuo.srm.quality.QualityStore.*;
@Component @RequiredArgsConstructor @Slf4j public class QualitySourceMonthlyJob {
 final QualityStore x;final QualitySources sources;
 @Scheduled(cron="0 30 2 1 * ?",zone="Asia/Shanghai") public void run(){String period=YearMonth.now(ZoneId.of("Asia/Shanghai")).minusMonths(1).toString();int completed=0,failed=0;for(var r:x.db.queryForList("SELECT DISTINCT ORG_ID,SUPPLIER_ID,MODE FROM SRM_QL_ERP_SCORE WHERE PERIOD=? ORDER BY SUPPLIER_ID",period)){try{var a=x.system(s(r,"ORG_ID"),"SYSTEM");var p=sources.preview(a,s(r,"SUPPLIER_ID"),period,s(r,"MODE"));if(Boolean.TRUE.equals(p.get("ready"))){sources.publish(a,new QualityInputs.Publish(s(r,"SUPPLIER_ID"),period,s(r,"MODE"),String.valueOf(p.get("basisHash"))));completed++;}}catch(Exception e){failed++;}}log.info("Quality source monthly period={}, completed={}, failed={}",period,completed,failed);}
}
