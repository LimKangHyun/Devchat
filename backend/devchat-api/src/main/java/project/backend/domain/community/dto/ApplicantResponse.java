package project.backend.domain.community.dto;

import project.backend.domain.community.entity.Applicant;

import java.time.LocalDateTime;

public record ApplicantResponse(
        Long id,
        String nickname,
        String profileImage,
        LocalDateTime appliedAt
) {
    public static ApplicantResponse from(Applicant applicant) {
        return new ApplicantResponse(
                applicant.getId(),
                applicant.getMemberNickname(),
                applicant.getProfileImage(),
                applicant.getAppliedAt()
        );
    }
}