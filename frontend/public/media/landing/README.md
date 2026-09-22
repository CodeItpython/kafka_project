# 랜딩 영상 규격 · 생성 프롬프트 · QA

이 디렉터리의 파일은 `WelcomeLanding`이 그대로 참조한다 (`/media/landing/...`).

| 파일 | 용도 | 규격 |
|---|---|---|
| `hero.mp4` / `hero.webm` | HERO FILM — 스크롤 스크러빙 | 1920×1080, 24fps+, 8~10s, 무음, H.264 `-g 6` + `+faststart` / VP9 `-g 6` |
| `hero-720.mp4` | 모바일·저속 네트워크 | 1280×720, 동일 GOP |
| `hero.jpg` | poster (첫 프레임) | 1920×1080 JPG q85 |
| `hero-last.jpg` | GET STARTED 섹션 스틸 (마지막 프레임) | 1920×1080 |
| `hero-still-{a,b,c}.jpg` | reduced-motion 대체 3장 (스트림 내부 / 코어 / 완성 기기) | 1920×1080 |
| `stream.mp4` / `stream.webm` / `stream.jpg` | THE STREAM FILM (자동 재생 루프, 지연 로딩) | 1920×1080, 6s |
| `room.mp4` / `room.webm` / `room.jpg` | THE ROOM 배경 (자동 재생 루프, 지연 로딩) | 1920×1080, 6s |

## 공통 시각 규칙 (모든 클립)

- 거의 검은 스튜디오(#04050a 톤). 주광은 파란색 한 가지(#3d6dff 계열), 따뜻한 주황(#ff7a2f)은 아주 작은 포인트로만
- 기기: 베젤이 얇은 짙은 그래파이트 스마트폰. 화면은 꺼져 있거나 추상 도형만 은은히 발광
- 카메라는 느리고 무게감 있게. 컷 전환·렌즈 플레어·손떨림 금지
- 화면 안에 읽을 수 있는 글자·숫자·로고·워터마크·앱 아이콘 금지 (추상 도형만)
- 실사에 가까운 시네마틱 3D 렌더 질감

## 1. HERO FILM — "FROM THE KEYS TO THE STREAM" (연속 한 컷)

원본(VANTA MOTORS)의 "엔진 내부 → 차 전체" 구조를 KAFKATALK에 맞게 *키보드 조립 → 타이핑 → 메시지가 스트림을 지나 → 기기 도착*으로 바꿨다.

```
Cinematic continuous single-shot 3D product film, photoreal render, no cuts.
Nearly black studio. One cool blue key light (#3d6dff), deep shadows, a faint warm orange rim only on one accent keycap. Slow, heavy, deliberate camera. No lens flare, no camera shake, no text, no logos, no readable characters anywhere.

0-20%: Extreme close-up. Dark matte graphite mechanical keycaps hang in the air, slowly descending and seating themselves onto a thin machined plate with a soft precise click — a keyboard assembling itself. One keycap is warm orange. Blue light rakes across the keycap edges.

20-45%: The board is complete. Two slender dark-metal robotic fingertips lower into frame and type in a calm rhythm. Every press releases a small blue pulse of light from beneath the keycap; the pulses gather along the plate like a heartbeat.

45-70%: The camera pulls back and rises. The pulses merge into a single thin ribbon of blue light that lifts off the keyboard and travels through a translucent glass conduit — it forks into several strands and rejoins, thin rings of light flashing once at each junction. The keyboard recedes into darkness below.

70-100%: The ribbon arrives at a thin dark graphite smartphone floating slightly tilted in the center of the studio. It slips behind the glass and the screen wakes with soft abstract rounded shapes — bubbles and cards, no text. A thin white strip light sweeps once along the phone's edge revealing the silhouette. The camera settles into a low front three-quarter view of the whole phone and comes to a complete rest for the final half second.

Same phone design, same graphite color, same lighting throughout. Restrained, technical, premium.
```

Negative: cuts, morphing between different devices, readable text/numbers/logos/watermarks/app icons, purple or pink neon, multiple colors, lens flare, fast camera, cheap game graphics, stock footage look, robot arms/humans/faces, parts intersecting or melting, existing messenger UI.

## 2. THE STREAM — 6초 보조

```
Cinematic 3D, photoreal, nearly black studio, single blue key light (#3d6dff). Two identical thin dark graphite smartphones float facing each other at a distance. A small blue pulse of light leaves the left phone and travels along a thin elegant curve through the dark to the right phone; on arrival the right phone's edge brightens for a brief instant. The camera slides sideways between them very slowly. Restrained technical visualization, not sci-fi hologram. No text, no logos, no flare, no cuts.
```

## 3. THE ROOM — 6초 보조

```
Macro lens, extreme close-up on the glass of a dark graphite smartphone screen. Photoreal, nearly black, blue light (#3d6dff) as the only color. Micro reflections on the glass. Inside the screen, soft abstract rounded card shapes drift slowly upward; a single small circular frame lights up as if a call is beginning. The camera pushes toward the glass very gently. No readable text, all UI is abstract shapes only. No cuts, no flare.
```

## VIDEO QA (클립마다 통과해야 사용)

```bash
ffmpeg -i hero.mp4 -vf "select='eq(n,0)+eq(n,round(N*0.25))+eq(n,round(N*0.5))+eq(n,round(N*0.75))+eq(n,N-1)'" -vsync vfr qa_%d.png
```
(실제로는 `ffprobe`로 총 프레임 수 N을 먼저 구해 5장을 뽑는다 — `scripts/landing-qa.sh`)

1. 처음부터 끝까지 동일한 기기·색·구도 규칙
2. 읽을 수 있는 글자·숫자·로고 없음
3. 컷 전환·급격한 카메라 점프 없음
4. 히어로 마지막 프레임: 기기 전체 선명 + 카피 얹을 여백
5. 보라·핑크 네온·과도한 플레어 없음
6. 부품 관통·모핑·이상한 형태 없음

클립당 최대 2회 재생성. 실패 시 가장 나은 결과 + 미달 항목 보고.

## 재인코딩 (스크러빙용)

```bash
# H.264, 짧은 GOP, faststart, 무음
ffmpeg -i src.mp4 -an -c:v libx264 -pix_fmt yuv420p -profile:v high -preset slow -crf 18 -g 6 -keyint_min 6 -sc_threshold 0 -movflags +faststart hero.mp4
# VP9 (Firefox 스크러빙용)
ffmpeg -i src.mp4 -an -c:v libvpx-vp9 -b:v 0 -crf 30 -g 6 -row-mt 1 hero.webm
# 720p
ffmpeg -i src.mp4 -an -vf scale=1280:-2 -c:v libx264 -pix_fmt yuv420p -preset slow -crf 20 -g 6 -keyint_min 6 -sc_threshold 0 -movflags +faststart hero-720.mp4
# poster / last frame
ffmpeg -i hero.mp4 -frames:v 1 -q:v 2 hero.jpg
ffmpeg -sseof -0.05 -i hero.mp4 -frames:v 1 -q:v 2 hero-last.jpg
```
