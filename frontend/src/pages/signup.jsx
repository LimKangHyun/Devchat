"use client"

import { useState } from "react"
import { useNavigate } from "react-router-dom";
import "../App.css"

function App() {
  const navigate = useNavigate();
  const [username, setUsername] = useState("")
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [nickname, setNickname] = useState("")

  const handleSubmit = async (e) => {
  e.preventDefault();

  const payload = {
    username,
    password,
    nickname,
    email,
  };

  try {
    const response = await fetch("http://localhost:8080/signup", {
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
    navigate("/login");

  } catch (error) {
    console.error("네트워크 또는 서버 에러:", error);
    alert("서버에 연결할 수 없습니다.");
  }
}

  return (
    <div className="app-container">
      <div className="form-container">
        <div className="logo-container">
          <a href="/">
            <img src="/images/devchat-logo.png" alt="DevChat Logo" className="logo-image" />
          </a>
        </div>

        <h1 className="heading">Let's Start!</h1>

      
        <form onSubmit={handleSubmit}>

          <div className="form-group">
            <label htmlFor="username">
              ID<span style={{ color: 'red' }}> *</span>
            </label>
            <input
              id="username"
              type="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="ID를 입력해주세요"
              required
              minLength="5"
              maxLength="12"
            />
          </div>

          <div className="form-group">
            <label htmlFor="password">
              Password<span style={{ color: 'red' }}> *</span>
            </label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="비밀번호를 입력해주세요"
              required
              minLength="4" //수정
            />
          </div>

          <div className="form-group">
            <label htmlFor="nickname">
              Nickname<span style={{ color: 'red' }}> *</span>
            </label>
            <input
              id="nickname"
              type="text"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              placeholder="사용할 닉네임을 입력해주세요"
              required
              minLength="3"
            />
          </div>

          <div className="form-group">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="이메일을 입력해주세요"
            />
          </div>

          <button type="submit" className="signup-button">
            Sign Up
          </button>
        </form>

        <div className="signup-link">
          <span>이미 계정이 있나요?</span>
          <a href="/login">로그인</a>
        </div>
      </div>

      <div className="background-container">
        <img src="/images/signup-background.png" alt="Background" className="background-image" />
      </div>
    </div>
  )
}

export default App