import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';

import ChatRoom from './pages/ChatRoom';
import Community from './pages/Community';
import CommunityWrite from './pages/CommunityWrite';
import CommunityDetail from './pages/CommunityDetail';
import CommunityEdit from './pages/CommunityEdit';
import CommunityApplicants from './pages/CommunityApplicants';
import Home from './pages/Home';
import BlankRoom from './pages/BlankRoom';
import Login from './pages/login-form';
import Signup from './pages/signup';
import MyPage from './pages/profile';
import EditProfilePage from './pages/editprofile';
import ErrorPage from './pages/ErrorPage';
import { WebSocketProvider } from './components/common/WebSocketContext'

import Layout from './Layout';
import { AlarmProvider } from './context/AlarmContext';
import { UserProvider } from './context/UserContext'

function App() {
  return (
    <AlarmProvider>
      <BrowserRouter>
        <Routes>
          <Route element={
            <UserProvider>
              <WebSocketProvider>
                <Layout />
              </WebSocketProvider>
            </UserProvider>
          }>
            <Route path="/chat/:inviteCode" element={<ChatRoom />} />
            <Route path="/blank" element={<BlankRoom />} />
            <Route path="/community" element={<Community />} />
            <Route path="/community/write" element={<CommunityWrite />} />
            <Route path="/community/:postId" element={<CommunityDetail />} />
            <Route path="/community/:postId/edit" element={<CommunityEdit />} />
            <Route path="/community/:postId/applicants" element={<CommunityApplicants />} />
            <Route path="/myprofile" element={<MyPage />} />
            <Route path="/myprofile/edit" element={<EditProfilePage />} />
            <Route path="/" element={<Home />} />
          </Route>
          <Route path="/login" element={<Login />} />
          <Route path="/signup" element={<Signup />} />
          <Route path="/error" element={<ErrorPage />} />
        </Routes>
      </BrowserRouter>
    </AlarmProvider>
  );
}

export default App;