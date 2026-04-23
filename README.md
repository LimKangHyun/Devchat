# NBE5-7-2-Team08
프로그래머스 백엔드 데브코스 5기 7회차 8팀 2차 프로젝트입니다.

# 🗨️DevChat
## 프로젝트 개요
실시간 채팅서비스를 깃허브와 연결하여 좀 더 쉬운 개발을 도울 수 있는 채팅서비스입니다.

## 서비스 주소
https://thedevchat.duckdns.org

## 💻개발 환경 및 기술 스택
<div align=center>
    <img src="https://img.shields.io/badge/github-181717?style=for-the-badge&logo=github&logoColor=white">
    <img src="https://img.shields.io/badge/spring_boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white">
    <img src="https://img.shields.io/badge/java-F2302F?style=for-the-badge&logo=openjdk&logoColor=white">
    <img src="https://img.shields.io/badge/mysql-4479A1?style=for-the-badge&logo=mysql&logoColor=white">
    <img src="https://img.shields.io/badge/redis-%23DD0031.svg?style=for-the-badge&logo=redis&logoColor=white">
    <img src="https://img.shields.io/badge/flyway-CC0200.svg?style=for-the-badge&logo=flyway&logoColor=white">
    <br>
    <img src="https://img.shields.io/badge/spring_security-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white">
    <img src="https://img.shields.io/badge/JWT-black?style=for-the-badge&logo=JSON%20web%20tokens">
    <br>
    <img src="https://img.shields.io/badge/react-61DAFB?style=for-the-badge&logo=react&logoColor=black">
    <img src = "https://img.shields.io/badge/html5-%23E34F26.svg?style=for-the-badge&logo=html5&logoColor=white">
    <img src = "https://img.shields.io/badge/javascript-%23323330.svg?style=for-the-badge&logo=javascript&logoColor=%23F7DF1E">


| 기술 | 버전 |
|-------|-------|
|  Java| 21 | 
|  JDK| OpenJDK 23.0.2 | 
| Spring Boot | 3.4.5 | 
|Spring Boot Libraries |Data JPA, Web, Web Socket, Security, OAuth2, JWT, Webflux, Flyway|
|Lombok|1.18.36|
|MySQL	MySQL Community|8.4.4|
|MySQL Connector| 9.1.0|
|Redis | 3.0.504|
|React | 19.1.0 |

</div>
    
<br>



---

# 📌주요기능

### 로그인
- 폼 로그인과 GitHub OAuth 로그인을 지원하며, 인증은 JWT 기반으로 관리

### 프로필 관리
- 프로필 사진 변경, 닉네임 변경, 비밀번호 변경 기능 지원

### 깃허브 레포지토리 연동형 채팅방
- GitHub 레포지토리 URL을 첨부하여 채팅방을 생성하면, 해당 레포지토리에 Webhook이 자동으로 연동되도록 구현
- 채팅방에 연결된 깃허브 레포지터리에서 이벤트 발생(이슈, PR 등)시 이를 채팅방에 채팅형식 알림으로 전송

### 채팅방 참여
- 각 채팅방 생성시 초대코드가 생성, 이를 통해 채팅방에 참여

### 그룹 채팅기능
- WebSocket 기반의 실시간 채팅 기능 제공
- 코드 전송 시 언어별 문법 하이라이팅을 적용하여 가독성 향상
- 이미지(사진) 전송 기능 지원

### 코드리뷰 기능
- 채팅방 내에서 실시간 코드 리뷰를 진행할 수 있도록 하여, 즉각적인 피드백과 원활한 협업이 가능하도록 구현### 친구 기능
### 친구 기능
- 유저 검색을 통해 친구 추가를 신청할 수 있으며, 친구 관계 성립 시 1:1 대화(DM) 기능을 제공

### 알림 기능
- 친구 요청 및 채팅 수신(DM, 채팅방) 이벤트에 대한 알림 수신함 기능 제공

<br>
<br>

---
# 👯역할 분담
|이 름|GitHub|역할|
|:---:|---|---|
|[TL]배문성|[gitHub](https://github.com/heets-blue)|-**문서**: 리드미, 와이어프레임 <br> -**기능**: 인증 구현(JWT 토큰), 토큰 동시성 문제 개선, 친구기능 및 유저 검색 API 구현, 수신함 구현, DM 구현, 알림기능 구현|
|임강현|[gitHub](https://github.com/LimKangHyun)|-**문서**: 시스템 구성도, 플로우차트  <br>   -**기능**: 프로필 업데이트 API 구현, 채팅 검색 API 구현, 메시지 비동기 처리, 조회 성능 최적화, CI/CD 구축, Blue/Green 배포|                                
|임창인|[gitHub](https://github.com/cba700)|-**문서**: 발표자료 <br>-**기능**: 채팅방 생성,초대,입장 구현, url 보안 강화|
|남지은|[gitHub](https://github.com/zie-ning)|-**문서**: 기획서 <br>-**기능**: 웹소켓을 통한 실시간 통신 구현, 깃허브 이벤트 메세지 제작, 채팅방 알림 구현|

<br>
<br>

---
# 📄문서
## 🛢️ERD
<img width="1833" height="1058" alt="2차 프로젝트" src="https://github.com/user-attachments/assets/fb91576c-285b-4dc5-8ae2-cafbc681b703" />
<br>

## 🔀Flow Chart
![image](https://github.com/user-attachments/assets/ce20f766-e6e0-4ca8-a9b5-568af69ed073)

<br>

## 🧾API 명세
> 👉 **[Swagger UI 바로가기](https://thedevchat.duckdns.org/api/swagger-ui/index.html#/)**

<br>

## 🌐시스템 구성도
<img width="3946" height="3728" alt="제목 없는 다이어그램" src="https://github.com/user-attachments/assets/b361ae26-72f4-479b-97a2-ac1f86ebcde2" />
