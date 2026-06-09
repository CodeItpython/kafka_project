import React, { FormEvent, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Client, IMessage } from '@stomp/stompjs';
import { AnimatePresence, motion } from 'motion/react';
import {
  Hash,
  KeyRound,
  LogOut,
  Mail,
  MessageCircle,
  Plus,
  Search,
  Send,
  ShieldCheck,
  Sparkles
} from 'lucide-react';
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

type ChatRoom = {
  id: string;
  name: string;
  description: string | null;
  createdBy: string;
  createdAt: string;
};

type ChatMessage = {
  id: string;
  roomId: string;
  roomName: string;
  senderEmail: string;
  senderName: string;
  content: string;
  createdAt: string;
};

const API_ROOT = '/api';

function App() {
  const [mode, setMode] = useState<Mode>('login');
  const [email, setEmail] = useState('user@example.com');
  const [name, setName] = useState('Gunwoo');
  const [password, setPassword] = useState('password123');
  const [code, setCode] = useState('');
  const [debugCode, setDebugCode] = useState('');
  const [status, setStatus] = useState('');
  const [token, setToken] = useState(() => localStorage.getItem('accessToken') ?? '');
  const [user, setUser] = useState<User | null>(null);
  const [rooms, setRooms] = useState<ChatRoom[]>([]);
  const [selectedRoomId, setSelectedRoomId] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [roomName, setRoomName] = useState('프로젝트 채팅방');
  const [roomDescription, setRoomDescription] = useState('Kafka 이벤트로 메시지를 주고받는 공간');
  const [draft, setDraft] = useState('');
  const [roomQuery, setRoomQuery] = useState('');
  const [messageQuery, setMessageQuery] = useState('');
  const [searchResults, setSearchResults] = useState<ChatMessage[]>([]);
  const [loading, setLoading] = useState(false);
  const [connected, setConnected] = useState(false);

  const selectedRoom = rooms.find((room) => room.id === selectedRoomId) ?? null;

  const title = useMemo(() => {
    if (mode === 'register') return '계정 만들기';
    if (mode === 'email') return '이메일 인증 로그인';
    return '일반 로그인';
  }, [mode]);

  async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const response = await fetch(`${API_ROOT}${path}`, {
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
    setStatus(`${data.user.name}님으로 로그인되었습니다.`);
  }

  async function submitPasswordFlow(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setStatus('');
    try {
      const payload = mode === 'register' ? { email, password, name } : { email, password };
      const data = await request<AuthResponse>(mode === 'register' ? '/auth/register' : '/auth/login', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      saveSession(data);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : '오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  }

  async function sendCode() {
    setLoading(true);
    setStatus('');
    try {
      const data = await request<{ expiresAt: string; debugCode: string }>('/auth/email/code', {
        method: 'POST',
        body: JSON.stringify({ email })
      });
      setDebugCode(data.debugCode);
      setCode(data.debugCode);
      setStatus(`인증코드를 생성했습니다. 만료시각: ${new Date(data.expiresAt).toLocaleTimeString()}`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : '오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  }

  async function submitEmailFlow(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setStatus('');
    try {
      const data = await request<AuthResponse>('/auth/email/login', {
        method: 'POST',
        body: JSON.stringify({ email, code, name })
      });
      saveSession(data);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : '오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  }

  async function loadMe() {
    if (!token) return;
    try {
      const data = await request<User>('/auth/me');
      setUser(data);
    } catch {
      localStorage.removeItem('accessToken');
      setToken('');
      setUser(null);
    }
  }

  async function loadRooms(query = roomQuery) {
    if (!token) return;
    const suffix = query.trim() ? `?query=${encodeURIComponent(query.trim())}` : '';
    const data = await request<ChatRoom[]>(`/chat/rooms${suffix}`);
    setRooms(data);
    if (!selectedRoomId && data.length > 0) {
      setSelectedRoomId(data[0].id);
    }
  }

  async function loadMessages(roomId: string) {
    if (!roomId) return;
    const data = await request<ChatMessage[]>(`/chat/rooms/${roomId}/messages`);
    setMessages(data);
  }

  async function createRoom(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    try {
      const room = await request<ChatRoom>('/chat/rooms', {
        method: 'POST',
        body: JSON.stringify({ name: roomName, description: roomDescription })
      });
      setRooms((current) => [room, ...current.filter((item) => item.id !== room.id)]);
      setSelectedRoomId(room.id);
      setRoomName('');
      setRoomDescription('');
      setStatus(`${room.name} 채팅방을 만들었습니다.`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : '채팅방 생성에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  }

  async function sendMessage(event: FormEvent) {
    event.preventDefault();
    if (!selectedRoomId || !draft.trim()) return;
    const content = draft.trim();
    setDraft('');
    try {
      await request(`/chat/rooms/${selectedRoomId}/messages`, {
        method: 'POST',
        body: JSON.stringify({ content })
      });
      setTimeout(() => loadMessages(selectedRoomId), 400);
    } catch (error) {
      setDraft(content);
      setStatus(error instanceof Error ? error.message : '메시지 전송에 실패했습니다.');
    }
  }

  async function searchMessages(event: FormEvent) {
    event.preventDefault();
    if (!messageQuery.trim()) {
      setSearchResults([]);
      return;
    }
    const data = await request<ChatMessage[]>(`/chat/messages/search?query=${encodeURIComponent(messageQuery.trim())}`);
    setSearchResults(data);
  }

  function logout() {
    localStorage.removeItem('accessToken');
    setToken('');
    setUser(null);
    setRooms([]);
    setMessages([]);
    setSearchResults([]);
    setSelectedRoomId('');
    setStatus('로그아웃되었습니다.');
  }

  useEffect(() => {
    loadMe();
  }, [token]);

  useEffect(() => {
    if (user) {
      loadRooms('');
    }
  }, [user?.email]);

  useEffect(() => {
    if (selectedRoomId) {
      loadMessages(selectedRoomId);
    }
  }, [selectedRoomId]);

  useEffect(() => {
    if (!selectedRoomId) return;

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const client = new Client({
      brokerURL: `${protocol}//${window.location.host}/ws/websocket`,
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/topic/rooms/${selectedRoomId}`, (frame: IMessage) => {
          const message = JSON.parse(frame.body) as ChatMessage;
          setMessages((current) => current.some((item) => item.id === message.id) ? current : [...current, message]);
        });
      },
      onDisconnect: () => setConnected(false),
      onWebSocketClose: () => setConnected(false)
    });

    client.activate();
    return () => {
      setConnected(false);
      client.deactivate();
    };
  }, [selectedRoomId]);

  if (!user) {
    return (
      <main className="auth-shell">
        <motion.section
          className="auth-panel"
          initial={{ opacity: 0, y: 18 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.45, ease: 'easeOut' }}
        >
          <div className="brand-row">
            <ShieldCheck size={32} aria-hidden />
            <div>
              <h1>Kafka Talk</h1>
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
              <motion.form key="email" onSubmit={submitEmailFlow} className="auth-form" initial={{ opacity: 0, x: 16 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -16 }} transition={{ duration: 0.2 }}>
                <label>이메일<input value={email} onChange={(event) => setEmail(event.target.value)} type="email" required /></label>
                <label>이름<input value={name} onChange={(event) => setName(event.target.value)} required /></label>
                <div className="code-row">
                  <label>인증코드<input value={code} onChange={(event) => setCode(event.target.value)} required /></label>
                  <button type="button" className="icon-button" onClick={sendCode} disabled={loading} title="인증코드 발송"><Mail size={20} aria-hidden /></button>
                </div>
                {debugCode && <p className="hint">개발용 코드: {debugCode}</p>}
                <button className="primary-button" disabled={loading}><KeyRound size={18} aria-hidden />인증 로그인</button>
              </motion.form>
            ) : (
              <motion.form key={mode} onSubmit={submitPasswordFlow} className="auth-form" initial={{ opacity: 0, x: 16 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -16 }} transition={{ duration: 0.2 }}>
                <label>이메일<input value={email} onChange={(event) => setEmail(event.target.value)} type="email" required /></label>
                {mode === 'register' && <label>이름<input value={name} onChange={(event) => setName(event.target.value)} required /></label>}
                <label>비밀번호<input value={password} onChange={(event) => setPassword(event.target.value)} type="password" minLength={8} required /></label>
                <button className="primary-button" disabled={loading}><KeyRound size={18} aria-hidden />{mode === 'register' ? '가입하고 로그인' : '로그인'}</button>
              </motion.form>
            )}
          </AnimatePresence>

          <button className="kakao-button" type="button" onClick={() => setStatus('Kakao Developers 등록 후 OAuth callback 구현 단계로 연결합니다.')}>카카오 로그인 준비</button>
          {status && <p className="message">{status}</p>}
        </motion.section>
      </main>
    );
  }

  return (
    <main className="chat-shell">
      <aside className="sidebar">
        <div className="workspace-title">
          <div className="app-mark"><MessageCircle size={22} aria-hidden /></div>
          <div>
            <h1>Kafka Talk</h1>
            <p>{user.name} / {user.provider}</p>
          </div>
        </div>

        <form className="search-row" onSubmit={(event) => { event.preventDefault(); loadRooms(roomQuery); }}>
          <Search size={17} aria-hidden />
          <input value={roomQuery} onChange={(event) => setRoomQuery(event.target.value)} placeholder="채팅방 검색" />
        </form>

        <form className="room-create" onSubmit={createRoom}>
          <label>방 이름<input value={roomName} onChange={(event) => setRoomName(event.target.value)} required /></label>
          <label>설명<input value={roomDescription} onChange={(event) => setRoomDescription(event.target.value)} /></label>
          <button disabled={loading}><Plus size={17} aria-hidden />방 만들기</button>
        </form>

        <div className="room-list">
          {rooms.map((room) => (
            <button key={room.id} className={room.id === selectedRoomId ? 'room-item active' : 'room-item'} onClick={() => setSelectedRoomId(room.id)}>
              <Hash size={16} aria-hidden />
              <span>{room.name}</span>
            </button>
          ))}
        </div>

        <button className="logout-button" onClick={logout}><LogOut size={17} aria-hidden />로그아웃</button>
      </aside>

      <section className="conversation">
        <header className="conversation-header">
          <div>
            <p className="eyebrow"><Sparkles size={15} aria-hidden />Kafka message stream</p>
            <h2>{selectedRoom?.name ?? '채팅방을 선택하세요'}</h2>
            <p>{selectedRoom?.description ?? '왼쪽에서 채팅방을 만들거나 선택하면 대화가 시작됩니다.'}</p>
          </div>
          <span className={connected ? 'status-pill online' : 'status-pill'}>{connected ? '실시간 연결됨' : '연결 대기'}</span>
        </header>

        <div className="message-list">
          {messages.length === 0 && <p className="empty-state">아직 메시지가 없습니다.</p>}
          {messages.map((message) => (
            <motion.article
              key={message.id}
              className={message.senderEmail === user.email ? 'chat-bubble mine' : 'chat-bubble'}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
            >
              <div className="bubble-meta">
                <strong>{message.senderName}</strong>
                <span>{new Date(message.createdAt).toLocaleTimeString()}</span>
              </div>
              <p>{message.content}</p>
            </motion.article>
          ))}
        </div>

        <form className="composer" onSubmit={sendMessage}>
          <input value={draft} onChange={(event) => setDraft(event.target.value)} placeholder="메시지를 입력하세요" disabled={!selectedRoomId} />
          <button disabled={!selectedRoomId || !draft.trim()}><Send size={18} aria-hidden /></button>
        </form>
      </section>

      <aside className="search-panel">
        <h2>메시지 검색</h2>
        <form className="search-row" onSubmit={searchMessages}>
          <Search size={17} aria-hidden />
          <input value={messageQuery} onChange={(event) => setMessageQuery(event.target.value)} placeholder="대화 내용 검색" />
        </form>
        <div className="search-results">
          {searchResults.map((message) => (
            <button key={message.id} onClick={() => setSelectedRoomId(message.roomId)}>
              <span>{message.roomName}</span>
              <strong>{message.content}</strong>
              <small>{message.senderName} · {new Date(message.createdAt).toLocaleString()}</small>
            </button>
          ))}
          {searchResults.length === 0 && <p className="empty-state">Elasticsearch 검색 결과가 여기에 표시됩니다.</p>}
        </div>
        {status && <p className="message">{status}</p>}
      </aside>
    </main>
  );
}

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
