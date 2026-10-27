package vip.xiaonuo.srm.workspace;
import java.time.LocalDate;
import java.util.Set;
import jakarta.validation.constraints.*;
public final class SrmInputs {
 public record SupplierCreate(@Pattern(regexp="(?:[A-Z0-9][A-Z0-9_-]{2,31})?") String supplierCode,@NotBlank @Size(max=128) String name,@NotBlank String category,@NotBlank @Size(min=18,max=18) String creditCode,@Size(max=64) String bankAccount,@Size(max=64) String bankName,@Size(max=255) String address){}
 public record SupplierEdit(@NotBlank @Size(max=128) String name,@NotBlank String category,@Size(max=18) String creditCode,@Size(max=64) String bankAccount,@Size(max=64) String bankName,@Size(max=255) String address,@Min(0) long rowVersion){}
 public record CertificateInput(@NotBlank String certType,@NotBlank @Size(max=64) String certNo,@NotNull LocalDate validFrom,@NotNull LocalDate validTo,@NotBlank String fileId,String previousId){}
 public record ContactInput(@NotBlank @Size(max=32) String name,@Pattern(regexp="^1[3-9][0-9]{9}$") @NotBlank String phone,@Email @Size(max=64) String email,@NotEmpty Set<String> roles,boolean enabled,@Min(0) long rowVersion){}
 public record ContactAccess(@NotEmpty Set<String> roles,boolean enabled,@Min(0) long rowVersion){}
 public record AccountInput(@NotBlank @Pattern(regexp="[A-Za-z0-9_.-]{4,64}") String account,@NotBlank @Size(max=2048) String passwordCipher){}
 public record CategoryInput(@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{1,31}") String code,@NotBlank @Size(max=64) String name,@NotNull Set<String> requiredCerts,boolean enabled,long rowVersion){}
 public record TierInput(@NotBlank String tier,long rowVersion){}
}
