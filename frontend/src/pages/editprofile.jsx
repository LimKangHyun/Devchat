"use client"

import { useState, useEffect, useRef } from "react"
import { useNavigate } from "react-router-dom"
import { User, Shield, Edit2 } from "lucide-react"
import Sidebar from '../components/SideBar';
import Header from '../components/header';
import PasswordChangeModal from "../components/modals/PasswordChangeModal"
import axiosInstance from "../components/api/axiosInstance"
import styles from "../edit-profile-page.module.css"

export default function EditProfilePage() {
  const navigate = useNavigate()
  const [userDetails, setUserDetails] = useState(null)
  const [nickname, setNickname] = useState("")
  const [email, setEmail] = useState("")
  const [selectedImage, setSelectedImage] = useState(null)
  const [imageFile, setImageFile] = useState(null)
  const [isModified, setIsModified] = useState(false);
  const [isPasswordModalOpen, setIsPasswordModalOpen] = useState(false)
  const [activeTab, setActiveTab] = useState("personal")

  const alertShownRef = useRef(false)
  const stopRequestRef = useRef(false)

  useEffect(() => {
    const fetchUserDetails = async () => {
      try {
        const response = await axiosInstance.get("/user/details")
        setUserDetails(response.data)
        setNickname(response.data.nickname)
        setEmail(response.data.email)
      } catch (error) {
        if (!alertShownRef.current) {
          alertShownRef.current = true
          const msg = error?.response?.data?.message || "사용자 정보 조회 실패"
          alert(msg)
          stopRequestRef.current = true
        }
      }
    }

    fetchUserDetails()
  }, [navigate])

  const handleImageChange = (e) => {
    const file = e.target.files[0]
    if (file) {
      const imageUrl = URL.createObjectURL(file)
      setSelectedImage(imageUrl)
      setImageFile(file)
      setIsModified(true);
    }
  }

  const handleSavePersonalInfo = async () => {
    const formData = new FormData()
    formData.append("request", new Blob([JSON.stringify({
      nickname: nickname,
      email: email
    })], { type: "application/json"}));
    if (imageFile) {
      formData.append("profileImg", imageFile)
    }

    try {
      await axiosInstance.put("/user/info", formData)
      alert("프로필이 성공적으로 수정되었습니다!")
      const response = await axiosInstance.get("/user/details")
      setUserDetails(response.data)
      setNickname(response.data.nickname)
      setEmail(response.data.email)
      setSelectedImage(null)
      setImageFile(null)

      window.dispatchEvent(new Event("profile-updated"))
      console.log("🔥 profile-updated 이벤트 발생")
    } catch (error) {
      const msg = error?.response?.data?.message || "업데이트 실패"
      alert(msg)
    }
  }

  if (!userDetails) {
    return (
      <div className={styles.appContainer}>
        <div className={styles.loadingContainer}>
          <div className={styles.loading}>Loading...</div>
        </div>
      </div>
    )
  }

  return (
    <div className={styles.appContainer}>
      <Header />
      {/* Main Content */}
      
      <div className={styles.mainContent}>
      <Sidebar />

        {/* Profile Content */}
        <main className={styles.profileMain}>
          <div className={styles.profileContainer}>
            <h1 className={styles.pageTitle}>Profile Setting</h1>

            {/* Tabs */}
            <div className={styles.tabs}>
              <div className={styles.tabsList}>
                <button
                  className={`${styles.tabTrigger} ${activeTab === "personal" ? styles.tabActive : ""}`}
                  onClick={() => setActiveTab("personal")}
                >
                  <User className={styles.tabIcon} />
                  Personal Info
                </button>
                {userDetails.provider === "LOCAL" && (
                  <button
                    className={`${styles.tabTrigger} ${activeTab === "security" ? styles.tabActive : ""}`}
                    onClick={() => setActiveTab("security")}
                  >
                    <Shield className={styles.tabIcon} />
                    Security
                  </button>
                )}
              </div>

              {/* Personal Info Tab */}
              {activeTab === "personal" && (
                <div className={styles.tabContent}>
                  <div className={styles.profileCard}>
                    {/* Profile Avatar Section */}
                    <div className={styles.avatarSection}>
                      <div className={styles.avatarContainer}>
                        <img
                          src={selectedImage || `http://localhost:8080/images/profile/${userDetails.profileImg}`}
                          alt="Profile"
                          className={styles.avatarImage}
                          onError={(e) => {
                            e.currentTarget.src = "/images/not-found-profile.png" 
                          }}
                          
                        />
                        <div className={styles.editIcon}>
                          <Edit2 size={20} style={{ pointerEvents: "none" }} />
                          <input
                            type="file"
                            accept="image/*"
                            className={styles.fileInput}
                            onChange={handleImageChange}
                          />
                        </div>
                      </div>
                    </div>

                    {/* Nickname Field */}
                    <div className={styles.formSection}>
                      <div className={styles.formGroup}>
                        <label className={styles.label}>Nickname</label>
                        <input
                          type="text"
                          placeholder="닉네임 입력"
                          value={nickname}
                          onChange={(e) => {
                            const value = e.target.value;
                            setNickname(value);
                            setIsModified(
                              value !== userDetails.nickname ||
                              email !== userDetails.email ||
                              imageFile !== null
                            );
                          }}
                          className={styles.input}
                          required
                        />
                      </div>

                      <div className={styles.formGroup}>
                        <label className={styles.label}>Email</label>
                        <input
                          type="text"
                          placeholder="사용할 email을 설정해주세요."
                          value={email}
                          onChange={(e) => {
                            const value = e.target.value;
                            setEmail(value);
                            setIsModified(
                              nickname !== userDetails.nickname ||
                              value !== userDetails.email ||
                              imageFile !== null
                            );
                          }}
                          className={styles.input}
                          required
                        />
                      </div>
                    </div>


                    {/* Save Button */}
                    <div className={styles.buttonSection}>
                      <button 
                      className={styles.saveButton}
                      onClick={handleSavePersonalInfo}
                      disabled={!isModified}
                      >
                        Save
                      </button>
                    </div>
                  </div>
                </div>
              )}

              {/* Security Tab */}
              {activeTab === "security" && (
                <div className={styles.tabContent}>
                  <div className={styles.profileCard}>
                    <div className={styles.securitySection}>
                      <h2 className={styles.securityTitle}>Password Settings</h2>

                      <div className={styles.securityContent}>
                        <p className={styles.securityDescription}>
                          Change your password to keep your account secure. We recommend using a strong, unique
                          password.
                        </p>

                        <button onClick={() => setIsPasswordModalOpen(true)} className={styles.changePasswordButton}>
                          Change Password
                        </button>
                      </div>

                      <div className={styles.sessionsSection}>
                        <h3 className={styles.sessionsTitle}>Two-Step Authentication</h3>
                        <p className={styles.sessionsDescription}>
                          Add an extra layer of security to your account by enabling two-step authentication.
                        </p>
                        <button onClick={() => alert("좀 있어보임? ㅇㅈ ㅋㅋㅋ")} className={styles.manageSessionsButton}>
                          Set Two-Step Authentication
                        </button>
                      </div>

                      <div className={styles.sessionsSection}>
                        <h3 className={styles.sessionsTitle}>Login Sessions</h3>
                        <p className={styles.sessionsDescription}>
                          You're currently logged in on this device. You can review your active sessions and log out
                          from other devices.
                        </p>
                        <button onClick={() => alert("좀 있어보임? ㅇㅈ ㅋㅋㅋ")} className={styles.manageSessionsButton}>
                          Manage Sessions
                        </button>
                      </div>

                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        </main>
      </div>

      <PasswordChangeModal
        isOpen={isPasswordModalOpen}
        onClose={() => setIsPasswordModalOpen(false)}
        axiosInstance={axiosInstance}
      />
    </div>
  )
}
