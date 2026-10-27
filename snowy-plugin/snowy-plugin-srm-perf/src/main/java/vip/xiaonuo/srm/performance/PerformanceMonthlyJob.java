package vip.xiaonuo.srm.performance;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import vip.xiaonuo.srm.api.SrmSupplierAccessApi.Context;
@Component @RequiredArgsConstructor @Slf4j
public class PerformanceMonthlyJob {
 private final PerformanceService service;private final JdbcTemplate db;
 @Value("${srm.performance.scheduler-enabled:true}") private boolean enabled;
 @Scheduled(cron="${srm.performance.cron:0 0 3 1 * ?}",zone="Asia/Shanghai")
 public void run(){if(!enabled)return;String period=YearMonth.now(ZoneId.of("Asia/Shanghai")).minusMonths(1).toString();int failed=0,computed=0;
  for(var r:db.queryForList("SELECT DISTINCT SUPPLIER_ID,ORG_ID FROM SRM_PERF_INPUT WHERE PERIOD=? ORDER BY SUPPLIER_ID",period)){
   String org=String.valueOf(r.get("ORG_ID"));var actor=new Context("SYSTEM",org,null,Set.of("SQE"),false,Set.of(org),false,false);
   try{service.calculate(actor,String.valueOf(r.get("SUPPLIER_ID")),period);computed++;}catch(Exception ex){failed++;}
  }
  // No source references, personal data or credentials in job logs.
  log.info("Performance monthly job period={}, completed={}, failed={}",period,computed,failed);
 }
}
