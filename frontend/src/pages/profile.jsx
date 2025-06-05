import React, { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import Sidebar from '../components/SideBar';
import Header from '../components/header';
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
          const status = error?.response?.status;
          const msg = error?.response?.data?.message || "사용자 정보 조회 실패";
          alert(msg);
          stopRequestRef.current = true;

          if (status === 401) {
            navigate("/login");
          }
        }
      }
    };

    fetchUserDetails();
  }, [navigate]);

  useEffect(() => {
    if (!userDetails || stopRequestRef.current) return;

    const fetchUserRooms = async () => {
      try {
        const res = await axiosInstance.get(`/chat-rooms/mine/${userDetails.id}`);
        setUserRooms(res.data.content);
      } catch (error) {
        if (!alertShownRef.current) {
          alertShownRef.current = true;
          const msg = error?.response?.data?.message || "채팅방 로딩 실패";
          alert(msg);
          stopRequestRef.current = true;
          navigate("/login");
        }
      }
    };

    fetchUserRooms();
  }, [userDetails, navigate]);

  const handleJoinClick = (inviteCode) => {
    navigate(`/chat/${inviteCode}`);
  };

  if (!userDetails) {
    return <div>Loading...</div>;
  }

  return (
    <div className={styles["app-container"]}>
      <Header />
      <div className={styles["content-wrapper"]}>
        <Sidebar />
        <div className={styles["main-content"]}>
          <h1 className={styles["page-title"]}>My Profile</h1>
          <div className={styles["profile-container"]}>
            <div className={styles["profile-section"]}>
              <div className={styles["profile-card"]}>
                <div className={styles["profile-image-container"]}>
                  <div className={styles["profile-image-in-page"]}>
                    <img
                      className={styles["profile-image"]}
                      src={`http://localhost:8080/images/profile/${userDetails.profileImg}`}
                      alt="Profile"
                      onError={(e) => {
                        e.target.onerror = null;
                        e.target.src = "/placeholder.svg";
                      }}
                    />
                  </div>
                </div>
                <h2 className={styles["profile-name"]}>{userDetails.nickname}</h2>
                <p className={styles["profile-email"]}>{userDetails.email}</p>
                <button
                  className={styles["edit-profile-button"]}
                  onClick={() => navigate("/myprofile/edit")}
                >
                  Edit Profile
                </button>
              </div>
            </div>

            <div className={styles["rooms-section"]}>
              <h2 className={styles["rooms-title"]}>Rooms I Created</h2>
              <div className={styles["rooms-list"]}>
                {userRooms.length > 0 ? (
                  userRooms.map((room) => (
                    <div key={room.id} className={styles["room-item"]}>
                      <div className={styles["room-info"]}>
                        <div className={styles["room-icon"]}>
                          <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
                          </svg>
                        </div>
                        <div>
                          <h3 className={styles["room-title"]}>{room.roomName}</h3>
                          <div className={styles["room-members"]}>
                            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                              <circle cx="9" cy="7" r="4"></circle>
                              <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                              <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
                            </svg>
                            <span>{room.participantCount} members</span>
                          </div>
                        </div>
                      </div>
                      <button
                        className={styles["join-button"]}
                        onClick={() => handleJoinClick(room.inviteCode)}
                      >
                        Join
                      </button>
                    </div>
                  ))
                ) : (
                  <div className={styles["empty-rooms"]}>
                    <p>🥲 No rooms created yet</p>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ProfilePage;
