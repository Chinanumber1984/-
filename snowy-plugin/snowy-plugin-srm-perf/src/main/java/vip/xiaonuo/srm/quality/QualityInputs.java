package vip.xiaonuo.srm.quality;
import java.util.*;import java.math.BigDecimal;import jakarta.validation.Valid;import jakarta.validation.constraints.*;
public final class QualityInputs {
 public record Draft(@NotBlank @Size(min=8,max=64)String requestKey,@NotBlank String supplierId,@NotBlank @Size(max=128)String title){}
 public record Scar(@NotBlank @Size(min=8,max=64)String requestKey,@NotBlank String supplierId,@NotBlank @Size(max=128)String title,@NotBlank @Size(max=80)String defectKey,@NotBlank @Size(max=2000)String description,@NotBlank @Pattern(regexp="MINOR|MAJOR|SEVERE")String severity){}
 public record Item(@NotBlank @Size(max=128)String name,@NotBlank String fileId){}
 public record Ppap(@Min(0)long rowVersion,@NotEmpty @Size(max=50)List<@Valid Item> items,@NotBlank @Size(max=1000)String comment){}
 public record EightD(@Min(0)long rowVersion,@NotNull @Size(min=8,max=8)Map<@Pattern(regexp="D[1-8]")String,@NotBlank @Size(max=3000)String> disciplines,@NotEmpty @Size(max=20)List<String> fileIds){}
 public record Review(@NotBlank @Pattern(regexp="APPROVE|REJECT|RETURN")String action,@NotBlank @Size(max=1000)String comment){}
 public record Close(@Min(0)long rowVersion,@NotBlank @Size(max=2000)String effectiveness,@NotBlank @Size(max=500)String evidenceRef){}
 public record Risk(@NotBlank @Size(min=8,max=64)String requestKey,@NotBlank String supplierId,@NotBlank @Size(max=2000)String description,@NotBlank @Size(max=500)String evidenceRef){}
 public record Assess(@Min(0)long rowVersion,@NotBlank @Pattern(regexp="HIGH|MEDIUM|LOW")String level,@NotBlank @Size(max=2000)String comment){}
 public record Dispose(@Min(0)long rowVersion,@NotBlank @Pattern(regexp="RECTIFIED|MONITOR|APPROVAL_SUBMITTED")String action,@NotBlank @Size(max=2000)String comment,@NotBlank @Size(max=500)String evidenceRef){}
 public record Policy(@Min(0)long expectedVersion,@Min(1) @Max(365)int responseDays,@Min(1) @Max(365)int repeatWindowDays,@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2)BigDecimal minorDeduction,@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2)BigDecimal majorDeduction,@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2)BigDecimal severeDeduction,@NotBlank @Size(max=500)String reason){}
 public record Mapping(@NotBlank String connectorId,@NotBlank @Size(max=80)String externalKey,@NotBlank String supplierId){}
 public record Inspection(@NotBlank @Size(max=80)String supplierKey,@NotBlank String period,@NotBlank @Pattern(regexp="PASS|FAIL")String result,@NotBlank @Pattern(regexp="MINOR|MAJOR|SEVERE")String severity,@NotBlank @Size(max=500)String sourceRef,@NotBlank @Size(max=2000)String description){}
 public record ErpScores(@NotBlank @Size(max=80)String supplierKey,@NotBlank String period,@NotNull @Size(min=4,max=4)List<@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2)BigDecimal> scores,@NotBlank @Size(max=500)String sourceRef){}
 public record Publish(@NotBlank String supplierId,@NotBlank String period,@NotBlank @Pattern(regexp="SANDBOX|LIVE")String mode,@NotBlank String basisHash){}
 public record Demo(@NotBlank @Size(min=8,max=64)String requestKey,@NotBlank String supplierId,@NotBlank String period,@NotBlank @Pattern(regexp="QMS|ERP")String system,@NotBlank @Pattern(regexp="PASS|FAIL")String result,@NotBlank @Pattern(regexp="MINOR|MAJOR|SEVERE")String severity){}
}
