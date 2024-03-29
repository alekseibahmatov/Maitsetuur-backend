package ee.maitsetuur.request.payment;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BusinessInformation {
    @NotNull
    private String businessName;

    @NotNull
    private Integer registerCode;

    private String businessKMKR;
}
