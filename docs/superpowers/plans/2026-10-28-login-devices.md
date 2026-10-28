# 로그인 기기 관리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로그인을 기기(세션) 단위로 저장해서 여러 기기 동시 로그인을 가능하게 하고, 내 로그인 기기 목록 보기와 원격 로그아웃을 만든다.

**Architecture:** 새 `login_session` 테이블에 로그인마다 행을 하나 만들고, 세션 id(`sid`)를 액세스 토큰과 리프레시 토큰 클레임에 넣는다. 재발급은 그 세션의 리프레시 토큰만 바꾼다. 끊은 세션 id는 Redis에 14일 동안 기록하고, HTTP 필터, 웹소켓 핸드셰이크, STOMP SEND/SUBSCRIBE에서 거절한다. 깃허브 액세스 토큰은 기존 `auth_token`에 회원 단위로 남긴다.

**Tech Stack:** Spring Boot 3 / Java 21, Spring Security, auth0 java-jwt, JPA + Flyway(MySQL), StringRedisTemplate, STOMP; React(CRA) + Jest

**Spec:** `docs/superpowers/specs/2026-10-28-calendar-and-devices-design.md` (2부)

## Global Constraints

- 리프레시 토큰 수명은 `JwtProvider.REFRESH_TOKEN_VALIDATION_SECOND`(2주), 액세스 토큰 수명은 `TOKEN_VALIDATION_SECOND`(10분)이다. 바꾸지 않는다.
- 배포해도 기존 사용자가 로그아웃되면 안 된다. `sid` 없는 옛 리프레시 토큰은 첫 재발급 때 세션으로 옮긴다.
- 일반 로그아웃은 지금 기기만 로그아웃한다.
- 비밀번호를 바꾸면 지금 기기를 뺀 모든 세션을 끊는다.
- 재발급할 때 닉네임과 username은 DB의 `Member`에서 읽는다. 옛 토큰 클레임에서 복사하지 않는다.
- IP는 저장하지 않는다 (스펙 "빌더 재량"에서 정한 것).
- 새 기기 로그인 알림, 위치 표시, 기기 이름 바꾸기는 만들지 않는다.
- 확인 창은 브라우저 `confirm` 대신 앱 모달을 쓴다.
- Flyway 새 파일은 `V15__create_login_session.sql`이다. 로컬은 Flyway가 꺼져 있어서 `docker exec -i mysql mysql -uroot -proot_pw devchat < 파일`로 직접 적용한다.
- 테스트 서버 포트는 백엔드 8098, 프론트 3006이다. 8080과 3000은 건드리지 않는다.
- 커밋 메시지에 Claude 공동 작성자나 생성 문구를 넣지 않는다.

## Review Focus

- 재발급 요청이 거의 동시에 두 번 오는 경우(탭 두 개)에도 로그아웃되면 안 된다. 기존 유예 캐시(`TokenRefreshCachePort`)가 세션 구조에서도 동작해야 한다. → Task 3의 `refresh_withinGracePeriod_returnsCachedToken`
- 끊긴 세션이 유예 캐시를 통해 액세스 토큰을 다시 받으면 안 된다. → Task 3의 `refresh_revokedSessionInGrace_rejected`
- 옛 토큰(`sid` 없음)이 `auth_token`과 맞지 않으면 세션이 생기면 안 된다. 예: 다른 기기에서 이미 덮어쓴 토큰. → Task 3의 `refresh_legacyTokenMismatch_rejected`
- 웹소켓을 연 채로 세션이 끊겨도 그 연결로 메시지를 보내거나 구독할 수 없어야 한다. → Task 5의 `revokedSession_sendDropped`
- 남의 세션 id로 끊기를 요청하면 거절해야 한다. 존재 여부도 드러내지 않고 404로 답한다. → Task 4의 `revoke_otherMembersSession_notFound`

---

## File Structure

