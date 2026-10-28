package vip.xiaonuo.srm.collaboration;
import java.util.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public final class CollaborationInputs {
 public record Version(@Min(0)long rowVersion){}
 public record Confirmation(@Min(0)long rowVersion,@NotBlank @Size(max=1000)String comment){}
 public record ProposedLine(@NotBlank String lineId,@NotNull @DecimalMin("0.001") @DecimalMax("1000000000") @Digits(integer=10,fraction=3)BigDecimal quantity,@NotNull LocalDate dueDate){}
 public record Change(@NotBlank @Size(min=8,max=64)String requestKey,@Min(0)long rowVersion,@NotBlank @Size(max=1000)String reason,@NotEmpty @Size(max=200)List<@Valid ProposedLine> lines){}
 public record ShipmentLine(@NotBlank @Pattern(regexp="[0-9]{1,20}")String poLineId,@NotBlank @Size(max=80)String batchNo,@NotNull @DecimalMin("0.001") @DecimalMax("1000000000") @Digits(integer=10,fraction=3)BigDecimal quantity){}
 public record Shipment(@NotBlank @Size(min=8,max=64)String requestKey,@Min(0)long rowVersion,@Size(max=64)String asnNo,@Min(0)Long eta,@Size(max=80)String carrier,@Size(max=96)String trackingNo,@NotEmpty @Size(max=200)List<@Valid ShipmentLine> lines){}
 public record Tracking(@Min(0)long rowVersion,@Min(0)Long eta,@Size(max=80)String carrier,@Size(max=96)String trackingNo){}
 public record ReceivedLine(@NotBlank @Pattern(regexp="[0-9]{1,20}")String asnLineId,@NotNull @DecimalMin("0.001") @DecimalMax("1000000000") @Digits(integer=10,fraction=3)BigDecimal quantity){}
 public record Receipt(@NotBlank @Size(min=8,max=64)String requestKey,@Size(max=64)String receiptNo,@Min(0)long rowVersion,@Min(0)long receivedAt,boolean finalReceipt,@NotEmpty @Size(max=200)List<@Valid ReceivedLine> lines){}
 public record Policy(@Min(0)long expectedVersion,@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=3)BigDecimal tolerancePct,@NotBlank @Size(max=500)String reason){}
 public record Reconciliation(@NotBlank @Size(min=8,max=64)String requestKey,@NotBlank @Pattern(regexp="[0-9]{1,20}")String supplierId,@Size(max=64)String recNo,@NotEmpty @Size(max=500)List<@Pattern(regexp="[0-9]{1,20}")String> receiptLineIds){}
 public record Opinion(@Min(0)long rowVersion,@NotBlank @Size(max=2000)String comment,@DecimalMin("0") @Digits(integer=12,fraction=3)BigDecimal claimedQuantity,@DecimalMin("0") @Digits(integer=11,fraction=2)BigDecimal claimedAmount){}
 public record CaseDecision(@NotBlank @Pattern(regexp="APPROVE|REJECT")String action,@NotBlank @Size(max=1000)String comment){}
 public record Invoice(@NotBlank @Size(min=8,max=64)String requestKey,@Min(0)long rowVersion,@NotBlank @Size(max=64)String invoiceNo,@NotNull @DecimalMin("0.01") @DecimalMax("90000000000") @Digits(integer=11,fraction=2)BigDecimal amount,@NotBlank @Pattern(regexp="[0-9]{1,20}")String fileId){}
 public record Revise(@Min(0)long rowVersion,@NotBlank @Pattern(regexp="[0-9]{1,20}")String fileId,@NotBlank @Size(max=1000)String reason){}
 public record Mapping(@NotBlank String connectorId,@NotBlank @Size(max=80)String externalKey,@NotBlank String supplierId){}
 public record Seed(@NotBlank @Size(min=8,max=64)String requestKey,@NotBlank String supplierId,@NotNull @DecimalMin("0.001") @DecimalMax("1000000000") @Digits(integer=10,fraction=3)BigDecimal quantity,@NotNull @DecimalMin("0.0001") @DecimalMax("9999999") @Digits(integer=7,fraction=4)BigDecimal unitPrice,@NotNull LocalDate dueDate){}
 public record SandboxCommand(@NotBlank @Size(min=8,max=64)String requestKey,@NotBlank @Pattern(regexp="PO_CHANGE|RECEIPT|MES_PRIORITY|PAYMENT|FAIL_NEXT")String type,@NotBlank String businessId,@NotNull Map<String,Object> data){}
 // Normalized source records. Vendor-specific mapping belongs outside the business aggregate.
 public record SourceLine(@NotBlank @Size(max=40)String lineKey,@NotBlank @Size(max=128)String material,@NotNull @DecimalMin("0.001") @DecimalMax("1000000000") @Digits(integer=10,fraction=3)BigDecimal quantity,@NotNull @DecimalMin("0.0001") @DecimalMax("9999999") @Digits(integer=7,fraction=4)BigDecimal unitPrice,@NotNull LocalDate dueDate){}
 public record SourcePo(@NotBlank @Size(max=64)String poNo,@NotBlank @Size(max=80)String supplierKey,@NotBlank @Pattern(regexp="CNY|USD|EUR")String currency,boolean cancelled,@Size(max=20)String changeRequestId,@NotEmpty @Size(max=200)List<@Valid SourceLine> lines){}
 public record SourceReceipt(@NotBlank String asnNo,@NotBlank @Size(max=64)String receiptNo,@Min(1)long receivedAt,boolean finalReceipt,@NotEmpty @Size(max=200)List<@Valid ReceivedLine> lines){}
 public record SourceAck(@NotBlank String invoiceId,@Min(1)long revision,@NotBlank String outboxId,@NotBlank @Pattern(regexp="ACCEPTED|REJECTED")String result,@NotBlank @Size(max=128)String remoteRef,@Size(max=1000)String message){}
 public record SourcePayment(@NotBlank String invoiceId,@NotBlank @Size(max=96)String paymentRef,@NotNull @DecimalMin("0.01") @DecimalMax("90000000000") @Digits(integer=11,fraction=2)BigDecimal amount,@NotBlank @Pattern(regexp="CNY|USD|EUR")String currency,@Min(1)long paidAt){}
 public record SourceMes(@NotBlank String poNo,@Min(0) @Max(100)int priority,@NotBlank @Size(max=128)String sourceRef,@NotNull @DecimalMin("0")BigDecimal requiredQuantity,@NotNull @DecimalMin("0")BigDecimal consumedQuantity,@NotNull LocalDate scheduledDate){}
}
