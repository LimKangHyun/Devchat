import { useEffect, useRef } from "react";
import { Client } from '@stomp/stompjs';
import { useNavigate } from 'react-router-dom';
import { safeRefreshToken } from "../api/refreshManager";

const useWebSocket = ({
    roomId,
    onMessageReceived,
    chatRooms = [], // 채팅방 목록
    currentRoomId, // 현재 활성화된 채팅방 ID
    onSidebarMessage, // 사이드바 메시지 처리 콜백
    onProfileUpdate,
    onRoomDeleted
}) => {
    const stompClientRef = useRef(null);
    const subscriptionRef = useRef(null);
    const profileSubscriptionRef = useRef(null);
    const hasConnectedRef = useRef(false); // 실제 연결에 성공했는지 추적
    const sidebarSubscriptionsRef = useRef(new Map()); // 사이드바 구독들 관리하는 Map
    const keepAliveIntervalRef = useRef(null);
    const deleteSubscriptionRef = useRef(null)

    const navigate = useNavigate(); 

    useEffect(() => {

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

            // 들어가 있는 채팅방 구독
            if(roomId){
                subscriptionRef.current = client.subscribe(`/topic/chat/${roomId}`, (message) => {
                    try {
                        const received = JSON.parse(message.body);
                        received.sendAt = received.sendAt || new Date().toISOString();
                        onMessageReceived(received)
                    } catch (e) {
                        console.error("📛 Failed to parse incoming message", e);
                    }
                });
            }

            // 사이드바 채팅방 구독
            if (chatRooms.length > 0) {
                chatRooms.forEach(room => {
                    const roomUniqueId = room.uniqueId;
                    if (roomUniqueId) {
                        const subscription = client.subscribe(`/topic/chat/${roomUniqueId}`, (message) => {
                            try {
                                const received = JSON.parse(message.body);
                                
                                // 현재 있는 채팅방이 아닌 경우에만 사이드바 알림 처리
                                if (Number(currentRoomId) !== Number(roomUniqueId) && received.type !== 'EVENT') {
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

            // 방 삭제 구독 추가
            deleteSubscriptionRef.current = client.subscribe(`/topic/chat/${roomId}/deleted`, (message) => {
                try {
                    const deleteData = JSON.parse(message.body);
                    console.log("🗑️ Room deletion received:", deleteData);
                    
                    if (onRoomDeleted && typeof onRoomDeleted === 'function') {
                        onRoomDeleted(deleteData);
                    }
                } catch (e) {
                    console.error("📛 Failed to parse delete message", e);
                }
            });


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
             if (deleteSubscriptionRef.current) {
                deleteSubscriptionRef.current.unsubscribe();
                deleteSubscriptionRef.current = null;
                console.log("🗑️ Delete subscription unsubscribed.");
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