package project.api.domain.feedback.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import project.api.domain.feedback.app.FeedbackService;
import project.api.domain.feedback.dto.FeedbackRequest;
import project.api.domain.feedback.dto.FeedbackResponse;
import project.api.domain.member.app.MemberService;

@Tag(name = "Feedback", description = "피드백 API")
@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final MemberService memberService;

    @Operation(summary = "피드백 보내기", description = "피드백을 DevChat GitHub 레포의 이슈로 등록한다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackResponse submit(@Valid @RequestBody FeedbackRequest request, Authentication auth) {
        Long memberId = memberService.checkAuthentication(auth).getId();
        return feedbackService.submit(memberId, request);
    }
}
