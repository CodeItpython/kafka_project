import React, { FormEvent, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { KeyRound, Mail, ShieldCheck } from 'lucide-react';
import { AnimatePresence, motion } from 'motion/react';
import './styles.css';

type Mode = 'login' | 'register' | 'email';

type User = {
  id: number;
  email: string;
  name: string;
  provider: string;
};

type AuthResponse = {
  accessToken: string;
  tokenType: string;
  user: User;
};

const API_BASE = '/api/auth';

function App() {
  const [mode, setMode] = useState<Mode>('login');
  const [email, setEmail] = useState('user@example.com');
  const [name, setName] = useState('Gunwoo');
  const [password, setPassword] = useState('password123');
  const [code, setCode] = useState('');
  const [debugCode, setDebugCode] = useState('');
  const [message, setMessage] = useState('');
  const [token, setToken] = useState(() => localStorage.getItem('accessToken') ?? '');
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(false);

  const title = useMemo(() => {
    if (mode === 'register') return '계정 만들기';
    if (mode === 'email') return '이메일 인증 로그인';
    return '일반 로그인';
  }, [mode]);

  async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const response = await fetch(`${API_BASE}${path}`, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...init?.headers
      }
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.message ?? '요청을 처리하지 못했습니다.');
    }
    return data as T;
  }

  function saveSession(data: AuthResponse) {
    localStorage.setItem('accessToken', data.accessToken);
    setToken(data.accessToken);
    setUser(data.user);
    setMessage(`${data.user.name}님으로 로그인되었습니다.`);
  }

  async function submitPasswordFlow(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setMessage('');
    try {
      const payload = mode === 'register' ? { email, password, name } : { email, password };
      const data = await request<AuthResponse>(mode === 'register' ? '/register' : '/login', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      saveSession(data);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  }

  async function sendCode() {
    setLoading(true);
    setMessage('');
    try {
      const data = await request<{ expiresAt: string; debugCode: string }>('/email/code', {
        method: 'POST',
        body: JSON.stringify({ email })
      });
      setDebugCode(data.debugCode);
      setCode(data.debugCode);
      setMessage(`인증코드를 생성했습니다. 만료시각: ${new Date(data.expiresAt).toLocaleTimeString()}`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  }

  async function submitEmailFlow(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setMessage('');
    try {
      const data = await request<AuthResponse>('/email/login', {
        method: 'POST',
        body: JSON.stringify({ email, code, name })
      });
      saveSession(data);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  }

  async function loadMe() {
    setLoading(true);
    setMessage('');
    try {
      const data = await request<User>('/me');
      setUser(data);
      setMessage('토큰 검증에 성공했습니다.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  }

  function logout() {
    localStorage.removeItem('accessToken');
    setToken('');
    setUser(null);
    setMessage('로그아웃되었습니다.');
  }

  return (
    <main className="app-shell">
      <motion.section
        className="auth-panel"
        initial={{ opacity: 0, y: 18 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.45, ease: 'easeOut' }}
      >
        <div className="brand-row">
          <ShieldCheck size={32} aria-hidden />
          <div>
            <h1>Kafka Auth</h1>
            <p>Spring Security JWT 로그인</p>
          </div>
        </div>

        <div className="mode-tabs" role="tablist" aria-label="로그인 방식">
          <button className={mode === 'login' ? 'active' : ''} onClick={() => setMode('login')}>일반</button>
          <button className={mode === 'register' ? 'active' : ''} onClick={() => setMode('register')}>가입</button>
          <button className={mode === 'email' ? 'active' : ''} onClick={() => setMode('email')}>이메일</button>
        </div>

        <h2>{title}</h2>
        <AnimatePresence mode="wait">
          {mode === 'email' ? (
            <motion.form
              key="email"
              onSubmit={submitEmailFlow}
              className="auth-form"
              initial={{ opacity: 0, x: 16 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -16 }}
              transition={{ duration: 0.2 }}
            >
              <label>
                이메일
                <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" required />
              </label>
              <label>
                이름
                <input value={name} onChange={(event) => setName(event.target.value)} required />
              </label>
              <div className="code-row">
                <label>
                  인증코드
                  <input value={code} onChange={(event) => setCode(event.target.value)} required />
                </label>
                <button type="button" className="icon-button" onClick={sendCode} disabled={loading} title="인증코드 발송">
                  <Mail size={20} aria-hidden />
                </button>
              </div>
              {debugCode && <p className="hint">개발용 코드: {debugCode}</p>}
              <button className="primary-button" disabled={loading}>
                <KeyRound size={18} aria-hidden />
                인증 로그인
              </button>
            </motion.form>
          ) : (
            <motion.form
              key={mode}
              onSubmit={submitPasswordFlow}
              className="auth-form"
              initial={{ opacity: 0, x: 16 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -16 }}
              transition={{ duration: 0.2 }}
            >
              <label>
                이메일
                <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" required />
              </label>
              {mode === 'register' && (
                <label>
                  이름
                  <input value={name} onChange={(event) => setName(event.target.value)} required />
                </label>
              )}
              <label>
                비밀번호
                <input value={password} onChange={(event) => setPassword(event.target.value)} type="password" minLength={8} required />
              </label>
              <button className="primary-button" disabled={loading}>
                <KeyRound size={18} aria-hidden />
                {mode === 'register' ? '가입하고 로그인' : '로그인'}
              </button>
            </motion.form>
          )}
        </AnimatePresence>

        <button className="kakao-button" type="button" onClick={() => setMessage('Kakao Developers 등록 후 OAuth callback 구현 단계로 연결합니다.')}>
          카카오 로그인 준비
        </button>

        {message && <p className="message">{message}</p>}
      </motion.section>

      <motion.aside
        className="session-panel"
        initial={{ opacity: 0, y: 18 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.45, delay: 0.08, ease: 'easeOut' }}
      >
        <h2>세션</h2>
        <dl>
          <dt>토큰</dt>
          <dd>{token ? `${token.slice(0, 32)}...` : '없음'}</dd>
          <dt>사용자</dt>
          <dd>{user ? `${user.name} / ${user.email} / ${user.provider}` : '미확인'}</dd>
        </dl>
        <div className="session-actions">
          <button onClick={loadMe} disabled={!token || loading}>토큰 확인</button>
          <button onClick={logout} disabled={!token}>로그아웃</button>
        </div>
      </motion.aside>
    </main>
  );
}

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
