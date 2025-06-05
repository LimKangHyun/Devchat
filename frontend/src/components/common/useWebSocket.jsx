import { useEffect, useRef } from "react";
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import { useNavigate } from 'react-router-dom';

const useWebSocket = ({
    roomId,
    onMessageReceived,
}) => {
    const stompClientRef = useRef(null);
    const subscriptionRef = useRef(null);
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
                    received.sendAt = received.sendAt || received.joinAt || new Date().toISOString();

                    onMessageReceived(received)
                } catch (e) {
                console.error("📛 Failed to parse incoming message", e);
                }
            });

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

            onWebSocketClose: async () => {
                try {
                    const res = await fetch('http://localhost:8080/auth', {
                    credentials: "include"
                    });

                    if (res.status === 401) {
                    console.warn("세션 만료 → 로그인 페이지로 이동");
                    window.location.href = '/login';
                }
            } catch (err) {
                console.warn("네트워크 오류 → 로그인 페이지로 이동", err);
                window.location.href = '/login';
            }
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
    }, [roomId]);

    return stompClientRef;
};

export default useWebSocket;