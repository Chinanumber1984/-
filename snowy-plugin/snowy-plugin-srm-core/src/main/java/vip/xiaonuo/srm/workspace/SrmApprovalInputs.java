package vip.xiaonuo.srm.workspace;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public final class SrmApprovalInputs {
 public record Submit(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{12,64}") String requestKey,@NotBlank String kind,@Min(0) long rowVersion,@Size(max=1000) String reason,@Valid SrmInputs.SupplierEdit changes){}
 public record Decision(@NotBlank String action,@NotBlank @Size(max=1000) String comment,@Pattern(regexp="[0-9]{1,20}") String targetId){}
 public record Withdrawal(@NotBlank @Size(max=1000) String comment){}
 public record Publication(@NotBlank String kind){}
 public record Suspension(boolean suspended){}
}
