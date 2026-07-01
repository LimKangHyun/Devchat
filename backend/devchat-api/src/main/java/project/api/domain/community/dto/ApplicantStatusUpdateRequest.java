package project.api.domain.community.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import project.api.domain.community.entity.ApplicantStatus;

@Getter
public class ApplicantStatusUpdateRequest {

    @NotNull
    private ApplicantStatus status;
}