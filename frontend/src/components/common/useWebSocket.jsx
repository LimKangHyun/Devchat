import { useEffect, useRef } from "react";
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import { useNavigate } from 'react-router-dom';

const useWebSocket = ({
    roomId,
    onMessageReceived,
    onProfileUpdate,
}) => {
    const stompClientRef = useRef(null);
    const subscriptionRef = useRef(null);
    const profileSubscriptionRef = useRef(null); // 프로필 업데이트 구독 ref 추가
    const hasConnectedRef = useRef(false); // 실제 연결에 성공했는지 추적
    const keepAliveIntervalRef = useRef(null);

    const navigate = useNavigate(); 

    useEffect(() => {
        
        if (!roomId) {
            console.log('⏳ roomId가 없어서 웹소켓 연결을 대기합니다.');
            return;
        }

        const client = new Client({
            webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
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
                    // received.sendAt ||= new Date().toISOString();
                    // sendAt → 없으면 joinAt → 없으면 현재 시간
                    received.sendAt = received.sendAt || new Date().toISOString();
                    onMessageReceived(received)
                } catch (e) {
                console.error("📛 Failed to parse incoming message", e);
                }
            });
            
            // 프로필 업데이트 구독 추가
            // 콜백이 전달된 경우에만 실행
            if (onProfileUpdate) {
                // 중복 구독 방지
            if (profileSubscriptionRef.current) {
                profileSubscriptionRef.current.unsubscribe();
                console.log("🔁 Previous profile subscription cleared.");
            }
            // 구독 시작
            profileSubscriptionRef.current = client.subscribe('/topic/profile-update', (message) => {
                try {
                    // 수신 메시지 JSON 파싱
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
                    body: 'ping'
                });
                console.log("📡 Sent keep-alive ping");
                }
            }, 20000);
            },
            
            onStompError: (frame) => {
                console.error("💥 STOMP error:", frame.headers['message']);
                if (frame.headers['message']?.includes('Unauthorized') || frame.body?.includes('expired')) {
                    navigate("/login");
                }
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
    }, [roomId, onProfileUpdate]);

    return stompClientRef;
};

export default useWebSocket;