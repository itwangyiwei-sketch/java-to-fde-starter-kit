package dev.javatofde.starter.api;

import dev.javatofde.starter.model.DemoFault;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReconcileRequest(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{1,30}") String tenantId,
        @NotBlank @Size(max = 500) String query,
        DemoFault demoFault
) {
    public DemoFault effectiveFault() {
        return demoFault == null ? DemoFault.NONE : demoFault;
    }
}
