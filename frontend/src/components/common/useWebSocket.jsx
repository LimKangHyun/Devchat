import { useEffect, useRef } from "react";
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import { useNavigate } from 'react-router-dom';
import { safeRefreshToken } from "../api/refreshManager";

const useWebSocket = ({
    roomId,
    onMessageReceived,
    chatRooms = [], // 사이드바에서 사용할 채팅방 목록
    currentRoomId, // 현재 활성화된 채팅방 ID
    onSidebarMessage, // 사이드바 메시지 처리 콜백
    onProfileUpdate,
}) => {
    const stompClientRef = useRef(null);
    const subscriptionRef = useRef(null);
    const profileSubscriptionRef = useRef(null);
    const hasConnectedRef = useRef(false); // 실제 연결에 성공했는지 추적
    const sidebarSubscriptionsRef = useRef(new Map()); // 사이드바 구독들 관리하는 Map
    const keepAliveIntervalRef = useRef(null);

    const navigate = useNavigate(); 

    useEffect(() => {
        
        if (!roomId) {
            console.log('⏳ roomId가 없어서 웹소켓 연결을 대기합니다.');
            return;
        }

        const client = new Client({
            webSocketFactory: () => new WebSocket('ws://localhost:8080/ws'),
            reconnectDelay: 1000,
            heartbeatIncoming: 15000,
            heartbeatOutgoing: 10000,
            debug: (str) => console.log(`[STOMP] ${str}`),

            onConnect: () => {
            console.log('✅ Connected to WebSocket');
            hasConnectedRef.current = true;

            if (subscriptionRef.current) {
                subscriptionRef.current.unsubscribe();
                console.log("🔁 Previous subscription cleared.");
            }

            subscriptionRef.current = client.subscribe(`/topic/chat/${roomId}`, (message) => {
                try {
                    const received = JSON.parse(message.body);
                    received.sendAt = received.sendAt || new Date().toISOString();
                    onMessageReceived(received)
                } catch (e) {
                console.error("📛 Failed to parse incoming message", e);
                }
            });

            // 사이드바용 채팅방들 구독 관리
            if (chatRooms.length > 0 && onSidebarMessage) {
            // 기존 사이드바 구독들 정리
            sidebarSubscriptionsRef.current.forEach((subscription, roomId) => {
                subscription.unsubscribe();
                console.log(`🔁 Previous sidebar subscription for room ${roomId} cleared.`);
            });
            sidebarSubscriptionsRef.current.clear();

            // 모든 채팅방에 대해 구독 설정
            chatRooms.forEach(room => {
                const roomUniqueId = room.uniqueId;
                if (roomUniqueId) {
                const subscription = client.subscribe(`/topic/chat/${roomUniqueId}`, (message) => {
                    try {
                    const received = JSON.parse(message.body);
                    
                    // 현재 있는 채팅방이 아닌 경우에만 사이드바 알림 처리
                    if (Number(currentRoomId) !== Number(roomUniqueId)) {
                        onSidebarMessage(roomUniqueId, received);
                        console.log(`📨 New message in room ${roomUniqueId}`);
                    }
                    } catch (e) {
                    console.error("📛 Failed to parse sidebar message", e);
                    }
                });
                
                sidebarSubscriptionsRef.current.set(roomUniqueId, subscription);
                console.log(`📡 Subscribed to sidebar room: ${roomUniqueId}`);
                }
            });
            }

            // 프로필 업데이트 구독
            if (onProfileUpdate) {
            if (profileSubscriptionRef.current) {
                profileSubscriptionRef.current.unsubscribe();
                console.log("🔁 Previous profile subscription cleared.");
            }
            
            profileSubscriptionRef.current = client.subscribe('/topic/profile-update', (message) => {
                try {
                const profileUpdate = JSON.parse(message.body);
                console.log('🔥 프로필 업데이트 수신:', profileUpdate);
                onProfileUpdate(profileUpdate);
                } catch (e) {
                console.error("📛 Failed to parse profile update message", e);
                }
            });
            
            console.log('👤 프로필 업데이트 구독 완료');
            }

            if (keepAliveIntervalRef.current) clearInterval(keepAliveIntervalRef.current);

            keepAliveIntervalRef.current = setInterval(() => {
                if (client && client.connected) {
                client.publish({
                    destination: '/app/ping',
                    body: 'p'
                });
                console.log("📡 Sent keep-alive ping");
                }
            }, 15000);
            },

            onWebSocketClose: async () => {
                console.warn('🛑 WebSocket 끊김 → 토큰 갱신 시도');
                try{
                    await safeRefreshToken(); // 중복 요청 방지됨
                } catch(err){
                    console.error('❌ 토큰 갱신 실패 → 로그인 페이지로 이동');
                    navigate('/login');
                }
                
            },
            
            onStompError: (frame) => {
                console.error("💥 STOMP error:", frame.headers['message']);
            }
        });

        client.activate();
        stompClientRef.current = client;

        return () => {
            console.log("🧹 Cleaning up WebSocket...");

            if (keepAliveIntervalRef.current) {
                clearInterval(keepAliveIntervalRef.current);
                keepAliveIntervalRef.current = null;
                console.log("🔕 Stopped keep-alive ping");
            }
            if (subscriptionRef.current) {
                subscriptionRef.current.unsubscribe();
                subscriptionRef.current = null;
                console.log("🔌 Subscription unsubscribed.");
            }
            if (client && client.active) {
                client.deactivate().then(() => {
                    console.log("🛑 Disconnected from WebSocket");
                });
            }
        };
    }, [currentRoomId, navigate, onProfileUpdate, roomId]);

    return stompClientRef;
};

export default useWebSocket;