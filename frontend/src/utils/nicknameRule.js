// 닉네임 규칙 (서버 NicknameRule.java와 같다)
// 3~20자, 숫자로만 불가(멘션에서 번호·비밀번호처럼 보임), 공백·@ 불가(멘션이 잘려 인식됨)
export const NICKNAME_MIN = 3
export const NICKNAME_MAX = 20
export const NICKNAME_HINT = "3~20자, 숫자로만은 안 되고 공백과 @는 쓸 수 없어요."

/** 문제가 있으면 안내 문구, 괜찮으면 null */
export function nicknameError(nickname) {
  const n = nickname ?? ""
  if (n.length < NICKNAME_MIN || n.length > NICKNAME_MAX) return `닉네임은 ${NICKNAME_MIN}자 이상 ${NICKNAME_MAX}자 이하여야 합니다.`
  if (/[\s@]/.test(n)) return "닉네임에는 공백과 @를 쓸 수 없어요."
  if (/^[0-9]+$/.test(n)) return "닉네임은 숫자로만 만들 수 없어요."
  return null
}
