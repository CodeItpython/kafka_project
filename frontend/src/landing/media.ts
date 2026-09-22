// 랜딩 미디어 경로. 파일 규격과 생성 프롬프트는 public/media/landing/README.md 참고.
const BASE = '/media/landing';

export const MEDIA = {
  hero: {
    mp4: `${BASE}/hero.mp4`,
    webm: `${BASE}/hero.webm`,
    mp4Mobile: `${BASE}/hero-720.mp4`,
    poster: `${BASE}/hero.jpg`,
    last: `${BASE}/hero-last.jpg`,
    // prefers-reduced-motion 대체 3장: 스트림 내부 / 코어 / 완성 기기
    stills: [`${BASE}/hero-still-a.jpg`, `${BASE}/hero-still-b.jpg`, `${BASE}/hero-still-c.jpg`]
  },
  stream: { mp4: `${BASE}/stream.mp4`, webm: `${BASE}/stream.webm`, poster: `${BASE}/stream.jpg` },
  room: { mp4: `${BASE}/room.mp4`, webm: `${BASE}/room.webm`, poster: `${BASE}/room.jpg` }
} as const;

// 저성능·저속 네트워크에서는 720p 를 고른다 (Network Information API 는 Chromium 계열만 지원)
export function prefersLowBandwidth() {
  if (typeof navigator === 'undefined') return false;
  const nav = navigator as Navigator & { connection?: { saveData?: boolean; effectiveType?: string } };
  const c = nav.connection;
  if (c?.saveData) return true;
  if (c?.effectiveType && /(^|-)2g$|3g/.test(c.effectiveType)) return true;
  return window.matchMedia('(max-width: 720px)').matches;
}
