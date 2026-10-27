"use client";

import { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { User, Shield, Edit2 } from "lucide-react";
import PasswordChangeModal from "../components/modals/PasswordChangeModal";
import axiosInstance from "../components/api/axiosInstance";
import styles from "../edit-profile-page.module.css";
import { useWebSocketContext } from "../components/common/WebSocketContext";

export default function EditProfilePage() {
  const navigate = useNavigate();
  const { reconnect } = useWebSocketContext();
  const [userDetails, setUserDetails] = useState(null);
  const [nickname, setNickname] = useState("");
  const [email, setEmail] = useState("");
  const [selectedImage, setSelectedImage] = useState(null);
  const [imageFile, setImageFile] = useState(null);
  const [isModified, setIsModified] = useState(false);
  const [isPasswordModalOpen, setIsPasswordModalOpen] = useState(false);
  const [activeTab, setActiveTab] = useState("personal");

  const alertShownRef = useRef(false);
  const stopRequestRef = useRef(false);

  useEffect(() => {
    const fetchUserDetails = async () => {
      try {
        const response = await axiosInstance.get("/user/details");
        setUserDetails(response.data);
        setNickname(response.data.nickname);
        setEmail(response.data.email);
      } catch (error) {
        if (!alertShownRef.current) {
          alertShownRef.current = true;
          const msg = error?.response?.data?.message || "사용자 정보 조회 실패";
          alert(msg);
          stopRequestRef.current = true;
        }
      }
    };

    fetchUserDetails();
  }, [navigate]);

  const handleImageChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      const imageUrl = URL.createObjectURL(file);
      setSelectedImage(imageUrl);
      setImageFile(file);
      setIsModified(true);
    }
  };

  const handleSavePersonalInfo = async () => {
    const formData = new FormData();
    formData.append(
      "request",
      new Blob(
        [JSON.stringify({ nickname: nickname, email: email })],
        { type: "application/json" }
      )
    );
    if (imageFile) {
      formData.append("profileImage", imageFile);
    }

    try {
      const nicknameChanged = nickname !== userDetails.nickname;
      await axiosInstance.put("/user/info", formData);
      alert("프로필이 성공적으로 수정되었습니다!");

      if (nicknameChanged) {
        await reconnect();
      }

      const response = await axiosInstance.get("/user/details");
      setUserDetails(response.data);
      setNickname(response.data.nickname);
      setEmail(response.data.email);
      setSelectedImage(null);
      setImageFile(null);

      window.dispatchEvent(new Event("profile-updated"));
      console.log("🔥 profile-updated 이벤트 발생");
    } catch (error) {
      const msg = error?.response?.data?.message || "업데이트 실패";
      alert(msg);
    }
  };

  if (!userDetails) {
    return (
      <div className={styles.appContainer}>
        <div className={styles.loadingContainer}>
          <div className={styles.loading}>불러오는 중...</div>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.appContainer}>
      <div className={styles.mainContent}>
        <main className={styles.profileMain}>
          <div className={styles.profileContainer}>
            <h1 className={styles.pageTitle}>프로필 설정</h1>

            <div className={styles.tabs}>
              <div className={styles.tabsList}>
                <button
                  className={`${styles.tabTrigger} ${activeTab === "personal" ? styles.tabActive : ""}`}
                  onClick={() => setActiveTab("personal")}
                >
                  <User className={styles.tabIcon} />
                  개인 정보
                </button>
                {userDetails.provider === "LOCAL" && (
                  <button
                    className={`${styles.tabTrigger} ${activeTab === "security" ? styles.tabActive : ""}`}
                    onClick={() => setActiveTab("security")}
                  >
                    <Shield className={styles.tabIcon} />
                    보안
                  </button>
                )}
              </div>

              {activeTab === "personal" && (
                <div className={styles.tabContent}>
                  <div className={styles.profileCard}>
                    <div className={styles.avatarSection}>
                      <div className={styles.avatarContainer}>
                        <img
                          src={selectedImage || `${process.env.REACT_APP_PROFILE_IMAGE_URL}/${userDetails.profileImg}`}
                          alt="프로필"
                          className={styles.avatarImage}
                          onError={(e) => { e.currentTarget.src = "/images/not-found-profile.webp" }}
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

                    <div className={styles.formSection}>
                      <div className={styles.formGroup}>
                        <label className={styles.label}>닉네임</label>
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
                        <label className={styles.label}>이메일</label>
                        <input
                          type="text"
                          placeholder="사용할 이메일을 입력해주세요."
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

                    <div className={styles.buttonSection}>
                      <button
                        className={styles.saveButton}
                        onClick={handleSavePersonalInfo}
                        disabled={!isModified}
                      >
                        저장
                      </button>
                    </div>
                  </div>
                </div>
              )}

              {activeTab === "security" && (
                <div className={styles.tabContent}>
                  <div className={styles.profileCard}>
                    <div className={styles.securitySection}>
                      <h2 className={styles.securityTitle}>비밀번호 설정</h2>
                      <div className={styles.securityContent}>
                        <p className={styles.securityDescription}>
                          Change your password to keep your account secure. We recommend using a strong, unique password.
                        </p>
                        <button
                          onClick={() => setIsPasswordModalOpen(true)}
                          className={styles.changePasswordButton}
                        >
                          비밀번호 변경
                        </button>
                      </div>

                      <div className={styles.sessionsSection}>
                        <h3 className={styles.sessionsTitle}>2단계 인증</h3>
                        <p className={styles.sessionsDescription}>
                          2단계 인증을 켜면 계정을 더 안전하게 지킬 수 있어요.
                        </p>
                        <button
                          onClick={() => alert("개발 예정입니다.")}
                          className={styles.manageSessionsButton}
                        >
                          2단계 인증 설정
                        </button>
                      </div>

                      <div className={styles.sessionsSection}>
                        <h3 className={styles.sessionsTitle}>로그인 세션</h3>
                        <p className={styles.sessionsDescription}>
                          현재 이 기기에서 로그인되어 있어요. 로그인된 기기를 확인하고 다른 기기에서 로그아웃할 수 있어요.
                        </p>
                        <button
                          onClick={() => alert("개발 예정입니다.")}
                          className={styles.manageSessionsButton}
                        >
                          세션 관리
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
  );
}