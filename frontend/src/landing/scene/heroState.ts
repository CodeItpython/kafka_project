// 히어로 스크롤 진행도(0..1)를 3D 씬으로 전달하는 공유 상태.
// Hero 가 target 을 갱신하고, 씬은 매 프레임 current 를 부드럽게 따라간다.
export const heroState = { target: 0, current: 0 };

export const clamp01 = (v: number) => Math.min(1, Math.max(0, v));
// a..b 구간을 0..1 로 (smoothstep)
export function ramp(v: number, a: number, b: number) {
  const t = clamp01((v - a) / (b - a));
  return t * t * (3 - 2 * t);
}