백엔드 (`backend/devchat-api/src/main/java/project/api/`)
- Create `auth/entity/LoginSession.java`: 세션 엔티티
- Create `auth/dao/LoginSessionRepository.java`
- Create `auth/app/DeviceNameParser.java`: User-Agent를 "Chrome · Windows" 같은 이름으로 바꾸는 순수 함수
- Create `auth/app/RevokedSessionStore.java`: Redis에 끊긴 sid 기록과 조회
- Create `auth/app/LoginSessionService.java`: 세션 만들기, 목록, 끊기, 다른 기기 모두 끊기
- Create `auth/api/LoginSessionController.java`: `/sessions` API
- Create `auth/dto/LoginSessionResponse.java`
- Create `auth/app/LoginSessionCleanupJob.java`
- Create `global/websocket/interceptor/RevokedSessionChannelInterceptor.java`
- Modify `auth/jwt/JwtProvider.java`, `auth/dto/MemberDetails.java`, `auth/app/AuthTokenService.java`, `auth/entity/AuthToken.java`, `auth/api/LogoutController.java`
- Modify `global/security/handler/form/FormSuccessHandler.java`, `global/security/handler/oauth/OAuth2SuccessHandler.java`
- Modify `global/security/filter/JwtAuthenticationFilter.java`, `global/websocket/interceptor/WebSocketHandShakeInterceptor.java`, `global/websocket/config/WebSocketConfig.java`
- Modify `domain/member/app/MemberService.java`: 닉네임 변경 재발급, 비밀번호 변경
- Create `resources/db/migration/V15__create_login_session.sql`

프론트 (`frontend/src/`)
- Create `utils/relativeTime.js` (+ `.test.js`): "방금 전 / 3분 전 / 어제 / 10월 2일"
- Create `components/modals/LoginDevicesModal.jsx`
- Modify `components/header.jsx`: 프로필 메뉴에 "로그인 기기"
- Modify `components/common/useWebSocket.jsx`: `/user/queue/session` 구독
- Modify `components/modals/Modals.module.css`

---

### Task 1: 세션 테이블과 엔티티

**Files:**
- Create: `backend/devchat-api/src/main/resources/db/migration/V15__create_login_session.sql`
- Create: `auth/entity/LoginSession.java`, `auth/dao/LoginSessionRepository.java`
- Modify: `auth/entity/AuthToken.java` (`refreshToken` nullable, `clearRefreshToken()` 추가)
- Test: `backend/devchat-api/src/test/java/project/api/auth/entity/LoginSessionTest.java`

**Interfaces:**
- Produces:
  - `LoginSession.create(Long memberId, String refreshTokenHash, String deviceName, LocalDateTime now, LocalDateTime expiresAt)`: id는 `UUID.randomUUID().toString()`
  - getters: `getId(): String`, `getMemberId()`, `getDeviceName()`, `getCreatedAt()`, `getLastUsedAt()`, `getExpiresAt()`, `getRevokedAt()`, `getRefreshTokenHash()`
  - `rotate(String newRefreshTokenHash, LocalDateTime now, LocalDateTime newExpiresAt)`: lastUsedAt도 갱신한다
  - `revoke(LocalDateTime now)`, `isActive(LocalDateTime now): boolean` (끊기지 않았고 만료 전)
  - `LoginSessionRepository extends JpaRepository<LoginSession, String>`
    - `List<LoginSession> findByMemberIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(Long memberId, LocalDateTime now)`
    - `@Modifying int deleteExpiredOrRevokedBefore(LocalDateTime before)`: `expires_at < before OR revoked_at < before`
  - `AuthToken.clearRefreshToken()`: 옛 토큰을 세션으로 옮긴 뒤 비운다

SQL (V15):
```sql
CREATE TABLE login_session (
  id varchar(36) NOT NULL,
  member_id bigint NOT NULL,
  refresh_token_hash char(64) NOT NULL,
  device_name varchar(100) NOT NULL,
  created_at datetime(6) NOT NULL,
  last_used_at datetime(6) NOT NULL,
  expires_at datetime(6) NOT NULL,
  revoked_at datetime(6) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_login_session_member (member_id, revoked_at, expires_at),
  CONSTRAINT fk_login_session_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE auth_token MODIFY refresh_token varchar(512) NULL;
```
(`member` 테이블과 PK 이름은 `V1__init_schema.sql`에서 확인해 맞춘다.)

- [ ] **Step 1: 실패하는 테스트 작성**: `LoginSessionTest`
  - `rotate_updatesHashAndLastUsed`: rotate 뒤 `getRefreshTokenHash()`가 새 값이고, `getLastUsedAt()`과 `getExpiresAt()`이 새 시각이다.
  - `revoke_makesInactive`: `isActive(now)`는 true, `revoke(now)` 뒤에는 false이다.
  - `expired_isInactive`: `expiresAt = now.minusSeconds(1)`이면 `isActive(now)`가 false이다.
