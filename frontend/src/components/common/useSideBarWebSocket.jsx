import { useEffect, useRef } from "react";
import { Client } from '@stomp/stompjs';
import { useNavigate } from 'react-router-dom';
import { safeRefreshToken } from "../api/refreshManager";

const useSideBarWebSocket = ({
    chatRooms = [], // 채팅방 목록
    currentRoomId, // 현재 활성화된 채팅방 ID
    onSidebarMessage, // 사이드바 메시지 처리 콜백
}) => {
    const stompClientRef = useRef(null);
    const subscriptionRef = useRef(null);
    const hasConnectedRef = useRef(false); // 실제 연결에 성공했는지 추적
    const sidebarSubscriptionsRef = useRef(new Map()); // 사이드바 구독들 관리하는 Map
    const keepAliveIntervalRef = useRef(null);

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

            // 모든 채팅방에 대해 구독 설정
            if (chatRooms.length > 0 && onSidebarMessage) {

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
    }, [currentRoomId, navigate]);

    return stompClientRef;
};

export default useSideBarWebSocket;