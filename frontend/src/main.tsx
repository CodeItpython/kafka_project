import React, { FormEvent, UIEvent, useEffect, useMemo, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Client, IMessage } from '@stomp/stompjs';
import { AnimatePresence, motion } from 'motion/react';
import {
  ArrowLeft,
  AtSign,
  Camera,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  Hash,
  KeyRound,
  LogOut,
  Mail,
  Menu,
  MessageCircle,
  Paperclip,
  Plus,
  RefreshCcw,
  Save,
  Search,
  Send,
  ShieldCheck,
  Sparkles,
  Trash2,
  X,
  UserRound
} from 'lucide-react';
import { nativeBridge } from './platform/nativeBridge';
import { safeStorage } from './platform/safeStorage';
import { AppProviders } from './ui/AppProviders';
import { useOverlay } from './ui/OverlayProvider';
import { useScrollLinkedPreview } from './ui/useScrollLinkedPreview';
import { useScrollReveal } from './ui/useScrollReveal';
import './styles.css';

type Mode = 'login' | 'register' | 'email';
type AuthStep = 'landing' | 'form';

type User = {
  id: number;
  email: string;
  name: string;
  provider: string;
  statusMessage: string;
  profileImageUrl: string | null;
};

type AuthResponse = {
  accessToken: string;
  tokenType: string;
  user: User;
};

type ApiErrorResponse = {
  code?: string;
  message?: string;
  details?: string[];
};

class ApiClientError extends Error {
  code?: string;
  details: string[];

  constructor(payload: ApiErrorResponse, fallback: string) {
    super(payload.message || fallback);
    this.name = 'ApiClientError';
    this.code = payload.code;
    this.details = payload.details ?? [];
  }
}

type ChatRoom = {
  id: string;
  name: string;
  description: string | null;
  createdBy: string;
  type: 'GROUP' | 'DIRECT';
  createdAt: string;
  unreadCount: number;
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
  statusMessage: string;
  profileImageUrl: string | null;
  online: boolean;
};

type ProfileHistory = {
  id: number;
  name: string;
  statusMessage: string;
  profileImageUrl: string | null;
  eventType: string;
  createdAt: string;
};

type UserProfile = {
  id: number;
  email: string;
  name: string;
  provider: string;
  statusMessage: string;
  profileImageUrl: string | null;
  createdAt: string;
  updatedAt: string;
  history: ProfileHistory[];
};

type AttachmentResponse = {
  url: string;
  type: string;
  name: string;
  size: number;
};

type SearchSuggestion = {
  text: string;
  type: 'ROOM' | 'MESSAGE';
  roomId: string | null;
  roomName: string | null;
};

type RoomPresence = {
  onlineUsers: string[];
  typingUsers: string[];
};

type ConversationSummary = {
  summary: string;
  model: string;
  generatedAt: string;
  messageCount: number;
};

const API_ROOT = '/api';
const SAMPLE_USERS = [
  { email: 'user@example.com', name: '건우' },
  { email: 'minji@example.com', name: '민지' },
  { email: 'junho@example.com', name: '준호' },
  { email: 'seoyeon@example.com', name: '서연' },
  { email: 'hyejin@example.com', name: '혜진' }
];

const LANDING_CARDS = [
  {
    title: '실시간 채팅',
    description: 'Kafka 이벤트로 새 메시지가 바로 도착해요.',
    tone: 'blue'
  },
  {
    title: '프로필 히스토리',
    description: '이름, 상태메시지, 이미지 변경 흐름을 추적해요.',
    tone: 'cream'
  },
  {
    title: '메시지 검색',
    description: 'Elastic 기반으로 대화 내용을 빠르게 찾아요.',
    tone: 'dark'
  },
  {
    title: '웹뷰 감성 UI',
    description: '스크롤과 프리뷰가 반응하는 앱 같은 화면이에요.',
    tone: 'green'
  }
];