- [ ] **Step 2: 실패 확인**: `cd backend && ./gradlew :devchat-api:test --tests "project.api.auth.entity.LoginSessionTest"` 결과가 컴파일 실패여야 한다.
- [ ] **Step 3: 엔티티, 리포지토리, 마이그레이션 작성.** `AuthToken.refreshToken`의 `nullable = false`를 지우고 `clearRefreshToken()`을 추가한다.
- [ ] **Step 4: 통과 확인**: 같은 명령으로 PASS가 나와야 한다.
- [ ] **Step 5: 로컬 DB에 V15 적용**: `docker exec -i mysql mysql -uroot -proot_pw devchat < backend/devchat-api/src/main/resources/db/migration/V15__create_login_session.sql`, 그다음 `SHOW CREATE TABLE login_session`으로 확인한다.
- [ ] **Step 6: 커밋**: `feat: 로그인 세션 테이블 추가`

### Task 2: 기기 이름 만들기

**Files:**
- Create: `auth/app/DeviceNameParser.java`
- Test: `src/test/java/project/api/auth/app/DeviceNameParserTest.java`

**Interfaces:**
- Produces: `static String DeviceNameParser.parse(String userAgent)`: 형식은 `"<브라우저> · <OS>"`이다. 결과는 100자로 자른다. null이나 빈 값이면 `"알 수 없는 기기"`를 돌려준다.

- [ ] **Step 1: 실패하는 테스트 작성**: `@ParameterizedTest @CsvSource`로 다음을 확인한다.
  - Windows Chrome UA → `Chrome · Windows`
  - macOS Safari UA (`Version/17.0 Safari/605.1.15`, Chrome 문자열 없음) → `Safari · Mac`
  - iPhone Safari UA → `Safari · iPhone`
  - Android Chrome UA → `Chrome · Android`
  - Windows Edge UA (`Edg/120`) → `Edge · Windows`
  - Firefox Linux UA → `Firefox · Linux`
  - iPad UA → `Safari · iPad`
  - `null` → `알 수 없는 기기`
- [ ] **Step 2: 실패 확인**: `./gradlew :devchat-api:test --tests "*DeviceNameParserTest"` 결과가 FAIL이어야 한다.
- [ ] **Step 3: 구현.** 순서가 중요하다.
  - 브라우저: `Edg/` → Edge, `Chrome/`·`CriOS/` → Chrome, `Firefox/`·`FxiOS/` → Firefox, `Safari/` → Safari, 그 외 → `브라우저`
  - OS: `iPhone`, `iPad`, `Android`, `Windows`, `Mac OS X` → Mac, `Linux`, 그 외 → `기타`
  - 외부 라이브러리는 쓰지 않는다.
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: 커밋**: `feat: 접속 기기 이름 만들기`

### Task 3: 토큰에 세션 넣기, 로그인과 재발급을 세션 단위로

**Files:**
- Modify: `auth/jwt/JwtProvider.java`, `auth/dto/MemberDetails.java`, `auth/app/AuthTokenService.java`, `global/security/handler/form/FormSuccessHandler.java`, `global/security/handler/oauth/OAuth2SuccessHandler.java`
- Create: `auth/app/RevokedSessionStore.java`
- Test: `src/test/java/project/api/auth/app/AuthTokenServiceSessionTest.java`, `src/test/java/project/api/auth/jwt/JwtProviderSessionTest.java`

