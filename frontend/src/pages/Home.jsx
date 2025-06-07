import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axiosInstance from '../components/api/axiosInstance';

const Home = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true); // 로딩 상태

  useEffect(() => {

    axiosInstance.get(`/chat-rooms/recent`, { 
    })
      .then(res => {
        const roomId = res.data.roomId;
        const inviteCode = res.data.inviteCode;
        console.log('최근 방 - roomId:', roomId, 'inviteCode:', inviteCode);
        
        if (inviteCode) {
            navigate(`/chat/${inviteCode}`); // ✅ inviteCode만 사용
        } else {
            navigate('/blank');
        }
      })
      .catch(err => {
        const status = err.response?.status;
        if (status === 404) {
          navigate('/blank'); // 참여 중인 채팅방 없음
        } else {
          navigate('/login');
        }
      })
      .finally(() => {
        setLoading(false);
      });
  }, [navigate]);

  if(loading){
    return <div>가장 최근 채팅방으로 이동 중...</div>;
  }
};

export default Home;