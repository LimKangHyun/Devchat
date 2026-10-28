import { nicknameError } from "./nicknameRule"

it.each(["Mock1", "개발자123", "dev_kim", "김철수", "2024개발자"])("허용: %s", (n) => {
  expect(nicknameError(n)).toBeNull()
})

it.each(["1234", "000", "ab", "김 철수", "dev@kim", "abcdefghijklmnopqrstu", ""])("거절: %s", (n) => {
  expect(nicknameError(n)).not.toBeNull()
})
