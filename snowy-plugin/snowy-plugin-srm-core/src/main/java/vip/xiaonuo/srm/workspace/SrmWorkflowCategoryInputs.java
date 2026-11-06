package vip.xiaonuo.srm.workspace;

import jakarta.validation.constraints.*;

public final class SrmWorkflowCategoryInputs {
 private SrmWorkflowCategoryInputs(){}
 public record Update(@Min(0) long expectedVersion,
                      @NotBlank @Pattern(regexp="[A-Z][A-Z0-9_-]{1,39}") String categoryCode,
                      @NotBlank @Size(max=80) String categoryName){}
}
