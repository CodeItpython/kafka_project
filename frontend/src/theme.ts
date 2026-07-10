// UI 테마(라이트/다크/시스템) — <html data-theme="light|dark">로 적용.
// 선호는 로그인 사용자 프로필(백엔드)에 저장하고, 로그인 전/즉시 반영을 위해 localStorage에도 캐시한다.

export type Theme = 'light' | 'dark' | 'system';

const KEY = 'theme';

export function loadTheme(): Theme {
  try {
    const v = localStorage.getItem(KEY);
    if (v === 'light' || v === 'dark' || v === 'system') return v;
  } catch {
    /* ignore */
  }
  return 'system';
}

export function saveThemeLocal(theme: Theme) {
  try {
    localStorage.setItem(KEY, theme);
  } catch {
    /* ignore */
  }
}

function prefersDark(): boolean {
  return typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-color-scheme: dark)').matches;
}

function resolve(theme: Theme): 'light' | 'dark' {
  if (theme === 'system') return prefersDark() ? 'dark' : 'light';
  return theme;
}

export function applyTheme(theme: Theme) {
  if (typeof document === 'undefined') return;
  document.documentElement.setAttribute('data-theme', resolve(theme));
}

// 'system'일 때 OS 테마 변경을 추적해 실시간 반영. 구독 해제 함수를 반환.
export function watchSystemTheme(getTheme: () => Theme): () => void {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return () => {};
  const media = window.matchMedia('(prefers-color-scheme: dark)');
  const handler = () => {
    if (getTheme() === 'system') applyTheme('system');
  };
  media.addEventListener('change', handler);
  return () => media.removeEventListener('change', handler);
}
