import React, { useEffect, useState } from 'react';
import Header from './components/header';
import Sidebar from './components/SideBar';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { consumeRedirect } from './utils/postLoginRedirect';
import { ChatManager } from './components/chat/chat-manager';
import { useUser } from './context/UserContext';
import useIsMobile from './hooks/useIsMobile';
import { ScheduleModalProvider } from './components/schedule/ScheduleModalContext';

const Layout = () => {
  const { currentUser, isLoading } = useUser();
  const [activePanel, setActivePanel] = useState('chat');
  const isMobile = useIsMobile();
  const { pathname } = useLocation();
  const navigate = useNavigate();

  // GitHub 로그인 전에 저장해 둔 경로(초대 링크 등)가 있으면 로그인 직후 그리로 보낸다.
  useEffect(() => {
    if (!currentUser) return;
    const redirect = consumeRedirect();
    if (redirect && redirect !== pathname) navigate(redirect, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentUser]);

  // 달력 주소로 바로 들어오면(알림·푸시) 왼쪽도 일정 탭으로, 채팅방으로 가면 채팅방 탭으로 돌린다
  useEffect(() => {
    if (pathname.startsWith('/calendar')) setActivePanel('calendar');
    else if (pathname.startsWith('/chat/')) setActivePanel((p) => (p === 'calendar' ? 'chat' : p));
  }, [pathname]);

  if (isLoading || !currentUser) return null;

  // 모바일: 목록 화면(/, /blank) ↔ 채팅방 화면(/chat/...) 을 전환해서 보여준다.
  // 헤더·사이드바는 알림/방 삭제 리스너를 갖고 있어서 언마운트하지 않고 숨기기만 한다.
  const inRoom = pathname.startsWith('/chat/');
  const showMobileList = isMobile && (pathname === '/' || pathname === '/blank');
  const showTabbar = isMobile && !inRoom;

  return (
    <div className={`dc-shell ${showTabbar ? 'has-tabbar' : ''}`}>
      <ChatManager currentUser={currentUser}>
        {({ openChatModal }) => (
          <ScheduleModalProvider>
            <Header
              activePanel={activePanel}
              onPanelChange={setActivePanel}
              hidden={isMobile && !showTabbar}
            />
            <div className="dc-workspace">
              <Sidebar
                activeTab={activePanel}
                onStartChat={openChatModal}
                hidden={isMobile && !showMobileList}
              />
              {!showMobileList && (
                <main className="dc-main dc-scroll">
                  <Outlet />
                </main>
              )}
            </div>
          </ScheduleModalProvider>
        )}
      </ChatManager>
    </div>
  );
};

export default Layout;
