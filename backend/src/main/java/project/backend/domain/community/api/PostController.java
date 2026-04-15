package project.backend.domain.community.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.community.app.ApplicantService;
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
    private final ApplicantService applicantService;

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

    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPost(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.getPost(postId));
    }

    @PostMapping
    public ResponseEntity<PostResponse> createPost(
            @RequestBody @Valid PostCreateRequest request,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        return ResponseEntity.ok(postService.createPost(request, memberDetails));
    }

    @PostMapping("/{postId}")
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

    @PostMapping("/{postId}/apply")
    public ResponseEntity<Void> apply(
            @PathVariable Long postId,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        applicantService.apply(postId, memberDetails);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{postId}/applicants")
    public ResponseEntity<List<ApplicantResponse>> getApplicants(
            @PathVariable Long postId,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        return ResponseEntity.ok(applicantService.getApplicants(postId, memberDetails));
    }

    @PostMapping("/{postId}/applicants/{applicantId}/approve")
    public ResponseEntity<Void> approve(
            @PathVariable Long postId,
            @PathVariable Long applicantId,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        applicantService.approve(postId, applicantId, memberDetails);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{postId}/applicants/{applicantId}/reject")
    public ResponseEntity<Void> reject(
            @PathVariable Long postId,
            @PathVariable Long applicantId,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        applicantService.reject(postId, applicantId, memberDetails);
        return ResponseEntity.ok().build();
    }
}