function App() {
  const overlay = useOverlay();
  const [authStep, setAuthStep] = useState<AuthStep>('landing');
  const [mode, setMode] = useState<Mode>('login');
  const [email, setEmail] = useState(SAMPLE_USERS[0].email);
  const [name, setName] = useState(SAMPLE_USERS[0].name);
  const [password, setPassword] = useState('password123');
  const [code, setCode] = useState('');
  const [debugCode, setDebugCode] = useState('');
  const [status, setStatus] = useState('');
  const [token, setToken] = useState(() => safeStorage.getString('accessToken') ?? '');
  const [user, setUser] = useState<User | null>(null);
  const [myProfile, setMyProfile] = useState<UserProfile | null>(null);
  const [selectedProfile, setSelectedProfile] = useState<UserProfile | null>(null);
  const [profileName, setProfileName] = useState('');
  const [profileStatus, setProfileStatus] = useState('');
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
  const [roomSuggestions, setRoomSuggestions] = useState<SearchSuggestion[]>([]);
  const [messageSuggestions, setMessageSuggestions] = useState<SearchSuggestion[]>([]);
  const [presence, setPresence] = useState<RoomPresence>({ onlineUsers: [], typingUsers: [] });
  const [attachment, setAttachment] = useState<File | null>(null);
  const [attachmentPreview, setAttachmentPreview] = useState('');
  const [loading, setLoading] = useState(false);
  const [connected, setConnected] = useState(false);
  const [isMenuOpen, setIsMenuOpen] = useState(true);
  const [conversationSummary, setConversationSummary] = useState<ConversationSummary | null>(null);
  const [summaryLoading, setSummaryLoading] = useState(false);
  const [messagesRefreshing, setMessagesRefreshing] = useState(false);
  const [showRefreshButton, setShowRefreshButton] = useState(false);
  const [activePreviewRoomId, setActivePreviewRoomId] = useState('');
  const [previewCursor, setPreviewCursor] = useState({ visible: false, active: false, x: 0, y: 0 });
  const chatShellRef = useRef<HTMLElement | null>(null);
  const sidebarScrollRef = useRef<HTMLDivElement | null>(null);
  const roomDirectoryRef = useRef<HTMLElement | null>(null);
  const messageListRef = useRef<HTMLDivElement | null>(null);

  const selectedRoom = rooms.find((room) => room.id === selectedRoomId) ?? null;
  const directRooms = useMemo(() => rooms.filter((room) => room.type === 'DIRECT'), [rooms]);
  const groupRooms = useMemo(() => rooms.filter((room) => room.type === 'GROUP'), [rooms]);
  const previewRooms = useMemo(() => [...groupRooms, ...directRooms], [groupRooms, directRooms]);
  const activePreviewRoom = previewRooms.find((room) => room.id === activePreviewRoomId) ?? previewRooms[0] ?? null;
  const showSidebarScrollHint = useScrollAffordance(sidebarScrollRef, [
    isMenuOpen,
    contacts.length,
    directRooms.length,
    Boolean(selectedProfile),
    myProfile?.history.length ?? 0
  ]);
  const showDirectoryScrollHint = useScrollAffordance(roomDirectoryRef, [
    selectedRoomId,
    groupRooms.length,
    directRooms.length,
    roomSuggestions.length,
    messageSuggestions.length,
    searchResults.length,
    status
  ]);

  const title = useMemo(() => {
    if (mode === 'register') return '계정 만들기';
    if (mode === 'email') return '이메일 인증 로그인';
    return '다시 오신 걸 환영해요';
  }, [mode]);

  function movePreviewCursor(event: React.PointerEvent<HTMLElement>, active: boolean) {
    if (event.pointerType === 'touch') return;
    setPreviewCursor({
      visible: true,
      active,
      x: event.clientX,
      y: event.clientY
    });
  }

  function scrollContainerDown(ref: React.RefObject<HTMLElement | null>) {
    ref.current?.scrollBy({
      top: Math.max(260, ref.current.clientHeight * 0.72),
      behavior: 'smooth'
    });
  }

  useScrollReveal(chatShellRef, [
    Boolean(user),
    isMenuOpen,
    selectedRoomId,
    contacts.length,
    directRooms.length,
    groupRooms.length,
    messages.length,
    searchResults.length,
    Boolean(conversationSummary)
  ]);

  useScrollLinkedPreview(
    roomDirectoryRef,
    [previewRooms.map((room) => room.id).join('|')],
    setActivePreviewRoomId
  );

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
      throw new ApiClientError(data ?? {}, '요청을 처리하지 못했습니다.');
    }
    return data as T;
  }

  function readableError(error: unknown, fallback = '잠시 후 다시 시도해주세요.') {
    if (error instanceof ApiClientError) {
      if (error.code === 'VALIDATION_FAILED') {
        return error.details[0] ?? '입력한 정보를 다시 확인해주세요.';
      }
      if (error.message.includes('이메일 또는 비밀번호')) {
        return '이메일 또는 비밀번호를 다시 확인해주세요.';
      }
      if (error.message.includes('인증코드')) {
        return error.message;
      }
      return error.message || fallback;
    }
    if (error instanceof Error) {
      return error.message;
    }
    return fallback;
  }

  function chooseSample(sample: { email: string; name: string }) {
    setEmail(sample.email);
    setName(sample.name);
    setPassword('password123');
  }

  function saveSession(data: AuthResponse) {
    safeStorage.setString('accessToken', data.accessToken);
    setToken(data.accessToken);
    setUser(data.user);
    setProfileName(data.user.name);
    setProfileStatus(data.user.statusMessage ?? '');
    nativeBridge.notifyLoginCompleted(data.user.id, data.user.provider);
    nativeBridge.haptic('success');
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
      setStatus(readableError(error, mode === 'register' ? '가입에 실패했습니다.' : '로그인에 실패했습니다.'));
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
      setStatus(readableError(error, '인증코드를 만들지 못했습니다.'));
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
      setStatus(readableError(error, '이메일 로그인에 실패했습니다.'));
    } finally {
      setLoading(false);
    }
  }

  async function loadMe() {
    if (!token) return;
    try {
      const data = await request<User>('/auth/me');
      setUser(data);
      setProfileName(data.name);
      setProfileStatus(data.statusMessage ?? '');
    } catch {
      safeStorage.remove('accessToken');
      setToken('');
      setUser(null);
    }
  }

  async function loadMyProfile() {
    if (!token) return;
    const data = await request<UserProfile>('/users/me/profile');
    setMyProfile(data);
    setProfileName(data.name);
    setProfileStatus(data.statusMessage ?? '');
  }

  async function saveProfile(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    try {
      const data = await request<UserProfile>('/users/me/profile', {
        method: 'PATCH',
        body: JSON.stringify({ name: profileName, statusMessage: profileStatus })
      });
      setMyProfile(data);
      setUser((current) => current ? {
        ...current,
        name: data.name,
        statusMessage: data.statusMessage,
        profileImageUrl: data.profileImageUrl
      } : current);
      setStatus('프로필을 저장했습니다.');
      await loadContacts(contactQuery);
    } catch (error) {
      setStatus(readableError(error, '프로필을 저장하지 못했습니다.'));
    } finally {
      setLoading(false);
    }
  }

  async function uploadProfileImage(file: File | null) {
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      setStatus('프로필 이미지는 이미지 파일만 등록할 수 있습니다.');
      return;
    }
    setLoading(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const data = await request<UserProfile>('/users/me/profile-image', {
        method: 'POST',
        body: formData
      });
      setMyProfile(data);
      setUser((current) => current ? {
        ...current,
        name: data.name,
        statusMessage: data.statusMessage,
        profileImageUrl: data.profileImageUrl
      } : current);
      setStatus('프로필 이미지를 변경했습니다.');
      await loadContacts(contactQuery);
    } catch (error) {
      setStatus(readableError(error, '프로필 이미지를 저장하지 못했습니다.'));
    } finally {
      setLoading(false);
    }
  }

  async function loadRooms(query = roomQuery) {
    if (!token) return;
    const suffix = query.trim() ? `?query=${encodeURIComponent(query.trim())}` : '';
    const data = await request<ChatRoom[]>(`/chat/rooms${suffix}`);
    setRooms(data);
  }

  function openRoom(roomId: string) {
    setSelectedRoomId(roomId);
    setConversationSummary(null);
    setShowRefreshButton(false);
    setRooms((current) => current.map((room) => room.id === roomId ? { ...room, unreadCount: 0 } : room));
    setIsMenuOpen(false);
    nativeBridge.notifyScreenOpened(`chat-room:${roomId}`);
    nativeBridge.haptic('light');
    requestAnimationFrame(() => window.scrollTo({ top: 0, left: 0, behavior: 'instant' }));
  }

  async function loadContacts(query = contactQuery) {
    if (!token) return;
    const suffix = query.trim() ? `?query=${encodeURIComponent(query.trim())}` : '';
    const data = await request<Contact[]>(`/chat/contacts${suffix}`);
    setContacts(data);
  }

  async function loadSuggestions(scope: 'rooms' | 'messages', query: string) {
    if (!token || query.trim().length < 1) return [];
    return request<SearchSuggestion[]>(`/chat/suggestions?scope=${scope}&query=${encodeURIComponent(query.trim())}`);
  }

  async function loadMessages(roomId: string) {
    if (!roomId) return;
    const data = await request<ChatMessage[]>(`/chat/rooms/${roomId}/messages`);
    setMessages(data);
    setRooms((current) => current.map((room) => room.id === roomId ? { ...room, unreadCount: 0 } : room));
  }

  async function refreshMessages() {
    if (!selectedRoomId || messagesRefreshing) return;
    setMessagesRefreshing(true);
    try {
      await loadMessages(selectedRoomId);
      setStatus('대화창을 새로고침했습니다.');
    } catch (error) {
      setStatus(readableError(error, '대화창을 새로고침하지 못했습니다.'));
    } finally {
      setMessagesRefreshing(false);
    }
  }

  function handleMessageScroll(event: UIEvent<HTMLDivElement>) {
    setShowRefreshButton(event.currentTarget.scrollTop < 28 && messages.length > 0);
  }

  async function summarizeConversation() {
    if (!selectedRoomId || summaryLoading) return;
    setSummaryLoading(true);
    try {
      const data = await request<ConversationSummary>(`/chat/rooms/${selectedRoomId}/summary`, { method: 'POST' });
      setConversationSummary(data);
      setStatus('대화 내용을 요약했습니다.');
    } catch (error) {
      setStatus(readableError(error, '대화 요약을 생성하지 못했습니다.'));
    } finally {
      setSummaryLoading(false);
    }
  }

  async function heartbeat() {
    if (!token) return;
    await request<void>('/chat/presence/heartbeat', { method: 'POST' });
  }

  async function loadPresence(roomId: string) {
    if (!roomId) return;
    const data = await request<RoomPresence>(`/chat/rooms/${roomId}/presence`);
    setPresence(data);
  }

  function pushTyping(typing: boolean) {
    if (!selectedRoomId || !token) return;
    request<void>(`/chat/rooms/${selectedRoomId}/typing`, {
      method: 'POST',
      body: JSON.stringify({ typing })
    }).catch(() => undefined);
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
      openRoom(room.id);
      setRoomName('');
      setRoomDescription('');
      setStatus(`${room.name} 채팅방을 만들었습니다.`);
    } catch (error) {
      setStatus(readableError(error, '채팅방 생성에 실패했습니다.'));
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
      setStatus(readableError(error, '채팅방 삭제에 실패했습니다.'));
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
      openRoom(room.id);
      setStatus(`${contact.name}님과 1:1 대화를 시작합니다.`);
    } catch (error) {
      setStatus(readableError(error, '1:1 채팅방을 열지 못했습니다.'));
    } finally {
      setLoading(false);
    }
  }

  async function openContactProfile(contact: Contact) {
    setLoading(true);
    try {
      const data = await request<UserProfile>(`/users/${contact.id}/profile`);
      setSelectedProfile(data);
    } catch (error) {
      setStatus(readableError(error, '프로필을 불러오지 못했습니다.'));
    } finally {
      setLoading(false);
    }
  }

  async function openDirectFromProfile(profile: UserProfile) {
    await openDirectRoom({
      id: profile.id,
      email: profile.email,
      name: profile.name,
      provider: profile.provider,
      statusMessage: profile.statusMessage,
      profileImageUrl: profile.profileImageUrl,
      online: false
    });
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
      setStatus(readableError(error, '메시지 전송에 실패했습니다.'));
    }
  }

  async function hideMessageForMe(message: ChatMessage) {
    try {
      await request<ChatMessage>(`/chat/rooms/${message.roomId}/messages/${message.id}/me`, { method: 'DELETE' });
      setMessages((current) => current.filter((item) => item.id !== message.id));
      setSearchResults((current) => current.filter((item) => item.id !== message.id));
    } catch (error) {
      setStatus(readableError(error, '메시지를 삭제하지 못했습니다.'));
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
      setStatus(readableError(error, '대화내용을 삭제하지 못했습니다.'));
    }
  }

  async function deleteMessageForEveryone(message: ChatMessage) {
    try {
      const updated = await request<ChatMessage>(`/chat/rooms/${message.roomId}/messages/${message.id}/everyone`, { method: 'DELETE' });
      setMessages((current) => current.map((item) => item.id === updated.id ? updated : item));
      setSearchResults((current) => current.filter((item) => item.id !== updated.id));
    } catch (error) {
      setStatus(readableError(error, '모두에게 삭제하지 못했습니다.'));
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

  async function runMessageSearch(query: string) {
    if (!query.trim()) {
      setSearchResults([]);
      return;
    }
    const data = await request<ChatMessage[]>(`/chat/messages/search?query=${encodeURIComponent(query.trim())}`);
    setSearchResults(data);
  }

  async function searchMessages(event: FormEvent) {
    event.preventDefault();
    setMessageSuggestions([]);
    await runMessageSearch(messageQuery);
  }

  function chooseRoomSuggestion(suggestion: SearchSuggestion) {
    setRoomQuery(suggestion.text);
    setRoomSuggestions([]);
    if (suggestion.roomId) {
      openRoom(suggestion.roomId);
      return;
    }
    loadRooms(suggestion.text);
  }

  function chooseMessageSuggestion(suggestion: SearchSuggestion) {
    setMessageQuery(suggestion.text);
    setMessageSuggestions([]);
    runMessageSearch(suggestion.text);
  }

  function logout() {
    safeStorage.remove('accessToken');
    setToken('');
    setUser(null);
    setRooms([]);
    setMessages([]);
    setSearchResults([]);
    setRoomSuggestions([]);
    setMessageSuggestions([]);
    setPresence({ onlineUsers: [], typingUsers: [] });
    setMyProfile(null);
    setSelectedProfile(null);
    setProfileName('');
    setProfileStatus('');
    setSelectedRoomId('');
    setIsMenuOpen(false);
    clearAttachment();
    nativeBridge.notifyLogoutCompleted();
    nativeBridge.haptic('medium');
    setStatus('로그아웃되었습니다.');
  }

  useEffect(() => {
    const hashParams = new URLSearchParams(window.location.hash.replace(/^#/, ''));
    const kakaoToken = hashParams.get('access_token');
    if (kakaoToken) {
      safeStorage.setString('accessToken', kakaoToken);
      setToken(kakaoToken);
      window.history.replaceState(null, '', window.location.pathname);
    }
  }, []);

  useEffect(() => {
    nativeBridge.notifyScreenOpened(user ? 'chat-home' : 'auth');
  }, [user?.id]);

  useEffect(() => {
    if (status) {
      overlay.toast(status);
    }
  }, [overlay, status]);

  useEffect(() => {
    if (!activePreviewRoomId && previewRooms.length > 0) {
      setActivePreviewRoomId(previewRooms[0].id);
      return;
    }

    if (activePreviewRoomId && !previewRooms.some((room) => room.id === activePreviewRoomId)) {
      setActivePreviewRoomId(previewRooms[0]?.id ?? '');
    }
  }, [activePreviewRoomId, previewRooms]);

  useEffect(() => {
    loadMe();
  }, [token]);

  useEffect(() => {
    if (user) {
      loadRooms('');
      loadContacts('');
      loadMyProfile().catch(() => undefined);
      heartbeat().catch(() => undefined);
    }
  }, [user?.email]);

  useEffect(() => {
    if (!token || !user) return;
    const timer = window.setInterval(() => {
      heartbeat()
        .then(() => loadContacts(contactQuery))
        .catch(() => undefined);
    }, 30000);
    return () => window.clearInterval(timer);
  }, [token, user?.email, contactQuery]);

  useEffect(() => {
    if (selectedRoomId) {
      loadMessages(selectedRoomId);
      loadPresence(selectedRoomId);
      setConversationSummary(null);
      setShowRefreshButton(false);
    }
  }, [selectedRoomId]);

  useEffect(() => {
    if (!selectedRoomId || !token) {
      setPresence({ onlineUsers: [], typingUsers: [] });
      return;
    }
    const timer = window.setInterval(() => {
      loadPresence(selectedRoomId).catch(() => undefined);
    }, 2500);
    return () => window.clearInterval(timer);
  }, [selectedRoomId, token]);

  useEffect(() => {
    if (!selectedRoomId || !token) return;
    if (!draft.trim()) {
      pushTyping(false);
      return;
    }
    pushTyping(true);
    const timer = window.setTimeout(() => pushTyping(false), 1800);
    return () => window.clearTimeout(timer);
  }, [draft, selectedRoomId, token]);

  useEffect(() => {
    if (!token || !roomQuery.trim()) {
      setRoomSuggestions([]);
      return;
    }
    const timer = window.setTimeout(() => {
      loadSuggestions('rooms', roomQuery)
        .then(setRoomSuggestions)
        .catch(() => setRoomSuggestions([]));
    }, 220);
    return () => window.clearTimeout(timer);
  }, [roomQuery, token]);

  useEffect(() => {
    if (!token || !messageQuery.trim()) {
      setMessageSuggestions([]);
      return;
    }
    const timer = window.setTimeout(() => {
      loadSuggestions('messages', messageQuery)
        .then(setMessageSuggestions)
        .catch(() => setMessageSuggestions([]));
    }, 220);
    return () => window.clearTimeout(timer);
  }, [messageQuery, token]);

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
    if (authStep === 'landing') {
      return <LandingPage onStart={() => setAuthStep('form')} />;
    }

    return (
      <main className="auth-shell">
        <motion.section
          className="auth-panel"
          initial={{ opacity: 0, y: 18 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.35, ease: 'easeOut' }}
        >
          <button className="auth-back-button" type="button" onClick={() => setAuthStep('landing')}>서비스 소개</button>
          <div className="auth-copy">
            <div className="brand-mark"><ShieldCheck size={25} aria-hidden /></div>
            <p className="eyebrow">Kafka Talk</p>
            <h1>{title}</h1>
            <p className="muted">친구와의 대화를 가볍게 이어가세요.</p>
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
                {debugCode && <p className="hint">인증코드: {debugCode}</p>}
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

          <button className="kakao-button" type="button" onClick={() => { window.location.href = `${API_ROOT}/auth/oauth/kakao/authorize`; }}>카카오로 로그인</button>
          {status && <p className="notice">{status}</p>}
        </motion.section>
      </main>
    );
  }

  return (
    <motion.main
      ref={chatShellRef}
      layout
      transition={{ layout: { type: 'spring', stiffness: 240, damping: 32 } }}
      className={['chat-shell', selectedRoomId && 'chat-open', isMenuOpen && 'menu-open'].filter(Boolean).join(' ')}
    >
      <motion.aside
        className="menu-pane"
        aria-hidden={!isMenuOpen}
        animate={{
          opacity: isMenuOpen ? 1 : 0,
          x: isMenuOpen ? 0 : -34,
          scale: isMenuOpen ? 1 : 0.985
        }}
        transition={{ type: 'spring', stiffness: 260, damping: 34, mass: 0.85 }}
        style={{ pointerEvents: isMenuOpen ? 'auto' : 'none' }}
      >
        <button className="menu-close-button" type="button" onClick={() => setIsMenuOpen(false)} title="친구 닫기">
          <X size={18} aria-hidden />
        </button>
        <section className="sidebar">
          <div className="sidebar-scroll" ref={sidebarScrollRef}>
            <div className="profile-card">
              <ProfileAvatar className="avatar" name={user.name} imageUrl={user.profileImageUrl} />
              <div>
                <h1>Kafka Talk</h1>
                <p>{user.name} · {user.provider}</p>
                {user.statusMessage && <small>{user.statusMessage}</small>}
              </div>
            </div>

            <form className="profile-editor" onSubmit={saveProfile}>
              <label>이름<input value={profileName} onChange={(event) => setProfileName(event.target.value)} maxLength={80} required /></label>
              <label>상태메시지<input value={profileStatus} onChange={(event) => setProfileStatus(event.target.value)} maxLength={500} placeholder="지금의 상태를 남겨보세요" /></label>
              <div className="profile-actions">
                <label className="profile-image-button" title="프로필 이미지 변경">
                  <Camera size={16} aria-hidden />
                  <input type="file" accept="image/*" onChange={(event) => uploadProfileImage(event.target.files?.[0] ?? null)} />
                </label>
                <button type="submit" disabled={loading}><Save size={16} aria-hidden />저장</button>
              </div>
            </form>

            {myProfile && myProfile.history.length > 0 && (
              <section className="panel-section compact-history">
                <SectionHeader title="내 프로필 히스토리" meta={myProfile.history.length} />
                <ProfileHistoryList history={myProfile.history.slice(0, 3)} />
              </section>
            )}

            <form className="search-row" onSubmit={(event) => { event.preventDefault(); loadContacts(contactQuery); }}>
              <Search size={17} aria-hidden />
              <input value={contactQuery} onChange={(event) => setContactQuery(event.target.value)} placeholder="친구 검색" />
            </form>

            <section className="panel-section">
              <SectionHeader title="친구" meta={contacts.length} />
              <div className="contact-list">
                {contacts.map((contact, index) => (
                  <React.Fragment key={contact.email}>
                    <EntityListItem
                      active={selectedProfile?.email === contact.email}
                      disabled={loading}
                      accentIndex={index}
                      leading={(
                        <div className="presence-avatar">
                          <ProfileAvatar className="mini-avatar" name={contact.name} imageUrl={contact.profileImageUrl} />
                          <i className={contact.online ? 'presence-dot online' : 'presence-dot'} aria-hidden />
                        </div>
                      )}
                      title={contact.name}
                      description={contact.statusMessage || (contact.online ? '온라인' : contact.email)}
                      trailing={<ChevronRight size={16} aria-hidden />}
                      onPointerMove={(event) => movePreviewCursor(event, true)}
                      onPointerLeave={() => setPreviewCursor((current) => ({ ...current, visible: false, active: false }))}
                      onClick={() => openContactProfile(contact)}
                    />
                    {selectedProfile?.email === contact.email && (
                      <ContactProfileCard
                        profile={selectedProfile}
                        loading={loading}
                        onMessage={() => openDirectFromProfile(selectedProfile)}
                      />
                    )}
                  </React.Fragment>
                ))}
              </div>
            </section>

            <section className="panel-section">
              <SectionHeader title="1:1 대화" meta={directRooms.length} />
              <RoomList rooms={directRooms} selectedRoomId={selectedRoomId} onCursorMove={movePreviewCursor} onCursorLeave={() => setPreviewCursor((current) => ({ ...current, visible: false, active: false }))} onSelect={openRoom} />
            </section>
          </div>

          <ScrollAffordanceButton
            visible={showSidebarScrollHint}
            label="사이드바 더 보기"
            className="sidebar-scroll-affordance"
            onClick={() => scrollContainerDown(sidebarScrollRef)}
          />

          <div className="sidebar-footer">
            <UiButton className="logout-button" variant="muted" onClick={logout}>
              <LogOut size={17} aria-hidden />로그아웃
            </UiButton>
          </div>
        </section>
      </motion.aside>

      <AnimatePresence mode="wait">
        {selectedRoomId ? (
          <motion.section
            key="conversation"
            className="conversation"
            layout
            initial={{ opacity: 0, x: 18 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -18 }}
            transition={{ type: 'spring', stiffness: 230, damping: 28 }}
          >
            <header className="conversation-header">
              <div className="header-navigation">
                <button className="menu-toggle-button" type="button" onClick={() => setIsMenuOpen((current) => !current)} title={isMenuOpen ? '친구 닫기' : '친구 열기'}>
                  {isMenuOpen ? <X size={18} aria-hidden /> : <Menu size={18} aria-hidden />}
                </button>
                <button className="back-button" type="button" onClick={() => setSelectedRoomId('')} title="채팅방 목록으로 돌아가기">
                  <ArrowLeft size={19} aria-hidden />
                  <span>채팅방 목록</span>
                </button>
              </div>
              <div>
                  <p className="eyebrow">{selectedRoom?.type === 'DIRECT' ? 'Private message' : 'Kafka message stream'}</p>
                  <h2>{selectedRoom?.name ?? '대화를 선택하세요'}</h2>
                  <p>{selectedRoom?.description ?? '친구를 선택하면 1:1 채팅방이 열립니다.'}</p>
                  <div className="presence-line">
                    {presence.onlineUsers.length > 0 && <span>{presence.onlineUsers.length}명 온라인</span>}
                    {presence.typingUsers.length > 0 && <strong>{presence.typingUsers.join(', ')} 입력 중</strong>}
                  </div>
                </div>
              <div className="header-actions">
                <span className={connected ? 'status-pill online' : 'status-pill'}>
                  <CheckCircle2 size={15} aria-hidden />
                  {connected ? '실시간 연결' : '연결 대기'}
                </span>
                <button className="soft-action-button" onClick={summarizeConversation} disabled={!selectedRoomId || messages.length === 0 || summaryLoading} type="button">
                  <Sparkles size={15} aria-hidden />{summaryLoading ? '요약 중' : 'GPT 요약'}
                </button>
                <button className="soft-action-button" onClick={clearRoomMessagesForMe} disabled={!selectedRoomId || messages.length === 0} type="button">대화 비우기</button>
                <button className="ghost-icon-button" onClick={deleteRoom} disabled={!selectedRoomId || loading} title="채팅방 삭제"><Trash2 size={17} aria-hidden /></button>
              </div>
            </header>

            <div className="message-list" ref={messageListRef} onScroll={handleMessageScroll}>
              {(showRefreshButton || messagesRefreshing) && (
                <button className="refresh-messages-button" type="button" onClick={refreshMessages} disabled={messagesRefreshing}>
                  <RefreshCcw size={15} aria-hidden />
                  {messagesRefreshing ? '불러오는 중' : '대화창 새로고침'}
                </button>
              )}
              {conversationSummary && (
                <section className="summary-card">
                  <SectionHeader title="대화 요약" meta={`${conversationSummary.model} · ${conversationSummary.messageCount}개`} />
                  <p>{conversationSummary.summary}</p>
                </section>
              )}
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
              <input value={draft} onChange={(event) => setDraft(event.target.value)} placeholder={presence.typingUsers.length > 0 ? `${presence.typingUsers[0]}님이 입력 중` : '메시지를 입력하세요'} disabled={!selectedRoomId} />
              <button disabled={!selectedRoomId || (!draft.trim() && !attachment)} title="메시지 보내기"><Send size={18} aria-hidden /></button>
            </form>
          </motion.section>
        ) : (
          <motion.section
            ref={roomDirectoryRef}
            key="rooms"
            className="room-directory"
            layout
            initial={{ opacity: 0, x: 18 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -18 }}
            transition={{ type: 'spring', stiffness: 230, damping: 28 }}
          >
            <div className="room-preview-layout">
              <RoomPreviewPanel room={activePreviewRoom} totalCount={previewRooms.length} />

              <div
                className={cn('room-preview-list', previewCursor.visible && 'cursor-active')}
                onPointerMove={(event) => {
                  movePreviewCursor(event, false);
                }}
                onPointerLeave={() => setPreviewCursor((current) => ({ ...current, visible: false, active: false }))}
              >
                <header className="directory-header">
                  <div className="header-navigation">
                    <button className="menu-toggle-button" type="button" onClick={() => setIsMenuOpen((current) => !current)} title={isMenuOpen ? '친구 닫기' : '친구 열기'}>
                      {isMenuOpen ? <X size={18} aria-hidden /> : <Menu size={18} aria-hidden />}
                    </button>
                  </div>
                  <div>
                    <p className="eyebrow">Kafka Talk</p>
                    <h2>채팅방</h2>
                    <p>대화할 방을 선택하거나 새 그룹 채팅방을 만들어보세요.</p>
                  </div>
                </header>

                <section className="directory-section">
                  <SectionHeader title="그룹 채팅" meta={groupRooms.length} />
                  <form className="room-create" onSubmit={createRoom}>
                    <label>방 이름<input value={roomName} onChange={(event) => setRoomName(event.target.value)} required /></label>
                    <label>설명<input value={roomDescription} onChange={(event) => setRoomDescription(event.target.value)} /></label>
                    <button disabled={loading}><Plus size={17} aria-hidden />방 만들기</button>
                  </form>
                  <form className="search-row compact" onSubmit={(event) => { event.preventDefault(); loadRooms(roomQuery); }}>
                    <Search size={17} aria-hidden />
                    <input value={roomQuery} onChange={(event) => setRoomQuery(event.target.value)} placeholder="방 검색" />
                  </form>
                  <SuggestionList suggestions={roomSuggestions} onSelect={chooseRoomSuggestion} />
                  <RoomList rooms={groupRooms} selectedRoomId={selectedRoomId} previewRoomId={activePreviewRoomId} onPreview={setActivePreviewRoomId} onCursorMove={movePreviewCursor} onCursorLeave={(event) => movePreviewCursor(event, false)} onSelect={openRoom} />
                </section>

                <section className="directory-section">
                  <SectionHeader title="최근 1:1 대화" meta={directRooms.length} />
                  <RoomList rooms={directRooms} selectedRoomId={selectedRoomId} previewRoomId={activePreviewRoomId} onPreview={setActivePreviewRoomId} onCursorMove={movePreviewCursor} onCursorLeave={(event) => movePreviewCursor(event, false)} onSelect={openRoom} />
                </section>

                <section className="directory-section">
                  <SectionHeader title="메시지 검색" meta="Elastic" />
                  <form className="search-row compact" onSubmit={searchMessages}>
                    <Search size={17} aria-hidden />
                    <input value={messageQuery} onChange={(event) => setMessageQuery(event.target.value)} placeholder="대화 내용 검색" />
                  </form>
                  <SuggestionList suggestions={messageSuggestions} onSelect={chooseMessageSuggestion} />
                  <div className="search-results">
                    {searchResults.map((message) => (
                      <button key={message.id} onClick={() => openRoom(message.roomId)}>
                        <span>{message.roomName}</span>
                        <strong>{message.deletedForEveryone ? '삭제된 메시지입니다.' : message.content || message.attachmentName || '첨부 메시지'}</strong>
                        <small>{message.senderName} · {new Date(message.createdAt).toLocaleString()}</small>
                      </button>
                    ))}
                    {searchResults.length === 0 && <p className="empty-state">색인된 메시지를 검색할 수 있어요.</p>}
                  </div>
                </section>

                {status && <p className="notice">{status}</p>}
              </div>

            </div>
            <ScrollAffordanceButton
              visible={showDirectoryScrollHint}
              label="채팅방 더 보기"
              className="directory-scroll-affordance"
              onClick={() => scrollContainerDown(roomDirectoryRef)}
            />
          </motion.section>
        )}
      </AnimatePresence>
      <span
        className={cn('preview-cursor', previewCursor.visible && 'visible', previewCursor.active && 'active')}
        style={{
          '--cursor-x': `${previewCursor.x}px`,
          '--cursor-y': `${previewCursor.y}px`
        } as React.CSSProperties}
        aria-hidden
      >
        열기
      </span>
    </motion.main>
  );
}

function SuggestionList({
  suggestions,
  onSelect
}: {
  suggestions: SearchSuggestion[];
  onSelect: (suggestion: SearchSuggestion) => void;
}) {
  if (suggestions.length === 0) {
    return null;
  }
  return (
    <div className="suggestion-list">
      {suggestions.map((suggestion) => (
        <button key={`${suggestion.type}-${suggestion.roomId ?? suggestion.text}-${suggestion.text}`} type="button" onClick={() => onSelect(suggestion)}>
          <Search size={14} aria-hidden />
          <span>{suggestion.text}</span>
          <small>{suggestion.type === 'ROOM' ? '방' : suggestion.roomName ?? '메시지'}</small>
        </button>
      ))}
    </div>
  );
}

function LandingPage({ onStart }: { onStart: () => void }) {
  const [dragX, setDragX] = useState(0);
  const [dragState, setDragState] = useState<{
    active: boolean;
    startX: number;
    originX: number;
    cursorVisible: boolean;
    cursorX: number;
    cursorY: number;
  }>({
    active: false,
    startX: 0,
    originX: 0,
    cursorVisible: false,
    cursorX: 0,
    cursorY: 0
  });

  function clampDrag(value: number) {
    return Math.max(-420, Math.min(80, value));
  }

  function getLocalPointer(event: React.PointerEvent<HTMLElement>) {
    const rect = event.currentTarget.getBoundingClientRect();
    return {
      x: event.clientX - rect.left,
      y: event.clientY - rect.top
    };
  }

  return (
    <main className="landing-shell">
      <section className="landing-hero">
        <nav className="landing-nav" aria-label="서비스 소개">
          <strong>Kafka Talk</strong>
          <button type="button" onClick={onStart}>로그인</button>
        </nav>

        <div className="landing-grid">
          <motion.div
            className="landing-copy"
            initial={{ opacity: 0, y: 18 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.45, ease: 'easeOut' }}
          >
            <p className="eyebrow">WebView-like chat experience</p>
            <h1>톡톡 튀는 대화 경험을 Kafka 위에 올립니다.</h1>
            <p>친구 목록, 실시간 메시지, 검색, 프로필 변경 흐름까지 하나의 가벼운 웹앱에서 자연스럽게 이어집니다.</p>
            <div className="landing-actions">
              <button type="button" onClick={onStart}>시작하기</button>
              <span>카드를 드래그해서 기능을 둘러보세요</span>
            </div>
          </motion.div>

          <motion.aside
            className="landing-preview"
            initial={{ opacity: 0, scale: 0.96, rotate: -1 }}
            animate={{ opacity: 1, scale: 1, rotate: 0 }}
            transition={{ duration: 0.5, ease: 'easeOut', delay: 0.1 }}
          >
            <div className="landing-preview-window">
              <span className="landing-preview-dot" />
              <span className="landing-preview-dot" />
              <span className="landing-preview-dot" />
            </div>
            <div className="landing-message-card mine">민지님이 입력 중...</div>
            <div className="landing-message-card">오늘 저녁에 Kafka Talk에서 얘기해요.</div>
            <div className="landing-message-card accent">새 메시지 3개</div>
          </motion.aside>
        </div>
      </section>

      <section
        className={cn('landing-drag-section', dragState.active && 'dragging')}
        onPointerMove={(event) => {
          const pointer = getLocalPointer(event);
          setDragState((current) => {
            const next = {
              ...current,
              cursorVisible: true,
              cursorX: pointer.x,
              cursorY: pointer.y
            };

            if (!current.active) return next;

            const nextX = clampDrag(current.originX + event.clientX - current.startX);
            setDragX(nextX);
            return next;
          });
        }}
        onPointerLeave={() => setDragState((current) => ({ ...current, active: false, cursorVisible: false }))}
        onPointerUp={() => setDragState((current) => ({ ...current, active: false }))}
      >
        <div className="landing-section-heading">
          <h2>서비스를 손으로 넘겨보세요</h2>
          <p>스크롤과 드래그가 살아있는 첫 화면으로 앱 같은 감각을 만듭니다.</p>
        </div>
        <div
          className="landing-card-track"
          style={{ transform: `translate3d(${dragX}px, 0, 0)` }}
          onPointerDown={(event) => {
            event.currentTarget.setPointerCapture(event.pointerId);
            const section = event.currentTarget.closest<HTMLElement>('.landing-drag-section');
            const rect = section?.getBoundingClientRect();
            setDragState({
              active: true,
              startX: event.clientX,
              originX: dragX,
              cursorVisible: true,
              cursorX: rect ? event.clientX - rect.left : 0,
              cursorY: rect ? event.clientY - rect.top : 0
            });
          }}
        >
          {LANDING_CARDS.map((card, index) => (
            <article className={cn('landing-feature-card', `tone-${card.tone}`)} key={card.title}>
              <span>{String(index + 1).padStart(2, '0')}</span>
              <h3>{card.title}</h3>
              <p>{card.description}</p>
            </article>
          ))}
        </div>
        <span
          className={cn('landing-drag-cursor', dragState.cursorVisible && 'visible', dragState.active && 'grabbing')}
          style={{
            '--cursor-x': `${dragState.cursorX}px`,
            '--cursor-y': `${dragState.cursorY}px`
          } as React.CSSProperties}
          aria-hidden
        >
          {dragState.active ? '끌기' : 'Drag'}
        </span>
      </section>
    </main>
  );
}

function cn(...classes: Array<string | false | null | undefined>) {
  return classes.filter(Boolean).join(' ');
}

function UiButton({
  children,
  className,
  variant = 'primary',
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'muted' | 'ghost';
}) {
  return (
    <button className={cn('ui-button', `ui-button-${variant}`, className)} type="button" {...props}>
      {children}
    </button>
  );
}

function SectionHeader({
  title,
  meta
}: {
  title: string;
  meta: string | number;
}) {
  return (
    <div className="section-title">
      <span>{title}</span>
      <small>{meta}</small>
    </div>
  );
}

function EntityListItem({
  active = false,
  disabled = false,
  accentIndex = 0,
  leading,
  title,
  description,
  trailing,
  onClick,
  onFocus,
  onMouseEnter,
  onPointerMove,
  onPointerLeave,
  previewId
}: {
  active?: boolean;
  disabled?: boolean;
  accentIndex?: number;
  leading: React.ReactNode;
  title: string;
  description: string | null;
  trailing?: React.ReactNode;
  onClick: () => void;
  onFocus?: () => void;
  onMouseEnter?: () => void;
  onPointerMove?: (event: React.PointerEvent<HTMLElement>) => void;
  onPointerLeave?: (event: React.PointerEvent<HTMLElement>) => void;
  previewId?: string;
}) {
  return (
    <button
      className={cn('entity-list-item', `accent-${accentIndex % 5}`, active && 'active')}
      data-preview-id={previewId}
      onClick={onClick}
      onFocus={onFocus}
      onMouseEnter={onMouseEnter}
      onPointerMove={onPointerMove}
      onPointerLeave={onPointerLeave}
      disabled={disabled}
      type="button"
    >
      <span className="entity-list-leading">{leading}</span>
      <span className="entity-list-copy">
        <strong>{title}</strong>
        {description && <small>{description}</small>}
      </span>
      {trailing && <span className="entity-list-trailing">{trailing}</span>}
    </button>
  );
}

function RoomPreviewPanel({
  room,
  totalCount
}: {
  room: ChatRoom | null;
  totalCount: number;
}) {
  const previewLetter = room?.type === 'DIRECT' ? '@' : '#';
  const previewTitle = room?.name ?? '채팅방을 선택하세요';
  const previewDescription = room?.type === 'DIRECT'
    ? '1:1 대화를 바로 이어갈 수 있어요.'
    : room?.description || '그룹 채팅방에서 팀 대화를 시작해보세요.';

  return (
    <aside className="room-preview-panel">
      <div className="room-preview-symbol" aria-hidden>{previewLetter}</div>
      <div className="room-preview-copy">
        <span>{room?.type === 'DIRECT' ? 'Direct' : 'Group'} Preview</span>
        <h3>{previewTitle}</h3>
        <p>{previewDescription}</p>
      </div>
      <dl className="room-preview-meta">
        <div>
          <dt>전체 방</dt>
          <dd>{totalCount}</dd>
        </div>
        <div>
          <dt>읽지 않음</dt>
          <dd>{room?.unreadCount ?? 0}</dd>
        </div>
      </dl>
    </aside>
  );
}

function ContactProfileCard({
  profile,
  loading,
  onMessage
}: {
  profile: UserProfile;
  loading: boolean;
  onMessage: () => void;
}) {
  return (
    <section className="contact-profile-inline">
      <div className="profile-card slim">
        <ProfileAvatar className="avatar" name={profile.name} imageUrl={profile.profileImageUrl} />
        <div>
          <h1>{profile.name}</h1>
          <p>{profile.email}</p>
          {profile.statusMessage && <small>{profile.statusMessage}</small>}
        </div>
      </div>
      <UiButton className="contact-message-button" onClick={onMessage} disabled={loading}>
        <MessageCircle size={17} aria-hidden />메시지
      </UiButton>
      <ProfileHistoryList history={profile.history} />
    </section>
  );
}

function ProfileAvatar({
  className,
  name,
  imageUrl
}: {
  className: string;
  name: string;
  imageUrl: string | null;
}) {
  return (
    <div className={`${className} profile-avatar`}>
      {imageUrl ? <img src={imageUrl} alt={`${name} 프로필`} /> : <span>{name.slice(0, 1)}</span>}
    </div>
  );
}

function ProfileHistoryList({ history }: { history: ProfileHistory[] }) {
  if (history.length === 0) {
    return <p className="empty-state">아직 프로필 변경 기록이 없습니다.</p>;
  }
  return (
    <div className="profile-history-list">
      {history.map((item) => (
        <article key={item.id}>
          <ProfileAvatar className="mini-avatar" name={item.name} imageUrl={item.profileImageUrl} />
          <span>
            <strong>{item.statusMessage || item.name}</strong>
            <small>{item.eventType === 'PROFILE_IMAGE_UPDATED' ? '이미지 변경' : '프로필 변경'} · {new Date(item.createdAt).toLocaleString()}</small>
          </span>
        </article>
      ))}
    </div>
  );
}

function RoomList({
  rooms,
  selectedRoomId,
  previewRoomId,
  onPreview,
  onCursorMove,
  onCursorLeave,
  onSelect
}: {
  rooms: ChatRoom[];
  selectedRoomId: string;
  previewRoomId?: string;
  onPreview?: (roomId: string) => void;
  onCursorMove?: (event: React.PointerEvent<HTMLElement>, active: boolean) => void;
  onCursorLeave?: (event: React.PointerEvent<HTMLElement>) => void;
  onSelect: (roomId: string) => void;
}) {
  if (rooms.length === 0) {
    return <p className="empty-state">아직 대화가 없습니다.</p>;
  }
  return (
    <div className="room-list">
      {rooms.map((room, index) => (
        <EntityListItem
          key={room.id}
          active={room.id === selectedRoomId || room.id === previewRoomId}
          accentIndex={index}
          leading={room.type === 'DIRECT' ? <AtSign size={16} aria-hidden /> : <Hash size={16} aria-hidden />}
          title={room.name}
          description={room.type === 'DIRECT' ? '개인 메시지' : room.description}
          trailing={room.unreadCount > 0 ? <i className="unread-badge" aria-label={`${room.unreadCount}개의 읽지 않은 메시지`}>{room.unreadCount > 99 ? '99+' : room.unreadCount}</i> : undefined}
          previewId={room.id}
          onFocus={() => onPreview?.(room.id)}
          onMouseEnter={() => onPreview?.(room.id)}
          onPointerMove={(event) => onCursorMove?.(event, true)}
          onPointerLeave={onCursorLeave}
          onClick={() => onSelect(room.id)}
        />
      ))}
    </div>
  );
}

function useScrollAffordance(
  ref: React.RefObject<HTMLElement | null>,
  dependencies: Array<string | number | boolean | null | undefined>
) {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const element = ref.current;
    if (!element) {
      setVisible(false);
      return undefined;
    }

    let frame = 0;
    const update = () => {
      window.cancelAnimationFrame(frame);
      frame = window.requestAnimationFrame(() => {
        const remaining = element.scrollHeight - element.scrollTop - element.clientHeight;
        setVisible(remaining > 32);
      });
    };

    const resizeObserver = new ResizeObserver(update);
    resizeObserver.observe(element);
    Array.from(element.children).forEach((child) => resizeObserver.observe(child));
    element.addEventListener('scroll', update, { passive: true });
    update();

    return () => {
      window.cancelAnimationFrame(frame);
      resizeObserver.disconnect();
      element.removeEventListener('scroll', update);
    };
  }, [ref, ...dependencies]);

  return visible;
}

function ScrollAffordanceButton({
  visible,
  label,
  className,
  onClick
}: {
  visible: boolean;
  label: string;
  className: string;
  onClick: () => void;
}) {
  return (
    <button
      className={cn('scroll-affordance', className, visible && 'visible')}
      type="button"
      onClick={onClick}
      aria-hidden={!visible}
      tabIndex={visible ? 0 : -1}
      title={label}
    >
      <ChevronDown size={22} aria-hidden />
      <span>{label}</span>
    </button>
  );
}

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AppProviders>
      <App />
    </AppProviders>
  </React.StrictMode>
);
