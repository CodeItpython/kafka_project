# 서드파티 자산 고지

## phone.glb
- 출처: https://github.com/heygen-com/hyperframes — `registry/blocks/vfx-iphone-device/models/iphone.glb`
- 라이선스: Apache License 2.0 (https://github.com/heygen-com/hyperframes/blob/main/LICENSE)
- 변경 사항: 랜딩 3D 씬(`src/landing/scene/HeroScene.tsx`)에서 런타임에 재질 색을 그래파이트로 바꾸고
  로고·카메라·버튼 등 브랜드 식별 노드는 렌더하지 않는다(일반적인 스마트폰 형태로만 사용). 파일 자체는 원본 그대로.
- 참고: Apache-2.0 은 파일의 저작권 라이선스이며, 제품 외형에 대한 제3자 디자인 권리는 별개다.

## 스틸 이미지 (hero*.jpg, stream.jpg, room.jpg)
- Higgsfield(Nano Banana Pro)로 이 프로젝트가 직접 생성. 프롬프트는 README.md.

## room.mp4 / room.webm
- 출처: Free Stock Footage Archive — "Blue Veil – Echo Abstract Effect Loop"
  https://freestockfootagearchive.com/blue-veil-echo-abstract-effect-loop/
- 라이선스: CC BY 4.0 (개인·상업 이용 허용, 출처 표기 필수)
- 변경 사항: H.264/VP9 로 재인코딩(GOP 6, faststart, 무음). THE ROOM 섹션 배경 루프로 사용, 화면 위 스크림으로 어둡게 처리.
