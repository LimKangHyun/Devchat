"use client"

import { useState } from "react"
import { useNavigate, useLocation } from "react-router-dom";
import styles from "./Auth.module.css"
import AuthLayout from "../components/AuthLayout"
import { loginUrlFor } from "../utils/postLoginRedirect"
import { nicknameError, NICKNAME_HINT } from "../utils/nicknameRule"

function App() {
  const navigate = useNavigate();
  // 초대 링크 등에서 넘어온 돌아갈 경로. 가입 후 로그인 화면으로 그대로 넘긴다.
  const loginUrl = loginUrlFor(new URLSearchParams(useLocation().search).get("redirect"));
  const [username, setUsername] = useState("")
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [nickname, setNickname] = useState("")

  const handleSubmit = async (e) => {
  e.preventDefault();

  const nickError = nicknameError(nickname);
  if (nickError) { alert(nickError); return; }

    // 이메일이 비어있을 경우 확인 메시지 표시
  if (!email) {
    const confirmProceed = window.confirm(
      "이메일을 입력하지 않으면 비밀번호 찾기, 이메일 발송 서비스 등을 이용 할 수 없습니다. \n그래도 진행하시겠습니까? \n(가입 후 프로필 관리에서 이메일을 설정 할 수 있습니다.)"
    );
    if (!confirmProceed) return;
  }

  const payload = {
    username,
    password,
    nickname,
    email,
  };

  try {
    const response = await fetch(`${process.env.REACT_APP_BASE_API_URL}/signup`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });

    if (!response.ok) {
      const errorData = await response.json();
      console.error(errorData)
      alert(errorData.message || "회원가입 실패");
      return;
    }

    const data = await response.json();
    console.log("✅ Signup successful:", data);
    alert("회원가입 성공!");
    navigate(loginUrl);

  } catch (error) {
    console.error("네트워크 또는 서버 에러:", error);
    alert("서버에 연결할 수 없습니다.");
  }
}

  return (
    <AuthLayout>
      <div className={styles.card}>
        <h1 className={styles.heading}>계정 만들기</h1>
        <p className={styles.sub}>몇 가지 정보만 입력하면 바로 시작할 수 있어요.</p>

        <form className={styles.form} onSubmit={handleSubmit}>
          <div className={styles.field}>
            <label className="dc-label" htmlFor="username">
              ID<span className={styles.req}>*</span>
            </label>
            <input
              id="username"
              className={`dc-input ${styles.input}`}
              type="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="ID를 입력해주세요"
              required
              minLength="5"
              maxLength="12"
            />
            <p className="dc-help">5~12자</p>
          </div>

          <div className={styles.field}>
            <label className="dc-label" htmlFor="password">
              비밀번호<span className={styles.req}>*</span>
            </label>
            <input
              id="password"
              className={`dc-input ${styles.input}`}
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="비밀번호를 입력해주세요"
              required
              minLength="4" //수정
            />
          </div>

          <div className={styles.field}>
            <label className="dc-label" htmlFor="nickname">
              닉네임<span className={styles.req}>*</span>
            </label>
            <input
              id="nickname"
              className={`dc-input ${styles.input}`}
              type="text"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              placeholder={NICKNAME_HINT}
              title={NICKNAME_HINT}
              required
              minLength="3"
              maxLength="20"
            />
          </div>

          <div className={styles.field}>
            <label className="dc-label" htmlFor="email">이메일</label>
            <input
              id="email"
              className={`dc-input ${styles.input}`}
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="이메일을 입력해주세요"
            />
            <p className="dc-help">선택 사항 · 비밀번호 찾기와 알림 메일에 사용돼요.</p>
          </div>

          <button type="submit" className={`dc-btn dc-btn-primary ${styles.submit}`}>
            회원가입
          </button>
        </form>

        <div className={styles.switch}>
          <span>이미 계정이 있나요?</span>
          <a href={loginUrl}>로그인</a>
        </div>
      </div>
    </AuthLayout>
  )
}

export default App
