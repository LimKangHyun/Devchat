package project.api.domain.member.dto;

/**
 * 닉네임 규칙 (가입·프로필 수정 공통, 프론트 utils/nicknameRule.js와 같다).
 * - 3~20자
 * - 숫자로만 된 닉네임 불가: 멘션 후보에서 비밀번호·번호처럼 보여 헷갈린다
 * - 공백·@ 불가: 멘션은 "@닉네임" 뒤 공백까지를 이름으로 읽어 잘려 인식된다
 */
public final class NicknameRule {

    public static final int MIN = 3;
    public static final int MAX = 20;
    public static final String PATTERN = "^(?![0-9]+$)[^\\s@]+$";
    public static final String MESSAGE = "닉네임은 숫자로만 만들 수 없고, 공백과 @는 쓸 수 없어요.";
    public static final String SIZE_MESSAGE = "닉네임은 3자 이상 20자 이하여야 합니다.";

    private NicknameRule() {
    }
}
