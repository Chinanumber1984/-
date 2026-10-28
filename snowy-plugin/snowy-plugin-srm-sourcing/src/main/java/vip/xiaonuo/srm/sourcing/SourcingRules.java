package vip.xiaonuo.srm.sourcing;
import java.math.*;
import java.util.*;
import vip.xiaonuo.common.exception.CommonException;
public final class SourcingRules {
 public static final long MAX_CENTS=9_000_000_000_000L;
 private SourcingRules(){}
 public static void require(boolean ok,int code,String message){if(!ok)throw new CommonException(code,message);}
 public static long cents(BigDecimal amount){try{long v=amount.movePointRight(2).longValueExact();require(v>0&&v<=MAX_CENTS,400,"金额须大于0，最多两位小数且不超过900亿");return v;}catch(ArithmeticException|NullPointerException e){throw new CommonException(400,"金额精度或范围错误");}}
 public static BigDecimal money(long cents){return BigDecimal.valueOf(cents,2);}
 public static long bidEnd(long now,long start,long end,int window,long anchor,long step,long offered){require(now>=start&&now<end,409,"竞价未开始或已截止");require(offered>0&&step>0&&offered<=anchor-step,409,"出价未满足最小降幅");return window>0&&end-now<=window*60000L?Math.addExact(end,window*60000L):end;}
 public static BigDecimal total(List<BigDecimal> units,List<BigDecimal> quantities,BigDecimal freight){require(units.size()==quantities.size()&&!units.isEmpty(),400,"报价物料与RFQ不一致");BigDecimal total=freight;for(int i=0;i<units.size();i++)total=total.add(units.get(i).multiply(quantities.get(i)).setScale(2,RoundingMode.HALF_UP));total=total.setScale(2,RoundingMode.HALF_UP);cents(total);return total;}
 public static BigDecimal costScore(BigDecimal minimum,BigDecimal value){return minimum.multiply(new BigDecimal("100")).divide(value,6,RoundingMode.HALF_UP);}
 public static BigDecimal weighted(BigDecimal cost,List<BigDecimal> performance,List<BigDecimal> weights){require(weights.size()==4&&performance.size()==4,400,"评分输入不完整");return performance.get(0).multiply(weights.get(0)).add(performance.get(1).multiply(weights.get(1))).add(cost.multiply(weights.get(2))).add(performance.get(3).multiply(weights.get(3))).divide(new BigDecimal("100"),4,RoundingMode.HALF_UP);}
}
