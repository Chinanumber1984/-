package vip.xiaonuo.srm.performance;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
public final class PerformanceInputs {
 private PerformanceInputs(){}
 public record Template(@NotNull @Min(1) Long expectedVersion,@NotBlank String effectivePeriod,@NotNull @Size(min=4,max=4) List<@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal> weights,@NotNull @Size(min=4,max=4) List<@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal> thresholds) {}
 public record Input(@NotBlank @Pattern(regexp="[0-9]{1,20}") String supplierId,@NotBlank String period,@NotBlank @Pattern(regexp="HISTORICAL|SANDBOX") String sourceType,@NotBlank @Size(max=256) String sourceRef,@NotNull @Size(min=4,max=4) List<@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal> scores) {}
 public record Import(@NotBlank @Pattern(regexp="[A-Za-z0-9_.:-]{8,64}") String requestKey,@NotEmpty @Size(max=500) List<@NotNull @Valid Input> rows) {}
 public record Calculate(@NotBlank @Pattern(regexp="[0-9]{1,20}") String supplierId,@NotBlank String period) {}
 public record Appeal(@Min(1) long scoreVersion,@NotBlank @Size(max=1000) String reason) {}
 public record Review(@Min(1) long scoreVersion,@NotNull Boolean approve,@NotBlank @Size(max=1000) String comment,@Size(min=4,max=4) List<@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal> correctedScores) {}
}
