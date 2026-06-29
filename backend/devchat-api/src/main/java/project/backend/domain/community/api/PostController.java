package project.backend.domain.community.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.community.app.ApplicantService;
import project.backend.domain.community.app.PostService;
import project.backend.domain.community.dto.*;

import java.util.List;

@Tag(name = "Community", description = "게시글 및 참가 지원 관리 API")
@RestController
@RequestMapping("/community")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final ApplicantService applicantService;

    @Operation(summary = "게시글 목록 조회", description = "정렬/필터 기반 게시글 조회")
    @GetMapping
    public ResponseEntity<?> getPosts(
            @RequestParam(defaultValue = "hot") String sort,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(defaultValue = "false") boolean myApplied,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        return ResponseEntity.ok(postService.getPosts(sort, activeOnly, myApplied, page, size, memberDetails));
    }

    @Operation(summary = "게시글 상세 조회")
    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPost(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.getPost(postId));
    }

    @Operation(summary = "게시글 생성")
    @PostMapping
    public ResponseEntity<PostResponse> createPost(
            @RequestBody @Valid PostCreateRequest request,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        return ResponseEntity.ok(postService.createPost(request, memberDetails));
    }

    @Operation(summary = "게시글 수정")
    @PutMapping("/{postId}")
    public ResponseEntity<PostResponse> updatePost(
            @PathVariable Long postId,
            @RequestBody @Valid PostUpdateRequest request,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        return ResponseEntity.ok(postService.updatePost(postId, request, memberDetails));
    }

    @Operation(summary = "게시글 삭제")
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @PathVariable Long postId,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        postService.deletePost(postId, memberDetails);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "게시글 지원")
    @PostMapping("/{postId}/apply")
    public ResponseEntity<Void> apply(
            @PathVariable Long postId,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        applicantService.apply(postId, memberDetails);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "지원자 목록 조회")
    @GetMapping("/{postId}/applicants")
    public ResponseEntity<List<ApplicantResponse>> getApplicants(
            @PathVariable Long postId,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        return ResponseEntity.ok(applicantService.getApplicants(postId, memberDetails));
    }

    @Operation(summary = "지원 상태 변경 (승인/거절)")
    @PatchMapping("/{postId}/applicants/{applicantId}")
    public ResponseEntity<Void> updateApplicantStatus(
            @PathVariable Long postId,
            @PathVariable Long applicantId,
            @RequestBody @Valid ApplicantStatusUpdateRequest request,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        applicantService.updateStatus(postId, applicantId, request.getStatus(), memberDetails);
        return ResponseEntity.ok().build();
    }
}