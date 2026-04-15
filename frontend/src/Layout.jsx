import React from 'react';
import Header from './components/header';
import Sidebar from './components/SideBar';
import { Outlet } from 'react-router-dom';
import { ChatManager } from './components/chat/chat-manager';
import { useUser } from './context/UserContext';

const Layout = () => {
  const { currentUser, isLoading } = useUser();

  if (isLoading || !currentUser) return null;

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