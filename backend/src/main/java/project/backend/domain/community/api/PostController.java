package project.backend.domain.community.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.community.app.PostService;
import project.backend.domain.community.dto.ApplicantResponse;
import project.backend.domain.community.dto.PostCreateRequest;
import project.backend.domain.community.dto.PostUpdateRequest;
import project.backend.domain.community.dto.PostResponse;

import java.util.List;

@RestController
@RequestMapping("/community")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    // 게시글 목록 조회
    @GetMapping
    public ResponseEntity<Slice<PostResponse>> getPosts(
        @RequestParam(defaultValue = "latest") String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "12") int size
    ) {
        return ResponseEntity.ok(postService.getPosts(sort, page, size));
    }

    // 게시글 상세 조회
    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPost(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.getPost(postId));
    }

    // 게시글 작성
    @PostMapping
    public ResponseEntity<PostResponse> createPost(
        @RequestBody @Valid PostCreateRequest request,
        @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        return ResponseEntity.ok(postService.createPost(request, memberDetails));
    }

    // 게시글 수정
    @PatchMapping("/{postId}")
    public ResponseEntity<PostResponse> updatePost(
        @PathVariable Long postId,
        @RequestBody @Valid PostUpdateRequest request,
        @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        return ResponseEntity.ok(postService.updatePost(postId, request, memberDetails));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
        @PathVariable Long postId,
        @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        postService.deletePost(postId, memberDetails);
        return ResponseEntity.noContent().build();
    }

    // 스터디 신청
    @PostMapping("/{postId}/apply")
    public ResponseEntity<Void> apply(
        @PathVariable Long postId,
        @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        postService.apply(postId, memberDetails);
        return ResponseEntity.ok().build();
    }

    // 신청자 목록 조회 (방장만)
    @GetMapping("/{postId}/applicants")
    public ResponseEntity<List<ApplicantResponse>> getApplicants(
        @PathVariable Long postId,
        @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        return ResponseEntity.ok(postService.getApplicants(postId, memberDetails));
    }

    // 신청 승인
    @PostMapping("/{postId}/applicants/{applicantId}/approve")
    public ResponseEntity<Void> approve(
        @PathVariable Long postId,
        @PathVariable Long applicantId,
        @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        postService.approve(postId, applicantId, memberDetails);
        return ResponseEntity.ok().build();
    }

    // 신청 거절
    @PostMapping("/{postId}/applicants/{applicantId}/reject")
    public ResponseEntity<Void> reject(
        @PathVariable Long postId,
        @PathVariable Long applicantId,
        @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        postService.reject(postId, applicantId, memberDetails);
        return ResponseEntity.ok().build();
    }
}