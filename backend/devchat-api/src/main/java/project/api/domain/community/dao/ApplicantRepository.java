package project.api.domain.community.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import project.api.domain.community.entity.Applicant;
import project.api.domain.community.entity.ApplicantStatus;

import java.util.List;
import java.util.Optional;

public interface ApplicantRepository extends JpaRepository<Applicant, Long> {

    boolean existsByPost_IdAndMember_Id(Long postId, Long memberId);

    List<Applicant> findByPost_IdAndStatus(Long postId, ApplicantStatus status);

    Optional<Applicant> findByPost_IdAndMember_Id(Long postId, Long memberId);
}