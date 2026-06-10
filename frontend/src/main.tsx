import React, { FormEvent, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Client, IMessage } from '@stomp/stompjs';
import { AnimatePresence, motion } from 'motion/react';
import {
  AtSign,
  CheckCircle2,
  ChevronRight,
  Hash,
  KeyRound,
  LogOut,
  Mail,
  Paperclip,
  Plus,
  Search,
  Send,
  ShieldCheck,
  Trash2,
  X,
  UserRound
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
  type: 'GROUP' | 'DIRECT';
  createdAt: string;
};

type ChatMessage = {
  id: string;
  roomId: string;
  roomName: string;
  senderEmail: string;
  senderName: string;
  content: string;
  attachmentUrl: string | null;
  attachmentType: string | null;
  attachmentName: string | null;
  attachmentSize: number | null;
  deletedForEveryone: boolean;
  createdAt: string;
};

type Contact = {
  id: number;
  email: string;
  name: string;
  provider: string;
};

type AttachmentResponse = {
  url: string;
  type: string;
  name: string;
  size: number;
};

const API_ROOT = '/api';
const SAMPLE_USERS = [
  { email: 'user@example.com', name: '건우' },
  { email: 'minji@example.com', name: '민지' },
  { email: 'junho@example.com', name: '준호' },
  { email: 'seoyeon@example.com', name: '서연' },
  { email: 'hyejin@example.com', name: '혜진' }
];

