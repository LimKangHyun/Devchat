package project.backend.domain.community.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import project.backend.domain.community.entity.ApplicantStatus;

@Getter
public class ApplicantStatusUpdateRequest {

    @NotNull
    private ApplicantStatus status;
}