**Interfaces:**
- Consumes: Task 1 `LoginSession`, `LoginSessionRepository`, `AuthToken.clearRefreshToken()`. Task 2 `DeviceNameParser.parse`
- Produces:
  - `JwtProvider.generateTokenPair(MemberDetails memberDetails, String sessionId): Token`: 클레임에 `"sid"`를 넣는다. 기존 1인자 메서드는 지운다. 호출하는 곳을 모두 바꾼다.
  - `JwtProvider.getAuthentication(token)`: `sid` 클레임을 `MemberDetails.sessionId`로 옮긴다. 없으면 null이다.
  - `MemberDetails`: 필드 `private final String sessionId`, 생성자 `MemberDetails(Long id, String username, String nickname, String profileImg, String sessionId)`. 기존 4인자 생성자는 `sessionId = null`로 위임하고, `MemberDetails(Member)`도 null로 둔다.
  - `static String AuthTokenService.hash(String refreshToken)`: SHA-256 hex 64자
  - `AuthTokenService.login(MemberDetails member, String userAgent, String githubAccessToken, HttpServletResponse response)`: 다음을 한 번에 한다.
    - 세션을 만든다.
    - 토큰을 발급해 쿠키에 저장한다.
    - 깃허브 토큰을 `AuthToken`에 저장한다. 깃허브 토큰이 null이면 기존 값을 유지한다. 지금 `saveToken`은 폼 로그인 때 null로 덮어쓰는데, 이것도 같이 고친다.
    - 기존 `saveToken`은 지운다.
  - `AuthTokenService.refresh(String refreshToken, String userAgent, HttpServletResponse response)`: `AuthController`가 `@RequestHeader(value = "User-Agent", required = false)`를 넘긴다.
  - `RevokedSessionStore`
    - `void markRevoked(String sessionId)`: 키 `auth:revoked-session:{sid}`, TTL은 `REFRESH_TOKEN_VALIDATION_SECOND`
    - `boolean isRevoked(String sessionId)`: null이면 false

refresh 동작 (위에서부터 차례로):
1. 리프레시 토큰을 검증한다 (기존 `verifyAndValidateRefreshToken`).
2. `sid`가 있고 `revokedSessionStore.isRevoked(sid)`이면 `INVALID_TOKEN`으로 거절한다. **유예 캐시보다 먼저 확인한다.**
3. 유예 캐시에 있으면 캐시된 액세스 토큰을 돌려주고 끝낸다 (기존 동작).
4. `sid`가 있으면 세션을 조회한다.
   - 없거나, `!isActive(now)`이거나, `hash(refreshToken)`이 다르면 `INVALID_TOKEN`으로 거절한다.
   - 맞으면 `Member`를 DB에서 읽어 새 토큰 쌍을 같은 sid로 발급하고, `session.rotate(...)`를 호출한다.
5. `sid`가 없으면(옛 토큰) `AuthToken`을 조회한다.
   - 저장된 토큰과 다르거나 null이면 `INVALID_TOKEN`으로 거절한다.
   - 맞으면 새 세션을 만들고(기기 이름은 userAgent로) 그 sid로 토큰을 발급한 뒤 `authToken.clearRefreshToken()`을 호출한다.
6. 유예 캐시에 저장하고 쿠키를 쓴다 (기존 동작).

- [ ] **Step 1: 실패하는 테스트 작성**: `JwtProviderSessionTest`
  - `tokenPair_carriesSid`: `generateTokenPair(details, "s-1")`의 액세스 토큰에서 `getAuthentication`을 하면 `getSessionId()`가 `"s-1"`이다.
  - `JwtProvider` 생성 방식은 기존 코드(`@Value` 필드 `SECRET_KEY`)를 보고 테스트에서 `ReflectionTestUtils.setField`로 넣는다.
- [ ] **Step 2: 실패하는 테스트 작성**: `AuthTokenServiceSessionTest` (Mockito, 객체를 먼저 만들고 `given`에 넣는다)
  - `login_createsSessionAndKeepsGithubTokenWhenNull`: 세션 save가 호출되고 `deviceName`이 `"Chrome · Windows"`이다. 기존 `AuthToken`의 깃허브 토큰은 바뀌지 않는다.
  - `refresh_rotatesSameSession`: 같은 sid로 새 토큰이 나오고 세션 해시가 바뀐다. 닉네임은 DB `Member`의 값이다 (옛 토큰 클레임과 다른 값으로 확인한다).
  - `refresh_hashMismatch_rejected`: `CustomJwtException(INVALID_TOKEN)`
  - `refresh_revokedSessionInGrace_rejected`: `isRevoked`가 true이면 유예 캐시를 보지 않고 거절한다. `verify(cache, never()).getNewTokenIfInGracePeriod(any())`로 확인한다.
  - `refresh_withinGracePeriod_returnsCachedToken`: 캐시에 있으면 세션을 조회하지 않고 캐시 토큰으로 쿠키를 쓴다.
  - `refresh_legacyTokenMatches_migratesToSession`: sid 없는 토큰이 `AuthToken`과 맞으면 세션 save, `clearRefreshToken`이 호출되고, 새 토큰에 sid가 있다.
  - `refresh_legacyTokenMismatch_rejected`: 거절하고 세션 save를 호출하지 않는다.
