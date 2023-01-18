# 모두의 일기장 REST API 서버

## Techs
* Backend `Kotlin` `Spring Boot` `Spring Data JPA` `QueryDSL` `WebSocket` `MySQL` `S3`
* DevOps  `EC2` `CodeDeploy` `Github Action`

## Authentication
토큰 기반 인증 방식
* AOP 및 ArgumentResolver를 사용하여 요청의 인증 여부 확인 및 유저 정보 바인딩

## Security
* HTTPS 프로토콜
* 유저의 비밀번호 및 IP 정보를 해싱하여 DB에 저장
* DB 계정, AWS 인증 키, JWT 서명 키 등을 암호화(base64)하여 secrets에 입력
* 이전 로그인 한 IP에 따른 메일 인증 구현

## WebSocket
STOMP를 사용한 실시간 개인 채팅 기능

* 서버가 메시지 브로커 역할을 수행

