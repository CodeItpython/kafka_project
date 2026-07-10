// 앱 UI 사운드 — Web Audio API로 합성한 짧은 톤(외부 오디오 파일 없음, CSP/저작권/번들 안전).
// 토스풍의 절제되고 부드러운 피드백을 목표로 한다.

type SoundName =
  | 'send'
  | 'receive'
  | 'notify'
  | 'tab'
  | 'tap'
  | 'reaction'
  | 'success'
  | 'toggle';

const STORAGE_KEY = 'soundEnabled';

function loadEnabled(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) !== 'off';
  } catch {
    return true;
  }
}

let enabled = loadEnabled();

let ctx: AudioContext | null = null;

function audio(): AudioContext | null {
  if (typeof window === 'undefined') return null;
  const AC = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!AC) return null;
  if (!ctx) ctx = new AC();
  if (ctx.state === 'suspended') void ctx.resume();
  return ctx;
}

// 자동재생 정책: 오디오 컨텍스트는 사용자 제스처에서만 시작된다.
// 첫 입력에서 잠금을 풀어, 이후 수신/알림 같은 비제스처 이벤트도 소리가 나게 한다.
if (typeof window !== 'undefined') {
  const unlock = () => {
    audio();
    window.removeEventListener('pointerdown', unlock);
    window.removeEventListener('keydown', unlock);
  };
  window.addEventListener('pointerdown', unlock, { once: true });
  window.addEventListener('keydown', unlock, { once: true });
}

// 하나의 오실레이터 + 게인 엔벨로프(빠른 어택, 부드러운 지수 감쇠)로 한 음을 낸다.
function tone(
  c: AudioContext,
  freq: number,
  start: number,
  dur: number,
  gain: number,
  type: OscillatorType = 'sine'
) {
  const osc = c.createOscillator();
  const g = c.createGain();
  osc.type = type;
  osc.frequency.setValueAtTime(freq, start);
  g.gain.setValueAtTime(0.0001, start);
  g.gain.linearRampToValueAtTime(gain, start + 0.008);
  g.gain.exponentialRampToValueAtTime(0.0001, start + dur);
  osc.connect(g).connect(c.destination);
  osc.start(start);
  osc.stop(start + dur + 0.02);
}

const M = 0.13; // 마스터 볼륨(절제된 수준)

const RECIPES: Record<SoundName, (c: AudioContext, t: number) => void> = {
  // 메시지 보내기 — 산뜻하게 올라가는 두 음
  send: (c, t) => {
    tone(c, 660, t, 0.11, M);
    tone(c, 988, t + 0.05, 0.12, M * 0.8);
  },
  // 메시지 받기 — 부드럽게 내려오는 두 음
  receive: (c, t) => {
    tone(c, 880, t, 0.13, M * 0.9);
    tone(c, 587, t + 0.07, 0.16, M * 0.7);
  },
  // 알림 — 맑은 벨 느낌(triangle)
  notify: (c, t) => {
    tone(c, 784, t, 0.16, M, 'triangle');
    tone(c, 1175, t + 0.09, 0.2, M * 0.8, 'triangle');
  },
  // 탭 전환 — 아주 짧은 틱
  tab: (c, t) => {
    tone(c, 523, t, 0.06, M * 0.5, 'triangle');
  },
  // 버튼/항목 탭 — 미세한 클릭
  tap: (c, t) => {
    tone(c, 430, t, 0.05, M * 0.42);
  },
  // 리액션 — 톡 튀는 팝
  reaction: (c, t) => {
    tone(c, 700, t, 0.07, M * 0.7);
    tone(c, 1046, t + 0.04, 0.1, M * 0.6);
  },
  // 성공(로그인/요약 등) — 상승 아르페지오
  success: (c, t) => {
    [523, 659, 784, 1046].forEach((f, i) => tone(c, f, t + i * 0.06, 0.22, M * 0.8, 'triangle'));
  },
  // 토글 — 짧은 확인음
  toggle: (c, t) => {
    tone(c, 600, t, 0.07, M * 0.6);
  },
};

export function playSound(name: SoundName) {
  if (!enabled) return;
  const c = audio();
  if (!c) return;
  try {
    RECIPES[name](c, c.currentTime);
  } catch {
    /* 오디오 실패는 조용히 무시 */
  }
}

export function isSoundEnabled(): boolean {
  return enabled;
}

export function setSoundEnabled(next: boolean) {
  enabled = next;
  try {
    localStorage.setItem(STORAGE_KEY, next ? 'on' : 'off');
  } catch {
    /* 저장 실패 무시 */
  }
  if (next) playSound('toggle'); // 켤 때 즉시 체감
}
