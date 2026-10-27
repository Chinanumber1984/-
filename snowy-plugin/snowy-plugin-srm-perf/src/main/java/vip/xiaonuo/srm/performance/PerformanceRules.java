package vip.xiaonuo.srm.performance;
import java.math.*;
import java.time.*;
import java.util.*;
import vip.xiaonuo.common.exception.CommonException;
public final class PerformanceRules {
 private PerformanceRules(){}
 static void require(boolean ok,int code,String msg){if(!ok)throw new CommonException(code,msg);}
 public static YearMonth period(String value){try{require(value!=null&&value.matches("[0-9]{4}-(0[1-9]|1[0-2])"),400,"月份格式为YYYY-MM");var p=YearMonth.parse(value);require(p.getYear()>=2000&&!p.isAfter(YearMonth.now(ZoneId.of("Asia/Shanghai"))),400,"月份须在2000年至当前月之间");return p;}catch(DateTimeException ex){throw new CommonException(400,"月份格式不正确");}}
 public static void templatePeriod(String value){try{require(value!=null&&value.matches("[0-9]{4}-(0[1-9]|1[0-2])"),400,"模板月份格式为YYYY-MM");var p=YearMonth.parse(value);require(p.getYear()>=2000&&!p.isAfter(YearMonth.now(ZoneId.of("Asia/Shanghai")).plusMonths(12)),400,"模板最多提前12个月发布");}catch(DateTimeException ex){throw new CommonException(400,"模板月份格式不正确");}}
 public static void scores(List<BigDecimal> values){require(values!=null&&values.size()==4&&values.stream().allMatch(v->v!=null&&v.signum()>=0&&v.compareTo(new BigDecimal("100"))<=0&&v.stripTrailingZeros().scale()<=2),400,"四项分值必须为0~100且最多两位小数");}
 public static void template(List<BigDecimal> weights,List<BigDecimal> thresholds){scores(weights);scores(thresholds);require(weights.stream().reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(new BigDecimal("100"))==0,400,"全司统一权重之和必须为100");for(int i=1;i<4;i++)require(thresholds.get(i-1).compareTo(thresholds.get(i))>0,400,"等级阈值必须严格递减");require(thresholds.get(3).signum()>0,400,"观察阈值必须大于0");}
 public static BigDecimal total(List<BigDecimal> scores,List<BigDecimal> weights){scores(scores);BigDecimal sum=BigDecimal.ZERO;for(int i=0;i<4;i++)sum=sum.add(scores.get(i).multiply(weights.get(i)));return sum.divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);}
 public static String grade(BigDecimal total,List<BigDecimal> thresholds){String[] grades={"STRATEGIC","PREFERRED","QUALIFIED","WATCH"};for(int i=0;i<4;i++)if(total.compareTo(thresholds.get(i))>=0)return grades[i];return "ELIMINATION_SUGGESTED";}
}
