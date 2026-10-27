import React, { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import styles from "../profile-page.module.css";
import axiosInstance from '../components/api/axiosInstance';

const ProfilePage = () => {
  const navigate = useNavigate();
  const [userDetails, setUserDetails] = useState(null);
  const [userRooms, setUserRooms] = useState([]);
  const alertShownRef = useRef(false);
  const stopRequestRef = useRef(false);

  useEffect(() => {
    if (stopRequestRef.current) return;

    const fetchUserDetails = async () => {
      try {
        const res = await axiosInstance.get("/user/details");
        setUserDetails(res.data);
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

  useEffect(() => {
    if (!userDetails || stopRequestRef.current) return;

    const fetchUserRooms = async () => {
      try {
        const res = await axiosInstance.get(`/chat-rooms/mine`);
        setUserRooms(res.data.content);
      } catch (error) {
        if (!alertShownRef.current) {
          alertShownRef.current = true;
          const msg = error?.response?.data?.message || "채팅방 로딩 실패";
          alert(msg);
          stopRequestRef.current = true;
        }
      }
    };

    fetchUserRooms();
  }, [userDetails, navigate]);

  const handleJoinClick = (inviteCode) => {
    navigate(`/chat/${inviteCode}`);
  };

  if (!userDetails) {
    return (
      <div className={styles["app-container"]}>
        <div className={styles["loading"]}>불러오는 중...</div>
      </div>
    );
  }

  return (
    <div className={styles["app-container"]}>
      <header className={styles["page-header"]}>
        <h1 className={styles["page-title"]}>내 프로필</h1>
      </header>

      <div className={styles["page-body"]}>
        <section className={styles["profile-section"]}>
          <div className={styles["profile-image-in-page"]}>
            <img
              className={styles["profile-image"]}
              src={`${process.env.REACT_APP_PROFILE_IMAGE_URL}/${userDetails.profileImg}`}
              alt="프로필"
              onError={(e) => {
                e.target.onerror = null;
                e.target.src = "/images/not-found-profile.webp";
              }}
            />
          </div>

          <div className={styles["profile-info"]}>
            <h2 className={styles["profile-name"]}>{userDetails.nickname}</h2>
            <p className={styles["profile-username"]}>@{userDetails.username}</p>

            <div className={styles["profile-meta"]}>
              <span className={styles["meta-label"]}>이메일</span>
              <p className={`${styles["profile-email"]} ${userDetails.email ? "" : styles["profile-email-empty"]}`}>
                {userDetails.email || "이메일을 설정해주세요"}
              </p>
            </div>

            <div className={styles["profile-actions"]}>
              <button
                type="button"
                className="dc-btn"
                onClick={() => navigate("/myprofile/edit")}
              >
                프로필 설정
              </button>
            </div>
          </div>
        </section>

        <section className={styles["rooms-section"]}>
          <h2 className={styles["rooms-title"]}>
            내가 만든 채팅방
            <span className={styles["rooms-count"]}>{userRooms.length}</span>
          </h2>
          <div className={styles["rooms-list"]}>
            {userRooms.length > 0 ? (
              userRooms.map((room) => (
                <div key={room.id} className={styles["room-item"]}>
                  <div className={styles["room-info"]}>
                    <div className={styles["room-icon"]}>
                      <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
                      </svg>
                    </div>
                    <div className={styles["room-text"]}>
                      <h3 className={styles["room-title"]}>{room.roomName}</h3>
                      <div className={styles["room-members"]}>
                        <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                          <circle cx="9" cy="7" r="4"></circle>
                          <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                          <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
                        </svg>
                        <span>{room.participantCount}명</span>
                      </div>
                    </div>
                  </div>
                  <button
                    type="button"
                    className={`dc-btn ${styles["join-button"]}`}
                    onClick={() => handleJoinClick(room.inviteCode)}
                  >
                    입장
                  </button>
                </div>
              ))
            ) : (
              <div className={styles["empty-rooms"]}>
                <p>아직 만든 채팅방이 없어요</p>
              </div>
            )}
          </div>
        </section>
      </div>
    </div>
  );
};

export default ProfilePage;