- [ ] **Step 3: 실패 확인**: `./gradlew :devchat-api:test --tests "*SessionTest"` 결과가 FAIL이어야 한다.
- [ ] **Step 4: 구현.** `FormSuccessHandler`와 `OAuth2SuccessHandler`는 `authTokenService.login(details, request.getHeader("User-Agent"), githubToken, response)` 한 줄로 바꾸고, 쿠키 저장은 `login` 안으로 옮긴다. `reissueTokensForNicknameChange`는 이 Task에서 Task 5의 시그니처 `(Member, String sessionId, HttpServletResponse)`로 바꾸고 `MemberService` 호출부도 고친다 (컴파일을 위해). 그 테스트는 Task 5에서 붙인다.
- [ ] **Step 5: 통과 확인**: 위 테스트와 `./gradlew :devchat-api:test` 전체를 돌린다. Docker 전용 2개(`CheckpointIntegrationTest`, `ChatMessageSearchFulltextMigrationTest`)를 빼고 모두 통과해야 한다.
- [ ] **Step 6: 커밋**: `feat: 로그인과 토큰 재발급을 기기(세션) 단위로`

### Task 4: 기기 목록과 원격 로그아웃 API

**Files:**
- Create: `auth/app/LoginSessionService.java`, `auth/api/LoginSessionController.java`, `auth/dto/LoginSessionResponse.java`
- Test: `src/test/java/project/api/auth/app/LoginSessionServiceTest.java`

**Interfaces:**
- Consumes: Task 1 repository, Task 3 `RevokedSessionStore`, `MemberDetails.getSessionId()`
- Produces:
  - `record LoginSessionResponse(String id, String deviceName, LocalDateTime createdAt, LocalDateTime lastUsedAt, boolean current)`
  - `LoginSessionService`
    - `List<LoginSessionResponse> list(Long memberId, String currentSessionId)`: 마지막 사용 순이고, 지금 세션이 맨 위에 온다.
    - `void revoke(Long memberId, String sessionId, String currentSessionId)`: 세션이 내 것이 아니거나 없으면 `LoginSessionException(NOT_FOUND)`(404), 지금 세션이면 `LoginSessionException(CANNOT_REVOKE_CURRENT)`(400)를 던진다. 성공하면 `session.revoke(now)` + `markRevoked` + `SessionRevokedEvent` 발행이다.
    - `int revokeOthers(Long memberId, String currentSessionId)`: 끊은 수를 돌려준다.
    - `void revokeCurrent(String currentSessionId)`: 로그아웃용. null이면 아무것도 안 한다.
  - `record SessionRevokedEvent(String username, List<String> sessionIds)`: 커밋 뒤 Task 5 리스너가 웹소켓으로 알린다.
  - API (`/sessions`, 인증 필요)
    - `GET /sessions`: `List<LoginSessionResponse>`
    - `DELETE /sessions/{id}`: 204
    - `POST /sessions/logout-others`: 204
  - 에러 코드는 기존 `FeedbackErrorCode`/`FeedbackException` 패턴을 따라 `LoginSessionErrorCode`, `LoginSessionException`으로 만든다.

- [ ] **Step 1: 실패하는 테스트 작성**: `LoginSessionServiceTest`
  - `list_marksCurrentAndPutsItFirst`
  - `revoke_marksRevokedAndPublishesEvent`: `markRevoked("s-2")`, 이벤트의 sessionIds는 `["s-2"]`
  - `revoke_otherMembersSession_notFound`: 다른 회원 세션이면 NOT_FOUND이고, `markRevoked`는 호출되지 않는다.
  - `revoke_currentSession_rejected`: CANNOT_REVOKE_CURRENT
  - `revokeOthers_keepsCurrent`: 세션 3개 중 2개만 끊고 2를 돌려준다.
- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현.** 컨트롤러는 `@AuthenticationPrincipal MemberDetails`에서 `getId()`와 `getSessionId()`를 쓴다.
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: 커밋**: `feat: 로그인 기기 목록과 원격 로그아웃 API`

### Task 5: 끊긴 세션 막기, 로그아웃, 비밀번호와 닉네임 변경 연결

