# 2026-06-21 프론트엔드 CI/CD 작업 로그

## 작업 내용

- 프론트엔드 pull request와 브랜치 push에서 실행되는 GitHub Actions CI 워크플로를 추가했다.
- main 브랜치 배포용 GitHub Container Registry 이미지 빌드/푸시 CD 워크플로를 추가했다.
- 프론트엔드 빌드 산출물을 artifact로 보관하도록 구성했다.
- GSAP 기반 스크롤/드래그 인터랙션과 Toss/Daangn 스타일의 랜딩, 채팅 목록 hover, 커스텀 커서 UI를 정리했다.
- 검증 명령 `npm run build`를 실행했고 Vite 프로덕션 빌드가 성공했다.
- 랜딩과 채팅방 프리뷰의 타이포그래피 스케일을 정리하고 커스텀 커서가 포인터 중앙에 고정되도록 좌표 보정 방식을 수정했다.
