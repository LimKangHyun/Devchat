# NBE5-7-2-Team08
프로그래머스 백엔드 데브코스 5기 7회차 8팀 2차 프로젝트입니다.

# 🗨️DevChat
## 프로젝트 개요
실시간 채팅서비스를 깃허브와 연결하여 좀 더 쉬운 개발을 도울 수 있는 채팅서비스입니다.

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
|HTML5|  - |
|javascript| - |

</div>
    
<br>



---

# 📌주요기능

### 로그인
- 폼로그인과 깃허브 OAuth로그인을 지원, 인증을 JWT로 관리

### 채팅방 생성
- 깃허브 레포지토리 URL을 첨부하여 채팅방을 개설하면 채팅방이 해당 레포지토리에 Webhook이 연결
### 채팅방 참여
- 각 채팅방 생성시 초대코드가 생성, 이를 통해 채팅방에 참여

### 채팅기능
- 실시간 채팅을 지원
- 코드 전송 시 각 언어에 맞추어 하이라이팅 지원하여 가독성 향상
- 사진 전송 기능

### 깃허브 연동 기능
- 채팅방에 연결된 깃허브 레포지터리에서 이벤트 발생(이슈, PR 등)시 이를 채팅방에 채팅형식 알림으로 전송

<br>
<br>

---
# 👯역할 분담
|이 름|GitHub|역할|
|:---:|---|---|
|[TL]배문성|[gitHub](https://github.com/heets-blue)|-**문서**: 리드미, 와이어프레임 <br> -**기능**: 로그인/회원가입, OAuth 로그인, JWT 토큰 인증, axios 인터셉터, 회원CRUD 기능, 회원정보 수정, 배포|
|임강현|[gitHub](https://github.com/LimKangHyun)|-**문서**: 시스템 구성도, 플로우차트  <br>   -**기능**: 프로필 업데이트 API 구현, 채팅 검색 API 구현, 메시지 비동기 처리, 조회 성능 최적화, CI/CD 구축, 무중단 배포|                                
|임창인|[gitHub](https://github.com/cba700)|-**문서**: 발표자료 <br>-**기능**: 채팅방 생성,초대,입장 구현, url 보안 강화|
|남지은|[gitHub](https://github.com/zie-ning)|-**문서**: 기획서 <br>-**기능**: 웹소켓을 통한 실시간 통신 구현, 깃허브 이벤트 메세지 제작|

<br>
<br>

---
# 📄문서
## 🛢️ERD
![image](https://github.com/user-attachments/assets/d84700d9-3e1b-476f-b3c8-aff4712cca61)

<br>

## 🔀Flow Chart
![image](https://github.com/user-attachments/assets/ce20f766-e6e0-4ca8-a9b5-568af69ed073)

<br>

## 🧾API 명세
Swagger를 사용하여 API명세서를 제작 <br>
![image](https://github.com/user-attachments/assets/d3867fcc-1198-4908-946a-6ecb98ed3a6b)
*Swagger명세 개요 이미지*

<br>

## 🌐시스템 구성도
![시스템 구성도  8팀_2차 팀프로젝트](https://github.com/user-attachments/assets/4d0eb7ed-9255-489e-88ff-3ebd6354c0e8)
배포환경 시스템 구성도 업데이트 예정
