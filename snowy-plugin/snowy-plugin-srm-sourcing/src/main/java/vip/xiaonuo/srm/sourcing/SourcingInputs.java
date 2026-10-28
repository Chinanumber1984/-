package vip.xiaonuo.srm.sourcing;
import java.util.*;
import java.math.BigDecimal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public final class SourcingInputs {
 public record Line(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,32}")String id,@NotBlank @Size(max=128)String material,@NotNull @DecimalMin("0.001") @DecimalMax("1000000") @Digits(integer=7,fraction=3)BigDecimal quantity,@NotBlank @Size(max=64)String delivery,@Size(max=500)String specification,@DecimalMin("0.0001") @DecimalMax("9999999") @Digits(integer=7,fraction=4)BigDecimal benchmark,@NotNull @Size(max=10)List<@Pattern(regexp="[0-9]{1,20}")String> fileIds){}
 public record Rfq(@NotBlank @Size(min=8,max=64)String requestKey,@Min(0)long rowVersion,@Size(max=64)String rfqNo,@NotBlank @Size(max=128)String title,@NotBlank @Pattern(regexp="[A-Z0-9_]{1,32}")String category,@NotBlank @Pattern(regexp="COMPARE|AUCTION")String strategy,@NotBlank @Pattern(regexp="CNY|USD|EUR")String currency,@Min(1)long deadline,@Min(0)Long startAt,@NotNull @Size(min=1,max=50)List<@NotBlank @Pattern(regexp="[0-9]{1,20}")String> supplierIds,@NotNull @Size(min=1,max=100)List<@NotNull @Valid Line> lines,@DecimalMin("0.01") @DecimalMax("90000000000") @Digits(integer=11,fraction=2)BigDecimal startingPrice,@DecimalMin("0.01") @DecimalMax("90000000000") @Digits(integer=11,fraction=2)BigDecimal minDecrement,@Min(0) @Max(60)Integer antiSnipeMinutes){}
 public record Version(@Min(0)long rowVersion){}
 public record Extend(@Min(0)long rowVersion,@Min(1)long deadline,@NotBlank @Size(max=1000)String reason){}
 public record QuoteLine(@NotBlank String lineId,@NotNull @DecimalMin("0.0001") @DecimalMax("9999999") @Digits(integer=7,fraction=4)BigDecimal unitPrice,@Min(1) @Max(3650)int leadDays){}
 public record Quote(@NotBlank @Size(min=8,max=64)String requestKey,@Min(0)long expectedRound,@NotEmpty @Size(max=100)List<@NotNull @Valid QuoteLine> lines,@NotNull @DecimalMin("0") @DecimalMax("90000000000") @Digits(integer=11,fraction=2)BigDecimal freight,@Size(max=1000)String remark){}
 public record Bid(@NotBlank @Pattern(regexp="[0-9]{1,20}")String rfqId,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,64}")String requestKey,@Min(0)long expectedVersion,@NotNull @DecimalMin("0.01") @DecimalMax("90000000000") @Digits(integer=11,fraction=2)BigDecimal amount){}
 public record Question(boolean isPublic,@Pattern(regexp="[0-9]{1,20}")String supplierId,@NotBlank @Size(max=2000)String question){}
 public record Answer(@NotBlank @Size(max=2000)String answer){}
 public record Risk(@NotBlank @Pattern(regexp="LOW|MEDIUM|HIGH")String level,@NotBlank @Size(max=256)String evidenceRef,@NotBlank @Size(max=1000)String note){}
 public record Award(@NotBlank @Pattern(regexp="[0-9]{1,20}")String supplierId,@NotBlank @Size(min=8,max=64)String requestKey,@NotBlank @Size(max=1000)String reason,@Size(max=1000)String noHistoryReason,boolean discloseLoss){}
 public record Decision(@NotBlank @Pattern(regexp="APPROVE|REJECT|TRANSFER|REASSIGN")String action,@NotBlank @Size(max=1000)String comment,@Pattern(regexp="[0-9]{1,20}")String targetId,boolean approveNoHistory,boolean acknowledgeRisk,@Size(max=1000)String noHistoryReason){}
 public record Ticket(@NotBlank @Pattern(regexp="[0-9]{1,20}")String rfqId){}
}
