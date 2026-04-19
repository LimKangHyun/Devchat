"use client"
import { useState } from "react"
import { useNavigate, useLocation } from "react-router-dom"
import "../App.css"

function App() {
  const navigate = useNavigate()
  const location = useLocation()
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
      const searchParams = new URLSearchParams(location.search)
      const redirectPath = searchParams.get("redirect") || "/"
      navigate(redirectPath)
    } catch (error) {
      console.error("네트워크 또는 서버 에러:", error)
      alert("서버에 연결할 수 없습니다.")
    }
  }

  const handleGithubLogin = () => {
    window.location.href = `${process.env.REACT_APP_BASE_API_URL}/oauth2/authorization/github`
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
          <hr className="oauth-separator" />
          <button
            type="button"
            className="oauth-button github"
            onClick={handleGithubLogin}
          >
            <svg
              className="oauth-icon"
              viewBox="0 0 98 96"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path
                d="M41.4395 69.3848C28.8066 67.8535 19.9062 58.7617 19.9062 46.9902C19.9062 42.2051 21.6289 37.0371 24.5 33.5918C23.2559 30.4336 23.4473 23.7344 24.8828 20.959C28.7109 20.4805 33.8789 22.4902 36.9414 25.2656C40.5781 24.1172 44.4062 23.543 49.0957 23.543C53.7852 23.543 57.6133 24.1172 61.0586 25.1699C64.0254 22.4902 69.2891 20.4805 73.1172 20.959C74.457 23.543 74.6484 30.2422 73.4043 33.4961C76.4668 37.1328 78.0937 42.0137 78.0937 46.9902C78.0937 58.7617 69.1934 67.6621 56.3691 69.2891C59.623 71.3945 61.8242 75.9883 61.8242 81.252L61.8242 91.2051C61.8242 94.0762 64.2168 95.7031 67.0879 94.5547C84.4102 87.9512 98 70.6289 98 49.1914C98 22.1074 75.9883 6.69539e-07 48.9043 4.309e-07C21.8203 1.92261e-07 -1.9479e-07 22.1074 -4.3343e-07 49.1914C-6.20631e-07 70.4375 13.4941 88.0469 31.6777 94.6504C34.2617 95.6074 36.75 93.8848 36.75 91.3008L36.75 83.6445C35.4102 84.2188 33.6875 84.6016 32.1562 84.6016C25.8398 84.6016 22.1074 81.1563 19.4277 74.7441C18.375 72.1602 17.2266 70.6289 15.0254 70.3418C13.877 70.2461 13.4941 69.7676 13.4941 69.1934C13.4941 68.0449 15.4082 67.1836 17.3223 67.1836C20.0977 67.1836 22.4902 68.9063 24.9785 72.4473C26.8926 75.2227 28.9023 76.4668 31.2949 76.4668C33.6875 76.4668 35.2187 75.6055 37.4199 73.4043C39.0469 71.7773 40.291 70.3418 41.4395 69.3848Z"
                fill="currentColor"
              />
            </svg>
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