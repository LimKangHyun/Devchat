import React, { useEffect, useState } from 'react';
import Header from './components/header';
import Sidebar from './components/SideBar';
import { Outlet } from 'react-router-dom';
import { ChatManager } from './components/chat/chat-manager'; // 경로 확인 필수
import axiosInstance from './components/api/axiosInstance';

const Layout = () => {
  const [currentUser, setCurrentUser] = useState(null);

  useEffect(() => {
    const fetchCurrentUser = async () => {
      try {
        const res = await axiosInstance.get('/user/details');
        setCurrentUser(res.data);
      } catch (err) {
        console.error('유저 정보 불러오기 실패:', err);
      }
    };

    fetchCurrentUser();
  }, []);

  if (!currentUser) return null; // 로딩 중이거나 실패 시 null

  return (
    <div style={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
      <Header />
      <ChatManager currentUser={currentUser}>
        {({ openChatModal }) => (
          <div style={{ flex: 1, display: 'flex', minHeight: 0 }}>
            <Sidebar onStartChat={openChatModal} />
            <div
              style={{
                flex: 1,
                overflow: 'auto',
                backgroundColor: '#e0e0e0',
                height: '100%',
              }}
            >
              <Outlet />
            </div>
          </div>
        )}
      </ChatManager>
    </div>
  );
};

export default Layout;
