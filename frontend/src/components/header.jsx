"use client"

import { useState, useEffect } from "react"
import "./header.css"
import axiosInstance from "./api/axiosInstance"

export function Header() {
  const [profileImage, setProfileImage] = useState(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState(null)
  

  useEffect(() => {
    const fetchProfileImage = async () => {
      try {
        setIsLoading(true)

        // ✅ axiosInstance 사용하여 인증 포함된 요청 처리
        const { data } = await axiosInstance.get("/user/details")

        setProfileImage(data.profileImg)
        setIsLoading(false)
      } catch (err) {
        console.error("Error fetching profile image:", err)
        setError(err.message)
        setIsLoading(false)
      }
    }

    fetchProfileImage()
  }, [])

  return (
    <header className="header">
      <div className="container">
          <a href="/">
            <img src="/images/devchat-logo.png" alt="DevChat Logo" className="header-logo-image" />     
          </a>
        <div className="profile-container">
          {isLoading ? (
            <div className="profile-image-loading"></div>
          ) : error ? (
            <div className="profile-image-error">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <circle cx="12" cy="12" r="10" />
                <line x1="12" y1="8" x2="12" y2="12" />
                <line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
            </div>
          ) : (
            <a href= {"/myprofile"}>
            <img 
              src={`http://localhost:8080/images/profile/${profileImage}`}
              alt="User profile"
              className="profile-image"
            />
            </a>
          )}
          <button
            className="logout-text"
            style={{
              background: "none",
              border: "none",
              padding: 0,
              margin: 0,
              cursor: "pointer",
              fontWeight: 500
            }}
            onClick={async () => {
              try {
                const response = await axiosInstance.post("/logout", {
                });
                if (response.status===204) {
                  alert("로그아웃 되었습니다.");
                  window.location.href = "/login"; // 또는 원하는 페이지로 이동
                } else {
                  alert("로그아웃 실패");
                }
              } catch (error) {
                console.error("로그아웃 요청 실패:", error);
                alert("서버 오류로 로그아웃 실패");
              }
            }}
          >
            Log Out
          </button>
        </div>
      </div>
    </header>
  )
}

export default Header;