**Files:**
- Modify: `global/security/filter/JwtAuthenticationFilter.java`, `global/websocket/interceptor/WebSocketHandShakeInterceptor.java`, `global/websocket/config/WebSocketConfig.java`, `auth/api/LogoutController.java`, `auth/app/AuthTokenService.java`, `domain/member/app/MemberService.java`
- Create: `global/websocket/interceptor/RevokedSessionChannelInterceptor.java`, `auth/app/SessionRevokedNotifier.java`, `auth/app/LoginSessionCleanupJob.java`
- Test: `src/test/java/project/api/global/websocket/interceptor/RevokedSessionChannelInterceptorTest.java`, `src/test/java/project/api/auth/app/SessionRevocationWiringTest.java`

**Interfaces:**
- Consumes: Task 3 `RevokedSessionStore.isRevoked`, `MemberDetails.getSessionId()`. Task 4 `LoginSessionService.revokeCurrent`, `revokeOthers`, `SessionRevokedEvent`
- Produces:
  - `JwtAuthenticationFilter`: 토큰이 VALID여도 `isRevoked(sid)`이면 401 `"로그아웃된 기기입니다."`로 답한다. 핸드셰이크도 같은 경우 `false`를 돌려준다.
  - `RevokedSessionChannelInterceptor implements ChannelInterceptor`: SEND와 SUBSCRIBE 프레임에서 principal의 sid가 끊긴 상태이면 `null`을 돌려 프레임을 버린다. CONNECT, DISCONNECT, UNSUBSCRIBE는 통과시킨다. `WebSocketConfig`의 inbound 인터셉터 목록 **맨 앞**에 등록한다.
  - `SessionRevokedNotifier`: `@TransactionalEventListener(AFTER_COMMIT)`로 `convertAndSendToUser(username, "/queue/session", Map.of("type", "SESSION_REVOKED", "sessionIds", ids))`를 호출한다.
  - `LogoutController`: `authTokenService.logout(memberId)` 대신 `loginSessionService.revokeCurrent(memberDetails.getSessionId())`를 호출한다. 이벤트는 발행하지 않는다. 지금 기기는 쿠키를 지우므로 필요 없다. `AuthTokenService.logout`은 지운다.
  - `AuthTokenService.reissueTokensForNicknameChange(Member member, String sessionId, HttpServletResponse response)`
    - sessionId가 있으면 그 세션을 rotate한다.
    - null(옛 토큰)이면 아무것도 하지 않는다. 다음 재발급 때 세션으로 옮겨지고, 닉네임은 DB에서 읽는다.
    - 다른 기기는 다음 재발급(최대 10분) 때 새 닉네임을 받는다.
  - `MemberService.updatePassword(...)`: 성공한 뒤 `loginSessionService.revokeOthers(memberId, memberDetails.getSessionId())`를 호출한다.
  - `LoginSessionCleanupJob`: `@Scheduled(cron = "0 10 4 * * *", zone = "Asia/Seoul")`, `deleteExpiredOrRevokedBefore(now.minusDays(1))`

- [ ] **Step 1: 실패하는 테스트 작성**: `RevokedSessionChannelInterceptorTest`
  - `revokedSession_sendDropped`: SEND 프레임 + sid가 끊긴 principal이면 `preSend`가 null이다.
  - `revokedSession_subscribeDropped`
  - `activeSession_passes`
  - `noSid_passes`: 옛 토큰 연결은 통과한다.
- [ ] **Step 2: 실패하는 테스트 작성**: `SessionRevocationWiringTest` (Mockito)
  - `logout_revokesOnlyCurrentSession`: `revokeCurrent("s-1")`이 호출되고 `revokeOthers`는 호출되지 않는다.
  - `passwordChange_revokesOtherSessions`: `revokeOthers(memberId, "s-1")`
  - `nicknameChange_rotatesCurrentSessionWithNewNickname`: 새 액세스 토큰의 nickname 클레임이 새 닉네임이다.
- [ ] **Step 3: 실패 확인**
- [ ] **Step 4: 구현**
- [ ] **Step 5: 통과 확인**: 전체 `./gradlew :devchat-api:test`를 돌린다 (Docker 전용 2개 제외).
- [ ] **Step 6: 커밋**: `feat: 끊긴 기기 차단, 지금 기기만 로그아웃, 비밀번호 변경 시 다른 기기 로그아웃`

