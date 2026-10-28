package project.api.domain.member.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("닉네임 규칙")
class NicknameRuleTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean valid(String nickname) {
        SignUpRequest req = new SignUpRequest();
        req.setUsername("tester1");
        req.setPassword("password");
        req.setNickname(nickname);
        return validator.validateProperty(req, "nickname").isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Mock1", "개발자123", "dev_kim", "a1b", "김철수", "2024개발자", "abcdefghijklmnopqrst"})
    @DisplayName("허용: 3~20자, 숫자가 섞여도 됨")
    void allowed(String nickname) {
        assertThat(valid(nickname)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234", "000", "12345678901", "ab", "김 철수", "dev@kim", "@dev", "abcdefghijklmnopqrstu"})
    @DisplayName("거절: 숫자만, 3자 미만, 공백, @, 20자 초과")
    void rejected(String nickname) {
        assertThat(valid(nickname)).isFalse();
    }
}
