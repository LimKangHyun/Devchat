import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';

import ChatRoom from './pages/ChatRoom';
import Home from './pages/Home';
import BlankRoom from './pages/BlankRoom';
import Login from './pages/login-form';
import Signup from './pages/signup';
import MyPage from './pages/profile';
import EditProfilePage from './pages/editprofile';
import ErrorPage from './pages/ErrorPage';

import Layout from './Layout';

function App() {
  // const [chatRooms, setChatRooms] = useState([]);
  // const [currentRoomId, setCurrentRoomId] = useState(null);
  // const [isLoggedIn, setIsLoggedIn] = useState(false);

  // // ✅ 로그인 후 채팅방 목록 가져오기
  // useEffect(() => {
  //   const fetchChatRooms = async () => {
  //     try {
  //       const res = await getChatRooms();
  //       setChatRooms(res);
  //       setIsLoggedIn(true);
  //     } catch (e) {
  //       console.error('🚨 채팅방 목록 로딩 실패:', e);
  //     }
  //   };

  // }, []);

  return (
    <BrowserRouter>
      <Routes>
        {/* 공통 레이아웃이 적용될 라우트들 */}
        <Route element={<Layout />}>
          <Route path="/chat/:inviteCode" element={<ChatRoom />} />
          <Route path="/blank" element={<BlankRoom />} />
          <Route path="/myprofile" element={<MyPage />} />
          <Route path="/myprofile/edit" element={<EditProfilePage />} />
          <Route path="/" element={<Home />} />
        </Route>
        {/* 레이아웃 없는 라우트 (예: 로그인, 회원가입, 에러 페이지) */}
        <Route path="/login" element={<Login />} />
        <Route path="/signup" element={<Signup />} />
        <Route path="/error" element={<ErrorPage />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;