### Task 6: 로그인 기기 화면

**Files:**
- Create: `frontend/src/utils/relativeTime.js`, `frontend/src/utils/relativeTime.test.js`, `frontend/src/components/modals/LoginDevicesModal.jsx`
- Modify: `frontend/src/components/header.jsx` (프로필 메뉴, "피드백 보내기" 위), `frontend/src/components/common/useWebSocket.jsx`, `frontend/src/components/modals/Modals.module.css`

**Interfaces:**
- Consumes: Task 4 API, Task 5 `/user/queue/session`
- Produces: `relativeTime(date: Date|string, now?: Date): string`

화면 내용:
- 제목은 "로그인 기기"이다.
- 목록 한 줄에는 기기 이름, 지금 기기면 "이 기기" 배지, "마지막 사용 · {relativeTime}", "처음 로그인 · {YYYY.M.D}"를 보여 준다. 지금 기기가 아니면 "로그아웃" 버튼을 둔다.
- 아래에는 "다른 기기 모두 로그아웃" 버튼을 둔다. 다른 기기가 없으면 숨긴다.
- 로그아웃 버튼을 누르면 모달 안에서 "○○에서 로그아웃할까요?" 확인 / 취소를 묻는다. 끝나면 목록을 새로 고치고 기존 `Toast`로 "로그아웃했어요"를 띄운다.
- 아이콘은 lucide의 `MonitorSmartphone`을 쓴다 (header가 이미 lucide를 쓴다).
- `useWebSocket.jsx`는 `/user/queue/session`을 구독한다. `SESSION_REVOKED`를 받으면 `safeRefreshToken()`을 부르고, 실패하면 기존 401 처리와 같이 `loginUrlFor(...)`로 이동한다. 끊긴 기기만 실패하므로 다른 기기는 영향이 없다.

- [ ] **Step 1: 실패하는 테스트 작성**: `relativeTime.test.js`, `now = new Date('2026-10-28T12:00:00+09:00')` 기준
  - 30초 전 → `방금 전`
  - 3분 전 → `3분 전`
  - 5시간 전 → `5시간 전`
  - 어제 23시 → `어제`
  - 10월 2일 → `10월 2일`
  - 2025-12-31 → `2025년 12월 31일`
- [ ] **Step 2: 실패 확인**: `cd frontend && npx react-scripts test --watchAll=false src/utils/relativeTime.test.js` 결과가 FAIL이어야 한다.
- [ ] **Step 3: 구현** (relativeTime, 모달, 메뉴, 구독)
- [ ] **Step 4: 통과 확인**: 같은 명령과 `npx react-scripts test --watchAll=false` 전체를 돌린다.
- [ ] **Step 5: 커밋**: `feat: 로그인 기기 화면`

### Task 7: 실제 브라우저로 확인

- [ ] **Step 1:** 백엔드를 8098(`--server.port=8098`, `URL_DOMAINURL=http://localhost:3006`), 프론트를 3006으로 띄운다.
- [ ] **Step 2:** puppeteer-core로 **브라우저 컨텍스트 두 개**(`browser.createBrowserContext()`)를 만들고 같은 계정으로 로그인한다. 다음을 확인한다.
  - 둘 다 11분 뒤에도 로그인이 유지된다. 액세스 토큰 만료 후 재발급으로 확인하는데, 기다리는 대신 액세스 쿠키를 지우고 API를 호출하면 된다.
  - A의 "로그인 기기" 목록에 2개가 보이고, A에 "이 기기"가 표시된다.
  - A에서 B를 로그아웃하면 B의 다음 API 호출이 로그인 화면으로 가고, B의 웹소켓 SEND가 무시된다.
  - A에서 일반 로그아웃을 해도 다른 컨텍스트 C(새 로그인)는 유지된다.
  - 옛 토큰 이전: DB `auth_token`에 옛 형식 토큰을 넣고 그 쿠키로 `/token/refresh`를 호출하면 성공하고 `login_session` 행이 생긴다.
  - 닉네임을 바꾸면 다른 기기도 재발급 뒤 새 닉네임을 쓴다.
- [ ] **Step 3:** 테스트 서버를 끈다 (`TaskStop` + 포트로 PID를 찾아 `taskkill //PID <pid> //T //F`).
- [ ] **Step 4:** 결과를 정리한다. PR 설명에 넣는다.
