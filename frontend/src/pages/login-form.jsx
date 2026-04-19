"use client"
import { useState } from "react"
import { useNavigate, useLocation } from "react-router-dom"  // ← useLocation 추가
import "../App.css"
function App() {
  const navigate = useNavigate()
  const location = useLocation()  // ← 현재 URL 정보
  const [username, setUsername] = useState("")
  const [password, setPassword] = useState("")
  const handleSubmit = async (e) => {
    e.preventDefault()
    const params = new URLSearchParams()
    params.append("username", username)
    params.append("password", password)
    try {
      const response = await fetch(`${process.env.REACT_APP_BASE_API_URL}/login`, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          "Accept": "application/json",
        },
        body: params.toString(),
        credentials: "include",
      })
      if (!response.ok) {
        const errorData = await response.json()
        console.error(errorData)
        alert(errorData.message || "로그인 실패")
        return
      }
      const data = await response.json()
      console.log("Login successful:", data)
      alert("로그인 성공!")
      // redirect 파라미터 있으면 해당 경로로, 없으면 "/"로
      const searchParams = new URLSearchParams(location.search)
      const redirectPath = searchParams.get("redirect") || "/"
      navigate(redirectPath)
    } catch (error) {
      console.error("네트워크 또는 서버 에러:", error)
      alert("서버에 연결할 수 없습니다.")
    }
  }
  /* 추가: GitHub OAuth 로그인 핸들러 */
const handleGithubLogin = () => {
  window.location.href = `${process.env.REACT_APP_BASE_API_URL}/oauth2/authorization/github`;
}
  return (
    <div className="app-container">
      <div className="form-container">
        <div className="logo-container">
          <a href="/">
            <img
              src="/images/devchat-logo.webp"
              alt="DevChat Logo"
              className="logo-image"
            />
          </a>
        </div>
        <h1 className="heading">Welcome Back!</h1>
         <form onSubmit={handleSubmit}>
          {/* Username / Password */}
          <div className="form-group">
            <label htmlFor="username">ID</label>
            <input
              id="username"
              type="username"
              value={username}
              onChange={e => setUsername(e.target.value)}
              placeholder="ID를 입력해주세요"
              required
            />
          </div>
          <div className="form-group">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              placeholder="비밀번호를 입력해주세요"
              required
            />
          </div>
          <button type="submit" className="signup-button">
            Login
          </button>
          <div className="signup-link">
            <span>계정이 아직 없으신가요?</span>
            <a href="/signup">회원가입</a>
          </div>
          {/*임창인*/}
          <hr className="oauth-separator" />
          {/* GitHub OAuth 버튼 */}
          <button
            type="button"
            className="oauth-button github"
            onClick={handleGithubLogin}
          >
            <img
              src="/images/github-mark.webp"
              alt="GitHub"
              className="oauth-icon"
            />
            <span>Github로 로그인</span>
          </button>
        </form>
      </div>
      <div className="background-container">
        <img
          src="/images/signup-background.webp"
          alt="Background"
          className="background-image"
        />
      </div>
    </div>
  )
}
export default App