function App() {
  const [mode, setMode] = useState<Mode>('login');
  const [email, setEmail] = useState(SAMPLE_USERS[0].email);
  const [name, setName] = useState(SAMPLE_USERS[0].name);
  const [password, setPassword] = useState('password123');
  const [code, setCode] = useState('');
  const [debugCode, setDebugCode] = useState('');
  const [status, setStatus] = useState('');
  const [token, setToken] = useState(() => localStorage.getItem('accessToken') ?? '');
  const [user, setUser] = useState<User | null>(null);
  const [rooms, setRooms] = useState<ChatRoom[]>([]);
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [selectedRoomId, setSelectedRoomId] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [roomName, setRoomName] = useState('프로젝트 채팅방');
  const [roomDescription, setRoomDescription] = useState('Kafka 이벤트로 메시지를 주고받는 공간');
  const [draft, setDraft] = useState('');
  const [roomQuery, setRoomQuery] = useState('');
  const [contactQuery, setContactQuery] = useState('');
  const [messageQuery, setMessageQuery] = useState('');
  const [searchResults, setSearchResults] = useState<ChatMessage[]>([]);
  const [attachment, setAttachment] = useState<File | null>(null);
  const [attachmentPreview, setAttachmentPreview] = useState('');
  const [loading, setLoading] = useState(false);
  const [connected, setConnected] = useState(false);

  const selectedRoom = rooms.find((room) => room.id === selectedRoomId) ?? null;
  const directRooms = rooms.filter((room) => room.type === 'DIRECT');
  const groupRooms = rooms.filter((room) => room.type === 'GROUP');

  const title = useMemo(() => {
    if (mode === 'register') return '계정 만들기';
    if (mode === 'email') return '이메일 인증 로그인';
    return '다시 오신 걸 환영해요';
  }, [mode]);

  async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const isFormData = init?.body instanceof FormData;
    const response = await fetch(`${API_ROOT}${path}`, {
      ...init,
      headers: {
        ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...init?.headers
      }
    });
    const data = response.status === 204 ? null : await response.json();
    if (!response.ok) {
      throw new Error(data?.message ?? '요청을 처리하지 못했습니다.');
    }
    return data as T;
  }

  function chooseSample(sample: { email: string; name: string }) {
    setEmail(sample.email);
    setName(sample.name);
    setPassword('password123');
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

  async function loadContacts(query = contactQuery) {
    if (!token) return;
    const suffix = query.trim() ? `?query=${encodeURIComponent(query.trim())}` : '';
    const data = await request<Contact[]>(`/chat/contacts${suffix}`);
    setContacts(data);
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

  async function deleteRoom() {
    if (!selectedRoomId || !selectedRoom) return;
    setLoading(true);
    try {
      await request<void>(`/chat/rooms/${selectedRoomId}`, { method: 'DELETE' });
      setRooms((current) => current.filter((room) => room.id !== selectedRoomId));
      setSelectedRoomId('');
      setMessages([]);
      setStatus(`${selectedRoom.name} 채팅방을 내 목록에서 삭제했습니다.`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : '채팅방 삭제에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  }

  async function openDirectRoom(contact: Contact) {
    setLoading(true);
    try {
      const room = await request<ChatRoom>('/chat/direct-rooms', {
        method: 'POST',
        body: JSON.stringify({ partnerEmail: contact.email })
      });
      setRooms((current) => [room, ...current.filter((item) => item.id !== room.id)]);
      setSelectedRoomId(room.id);
      setStatus(`${contact.name}님과 1:1 대화를 시작합니다.`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : '1:1 채팅방을 열지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }

  async function sendMessage(event: FormEvent) {
    event.preventDefault();
    if (!selectedRoomId || (!draft.trim() && !attachment)) return;
    const content = draft.trim();
    setDraft('');
    try {
      let uploaded: AttachmentResponse | null = null;
      if (attachment) {
        const formData = new FormData();
        formData.append('file', attachment);
        uploaded = await request<AttachmentResponse>('/chat/attachments', {
          method: 'POST',
          body: formData
        });
      }
      await request(`/chat/rooms/${selectedRoomId}/messages`, {
        method: 'POST',
        body: JSON.stringify({ content, attachment: uploaded })
      });
      clearAttachment();
      setTimeout(() => loadMessages(selectedRoomId), 350);
    } catch (error) {
      setDraft(content);
      setStatus(error instanceof Error ? error.message : '메시지 전송에 실패했습니다.');
    }
  }

  async function hideMessageForMe(message: ChatMessage) {
    try {
      await request<ChatMessage>(`/chat/rooms/${message.roomId}/messages/${message.id}/me`, { method: 'DELETE' });
      setMessages((current) => current.filter((item) => item.id !== message.id));
      setSearchResults((current) => current.filter((item) => item.id !== message.id));
    } catch (error) {
      setStatus(error instanceof Error ? error.message : '메시지를 삭제하지 못했습니다.');
    }
  }

  async function clearRoomMessagesForMe() {
    if (!selectedRoomId) return;
    try {
      await request<void>(`/chat/rooms/${selectedRoomId}/messages/me`, { method: 'DELETE' });
      setMessages([]);
      setSearchResults((current) => current.filter((message) => message.roomId !== selectedRoomId));
      setStatus('이 채팅방의 대화내용을 내 화면에서 삭제했습니다.');
    } catch (error) {
      setStatus(error instanceof Error ? error.message : '대화내용을 삭제하지 못했습니다.');
    }
  }

  async function deleteMessageForEveryone(message: ChatMessage) {
    try {
      const updated = await request<ChatMessage>(`/chat/rooms/${message.roomId}/messages/${message.id}/everyone`, { method: 'DELETE' });
      setMessages((current) => current.map((item) => item.id === updated.id ? updated : item));
      setSearchResults((current) => current.filter((item) => item.id !== updated.id));
    } catch (error) {
      setStatus(error instanceof Error ? error.message : '모두에게 삭제하지 못했습니다.');
    }
  }

  function selectAttachment(file: File | null) {
    clearAttachment();
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      setStatus('이미지와 GIF 파일만 첨부할 수 있습니다.');
      return;
    }
    setAttachment(file);
    setAttachmentPreview(URL.createObjectURL(file));
  }

  function clearAttachment() {
    if (attachmentPreview) {
      URL.revokeObjectURL(attachmentPreview);
    }
    setAttachment(null);
    setAttachmentPreview('');
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
    clearAttachment();
    setStatus('로그아웃되었습니다.');
  }

  useEffect(() => {
    loadMe();
  }, [token]);

  useEffect(() => {
    if (user) {
      loadRooms('');
      loadContacts('');
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
          setMessages((current) => current.some((item) => item.id === message.id)
            ? current.map((item) => item.id === message.id ? message : item)
            : [...current, message]);
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
          transition={{ duration: 0.35, ease: 'easeOut' }}
        >
          <div className="auth-copy">
            <div className="brand-mark"><ShieldCheck size={25} aria-hidden /></div>
            <p className="eyebrow">Kafka Talk</p>
            <h1>{title}</h1>
            <p className="muted">JWT, 이메일 인증, Kafka 메시징을 한 화면에서 확인할 수 있어요.</p>
          </div>

          <div className="sample-grid" aria-label="테스트 계정">
            {SAMPLE_USERS.map((sample) => (
              <button key={sample.email} type="button" onClick={() => chooseSample(sample)}>
                <UserRound size={16} aria-hidden />
                <span>{sample.name}</span>
              </button>
            ))}
          </div>

          <div className="mode-tabs" role="tablist" aria-label="로그인 방식">
            <button className={mode === 'login' ? 'active' : ''} onClick={() => setMode('login')}>일반</button>
            <button className={mode === 'register' ? 'active' : ''} onClick={() => setMode('register')}>가입</button>
            <button className={mode === 'email' ? 'active' : ''} onClick={() => setMode('email')}>이메일</button>
          </div>

          <AnimatePresence mode="wait">
            {mode === 'email' ? (
              <motion.form key="email" onSubmit={submitEmailFlow} className="auth-form" initial={{ opacity: 0, x: 12 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -12 }} transition={{ duration: 0.18 }}>
                <label>이메일<input value={email} onChange={(event) => setEmail(event.target.value)} type="email" required /></label>
                <label>이름<input value={name} onChange={(event) => setName(event.target.value)} required /></label>
                <div className="code-row">
                  <label>인증코드<input value={code} onChange={(event) => setCode(event.target.value)} required /></label>
                  <button type="button" className="icon-button" onClick={sendCode} disabled={loading} title="인증코드 발송"><Mail size={19} aria-hidden /></button>
                </div>
                {debugCode && <p className="hint">개발용 코드: {debugCode}</p>}
                <button className="primary-button" disabled={loading}><KeyRound size={18} aria-hidden />인증 로그인</button>
              </motion.form>
            ) : (
              <motion.form key={mode} onSubmit={submitPasswordFlow} className="auth-form" initial={{ opacity: 0, x: 12 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -12 }} transition={{ duration: 0.18 }}>
                <label>이메일<input value={email} onChange={(event) => setEmail(event.target.value)} type="email" required /></label>
                {mode === 'register' && <label>이름<input value={name} onChange={(event) => setName(event.target.value)} required /></label>}
                <label>비밀번호<input value={password} onChange={(event) => setPassword(event.target.value)} type="password" minLength={8} required /></label>
                <button className="primary-button" disabled={loading}><KeyRound size={18} aria-hidden />{mode === 'register' ? '가입하고 로그인' : '로그인'}</button>
              </motion.form>
            )}
          </AnimatePresence>

          <button className="kakao-button" type="button" onClick={() => setStatus('Kakao Developers 등록 후 OAuth callback 구현 단계로 연결합니다.')}>카카오 로그인 준비</button>
          {status && <p className="notice">{status}</p>}
        </motion.section>
      </main>
    );
  }

  return (
    <main className="chat-shell">
      <aside className="sidebar">
        <div className="profile-card">
          <div className="avatar">{user.name.slice(0, 1)}</div>
          <div>
            <h1>Kafka Talk</h1>
            <p>{user.name} · {user.provider}</p>
          </div>
        </div>

        <form className="search-row" onSubmit={(event) => { event.preventDefault(); loadContacts(contactQuery); }}>
          <Search size={17} aria-hidden />
          <input value={contactQuery} onChange={(event) => setContactQuery(event.target.value)} placeholder="친구 검색" />
        </form>

        <section className="panel-section">
          <div className="section-title">
            <span>친구</span>
            <small>{contacts.length}</small>
          </div>
          <div className="contact-list">
            {contacts.map((contact) => (
              <button key={contact.email} onClick={() => openDirectRoom(contact)} disabled={loading}>
                <div className="mini-avatar">{contact.name.slice(0, 1)}</div>
                <span>
                  <strong>{contact.name}</strong>
                  <small>{contact.email}</small>
                </span>
                <ChevronRight size={16} aria-hidden />
              </button>
            ))}
          </div>
        </section>

        <section className="panel-section">
          <div className="section-title">
            <span>1:1 대화</span>
            <small>{directRooms.length}</small>
          </div>
          <RoomList rooms={directRooms} selectedRoomId={selectedRoomId} onSelect={setSelectedRoomId} />
        </section>

        <button className="logout-button" onClick={logout}><LogOut size={17} aria-hidden />로그아웃</button>
      </aside>

      <section className="conversation">
        <header className="conversation-header">
          <div>
            <p className="eyebrow">{selectedRoom?.type === 'DIRECT' ? 'Private message' : 'Kafka message stream'}</p>
            <h2>{selectedRoom?.name ?? '대화를 선택하세요'}</h2>
            <p>{selectedRoom?.description ?? '친구를 선택하면 1:1 채팅방이 열립니다.'}</p>
          </div>
          <div className="header-actions">
            <span className={connected ? 'status-pill online' : 'status-pill'}>
              <CheckCircle2 size={15} aria-hidden />
              {connected ? '실시간 연결' : '연결 대기'}
            </span>
            <button className="soft-action-button" onClick={clearRoomMessagesForMe} disabled={!selectedRoomId || messages.length === 0} type="button">대화 비우기</button>
            <button className="ghost-icon-button" onClick={deleteRoom} disabled={!selectedRoomId || loading} title="채팅방 삭제"><Trash2 size={17} aria-hidden /></button>
          </div>
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
              {message.deletedForEveryone ? (
                <p className="deleted-message">삭제된 메시지입니다.</p>
              ) : (
                <>
                  {message.attachmentUrl && (
                    <a className="message-media" href={message.attachmentUrl} target="_blank" rel="noreferrer">
                      <img src={message.attachmentUrl} alt={message.attachmentName ?? '첨부 이미지'} />
                    </a>
                  )}
                  {message.content && <p>{message.content}</p>}
                  <div className="message-actions">
                    <button onClick={() => hideMessageForMe(message)} type="button">나에게 삭제</button>
                    {message.senderEmail === user.email && <button onClick={() => deleteMessageForEveryone(message)} type="button">모두에게 삭제</button>}
                  </div>
                </>
              )}
            </motion.article>
          ))}
        </div>

        <form className="composer" onSubmit={sendMessage}>
          {attachmentPreview && (
            <div className="attachment-preview">
              <img src={attachmentPreview} alt={attachment?.name ?? '첨부 미리보기'} />
              <span>{attachment?.name}</span>
              <button type="button" onClick={clearAttachment} title="첨부 제거"><X size={16} aria-hidden /></button>
            </div>
          )}
          <label className="attach-button" title="이미지 또는 GIF 첨부">
            <Paperclip size={18} aria-hidden />
            <input type="file" accept="image/*,.gif" onChange={(event) => selectAttachment(event.target.files?.[0] ?? null)} disabled={!selectedRoomId} />
          </label>
          <input value={draft} onChange={(event) => setDraft(event.target.value)} placeholder="메시지를 입력하세요" disabled={!selectedRoomId} />
          <button disabled={!selectedRoomId || (!draft.trim() && !attachment)} title="메시지 보내기"><Send size={18} aria-hidden /></button>
        </form>
      </section>

      <aside className="right-panel">
        <section className="create-card">
          <div className="section-title">
            <span>그룹 채팅</span>
            <small>{groupRooms.length}</small>
          </div>
          <form className="room-create" onSubmit={createRoom}>
            <label>방 이름<input value={roomName} onChange={(event) => setRoomName(event.target.value)} required /></label>
            <label>설명<input value={roomDescription} onChange={(event) => setRoomDescription(event.target.value)} /></label>
            <button disabled={loading}><Plus size={17} aria-hidden />방 만들기</button>
          </form>
          <form className="search-row compact" onSubmit={(event) => { event.preventDefault(); loadRooms(roomQuery); }}>
            <Search size={17} aria-hidden />
            <input value={roomQuery} onChange={(event) => setRoomQuery(event.target.value)} placeholder="방 검색" />
          </form>
          <RoomList rooms={groupRooms} selectedRoomId={selectedRoomId} onSelect={setSelectedRoomId} />
        </section>

        <section className="search-panel">
          <div className="section-title">
            <span>메시지 검색</span>
            <small>Elastic</small>
          </div>
          <form className="search-row compact" onSubmit={searchMessages}>
            <Search size={17} aria-hidden />
            <input value={messageQuery} onChange={(event) => setMessageQuery(event.target.value)} placeholder="대화 내용 검색" />
          </form>
          <div className="search-results">
            {searchResults.map((message) => (
              <button key={message.id} onClick={() => setSelectedRoomId(message.roomId)}>
                <span>{message.roomName}</span>
                <strong>{message.deletedForEveryone ? '삭제된 메시지입니다.' : message.content || message.attachmentName || '첨부 메시지'}</strong>
                <small>{message.senderName} · {new Date(message.createdAt).toLocaleString()}</small>
              </button>
            ))}
            {searchResults.length === 0 && <p className="empty-state">색인된 메시지를 검색할 수 있어요.</p>}
          </div>
        </section>

        {status && <p className="notice">{status}</p>}
      </aside>
    </main>
  );
}

function RoomList({
  rooms,
  selectedRoomId,
  onSelect
}: {
  rooms: ChatRoom[];
  selectedRoomId: string;
  onSelect: (roomId: string) => void;
}) {
  if (rooms.length === 0) {
    return <p className="empty-state">아직 대화가 없습니다.</p>;
  }
  return (
    <div className="room-list">
      {rooms.map((room) => (
        <button key={room.id} className={room.id === selectedRoomId ? 'room-item active' : 'room-item'} onClick={() => onSelect(room.id)}>
          {room.type === 'DIRECT' ? <AtSign size={16} aria-hidden /> : <Hash size={16} aria-hidden />}
          <span>
            <strong>{room.name}</strong>
            <small>{room.type === 'DIRECT' ? '개인 메시지' : room.description}</small>
          </span>
        </button>
      ))}
    </div>
  );
}

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
