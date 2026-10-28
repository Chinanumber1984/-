package vip.xiaonuo.srm.collaboration;
import java.math.*;
import vip.xiaonuo.common.exception.CommonException;
public final class CollaborationRules {
 private CollaborationRules(){}
 public static void require(boolean valid,int code,String text){if(!valid)throw new CommonException(code,text);}
 public static BigDecimal money(BigDecimal amount){require(amount!=null&&amount.signum()>=0&&amount.compareTo(new BigDecimal("90000000000"))<=0,400,"金额范围无效");try{return amount.setScale(2,RoundingMode.UNNECESSARY);}catch(ArithmeticException e){throw new CommonException(400,"金额最多两位小数");}}
 public static BigDecimal line(BigDecimal quantity,BigDecimal price){return money(quantity.multiply(price).setScale(2,RoundingMode.HALF_UP));}
 public static boolean differs(BigDecimal expected,BigDecimal actual,BigDecimal pct,boolean closed){require(expected.signum()>0&&actual.signum()>=0&&pct.signum()>=0&&pct.compareTo(new BigDecimal("100"))<=0,400,"容差输入无效");BigDecimal delta=actual.subtract(expected);if(delta.signum()<0&&!closed)return false;return delta.abs().multiply(new BigDecimal("100")).compareTo(expected.multiply(pct))>0;}
 public static void invoiceMatches(BigDecimal expected,BigDecimal invoice){require(money(expected).compareTo(money(invoice))==0,409,"发票金额与已确认对账单不匹配");}
}
