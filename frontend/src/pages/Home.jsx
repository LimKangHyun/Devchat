import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';

const Home = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true); // 로딩 상태

  useEffect(() => {

    axios.get(`http://localhost:8080/auth`, {
      withCredentials: true,
    })
      .catch(err => {
        const status = err.response?.status;
        if(status===401) {
          navigate("/login")
        } else {
          console.error("에러 발생", err);
          navigate("/login")
        }
      })

    axios.get(`http://localhost:8080/chat-rooms/recent`, { 
        withCredentials: true,

    })
      .then(res => {
        const roomId = res.data.roomId;
        const inviteCode = res.data.inviteCode;
        console.log('최근 방 - roomId:', roomId, 'inviteCode:', inviteCode);
        
        if (inviteCode) {
            navigate(`/chat/${inviteCode}`); // ✅ inviteCode만 사용
        } else if (roomId) {
            // inviteCode가 없지만 roomId가 있는 경우 fallback
            console.warn('inviteCode가 없어서 /blank로 이동');
            navigate('/blank');
        } else {
            console.warn('roomId와 inviteCode가 모두 응답에 없음');
            navigate('/blank'); // fallback
        }
      })
      .catch(err => {
        const status = err.response?.status;
        if (status === 404) {
          navigate('/blank'); // 참여 중인 채팅방 없음
        } else if (status === 401) {
          navigate('/login'); // 인증 필요
        } else {
          console.error('채팅방 이동 실패:', status);
          alert(err);

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