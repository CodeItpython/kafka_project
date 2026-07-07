import React, { ClipboardEvent, FormEvent, KeyboardEvent, TouchEvent, UIEvent, WheelEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Client, IMessage } from '@stomp/stompjs';
import { AnimatePresence, LayoutGroup, motion, useReducedMotion, useScroll, useSpring, useTransform } from 'motion/react';
import {
  ArrowLeft,
  AtSign,
  Bell,
  BellOff,
  BellRing,
  Camera,
  CheckCircle2,
  ChevronRight,
  Hash,
  KeyRound,
  LogOut,
  Mail,
  MessageCircle,
  Reply,
  SmilePlus,
  Paperclip,
  Pin,
  Plus,
  Pencil,
  RefreshCcw,
  Save,
  Search,
  Send,
  Settings,
  Share2,
  ShoppingBag,
  ShoppingCart,
  Sparkles,
  Trash2,
  UserPlus,
  Users,
  Newspaper,
  X,
  UserRound
} from 'lucide-react';
import * as Popover from '@radix-ui/react-popover';
import * as ContextMenu from '@radix-ui/react-context-menu';
import './styles.css';

// 풀스크린 3D 파티클 씬은 무거우므로 코드 스플리팅(로그인 사용자 번들에 영향 없음)
const WelcomeScene = React.lazy(() => import('./WelcomeScene'));
import NewsFeed, { NewsItem } from './NewsFeed';
import ShoppingFeed, { ShoppingProduct } from './ShoppingFeed';
import { MessageLinkPreview, firstMessageUrl } from './LinkPreview';
import WelcomeLanding from './WelcomeLanding';

type Mode = 'login' | 'register' | 'email';
type HomeTab = 'friends' | 'chats' | 'news' | 'shopping' | 'settings';

// 하단 탭 순서 — 탭 전환 시 이 순서 기준으로 좌/우 슬라이드 방향을 정한다.
const TAB_ORDER: HomeTab[] = ['friends', 'chats', 'news', 'shopping', 'settings'];
// 오른쪽 탭으로 이동하면 새 패널이 오른쪽에서, 왼쪽 탭이면 왼쪽에서 슬라이드 인.
// 나가는 패널은 반대 방향으로 슬라이드 아웃(패널은 absolute 오버레이라 서로 겹쳐 크로스 슬라이드).
const TAB_SLIDE = {
  enter: (dir: number) => ({ x: dir >= 0 ? '100%' : '-100%' }),
  center: { x: '0%' },
  exit: (dir: number) => ({ x: dir >= 0 ? '-100%' : '100%' })
};
const TAB_SLIDE_TRANSITION = { type: 'spring', stiffness: 300, damping: 34 } as const;

type ShoppingCartItem = {
  id: number;
  productId: string;
  title: string;
  link: string;
  image: string | null;
  price: number;
  mallName: string | null;
  brand: string | null;
  quantity: number;
};

type ShoppingCart = {
  items: ShoppingCartItem[];
  totalCount: number;
  totalPrice: number;
};

type User = {
  id: number;
  email: string;
  name: string;
  provider: string;
  role: 'USER' | 'ADMIN';
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
  pinned: boolean;
  muted: boolean;
  participantCount: number;
  lastMessageAt: string | null;
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
  editedAt: string | null;
  readCount: number;
  deliveredCount: number;
  deliveryStatus: 'SENT' | 'DELIVERED' | 'READ';
  reactions: MessageReaction[];
  replyToMessageId: string | null;
  replyToSenderName: string | null;
  replyToContent: string | null;
};

type MessageReaction = {
  emoji: string;
  count: number;
  reactedByMe: boolean;
  reactorEmails: string[];
};

type ReadReceipt = {
  email: string;
  name: string;
  profileImageUrl: string | null;
  online: boolean;
  lastReadAt: string | null;
};

type RoomReadSummary = {
  roomId: string;
  currentUserLastReadAt: string | null;
  receipts: ReadReceipt[];
};

type MessageDeliverySummary = {
  roomId: string;
  messageId: string;
  deliveredCount: number;
  readCount: number;
  deliveryStatus: 'SENT' | 'DELIVERED' | 'READ';
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

type RoomParticipant = {
  id: number;
  email: string;
  name: string;
  provider: string;
  statusMessage: string;
  profileImageUrl: string | null;
  online: boolean;
  owner: boolean;
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

type UserNotification = {
  id: number;
  type: string;
  title: string;
  body: string;
  actorEmail: string;
  actorName: string;
  targetRoomId: string | null;
  targetMessageId: string | null;
  read: boolean;
  createdAt: string;
};

type NotificationListResponse = {
  notifications: UserNotification[];
  unreadCount: number;
};

type NotificationSubscriptionResponse = {
  topic: string;
  unreadCount: number;
};

type AppToast = {
  id: string;
  notification: UserNotification;
};

type DltMessage = {
  topic: string;
  partition: number;
  offset: number;
  key: string | null;
  messageId: string;
  roomId: string;
  roomName: string;
  senderEmail: string;
  senderName: string;
  createdAt: string;
};

type DltMessageListResponse = {
  topic: string;
  requestedLimit: number;
  messages: DltMessage[];
};

type DltReplayResponse = {
  sourceTopic: string;
  targetTopic: string;
  dryRun: boolean;
  scannedCount: number;
  replayedCount: number;
  replayedMessages: DltMessage[];
};

const API_ROOT = '/api';
const EMAIL_CODE_LENGTH = 6;
const QUICK_REACTIONS = ['👍', '❤️', '😂', '👏', '🔥'] as const;
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
  const [status, setStatus] = useState('');
  const [token, setToken] = useState(() => localStorage.getItem('accessToken') ?? '');
  const [user, setUser] = useState<User | null>(null);
  const [myProfile, setMyProfile] = useState<UserProfile | null>(null);
  const [selectedProfile, setSelectedProfile] = useState<UserProfile | null>(null);
  const [profileName, setProfileName] = useState('');
  const [profileStatus, setProfileStatus] = useState('');
  const [rooms, setRooms] = useState<ChatRoom[]>([]);
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [selectedRoomId, setSelectedRoomId] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [replyTarget, setReplyTarget] = useState<ChatMessage | null>(null);
  const [editingMessageId, setEditingMessageId] = useState('');
  const [editingDraft, setEditingDraft] = useState('');
  const [roomParticipants, setRoomParticipants] = useState<RoomParticipant[]>([]);
  const [inviteEmail, setInviteEmail] = useState('');
  const [readReceipts, setReadReceipts] = useState<ReadReceipt[]>([]);
  const [roomName, setRoomName] = useState('프로젝트 채팅방');
  const [roomDescription, setRoomDescription] = useState('Kafka 이벤트로 메시지를 주고받는 공간');
  const [draft, setDraft] = useState('');
  const [roomQuery, setRoomQuery] = useState('');
  const [contactQuery, setContactQuery] = useState('');
  const [messageQuery, setMessageQuery] = useState('');
  const [chatFilter, setChatFilter] = useState<'all' | 'unread'>('all');
  const [createChatOpen, setCreateChatOpen] = useState(false);
  const [createChatMode, setCreateChatMode] = useState<'group' | 'direct'>('group');
  const [chatSearchOpen, setChatSearchOpen] = useState(false);
  const [searchResults, setSearchResults] = useState<ChatMessage[]>([]);
  const [roomSuggestions, setRoomSuggestions] = useState<SearchSuggestion[]>([]);
  const [messageSuggestions, setMessageSuggestions] = useState<SearchSuggestion[]>([]);
  const [presence, setPresence] = useState<RoomPresence>({ onlineUsers: [], typingUsers: [] });
  const [attachment, setAttachment] = useState<File | null>(null);
  const [attachmentPreview, setAttachmentPreview] = useState('');
  const [loading, setLoading] = useState(false);
  const [connected, setConnected] = useState(false);
  const [activeTab, setActiveTab] = useState<HomeTab>('chats');
  const [tabDirection, setTabDirection] = useState(0);
  // 탭 전환: 이동 방향(순서 기준)을 기록해 슬라이드 방향 결정 후 탭 변경
  const switchTab = useCallback((next: HomeTab) => {
    if (next === activeTab) return;
    setTabDirection(TAB_ORDER.indexOf(next) >= TAB_ORDER.indexOf(activeTab) ? 1 : -1);
    setActiveTab(next);
  }, [activeTab]);
  const [authStage, setAuthStage] = useState<'landing' | 'login'>('landing');
  const [conversationSummary, setConversationSummary] = useState<ConversationSummary | null>(null);
  const [notifications, setNotifications] = useState<UserNotification[]>([]);
  const [notificationTopic, setNotificationTopic] = useState('');
  const [notificationUnreadCount, setNotificationUnreadCount] = useState(0);
  const [appToasts, setAppToasts] = useState<AppToast[]>([]);
  // 네이버 쇼핑 장바구니
  const [shoppingCart, setShoppingCart] = useState<ShoppingCart | null>(null);
  const [cartOpen, setCartOpen] = useState(false);
  const [cartAddingId, setCartAddingId] = useState<string | null>(null);
  const [pendingEmailAuth, setPendingEmailAuth] = useState<AuthResponse | null>(null);
  const [inAppNotificationsEnabled, setInAppNotificationsEnabled] = useState(() => localStorage.getItem('inAppNotificationsEnabled') !== 'false');
  const [dltMessages, setDltMessages] = useState<DltMessage[]>([]);
  const [dltTopic, setDltTopic] = useState('');
  const [dltLimit, setDltLimit] = useState(20);
  const [dltSelectedIds, setDltSelectedIds] = useState<string[]>([]);
  const [dltLoading, setDltLoading] = useState(false);
  const [dltResult, setDltResult] = useState<DltReplayResponse | null>(null);
  const [summaryLoading, setSummaryLoading] = useState(false);
  const [messagesRefreshing, setMessagesRefreshing] = useState(false);
  const [pullRefreshDistance, setPullRefreshDistance] = useState(0);
  const [roomDeleteConfirmOpen, setRoomDeleteConfirmOpen] = useState(false);
  const [withdrawConfirmOpen, setWithdrawConfirmOpen] = useState(false);
  // 뉴스 → 채팅 공유
  const [shareItem, setShareItem] = useState<NewsItem | null>(null);
  const [shareTab, setShareTab] = useState<'friends' | 'rooms'>('friends');
  const [shareSelected, setShareSelected] = useState<string[]>([]);
  const [shareLoading, setShareLoading] = useState(false);
  const [shareResult, setShareResult] = useState<{ count: number; title: string } | null>(null);
  // 이메일 변경
  const [emailChangeOpen, setEmailChangeOpen] = useState(false);
  const [emailChangeNew, setEmailChangeNew] = useState('');
  const [emailChangeCode, setEmailChangeCode] = useState('');
  const [emailChangeSent, setEmailChangeSent] = useState(false);
  const [emailChangeLoading, setEmailChangeLoading] = useState(false);
  const [emailChangeStatus, setEmailChangeStatus] = useState('');
  const messageListRef = useRef<HTMLDivElement | null>(null);
  const codeInputRefs = useRef<Array<HTMLInputElement | null>>([]);
  const roomDirectoryRef = useRef<HTMLElement | null>(null);
  const readReceiptsRef = useRef<ReadReceipt[]>([]);
  const openedNotificationRoomRef = useRef('');
  const shouldStickToLatestMessageRef = useRef(true);
  const forceLatestMessageScrollRef = useRef(false);
  const pullStartYRef = useRef<number | null>(null);
  const pullDistanceRef = useRef(0);
  const pullWheelReleaseTimerRef = useRef<number | null>(null);
  const lastAutoSubmittedCodeRef = useRef('');
  const shouldReduceMotion = useReducedMotion();
  const { scrollYProgress: directoryScrollYProgress } = useScroll({ container: roomDirectoryRef });
  const directoryProgressScaleX = useSpring(directoryScrollYProgress, {
    stiffness: 130,
    damping: 28,
    mass: 0.35
  });
  const directoryHeaderY = useTransform(directoryScrollYProgress, [0, 1], [0, -16]);
  const directoryHeaderOpacity = useTransform(directoryScrollYProgress, [0, 0.35], [1, 0.94]);

  const selectedRoom = rooms.find((room) => room.id === selectedRoomId) ?? null;
  const directRooms = rooms.filter((room) => room.type === 'DIRECT');
  const totalUnread = rooms.reduce((sum, room) => sum + room.unreadCount, 0);
  const unreadRoomCount = rooms.filter((room) => room.unreadCount > 0).length;
  const visibleChatRooms = chatFilter === 'unread' ? rooms.filter((room) => room.unreadCount > 0) : rooms;
  const chatSearchTerm = messageQuery.trim().toLowerCase();
  const roomSearchResults = chatSearchTerm
    ? rooms.filter((room) => room.name.toLowerCase().includes(chatSearchTerm))
    : [];
  const inviteOptions = contacts.filter((contact) => !roomParticipants.some((participant) => participant.email.toLowerCase() === contact.email.toLowerCase()));
  const isAdmin = user?.role === 'ADMIN';
  const latestMessageId = messages[messages.length - 1]?.id ?? '';
  const emailCodeDigits = Array.from({ length: EMAIL_CODE_LENGTH }, (_, index) => code[index] ?? '');
  const pullRefreshProgress = messagesRefreshing ? 1 : Math.min(1, pullRefreshDistance / 58);
  const pullRefreshVisible = messagesRefreshing || pullRefreshDistance > 0;

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
    localStorage.setItem('accessToken', data.accessToken);
    setToken(data.accessToken);
    setUser(data.user);
    setProfileName(data.user.name);
    setProfileStatus(data.user.statusMessage ?? '');
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
      const data = await request<{ expiresAt: string; sentTo: string }>('/auth/email/code', {
        method: 'POST',
        body: JSON.stringify({ email })
      });
      setCode('');
      lastAutoSubmittedCodeRef.current = '';
      requestAnimationFrame(() => codeInputRefs.current[0]?.focus());
      setStatus(`${data.sentTo}로 6자리 인증코드를 보냈습니다. 만료시각: ${new Date(data.expiresAt).toLocaleTimeString()}`);
    } catch (error) {
      setStatus(readableError(error, '인증코드를 보내지 못했습니다.'));
    } finally {
      setLoading(false);
    }
  }

  async function submitEmailFlow(event?: FormEvent) {
    event?.preventDefault();
    const verificationCode = code.replace(/\D/g, '').slice(0, EMAIL_CODE_LENGTH);
    if (verificationCode.length !== EMAIL_CODE_LENGTH) {
      setStatus('6자리 인증코드를 입력해주세요.');
      return;
    }
    setLoading(true);
    setStatus('');
    try {
      const data = await request<AuthResponse>('/auth/email/login', {
        method: 'POST',
        body: JSON.stringify({ email, code: verificationCode, name })
      });
      setPendingEmailAuth(data);
    } catch (error) {
      lastAutoSubmittedCodeRef.current = '';
      setStatus(readableError(error, '이메일 로그인에 실패했습니다.'));
    } finally {
      setLoading(false);
    }
  }

  function focusCodeInput(index: number) {
    requestAnimationFrame(() => codeInputRefs.current[index]?.focus());
  }

  function updateCode(nextCode: string, nextFocusIndex?: number) {
    const sanitizedCode = nextCode.replace(/\D/g, '').slice(0, EMAIL_CODE_LENGTH);
    setCode(sanitizedCode);
    if (nextFocusIndex !== undefined) {
      focusCodeInput(Math.min(Math.max(nextFocusIndex, 0), EMAIL_CODE_LENGTH - 1));
    }
  }

  function handleCodeDigitChange(index: number, value: string) {
    const numericValue = value.replace(/\D/g, '');
    if (!numericValue) {
      updateCode(`${code.slice(0, index)}${code.slice(index + 1)}`, index);
      return;
    }
    const nextDigits = code.split('');
    numericValue.slice(0, EMAIL_CODE_LENGTH - index).split('').forEach((digit, offset) => {
      nextDigits[index + offset] = digit;
    });
    const nextCode = nextDigits.join('').slice(0, EMAIL_CODE_LENGTH);
    updateCode(nextCode, Math.min(index + numericValue.length, EMAIL_CODE_LENGTH - 1));
  }

  function handleCodeKeyDown(index: number, event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Backspace' && !emailCodeDigits[index] && index > 0) {
      event.preventDefault();
      updateCode(`${code.slice(0, index - 1)}${code.slice(index)}`, index - 1);
    }
    if (event.key === 'ArrowLeft' && index > 0) {
      event.preventDefault();
      focusCodeInput(index - 1);
    }
    if (event.key === 'ArrowRight' && index < EMAIL_CODE_LENGTH - 1) {
      event.preventDefault();
      focusCodeInput(index + 1);
    }
  }

  function handleCodePaste(event: ClipboardEvent<HTMLInputElement>) {
    event.preventDefault();
    const pastedCode = event.clipboardData.getData('text').replace(/\D/g, '').slice(0, EMAIL_CODE_LENGTH);
    updateCode(pastedCode, Math.min(pastedCode.length, EMAIL_CODE_LENGTH - 1));
  }

  async function loadMe() {
    if (!token) return;
    try {
      const data = await request<User>('/auth/me');
      setUser(data);
      setProfileName(data.name);
      setProfileStatus(data.statusMessage ?? '');
    } catch {
      localStorage.removeItem('accessToken');
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
    setRooms(sortRooms(data));
  }

  async function updateRoomPreference(room: ChatRoom, preference: { pinned?: boolean; muted?: boolean }) {
    try {
      const updated = await request<ChatRoom>(`/chat/rooms/${room.id}/preferences`, {
        method: 'PATCH',
        body: JSON.stringify(preference)
      });
      setRooms((current) => sortRooms(current.map((item) => item.id === updated.id ? updated : item)));
      if (preference.muted !== undefined) {
        setStatus(preference.muted ? `${updated.name} 알림을 껐습니다.` : `${updated.name} 알림을 켰습니다.`);
      } else if (preference.pinned !== undefined) {
        setStatus(preference.pinned ? `${updated.name} 채팅방을 고정했습니다.` : `${updated.name} 채팅방 고정을 해제했습니다.`);
      } else {
        setStatus(`${updated.name} 설정을 변경했습니다.`);
      }
    } catch (error) {
      setStatus(readableError(error, '채팅방 설정을 변경하지 못했습니다.'));
    }
  }

  function roomActivityTime(room: ChatRoom) {
    return new Date(room.lastMessageAt ?? room.createdAt).getTime();
  }

  function sortRooms(items: ChatRoom[]) {
    return [...items].sort((first, second) => {
      if (first.pinned !== second.pinned) {
        return first.pinned ? -1 : 1;
      }
      // 최근 대화(마지막 메시지 시각)가 위로. 메시지 없으면 방 생성 시각으로 대체.
      return roomActivityTime(second) - roomActivityTime(first);
    });
  }

  function openRoom(roomId: string) {
    setSelectedRoomId(roomId);
    setConversationSummary(null);
    cancelEditingMessage();
    setRooms((current) => current.map((room) => room.id === roomId ? { ...room, unreadCount: 0 } : room));
    requestAnimationFrame(() => window.scrollTo({ top: 0, left: 0, behavior: 'instant' }));
  }

  const dismissAppToast = useCallback((toastId: string) => {
    setAppToasts((current) => current.filter((toast) => toast.id !== toastId));
  }, []);

  const showInAppNotification = useCallback((notification: UserNotification) => {
    const isCurrentRoom = Boolean(notification.targetRoomId && notification.targetRoomId === selectedRoomId);
    if (!inAppNotificationsEnabled || isCurrentRoom) {
      return;
    }
    const toastId = `${notification.id}-${Date.now()}`;
    setAppToasts((current) => [
      { id: toastId, notification },
      ...current.filter((toast) => toast.notification.id !== notification.id)
    ].slice(0, 3));
    window.setTimeout(() => dismissAppToast(toastId), 5200);
  }, [dismissAppToast, inAppNotificationsEnabled, selectedRoomId]);

  function openNotificationToast(toast: AppToast) {
    if (toast.notification.targetRoomId) {
      openRoom(toast.notification.targetRoomId);
    }
    dismissAppToast(toast.id);
  }

  function toggleInAppNotifications() {
    setInAppNotificationsEnabled((current) => {
      const next = !current;
      localStorage.setItem('inAppNotificationsEnabled', String(next));
      if (!next) {
        setAppToasts([]);
      }
      return next;
    });
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

  function withReadCounts(items: ChatMessage[], receipts: ReadReceipt[]) {
    return items.map((message) => {
      const readCount = receipts.filter((receipt) => {
        if (receipt.email.toLowerCase() === message.senderEmail.toLowerCase() || !receipt.lastReadAt) {
          return false;
        }
        return new Date(receipt.lastReadAt).getTime() >= new Date(message.createdAt).getTime();
      }).length;
      const deliveredCount = message.deliveredCount ?? 0;
      const deliveryStatus = readCount > 0
        ? 'READ' as const
        : deliveredCount > 0 || message.deliveryStatus === 'DELIVERED'
          ? 'DELIVERED' as const
          : 'SENT' as const;
      return {
        ...message,
        readCount,
        deliveredCount,
        deliveryStatus,
        reactions: normalizeReactions(message.reactions ?? [])
      };
    });
  }

  function deliveryStatusLabel(message: ChatMessage) {
    if (message.readCount > 0 || message.deliveryStatus === 'READ') {
      return `읽음 ${Math.max(message.readCount, 1)}`;
    }
    if (message.deliveredCount > 0 || message.deliveryStatus === 'DELIVERED') {
      return `전달됨 ${Math.max(message.deliveredCount, 1)}`;
    }
    return '전송됨';
  }

  function normalizeReactions(reactions: MessageReaction[]) {
    if (!user) {
      return reactions;
    }
    return reactions.map((reaction) => ({
      ...reaction,
      reactedByMe: reaction.reactorEmails.some((email) => email.toLowerCase() === user.email.toLowerCase())
    }));
  }

  function applyReadReceipts(receipts: ReadReceipt[]) {
    readReceiptsRef.current = receipts;
    setReadReceipts(receipts);
    setMessages((current) => withReadCounts(current, receipts));
  }

  async function loadReadReceipts(roomId: string) {
    if (!roomId) return;
    const data = await request<RoomReadSummary>(`/chat/rooms/${roomId}/read-receipts`);
    applyReadReceipts(data.receipts);
  }

  async function markRoomRead(roomId: string) {
    if (!roomId) return;
    const data = await request<RoomReadSummary>(`/chat/rooms/${roomId}/read`, { method: 'POST' });
    applyReadReceipts(data.receipts);
    setRooms((current) => current.map((room) => room.id === roomId ? { ...room, unreadCount: 0 } : room));
  }

  async function markMessageDelivered(roomId: string, messageId: string) {
    if (!roomId || !messageId) return;
    const data = await request<MessageDeliverySummary>(`/chat/rooms/${roomId}/messages/${messageId}/delivered`, { method: 'POST' });
    applyDeliverySummary(data);
  }

  function applyDeliverySummary(summary: MessageDeliverySummary) {
    setMessages((current) => current.map((message) => {
      if (message.id !== summary.messageId) {
        return message;
      }
      return {
        ...message,
        deliveredCount: summary.deliveredCount,
        readCount: summary.readCount,
        deliveryStatus: summary.deliveryStatus
      };
    }));
  }

  async function loadMessages(roomId: string) {
    if (!roomId) return;
    const data = await request<ChatMessage[]>(`/chat/rooms/${roomId}/messages`);
    setMessages(withReadCounts(data, readReceiptsRef.current));
    setRooms((current) => current.map((room) => room.id === roomId ? { ...room, unreadCount: 0 } : room));
    await loadReadReceipts(roomId);
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

  function updatePullRefreshDistance(distance: number) {
    const nextDistance = Math.max(0, Math.min(82, distance));
    pullDistanceRef.current = nextDistance;
    setPullRefreshDistance(nextDistance);
  }

  function resetPullRefreshDistance() {
    pullStartYRef.current = null;
    pullDistanceRef.current = 0;
    setPullRefreshDistance(0);
  }

  async function releasePullRefresh() {
    if (pullDistanceRef.current >= 58 && selectedRoomId && !messagesRefreshing) {
      setPullRefreshDistance(82);
      await refreshMessages();
    }
    resetPullRefreshDistance();
  }

  function handleMessageScroll(event: UIEvent<HTMLDivElement>) {
    const { scrollHeight, scrollTop, clientHeight } = event.currentTarget;
    const distanceFromBottom = scrollHeight - scrollTop - clientHeight;
    shouldStickToLatestMessageRef.current = distanceFromBottom < 120;
  }

  function handleMessageWheel(event: WheelEvent<HTMLDivElement>) {
    if (event.currentTarget.scrollTop > 0 || event.deltaY >= 0 || messagesRefreshing) return;
    event.preventDefault();
    updatePullRefreshDistance(pullDistanceRef.current + Math.abs(event.deltaY) * 0.28);
    if (pullWheelReleaseTimerRef.current) {
      window.clearTimeout(pullWheelReleaseTimerRef.current);
    }
    pullWheelReleaseTimerRef.current = window.setTimeout(() => {
      releasePullRefresh().catch(() => undefined);
    }, 160);
  }

  function handleMessageTouchStart(event: TouchEvent<HTMLDivElement>) {
    if (event.currentTarget.scrollTop > 0 || messagesRefreshing) return;
    pullStartYRef.current = event.touches[0]?.clientY ?? null;
  }

  function handleMessageTouchMove(event: TouchEvent<HTMLDivElement>) {
    if (pullStartYRef.current === null || event.currentTarget.scrollTop > 0 || messagesRefreshing) return;
    const currentY = event.touches[0]?.clientY ?? pullStartYRef.current;
    const distance = currentY - pullStartYRef.current;
    if (distance <= 0) return;
    event.preventDefault();
    updatePullRefreshDistance(distance * 0.48);
  }

  function handleMessageTouchEnd() {
    releasePullRefresh().catch(() => undefined);
  }

  function scrollLatestMessageIntoView(behavior: ScrollBehavior = 'smooth') {
    const messageList = messageListRef.current;
    if (!messageList) return;
    messageList.scrollTo({
      top: messageList.scrollHeight - messageList.clientHeight,
      left: 0,
      behavior
    });
  }

  // 대화창 진입 애니메이션(AnimatePresence mode="wait")이 끝나 목록이 확실히
  // 마운트된 시점에, 방 열기로 예약된 강제 스크롤을 최신 메시지로 반영한다.
  // 메시지가 아직 로드 전이면 소비하지 않고 latestMessageId 이펙트가 처리하게 둔다.
  function handleConversationEntered() {
    if (!forceLatestMessageScrollRef.current) return;
    if (!messageListRef.current || messages.length === 0) return;
    scrollLatestMessageIntoView('auto');
    window.requestAnimationFrame(() => scrollLatestMessageIntoView('auto'));
    forceLatestMessageScrollRef.current = false;
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

  async function loadRoomParticipants(roomId: string) {
    if (!roomId) return;
    const data = await request<RoomParticipant[]>(`/chat/rooms/${roomId}/participants`);
    setRoomParticipants(data);
  }

  async function inviteParticipant(event: FormEvent) {
    event.preventDefault();
    if (!selectedRoomId || !inviteEmail) return;
    try {
      const data = await request<RoomParticipant[]>(`/chat/rooms/${selectedRoomId}/participants`, {
        method: 'POST',
        body: JSON.stringify({ emails: [inviteEmail] })
      });
      setRoomParticipants(data);
      setInviteEmail('');
      await loadRooms(roomQuery);
      setStatus('친구를 채팅방에 초대했습니다.');
    } catch (error) {
      setStatus(readableError(error, '친구를 초대하지 못했습니다.'));
    }
  }

  async function leaveSelectedRoom() {
    if (!selectedRoomId || !selectedRoom) return;
    try {
      await request<void>(`/chat/rooms/${selectedRoomId}/participants/me`, { method: 'DELETE' });
      setRooms((current) => current.filter((room) => room.id !== selectedRoomId));
      setSelectedRoomId('');
      setMessages([]);
      setRoomParticipants([]);
      setStatus(`${selectedRoom.name} 채팅방에서 나갔습니다.`);
    } catch (error) {
      setStatus(readableError(error, '채팅방에서 나가지 못했습니다.'));
    }
  }

  async function loadNotifications() {
    if (!token) return;
    const data = await request<NotificationListResponse>('/notifications');
    setNotifications(data.notifications);
    setNotificationUnreadCount(data.unreadCount);
  }

  async function loadNotificationSubscription() {
    if (!token) return;
    const data = await request<NotificationSubscriptionResponse>('/notifications/subscription');
    setNotificationTopic(data.topic);
    setNotificationUnreadCount(data.unreadCount);
  }

  async function markAllNotificationsRead() {
    const data = await request<NotificationListResponse>('/notifications/read-all', { method: 'PATCH' });
    setNotifications(data.notifications);
    setNotificationUnreadCount(data.unreadCount);
  }

  async function fetchDltMessages(limit: number) {
    const safeLimit = Math.min(Math.max(limit, 1), 100);
    const data = await request<DltMessageListResponse>(`/admin/kafka/dlt/messages?limit=${safeLimit}`);
    setDltTopic(data.topic);
    setDltMessages(data.messages);
    setDltSelectedIds((current) => current.filter((messageId) => data.messages.some((message) => message.messageId === messageId)));
    return data;
  }

  async function loadDltMessages() {
    if (!token || dltLoading) return;
    setDltLoading(true);
    try {
      const data = await fetchDltMessages(dltLimit);
      setDltResult(null);
      setStatus(data.messages.length > 0 ? `${data.messages.length}개의 실패 메시지를 불러왔습니다.` : 'DLT에 쌓인 메시지가 없습니다.');
    } catch (error) {
      setStatus(readableError(error, '실패 메시지를 불러오지 못했습니다.'));
    } finally {
      setDltLoading(false);
    }
  }

  function toggleDltMessage(messageId: string) {
    setDltSelectedIds((current) => current.includes(messageId)
      ? current.filter((item) => item !== messageId)
      : [...current, messageId]);
  }

  async function replayDltMessages(dryRun: boolean) {
    if (!token || dltLoading) return;
    setDltLoading(true);
    try {
      const safeLimit = Math.min(Math.max(dltLimit, 1), 100);
      const data = await request<DltReplayResponse>('/admin/kafka/dlt/replay', {
        method: 'POST',
        body: JSON.stringify({
          messageIds: dltSelectedIds,
          limit: safeLimit,
          dryRun
        })
      });
      setDltResult(data);
      setStatus(dryRun
        ? `${data.replayedCount}개의 재처리 후보를 확인했습니다.`
        : `${data.replayedCount}개의 실패 메시지를 원본 토픽으로 재발행했습니다.`);
      if (!dryRun) {
        await fetchDltMessages(safeLimit);
      }
    } catch (error) {
      setStatus(readableError(error, dryRun ? '재처리 후보 확인에 실패했습니다.' : '실패 메시지 재처리에 실패했습니다.'));
    } finally {
      setDltLoading(false);
    }
  }

  function pushTyping(typing: boolean) {
    if (!selectedRoomId || !token) return;
    request<void>(`/chat/rooms/${selectedRoomId}/typing`, {
      method: 'POST',
      body: JSON.stringify({ typing })
    }).catch(() => undefined);
  }

  function openCreateChat(mode: 'group' | 'direct' = 'group') {
    setCreateChatMode(mode);
    setCreateChatOpen(true);
    loadContacts('').catch(() => undefined);
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
      setCreateChatOpen(false);
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
      setRoomDeleteConfirmOpen(false);
      setStatus(`${selectedRoom.name} 채팅방을 나갔습니다.`);
    } catch (error) {
      setStatus(readableError(error, '채팅방을 나가지 못했습니다.'));
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
      setCreateChatOpen(false);
      openRoom(room.id);
      setStatus(`${contact.name}님과 1:1 대화를 시작합니다.`);
    } catch (error) {
      setStatus(readableError(error, '1:1 채팅방을 열지 못했습니다.'));
    } finally {
      setLoading(false);
    }
  }

  async function loadShoppingCart() {
    try {
      const data = await request<ShoppingCart>('/shopping/cart');
      setShoppingCart(data);
    } catch {
      // 장바구니는 로그인 사용자 전용 — 실패 시 조용히 무시
    }
  }

  async function addToShoppingCart(product: ShoppingProduct) {
    setCartAddingId(product.productId);
    try {
      const data = await request<ShoppingCart>('/shopping/cart', {
        method: 'POST',
        body: JSON.stringify({
          productId: product.productId,
          title: product.title,
          link: product.link,
          image: product.image,
          price: product.price,
          mallName: product.mallName,
          brand: product.brand
        })
      });
      setShoppingCart(data);
      setStatus(`장바구니에 담았어요. (${data.totalCount}개)`);
    } catch (error) {
      setStatus(readableError(error, '장바구니에 담지 못했습니다.'));
    } finally {
      setCartAddingId(null);
    }
  }

  async function updateCartQuantity(id: number, quantity: number) {
    try {
      const data = await request<ShoppingCart>(`/shopping/cart/${id}`, {
        method: 'PATCH',
        body: JSON.stringify({ quantity })
      });
      setShoppingCart(data);
    } catch (error) {
      setStatus(readableError(error, '수량을 변경하지 못했습니다.'));
    }
  }

  async function removeCartItem(id: number) {
    try {
      const data = await request<ShoppingCart>(`/shopping/cart/${id}`, { method: 'DELETE' });
      setShoppingCart(data);
    } catch (error) {
      setStatus(readableError(error, '삭제하지 못했습니다.'));
    }
  }

  async function clearShoppingCart() {
    try {
      const data = await request<ShoppingCart>('/shopping/cart', { method: 'DELETE' });
      setShoppingCart(data);
    } catch (error) {
      setStatus(readableError(error, '장바구니를 비우지 못했습니다.'));
    }
  }

  function openShareModal(item: NewsItem) {
    setShareItem(item);
    setShareTab('friends');
    setShareSelected([]);
    if (contacts.length === 0) loadContacts('');
    if (rooms.length === 0) loadRooms('');
  }

  function closeShareModal() {
    setShareItem(null);
    setShareSelected([]);
  }

  function toggleShareTarget(key: string) {
    setShareSelected((current) => (current.includes(key) ? current.filter((item) => item !== key) : [...current, key]));
  }

  async function submitShareNews() {
    if (!shareItem || shareSelected.length === 0) return;
    setShareLoading(true);
    const content = `${shareItem.title}\n${shareItem.url}`;
    const title = shareItem.title;
    let shared = 0;
    try {
      for (const key of shareSelected) {
        try {
          let roomId: string;
          if (key.startsWith('contact:')) {
            const partnerEmail = key.slice('contact:'.length);
            const room = await request<ChatRoom>('/chat/direct-rooms', {
              method: 'POST',
              body: JSON.stringify({ partnerEmail })
            });
            setRooms((current) => [room, ...current.filter((item) => item.id !== room.id)]);
            roomId = room.id;
          } else {
            roomId = key.slice('room:'.length);
          }
          await request(`/chat/rooms/${roomId}/messages`, {
            method: 'POST',
            body: JSON.stringify({ content, attachment: null, replyToMessageId: null })
          });
          shared += 1;
        } catch (error) {
          // 개별 대상 전송 실패는 건너뛰고 나머지를 계속 공유한다.
          console.warn('news share failed', key, error);
        }
      }
      closeShareModal();
      if (shared > 0) {
        setShareResult({ count: shared, title });
      } else {
        setStatus('뉴스를 공유하지 못했습니다. 잠시 후 다시 시도해주세요.');
      }
    } finally {
      setShareLoading(false);
    }
  }

  function openEmailChange() {
    setEmailChangeOpen(true);
    setEmailChangeNew('');
    setEmailChangeCode('');
    setEmailChangeSent(false);
    setEmailChangeStatus('');
  }

  function closeEmailChange() {
    setEmailChangeOpen(false);
    setEmailChangeNew('');
    setEmailChangeCode('');
    setEmailChangeSent(false);
    setEmailChangeStatus('');
  }

  async function sendEmailChangeCode() {
    const target = emailChangeNew.trim();
    if (!target) {
      setEmailChangeStatus('변경할 이메일을 입력해주세요.');
      return;
    }
    setEmailChangeLoading(true);
    setEmailChangeStatus('');
    try {
      const data = await request<{ expiresAt: string; sentTo: string }>('/auth/me/email/code', {
        method: 'POST',
        body: JSON.stringify({ email: target })
      });
      setEmailChangeSent(true);
      setEmailChangeCode('');
      setEmailChangeStatus(`${data.sentTo}로 6자리 인증코드를 보냈습니다.`);
    } catch (error) {
      setEmailChangeStatus(readableError(error, '인증코드를 보내지 못했습니다.'));
    } finally {
      setEmailChangeLoading(false);
    }
  }

  async function submitEmailChange() {
    const target = emailChangeNew.trim();
    const verificationCode = emailChangeCode.replace(/\D/g, '').slice(0, EMAIL_CODE_LENGTH);
    if (verificationCode.length !== EMAIL_CODE_LENGTH) {
      setEmailChangeStatus('6자리 인증코드를 입력해주세요.');
      return;
    }
    setEmailChangeLoading(true);
    setEmailChangeStatus('');
    try {
      const data = await request<AuthResponse>('/auth/me/email', {
        method: 'POST',
        body: JSON.stringify({ email: target, code: verificationCode })
      });
      saveSession(data);
      setEmail(data.user.email);
      closeEmailChange();
      setStatus(`이메일이 ${data.user.email}로 변경되었습니다.`);
    } catch (error) {
      setEmailChangeStatus(readableError(error, '이메일을 변경하지 못했습니다.'));
    } finally {
      setEmailChangeLoading(false);
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
    const replyToMessageId = replyTarget?.id ?? null;
    forceLatestMessageScrollRef.current = true;
    shouldStickToLatestMessageRef.current = true;
    setDraft('');
    setReplyTarget(null);
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
        body: JSON.stringify({ content, attachment: uploaded, replyToMessageId })
      });
      clearAttachment();
      setTimeout(() => loadMessages(selectedRoomId), 350);
    } catch (error) {
      setDraft(content);
      setReplyTarget(replyTarget);
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
      const normalized = withReadCounts([updated], readReceiptsRef.current)[0];
      setMessages((current) => current.map((item) => item.id === normalized.id ? normalized : item));
      setSearchResults((current) => current.filter((item) => item.id !== updated.id));
    } catch (error) {
      setStatus(readableError(error, '모두에게 삭제하지 못했습니다.'));
    }
  }

  function startEditingMessage(message: ChatMessage) {
    setReplyTarget(null);
    setEditingMessageId(message.id);
    setEditingDraft(message.content);
  }

  function cancelEditingMessage() {
    setEditingMessageId('');
    setEditingDraft('');
  }

  async function editMessage(message: ChatMessage) {
    const content = editingDraft.trim();
    if (!content) {
      setStatus('수정할 메시지 내용을 입력해주세요.');
      return;
    }
    try {
      const updated = await request<ChatMessage>(`/chat/rooms/${message.roomId}/messages/${message.id}`, {
        method: 'PATCH',
        body: JSON.stringify({ content })
      });
      const normalized = withReadCounts([updated], readReceiptsRef.current)[0];
      setMessages((current) => current.map((item) => item.id === normalized.id ? normalized : item));
      setSearchResults((current) => current.map((item) => item.id === normalized.id ? normalized : item));
      cancelEditingMessage();
    } catch (error) {
      setStatus(readableError(error, '메시지를 수정하지 못했습니다.'));
    }
  }

  async function toggleMessageReaction(message: ChatMessage, emoji: string) {
    try {
      const updated = await request<ChatMessage>(`/chat/rooms/${message.roomId}/messages/${message.id}/reactions`, {
        method: 'POST',
        body: JSON.stringify({ emoji })
      });
      const normalized = withReadCounts([updated], readReceiptsRef.current)[0];
      setMessages((current) => current.map((item) => item.id === normalized.id ? normalized : item));
    } catch (error) {
      setStatus(readableError(error, '반응을 저장하지 못했습니다.'));
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
    localStorage.removeItem('accessToken');
    setToken('');
    setUser(null);
    setRooms([]);
    setMessages([]);
    setReplyTarget(null);
    cancelEditingMessage();
    setRoomParticipants([]);
    setInviteEmail('');
    setReadReceipts([]);
    readReceiptsRef.current = [];
    setSearchResults([]);
    setRoomSuggestions([]);
    setMessageSuggestions([]);
    setPresence({ onlineUsers: [], typingUsers: [] });
    setMyProfile(null);
    setSelectedProfile(null);
    setNotifications([]);
    setNotificationTopic('');
    setNotificationUnreadCount(0);
    setAppToasts([]);
    setDltMessages([]);
    setDltTopic('');
    setDltSelectedIds([]);
    setDltResult(null);
    setProfileName('');
    setProfileStatus('');
    setSelectedRoomId('');
    setActiveTab('chats');
    setAuthStage('landing');
    clearAttachment();
    setStatus('로그아웃되었습니다.');
  }

  async function deleteAccount() {
    setLoading(true);
    try {
      await request<void>('/auth/me', { method: 'DELETE' });
      setWithdrawConfirmOpen(false);
      logout();
      setStatus('회원 탈퇴가 완료되었습니다. 그동안 이용해 주셔서 감사합니다.');
    } catch (error) {
      setStatus(readableError(error, '회원 탈퇴에 실패했습니다.'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    const hashParams = new URLSearchParams(window.location.hash.replace(/^#/, ''));
    const kakaoToken = hashParams.get('access_token');
    if (kakaoToken) {
      localStorage.setItem('accessToken', kakaoToken);
      setToken(kakaoToken);
      window.history.replaceState(null, '', window.location.pathname);
    }
  }, []);

  useEffect(() => {
    loadMe();
  }, [token]);

  useEffect(() => {
    if (user) {
      loadRooms('');
      loadContacts('');
      loadMyProfile().catch(() => undefined);
      loadNotifications().catch(() => undefined);
      loadNotificationSubscription().catch(() => undefined);
      heartbeat().catch(() => undefined);
    }
  }, [user?.email]);

  useEffect(() => {
    const verificationCode = code.replace(/\D/g, '').slice(0, EMAIL_CODE_LENGTH);
    if (mode !== 'email' || verificationCode.length !== EMAIL_CODE_LENGTH || loading || !email.trim() || !name.trim()) {
      return;
    }
    if (lastAutoSubmittedCodeRef.current === verificationCode) {
      return;
    }
    lastAutoSubmittedCodeRef.current = verificationCode;
    submitEmailFlow().catch(() => undefined);
  }, [code, mode, loading, email, name]);

  useEffect(() => {
    if (!user || rooms.length === 0) return;
    const roomId = new URLSearchParams(window.location.search).get('roomId');
    if (!roomId || openedNotificationRoomRef.current === roomId) return;
    if (rooms.some((room) => room.id === roomId)) {
      openedNotificationRoomRef.current = roomId;
      openRoom(roomId);
      window.history.replaceState(null, '', window.location.pathname);
    }
  }, [user?.email, rooms.length]);

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
      forceLatestMessageScrollRef.current = true;
      shouldStickToLatestMessageRef.current = true;
      setReadReceipts([]);
      readReceiptsRef.current = [];
      setMessages([]);
      setReplyTarget(null);
      setInviteEmail('');
      loadMessages(selectedRoomId);
      loadPresence(selectedRoomId);
      loadRoomParticipants(selectedRoomId);
      setConversationSummary(null);
      resetPullRefreshDistance();
    }
  }, [selectedRoomId]);

  useEffect(() => {
    return () => {
      if (pullWheelReleaseTimerRef.current) {
        window.clearTimeout(pullWheelReleaseTimerRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (messages.length === 0) return;
    if (!forceLatestMessageScrollRef.current && !shouldStickToLatestMessageRef.current) return;
    const force = forceLatestMessageScrollRef.current;
    const behavior: ScrollBehavior = force ? 'auto' : 'smooth';
    let attempts = 0;
    const runScroll = () => {
      const messageList = messageListRef.current;
      // 방 전환은 AnimatePresence mode="wait"라 이전 화면이 빠져나간 뒤에야
      // 대화창이 마운트된다. 강제 스크롤(방 열기)일 때는 목록이 마운트될
      // 때까지 재시도해, force 플래그가 헛되이 소비돼 첫 메시지에 머무는 것을 막는다.
      if (!messageList) {
        if (force && attempts < 40) {
          attempts += 1;
          window.requestAnimationFrame(runScroll);
        }
        return;
      }
      scrollLatestMessageIntoView(behavior);
      window.requestAnimationFrame(() => scrollLatestMessageIntoView(behavior));
      if (force) {
        forceLatestMessageScrollRef.current = false;
        // 첨부 이미지 로딩 등으로 뒤늦게 높이가 늘어나는 경우 대비(사용자가
        // 위로 스크롤하지 않았을 때만 다시 맨 아래로 고정).
        window.setTimeout(() => {
          if (shouldStickToLatestMessageRef.current) {
            scrollLatestMessageIntoView('auto');
          }
        }, 160);
      }
    };
    window.requestAnimationFrame(runScroll);
  }, [latestMessageId, selectedRoomId]);

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
    if (!selectedRoomId || !token) return;

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const client = new Client({
      brokerURL: `${protocol}//${window.location.host}/ws/websocket`,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/topic/rooms/${selectedRoomId}`, (frame: IMessage) => {
          const message = JSON.parse(frame.body) as ChatMessage;
          const messageWithReadState = withReadCounts([message], readReceiptsRef.current)[0];
          setMessages((current) => current.some((item) => item.id === message.id)
            ? current.map((item) => item.id === message.id ? messageWithReadState : item)
            : [...current, messageWithReadState]);
          if (user && message.senderEmail.toLowerCase() !== user.email.toLowerCase()) {
            markRoomRead(selectedRoomId).catch(() => undefined);
          }
        });
        client.subscribe(`/topic/rooms/${selectedRoomId}/read-receipts`, (frame: IMessage) => {
          const summary = JSON.parse(frame.body) as RoomReadSummary;
          applyReadReceipts(summary.receipts);
        });
        client.subscribe(`/topic/rooms/${selectedRoomId}/delivery-states`, (frame: IMessage) => {
          const summary = JSON.parse(frame.body) as MessageDeliverySummary;
          applyDeliverySummary(summary);
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
  }, [selectedRoomId, user?.email, token]);

  useEffect(() => {
    if (!notificationTopic || !token) return;
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const client = new Client({
      brokerURL: `${protocol}//${window.location.host}/ws/websocket`,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
      onConnect: () => {
        client.subscribe(notificationTopic, (frame: IMessage) => {
          const notification = JSON.parse(frame.body) as UserNotification;
          setNotifications((current) => [notification, ...current.filter((item) => item.id !== notification.id)].slice(0, 30));
          setNotificationUnreadCount((current) => current + (notification.read ? 0 : 1));
          if (notification.targetRoomId !== selectedRoomId) {
            setRooms((current) => current.map((room) => room.id === notification.targetRoomId ? { ...room, unreadCount: room.unreadCount + 1 } : room));
          }
          if (notification.targetRoomId && notification.targetMessageId && notification.targetRoomId !== selectedRoomId) {
            markMessageDelivered(notification.targetRoomId, notification.targetMessageId).catch(() => undefined);
          }
          showInAppNotification(notification);
        });
      }
    });
    client.activate();
    return () => {
      client.deactivate();
    };
  }, [notificationTopic, selectedRoomId, showInAppNotification, token]);

  if (!user) {
    if (authStage === 'landing') {
      return <WelcomeLanding onStart={() => setAuthStage('login')} />;
    }
    return (
      <main className="auth-shell auth-shell--3d">
        <React.Suspense fallback={null}>
          <WelcomeScene />
        </React.Suspense>
        <AnimatePresence>
          {pendingEmailAuth && (
            <motion.div
              className="confirm-dialog-backdrop"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              role="presentation"
            >
              <motion.section
                className="confirm-dialog success-dialog"
                role="dialog"
                aria-modal="true"
                aria-labelledby="email-auth-success-title"
                initial={{ opacity: 0, y: 18, scale: 0.96 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: 12, scale: 0.96 }}
                transition={{ type: 'spring', stiffness: 340, damping: 28 }}
              >
                <span className="confirm-dialog-icon success"><CheckCircle2 size={22} aria-hidden /></span>
                <div>
                  <strong id="email-auth-success-title">이메일 인증이 완료되었습니다</strong>
                  <p>{pendingEmailAuth.user.name}님, 이제 Kafka Talk를 시작할 수 있어요.</p>
                </div>
                <div className="confirm-dialog-actions single">
                  <button type="button" onClick={() => saveSession(pendingEmailAuth)}>채팅 시작하기</button>
                </div>
              </motion.section>
            </motion.div>
          )}
        </AnimatePresence>
        <div className="auth-stage">
          <motion.div
            className="hero-copy"
            initial={{ y: 16 }}
            animate={{ y: 0 }}
            transition={{ duration: 0.5, ease: 'easeOut' }}
          >
            <button type="button" className="auth-back" onClick={() => setAuthStage('landing')}>
              <ArrowLeft size={16} aria-hidden /> 돌아가기
            </button>
            <p className="eyebrow">Kafka Talk</p>
            <h1>반가워요 👋</h1>
            <p className="hero-sub">테스트 계정으로 바로 체험하거나 로그인하세요.</p>
          </motion.div>

          <motion.section
            className="auth-panel"
            initial={{ y: 18 }}
            animate={{ y: 0 }}
            transition={{ duration: 0.4, ease: 'easeOut' }}
          >
            <div className="auth-panel-head">
              <h2>{title}</h2>
              <p className="muted">테스트 계정으로 바로 체험하거나 로그인하세요.</p>
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
                <div className="email-code-panel">
                  <div className="email-code-title">
                    <span>인증코드</span>
                    <small>{loading && code.length === EMAIL_CODE_LENGTH ? '확인 중' : '6자리 숫자 입력 시 자동 확인'}</small>
                  </div>
                  <div className="otp-inputs" aria-label="6자리 이메일 인증코드">
                    {emailCodeDigits.map((digit, index) => (
                      <input
                        key={`email-code-${index}`}
                        ref={(element) => { codeInputRefs.current[index] = element; }}
                        value={digit}
                        onChange={(event) => handleCodeDigitChange(index, event.target.value)}
                        onKeyDown={(event) => handleCodeKeyDown(index, event)}
                        onPaste={handleCodePaste}
                        inputMode="numeric"
                        pattern="[0-9]*"
                        autoComplete={index === 0 ? 'one-time-code' : 'off'}
                        maxLength={1}
                        aria-label={`인증코드 ${index + 1}번째 숫자`}
                      />
                    ))}
                  </div>
                  <button type="button" className="icon-button" onClick={sendCode} disabled={loading} title="인증코드 발송"><Mail size={19} aria-hidden /></button>
                </div>
                <p className="hint">메일함에서 6자리 인증코드를 확인하세요.</p>
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
          <button className="naver-button" type="button" onClick={() => { window.location.href = `${API_ROOT}/auth/oauth/naver/authorize`; }}><span className="naver-mark" aria-hidden>N</span>네이버로 로그인</button>
          {status && <p className="notice">{status}</p>}
          </motion.section>
        </div>
      </main>
    );
  }

  return (
    <div className={['app-shell', `tab-${activeTab}`, selectedRoomId ? 'in-conversation' : ''].filter(Boolean).join(' ')}>
      <NotificationToastStack
        toasts={appToasts}
        onOpen={openNotificationToast}
        onDismiss={dismissAppToast}
      />
      <AnimatePresence>
        {roomDeleteConfirmOpen && selectedRoom && (
          <motion.div
            className="confirm-dialog-backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            role="presentation"
            onClick={() => setRoomDeleteConfirmOpen(false)}
          >
            <motion.section
              className="confirm-dialog"
              role="dialog"
              aria-modal="true"
              aria-labelledby="room-exit-confirm-title"
              onClick={(event) => event.stopPropagation()}
              initial={{ opacity: 0, y: 18, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 12, scale: 0.96 }}
              transition={{ type: 'spring', stiffness: 340, damping: 28 }}
            >
              <button className="confirm-dialog-close" type="button" onClick={() => setRoomDeleteConfirmOpen(false)} title="닫기">
                <X size={17} aria-hidden />
              </button>
              <span className="confirm-dialog-icon"><Trash2 size={20} aria-hidden /></span>
              <div>
                <strong id="room-exit-confirm-title">채팅방을 나가겠습니까?</strong>
                <p>{selectedRoom.name} 채팅방이 목록에서 사라지고, 이 화면의 대화가 닫힙니다.</p>
              </div>
              <div className="confirm-dialog-actions">
                <button type="button" onClick={() => setRoomDeleteConfirmOpen(false)} disabled={loading}>취소</button>
                <button type="button" onClick={deleteRoom} disabled={loading}>{loading ? '나가는 중' : '나가기'}</button>
              </div>
            </motion.section>
          </motion.div>
        )}
      </AnimatePresence>
      <AnimatePresence>
        {withdrawConfirmOpen && (
          <motion.div
            className="confirm-dialog-backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            role="presentation"
            onClick={() => setWithdrawConfirmOpen(false)}
          >
            <motion.section
              className="confirm-dialog"
              role="dialog"
              aria-modal="true"
              aria-labelledby="withdraw-confirm-title"
              onClick={(event) => event.stopPropagation()}
              initial={{ opacity: 0, y: 18, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 12, scale: 0.96 }}
              transition={{ type: 'spring', stiffness: 340, damping: 28 }}
            >
              <button className="confirm-dialog-close" type="button" onClick={() => setWithdrawConfirmOpen(false)} title="닫기">
                <X size={17} aria-hidden />
              </button>
              <span className="confirm-dialog-icon danger"><Trash2 size={20} aria-hidden /></span>
              <div>
                <strong id="withdraw-confirm-title">정말 탈퇴하시겠어요?</strong>
                <p>계정이 삭제되어 더 이상 로그인할 수 없습니다. 이 작업은 되돌릴 수 없어요.</p>
              </div>
              <div className="confirm-dialog-actions">
                <button type="button" onClick={() => setWithdrawConfirmOpen(false)} disabled={loading}>취소</button>
                <button type="button" className="danger" onClick={deleteAccount} disabled={loading}>{loading ? '탈퇴 중' : '탈퇴하기'}</button>
              </div>
            </motion.section>
          </motion.div>
        )}
      </AnimatePresence>
      <AnimatePresence>
        {createChatOpen && (
          <motion.div
            className="confirm-dialog-backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            role="presentation"
            onClick={() => setCreateChatOpen(false)}
          >
            <motion.section
              className="confirm-dialog create-chat-dialog"
              role="dialog"
              aria-modal="true"
              aria-labelledby="create-chat-title"
              onClick={(event) => event.stopPropagation()}
              initial={{ opacity: 0, y: 20, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 14, scale: 0.96 }}
              transition={{ type: 'spring', stiffness: 340, damping: 28 }}
            >
              <button className="confirm-dialog-close" type="button" onClick={() => setCreateChatOpen(false)} title="닫기">
                <X size={17} aria-hidden />
              </button>
              <strong id="create-chat-title" className="create-chat-heading">새 채팅</strong>
              <div className="create-chat-tabs" role="tablist" aria-label="채팅 종류">
                <button type="button" role="tab" aria-selected={createChatMode === 'group'} className={createChatMode === 'group' ? 'active' : ''} onClick={() => setCreateChatMode('group')}>
                  <Hash size={15} aria-hidden />그룹 채팅
                </button>
                <button type="button" role="tab" aria-selected={createChatMode === 'direct'} className={createChatMode === 'direct' ? 'active' : ''} onClick={() => { setCreateChatMode('direct'); loadContacts('').catch(() => undefined); }}>
                  <AtSign size={15} aria-hidden />1:1 대화
                </button>
              </div>

              {createChatMode === 'group' ? (
                <form className="create-chat-form" onSubmit={createRoom}>
                  <label>방 이름<input value={roomName} onChange={(event) => setRoomName(event.target.value)} required /></label>
                  <label>설명<input value={roomDescription} onChange={(event) => setRoomDescription(event.target.value)} placeholder="어떤 대화를 나눌까요?" /></label>
                  <button className="primary-button" disabled={loading}><Plus size={17} aria-hidden />그룹 채팅 만들기</button>
                </form>
              ) : (
                <div className="create-chat-contacts">
                  {contacts.length === 0 ? (
                    <p className="empty-state">대화할 친구가 없어요. 친구 탭에서 친구를 검색해보세요.</p>
                  ) : (
                    contacts.map((contact) => (
                      <button key={contact.email} type="button" onClick={() => openDirectRoom(contact)} disabled={loading}>
                        <ProfileAvatar className="mini-avatar" name={contact.name} imageUrl={contact.profileImageUrl} />
                        <span>
                          <strong>{contact.name}</strong>
                          <small>{contact.statusMessage || (contact.online ? '온라인' : contact.email)}</small>
                        </span>
                        <ChevronRight size={16} aria-hidden />
                      </button>
                    ))
                  )}
                </div>
              )}
            </motion.section>
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {shareItem && (
          <motion.div
            className="confirm-dialog-backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            role="presentation"
            onClick={closeShareModal}
          >
            <motion.section
              className="share-dialog"
              role="dialog"
              aria-modal="true"
              aria-labelledby="share-dialog-title"
              onClick={(event) => event.stopPropagation()}
              initial={{ opacity: 0, y: 18, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 12, scale: 0.96 }}
              transition={{ type: 'spring', stiffness: 340, damping: 28 }}
            >
              <button className="confirm-dialog-close" type="button" onClick={closeShareModal} title="닫기">
                <X size={17} aria-hidden />
              </button>
              <header className="share-dialog-head">
                <span className="confirm-dialog-icon"><Share2 size={20} aria-hidden /></span>
                <div>
                  <strong id="share-dialog-title">채팅으로 공유</strong>
                  <p className="share-news-title">{shareItem.title}</p>
                </div>
              </header>
              <div className="share-toggle" role="tablist" aria-label="공유 대상">
                <button type="button" role="tab" aria-selected={shareTab === 'friends'} className={shareTab === 'friends' ? 'active' : ''} onClick={() => setShareTab('friends')}>
                  <Users size={15} aria-hidden />친구
                </button>
                <button type="button" role="tab" aria-selected={shareTab === 'rooms'} className={shareTab === 'rooms' ? 'active' : ''} onClick={() => setShareTab('rooms')}>
                  <MessageCircle size={15} aria-hidden />대화방
                </button>
              </div>
              <div className="share-list">
                {shareTab === 'friends' && contacts.map((contact) => {
                  const key = `contact:${contact.email}`;
                  const selected = shareSelected.includes(key);
                  return (
                    <button type="button" key={contact.email} className={selected ? 'share-item selected' : 'share-item'} onClick={() => toggleShareTarget(key)}>
                      <ProfileAvatar className="mini-avatar" name={contact.name} imageUrl={contact.profileImageUrl} />
                      <span><strong>{contact.name}</strong><small>{contact.statusMessage || contact.email}</small></span>
                      {selected && <CheckCircle2 className="share-check" size={18} aria-hidden />}
                    </button>
                  );
                })}
                {shareTab === 'friends' && contacts.length === 0 && <p className="empty-state">친구 목록이 비어 있습니다.</p>}
                {shareTab === 'rooms' && rooms.map((room) => {
                  const key = `room:${room.id}`;
                  const selected = shareSelected.includes(key);
                  return (
                    <button type="button" key={room.id} className={selected ? 'share-item selected' : 'share-item'} onClick={() => toggleShareTarget(key)}>
                      <span className="share-room-glyph" aria-hidden>{room.type === 'DIRECT' ? <MessageCircle size={16} /> : <Hash size={16} />}</span>
                      <span><strong>{room.name}</strong><small>{room.type === 'DIRECT' ? '1:1 대화' : `참여자 ${room.participantCount}명`}</small></span>
                      {selected && <CheckCircle2 className="share-check" size={18} aria-hidden />}
                    </button>
                  );
                })}
                {shareTab === 'rooms' && rooms.length === 0 && <p className="empty-state">대화방이 없습니다.</p>}
              </div>
              <div className="confirm-dialog-actions">
                <button type="button" onClick={closeShareModal} disabled={shareLoading}>취소</button>
                <button type="button" className="primary" onClick={submitShareNews} disabled={shareLoading || shareSelected.length === 0}>
                  {shareLoading ? '공유 중' : shareSelected.length > 0 ? `${shareSelected.length}곳에 공유` : '공유'}
                </button>
              </div>
            </motion.section>
          </motion.div>
        )}
      </AnimatePresence>
      <AnimatePresence>
        {shareResult && (
          <motion.div
            className="confirm-dialog-backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            role="presentation"
            onClick={() => setShareResult(null)}
          >
            <motion.section
              className="confirm-dialog"
              role="dialog"
              aria-modal="true"
              aria-labelledby="share-done-title"
              onClick={(event) => event.stopPropagation()}
              initial={{ opacity: 0, y: 18, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 12, scale: 0.96 }}
              transition={{ type: 'spring', stiffness: 340, damping: 28 }}
            >
              <button className="confirm-dialog-close" type="button" onClick={() => setShareResult(null)} title="닫기">
                <X size={17} aria-hidden />
              </button>
              <span className="confirm-dialog-icon success"><CheckCircle2 size={20} aria-hidden /></span>
              <div>
                <strong id="share-done-title">공유 완료</strong>
                <p>{shareResult.count}개의 대화방에 뉴스를 공유했어요.</p>
              </div>
              <div className="confirm-dialog-actions single">
                <button type="button" className="primary" onClick={() => setShareResult(null)}>확인</button>
              </div>
            </motion.section>
          </motion.div>
        )}
      </AnimatePresence>
      <AnimatePresence>
        {emailChangeOpen && (
          <motion.div
            className="confirm-dialog-backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            role="presentation"
            onClick={closeEmailChange}
          >
            <motion.section
              className="confirm-dialog email-change-dialog"
              role="dialog"
              aria-modal="true"
              aria-labelledby="email-change-title"
              onClick={(event) => event.stopPropagation()}
              initial={{ opacity: 0, y: 18, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 12, scale: 0.96 }}
              transition={{ type: 'spring', stiffness: 340, damping: 28 }}
            >
              <button className="confirm-dialog-close" type="button" onClick={closeEmailChange} title="닫기">
                <X size={17} aria-hidden />
              </button>
              <span className="confirm-dialog-icon"><Mail size={20} aria-hidden /></span>
              <div>
                <strong id="email-change-title">이메일 변경</strong>
                <p>새 이메일로 인증코드를 보내 확인한 뒤 변경합니다. 현재 이메일: {user.email}</p>
              </div>
              <form
                className="email-change-form"
                onSubmit={(event) => {
                  event.preventDefault();
                  if (emailChangeSent) {
                    submitEmailChange();
                  } else {
                    sendEmailChangeCode();
                  }
                }}
              >
                <label>새 이메일
                  <input
                    type="email"
                    value={emailChangeNew}
                    onChange={(event) => setEmailChangeNew(event.target.value)}
                    placeholder="new@example.com"
                    disabled={emailChangeLoading || emailChangeSent}
                    required
                  />
                </label>
                {emailChangeSent && (
                  <label>인증코드
                    <input
                      inputMode="numeric"
                      value={emailChangeCode}
                      onChange={(event) => setEmailChangeCode(event.target.value.replace(/\D/g, '').slice(0, EMAIL_CODE_LENGTH))}
                      placeholder="6자리 숫자"
                      maxLength={EMAIL_CODE_LENGTH}
                      disabled={emailChangeLoading}
                      autoFocus
                    />
                  </label>
                )}
                {emailChangeStatus && <small className="email-change-status">{emailChangeStatus}</small>}
                <div className={emailChangeSent ? 'confirm-dialog-actions' : 'confirm-dialog-actions single'}>
                  {!emailChangeSent ? (
                    <button type="submit" className="primary" disabled={emailChangeLoading || !emailChangeNew.trim()}>
                      {emailChangeLoading ? '전송 중' : '인증코드 받기'}
                    </button>
                  ) : (
                    <>
                      <button type="button" onClick={sendEmailChangeCode} disabled={emailChangeLoading}>재전송</button>
                      <button type="submit" className="primary" disabled={emailChangeLoading || emailChangeCode.length !== EMAIL_CODE_LENGTH}>
                        {emailChangeLoading ? '변경 중' : '이메일 변경'}
                      </button>
                    </>
                  )}
                </div>
              </form>
            </motion.section>
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence custom={tabDirection} initial={false}>
      {(activeTab === 'friends' || activeTab === 'settings') && (
      <motion.aside key={activeTab} className="side-pane" custom={tabDirection} variants={TAB_SLIDE} initial="enter" animate="center" exit="exit" transition={TAB_SLIDE_TRANSITION}>
        <section className="sidebar">
          <div className="sidebar-content">
            <header className="panel-header"><h2>{activeTab === 'friends' ? '친구' : '설정'}</h2></header>
            <div className="profile-card">
              <ProfileAvatar className="avatar" name={user.name} imageUrl={user.profileImageUrl} />
              <div>
                <h1>{user.name}</h1>
                <p>{user.email} · {user.provider}</p>
                {user.statusMessage && <small>{user.statusMessage}</small>}
              </div>
            </div>

            {activeTab === 'settings' && (<>
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

            <section className="panel-section notification-panel">
              <div className="section-title">
                <span>알림</span>
                <small>{notificationUnreadCount > 99 ? '99+' : notificationUnreadCount}</small>
              </div>
              <div className="push-status">
                {notificationUnreadCount > 0 ? <BellRing size={16} aria-hidden /> : <Bell size={16} aria-hidden />}
                <span>{inAppNotificationsEnabled ? '앱내 알림 켜짐' : '앱내 알림 꺼짐'}</span>
                <button type="button" onClick={toggleInAppNotifications}>
                  {inAppNotificationsEnabled ? '끄기' : '켜기'}
                </button>
                <button type="button" onClick={markAllNotificationsRead} disabled={notificationUnreadCount === 0}>읽음</button>
              </div>
              <div className="notification-list">
                {notifications.slice(0, 5).map((notification) => (
                  <button
                    key={notification.id}
                    type="button"
                    className={notification.read ? '' : 'unread'}
                    onClick={() => {
                      if (notification.targetRoomId) {
                        openRoom(notification.targetRoomId);
                      }
                    }}
                  >
                    <strong>{notification.title}</strong>
                    <span>{notification.body}</span>
                    <small>{new Date(notification.createdAt).toLocaleTimeString()}</small>
                  </button>
                ))}
                {notifications.length === 0 && <p className="empty-state">새 알림이 없습니다.</p>}
              </div>
            </section>

            {isAdmin && (
              <section className="panel-section dlt-panel">
                <div className="section-title">
                  <span>실패 메시지</span>
                  <small>{dltTopic || 'DLT'}</small>
                </div>
                <div className="dlt-toolbar">
                  <label>
                    조회 개수
                    <input
                      value={dltLimit}
                      type="number"
                      min={1}
                      max={100}
                      onChange={(event) => setDltLimit(Number(event.target.value))}
                    />
                  </label>
                  <button type="button" onClick={loadDltMessages} disabled={dltLoading}>
                    <RefreshCcw size={15} aria-hidden />
                    조회
                  </button>
                </div>
                <div className="dlt-list">
                  {dltMessages.map((message) => (
                    <label key={`${message.topic}-${message.partition}-${message.offset}`} className={dltSelectedIds.includes(message.messageId) ? 'dlt-item selected' : 'dlt-item'}>
                      <input
                        type="checkbox"
                        checked={dltSelectedIds.includes(message.messageId)}
                        onChange={() => toggleDltMessage(message.messageId)}
                      />
                      <span>
                        <strong>{message.roomName || message.roomId}</strong>
                        <small>{message.senderName || message.senderEmail} · offset {message.offset}</small>
                        <em>{message.messageId}</em>
                      </span>
                    </label>
                  ))}
                  {dltMessages.length === 0 && <p className="empty-state">조회된 실패 메시지가 없습니다.</p>}
                </div>
                <div className="dlt-actions">
                  <button type="button" onClick={() => replayDltMessages(true)} disabled={dltLoading || dltMessages.length === 0}>
                    <CheckCircle2 size={15} aria-hidden />
                    미리 확인
                  </button>
                  <button type="button" onClick={() => replayDltMessages(false)} disabled={dltLoading || dltMessages.length === 0}>
                    <Send size={15} aria-hidden />
                    재처리
                  </button>
                </div>
                {dltResult && (
                  <div className="dlt-result">
                    <strong>{dltResult.dryRun ? '미리 확인 완료' : '재처리 완료'}</strong>
                    <span>{dltResult.scannedCount}개 스캔 · {dltResult.replayedCount}개 대상</span>
                    <small>{dltResult.sourceTopic} → {dltResult.targetTopic}</small>
                  </div>
                )}
              </section>
            )}

            {myProfile && myProfile.history.length > 0 && (
              <section className="panel-section compact-history">
                <div className="section-title">
                  <span>내 프로필 히스토리</span>
                  <small>{myProfile.history.length}</small>
                </div>
                <ProfileHistoryList history={myProfile.history.slice(0, 3)} />
              </section>
            )}

            <section className="panel-section settings-group">
              <div className="section-title"><span>계정 정보</span></div>
              <div className="settings-row">
                <span className="settings-row-label"><AtSign size={16} aria-hidden />이메일</span>
                <span className="settings-row-value">{user.email}</span>
                <button type="button" className="settings-row-action" onClick={openEmailChange} disabled={loading}>변경</button>
              </div>
              <div className="settings-row static">
                <span className="settings-row-label"><KeyRound size={16} aria-hidden />로그인 방식</span>
                <span className="settings-row-value">{user.provider}</span>
              </div>
            </section>

            <section className="panel-section settings-group danger-zone">
              <div className="section-title"><span>계정 관리</span></div>
              <button type="button" className="settings-action" onClick={logout} disabled={loading}>
                <LogOut size={17} aria-hidden /><span>로그아웃</span>
              </button>
              <button type="button" className="settings-action danger" onClick={() => setWithdrawConfirmOpen(true)} disabled={loading}>
                <Trash2 size={17} aria-hidden /><span>회원 탈퇴</span>
              </button>
            </section>
            </>)}

            {activeTab === 'friends' && (<>
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
                  <React.Fragment key={contact.email}>
                    <button className={selectedProfile?.email === contact.email ? 'active' : ''} onClick={() => openContactProfile(contact)} disabled={loading}>
                      <div className="presence-avatar">
                        <ProfileAvatar className="mini-avatar" name={contact.name} imageUrl={contact.profileImageUrl} />
                        <i className={contact.online ? 'presence-dot online' : 'presence-dot'} aria-hidden />
                      </div>
                      <span>
                        <strong>{contact.name}</strong>
                        <small>{contact.statusMessage || (contact.online ? '온라인' : contact.email)}</small>
                      </span>
                      <ChevronRight size={16} aria-hidden />
                    </button>
                    {selectedProfile?.email === contact.email && (
                      <section className="contact-profile-inline">
                        <div className="profile-card slim">
                          <ProfileAvatar className="avatar" name={selectedProfile.name} imageUrl={selectedProfile.profileImageUrl} />
                          <div>
                            <h1>{selectedProfile.name}</h1>
                            <p>{selectedProfile.email}</p>
                            {selectedProfile.statusMessage && <small>{selectedProfile.statusMessage}</small>}
                          </div>
                        </div>
                        <button className="primary-button" type="button" onClick={() => openDirectFromProfile(selectedProfile)} disabled={loading}>
                          <MessageCircle size={17} aria-hidden />메시지
                        </button>
                        <ProfileHistoryList history={selectedProfile.history} />
                      </section>
                    )}
                  </React.Fragment>
                ))}
              </div>
            </section>

            <section className="panel-section">
              <div className="section-title">
                <span>1:1 대화</span>
                <small>{directRooms.length}</small>
              </div>
              <RoomList
                rooms={directRooms}
                selectedRoomId={selectedRoomId}
                onSelect={openRoom}
                onTogglePinned={(room) => updateRoomPreference(room, { pinned: !room.pinned })}
                onToggleMuted={(room) => updateRoomPreference(room, { muted: !room.muted })}
              />
            </section>
            </>)}
          </div>
        </section>
      </motion.aside>
      )}

      {activeTab === 'news' && (
        <motion.section key="news" className="tab-panel news-panel" custom={tabDirection} variants={TAB_SLIDE} initial="enter" animate="center" exit="exit" transition={TAB_SLIDE_TRANSITION}>
          <NewsFeed onShare={openShareModal} />
        </motion.section>
      )}

      {activeTab === 'shopping' && (
        <motion.section key="shopping" className="tab-panel shop-panel" custom={tabDirection} variants={TAB_SLIDE} initial="enter" animate="center" exit="exit" transition={TAB_SLIDE_TRANSITION}>
          <ShoppingFeed
            onAddToCart={addToShoppingCart}
            onOpenCart={() => { loadShoppingCart(); setCartOpen(true); }}
            cartCount={shoppingCart?.totalCount ?? 0}
            addingId={cartAddingId}
          />
        </motion.section>
      )}
      </AnimatePresence>

      <AnimatePresence>
        {cartOpen && (
          <motion.div
            className="cart-drawer-backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            role="presentation"
            onClick={() => setCartOpen(false)}
          >
            <motion.aside
              className="cart-drawer"
              role="dialog"
              aria-modal="true"
              aria-label="장바구니"
              onClick={(event) => event.stopPropagation()}
              initial={{ x: '100%' }}
              animate={{ x: 0 }}
              exit={{ x: '100%' }}
              transition={{ type: 'spring', stiffness: 320, damping: 34 }}
            >
              <header className="cart-drawer-head">
                <strong><ShoppingCart size={18} aria-hidden /> 장바구니</strong>
                <button type="button" className="cart-drawer-close" onClick={() => setCartOpen(false)} title="닫기"><X size={18} aria-hidden /></button>
              </header>

              {(!shoppingCart || shoppingCart.items.length === 0) ? (
                <div className="cart-empty">
                  <ShoppingBag size={26} aria-hidden />
                  <p>장바구니가 비어 있어요.</p>
                </div>
              ) : (
                <div className="cart-items">
                  {shoppingCart.items.map((item) => (
                    <div className="cart-item" key={item.id}>
                      <a className="cart-item-thumb" href={item.link} target="_blank" rel="noreferrer noopener" title={item.title}>
                        {item.image ? <img src={item.image} alt="" loading="lazy" referrerPolicy="no-referrer" /> : <ShoppingBag size={18} aria-hidden />}
                      </a>
                      <div className="cart-item-body">
                        <a className="cart-item-title" href={item.link} target="_blank" rel="noreferrer noopener">{item.title}</a>
                        <span className="cart-item-mall">{item.mallName || item.brand || '네이버쇼핑'}</span>
                        <strong className="cart-item-price">{(item.price * item.quantity).toLocaleString('ko-KR')}원</strong>
                      </div>
                      <div className="cart-item-actions">
                        <div className="cart-qty">
                          <button type="button" onClick={() => updateCartQuantity(item.id, item.quantity - 1)} aria-label="수량 감소">−</button>
                          <span>{item.quantity}</span>
                          <button type="button" onClick={() => updateCartQuantity(item.id, item.quantity + 1)} aria-label="수량 증가">+</button>
                        </div>
                        <button type="button" className="cart-item-remove" onClick={() => removeCartItem(item.id)} title="삭제"><Trash2 size={15} aria-hidden /></button>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              <footer className="cart-drawer-foot">
                <div className="cart-total">
                  <span>합계</span>
                  <strong>{(shoppingCart?.totalPrice ?? 0).toLocaleString('ko-KR')}원</strong>
                </div>
                <p className="cart-note">상품을 누르면 네이버에서 구매할 수 있어요.</p>
                <div className="cart-foot-actions">
                  <button type="button" onClick={clearShoppingCart} disabled={!shoppingCart || shoppingCart.items.length === 0}>비우기</button>
                  <button type="button" className="primary" onClick={() => setCartOpen(false)}>계속 쇼핑</button>
                </div>
              </footer>
            </motion.aside>
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {selectedRoomId && (
          <motion.section
            key="conversation"
            className="conversation"
            layout
            initial={{ opacity: 0, x: 18 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -18 }}
            transition={{ type: 'spring', stiffness: 230, damping: 28 }}
            onAnimationComplete={handleConversationEntered}
          >
            <header className="conversation-header">
              <div className="header-navigation">
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
                    {readReceipts.length > 0 && <span>{readReceipts.filter((receipt) => receipt.lastReadAt).length}명 읽음 상태</span>}
                  </div>
                </div>
              <div className="header-actions">
                <span className={connected ? 'status-pill online' : 'status-pill'}>
                  <CheckCircle2 size={15} aria-hidden />
                  {connected ? '실시간 연결' : '연결 대기'}
                </span>
                <span className="tip-wrap" data-tip="서비스 준비중입니다">
                  <button className="soft-action-button is-soon" onClick={() => setStatus('GPT 요약은 서비스 준비중입니다.')} type="button" aria-label="GPT 요약 (서비스 준비중)">
                    <Sparkles size={15} aria-hidden />GPT 요약
                  </button>
                </span>
                <button className="soft-action-button" onClick={clearRoomMessagesForMe} disabled={!selectedRoomId || messages.length === 0} type="button">대화 비우기</button>
                {selectedRoom?.type === 'GROUP' && <button className="soft-action-button" onClick={leaveSelectedRoom} disabled={loading} type="button">나가기</button>}
                <button className="ghost-icon-button" onClick={() => setRoomDeleteConfirmOpen(true)} disabled={!selectedRoomId || loading} title="채팅방 나가기"><Trash2 size={17} aria-hidden /></button>
              </div>
            </header>

            <section className="participant-panel">
              <div className="participant-list">
                {roomParticipants.slice(0, 8).map((participant) => (
                  <span key={participant.email} className={participant.online ? 'participant-chip online' : 'participant-chip'}>
                    <ProfileAvatar className="tiny-avatar" name={participant.name} imageUrl={participant.profileImageUrl} />
                    <strong>{participant.name}</strong>
                    {participant.owner && <small>방장</small>}
                  </span>
                ))}
                {roomParticipants.length > 8 && <span className="participant-more">+{roomParticipants.length - 8}</span>}
              </div>
              {selectedRoom?.type === 'GROUP' && (
                <form className="invite-form" onSubmit={inviteParticipant}>
                  <select value={inviteEmail} onChange={(event) => setInviteEmail(event.target.value)} disabled={inviteOptions.length === 0}>
                    <option value="">친구 초대</option>
                    {inviteOptions.map((contact) => <option key={contact.email} value={contact.email}>{contact.name} · {contact.email}</option>)}
                  </select>
                  <button type="submit" disabled={!inviteEmail || loading} title="친구 초대"><UserPlus size={16} aria-hidden /></button>
                </form>
              )}
            </section>

            <div
              className="message-list"
              ref={messageListRef}
              onScroll={handleMessageScroll}
              onWheel={handleMessageWheel}
              onTouchStart={handleMessageTouchStart}
              onTouchMove={handleMessageTouchMove}
              onTouchEnd={handleMessageTouchEnd}
              onTouchCancel={handleMessageTouchEnd}
            >
              {pullRefreshVisible && (
                <div
                  className={messagesRefreshing ? 'pull-refresh-indicator loading' : 'pull-refresh-indicator'}
                  style={{
                    '--pull-distance': `${messagesRefreshing ? 58 : pullRefreshDistance}px`,
                    '--pull-progress': String(pullRefreshProgress)
                  } as React.CSSProperties}
                  aria-live="polite"
                >
                  <span><RefreshCcw size={15} aria-hidden /></span>
                  <strong>{messagesRefreshing ? '새 대화 불러오는 중' : pullRefreshProgress >= 1 ? '놓으면 새로고침' : '아래로 당겨 새로고침'}</strong>
                </div>
              )}
              {conversationSummary && (
                <section className="summary-card">
                  <div className="section-title">
                    <span>대화 요약</span>
                    <small>{conversationSummary.model} · {conversationSummary.messageCount}개</small>
                  </div>
                  <p>{conversationSummary.summary}</p>
                </section>
              )}
              {messages.length === 0 && <p className="empty-state">아직 메시지가 없습니다.</p>}
              {messages.map((message) => {
                const linkUrl = message.deletedForEveryone ? null : firstMessageUrl(message.content);
                return (
                <ContextMenu.Root key={message.id}>
                  <ContextMenu.Trigger asChild>
                    <motion.div
                      className={message.senderEmail === user.email ? 'chat-row mine' : 'chat-row'}
                      initial={{ opacity: 0, y: 8 }}
                      animate={{ opacity: 1, y: 0 }}
                    >
                      {message.senderEmail !== user.email && <strong className="bubble-sender">{message.senderName}</strong>}
                      {editingMessageId === message.id ? (
                        <div className="message-edit-panel">
                          <textarea value={editingDraft} onChange={(event) => setEditingDraft(event.target.value)} maxLength={2000} autoFocus />
                          <div>
                            <button type="button" onClick={cancelEditingMessage}>취소</button>
                            <button type="button" onClick={() => editMessage(message)} disabled={!editingDraft.trim()}>저장</button>
                          </div>
                        </div>
                      ) : (
                        <div className="bubble-cluster">
                          <article className="chat-bubble">
                            {message.deletedForEveryone ? (
                              <p className="deleted-message">삭제된 메시지입니다.</p>
                            ) : (
                              <>
                                {message.replyToMessageId && (
                                  <div className="reply-preview">
                                    <strong>{message.replyToSenderName ?? '메시지'}</strong>
                                    <span>{message.replyToContent ?? '원본 메시지'}</span>
                                  </div>
                                )}
                                {message.attachmentUrl && (
                                  <a className="message-media" href={message.attachmentUrl} target="_blank" rel="noreferrer">
                                    <img src={message.attachmentUrl} alt={message.attachmentName ?? '첨부 이미지'} />
                                  </a>
                                )}
                                {message.content && <p>{message.content}</p>}
                                {linkUrl && <MessageLinkPreview url={linkUrl} />}
                                {message.editedAt && <span className="edited-label">수정됨</span>}
                                {message.reactions.length > 0 && (
                                  <div className="reaction-strip" aria-label="메시지 반응">
                                    {message.reactions.map((reaction) => (
                                      <button
                                        key={`${message.id}-${reaction.emoji}`}
                                        type="button"
                                        className={reaction.reactedByMe ? 'active' : ''}
                                        onClick={() => toggleMessageReaction(message, reaction.emoji)}
                                        title={`${reaction.emoji} ${reaction.count}명`}
                                      >
                                        <span>{reaction.emoji}</span>
                                        <strong>{reaction.count}</strong>
                                      </button>
                                    ))}
                                  </div>
                                )}
                              </>
                            )}
                          </article>
                          <time className="bubble-time">{new Date(message.createdAt).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' })}</time>
                          {!message.deletedForEveryone && (
                            <div className="message-actions">
                              <button className="msg-quick-btn" type="button" title="답장" aria-label="답장" onClick={() => setReplyTarget(message)}>
                                <Reply size={16} aria-hidden />
                              </button>
                              <Popover.Root>
                                <Popover.Trigger asChild>
                                  <button className="msg-quick-btn" type="button" title="공감" aria-label="공감 남기기">
                                    <SmilePlus size={16} aria-hidden />
                                  </button>
                                </Popover.Trigger>
                                <Popover.Portal>
                                  <Popover.Content className="reaction-picker" side="top" align="center" sideOffset={8} collisionPadding={12} onOpenAutoFocus={(event) => event.preventDefault()}>
                                    {QUICK_REACTIONS.map((emoji) => (
                                      <button
                                        key={`${message.id}-pick-${emoji}`}
                                        type="button"
                                        className={message.reactions.some((reaction) => reaction.emoji === emoji && reaction.reactedByMe) ? 'active' : ''}
                                        onClick={() => toggleMessageReaction(message, emoji)}
                                        title={`${emoji} 반응`}
                                      >
                                        {emoji}
                                      </button>
                                    ))}
                                  </Popover.Content>
                                </Popover.Portal>
                              </Popover.Root>
                            </div>
                          )}
                        </div>
                      )}
                    </motion.div>
                  </ContextMenu.Trigger>
                  {!message.deletedForEveryone && editingMessageId !== message.id && (
                    <ContextMenu.Portal>
                      <ContextMenu.Content className="message-context-menu" collisionPadding={12}>
                        <div className="context-reactions" aria-label="빠른 반응">
                          {QUICK_REACTIONS.map((emoji) => (
                            <ContextMenu.Item
                              key={`${message.id}-ctx-${emoji}`}
                              className={message.reactions.some((reaction) => reaction.emoji === emoji && reaction.reactedByMe) ? 'ctx-emoji active' : 'ctx-emoji'}
                              onSelect={() => toggleMessageReaction(message, emoji)}
                            >
                              {emoji}
                            </ContextMenu.Item>
                          ))}
                        </div>
                        <ContextMenu.Separator className="context-sep" />
                        {message.senderEmail === user.email && (
                          <div className="context-read-state">{deliveryStatusLabel(message)}</div>
                        )}
                        {message.senderEmail === user.email && message.content && (
                          <ContextMenu.Item className="ctx-item" onSelect={() => startEditingMessage(message)}><Pencil size={14} aria-hidden />수정</ContextMenu.Item>
                        )}
                        <ContextMenu.Item className="ctx-item" onSelect={() => setReplyTarget(message)}><Reply size={14} aria-hidden />답장</ContextMenu.Item>
                        <ContextMenu.Item className="ctx-item" onSelect={() => hideMessageForMe(message)}>나에게 삭제</ContextMenu.Item>
                        {message.senderEmail === user.email && (
                          <ContextMenu.Item className="ctx-item danger" onSelect={() => deleteMessageForEveryone(message)}><Trash2 size={14} aria-hidden />모두에게 삭제</ContextMenu.Item>
                        )}
                      </ContextMenu.Content>
                    </ContextMenu.Portal>
                  )}
                </ContextMenu.Root>
                );
              })}
            </div>

            <form className="composer" onSubmit={sendMessage}>
              {replyTarget && (
                <div className="composer-reply-preview">
                  <span>
                    <strong>{replyTarget.senderName}</strong>
                    <small>{replyTarget.content || replyTarget.attachmentName || '첨부 메시지'}</small>
                  </span>
                  <button type="button" onClick={() => setReplyTarget(null)} title="답장 취소"><X size={16} aria-hidden /></button>
                </div>
              )}
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
        )}
      </AnimatePresence>
      <AnimatePresence custom={tabDirection} initial={false}>
        {activeTab === 'chats' && (
          <motion.section
            key="rooms"
            className="room-directory"
            ref={roomDirectoryRef}
            custom={tabDirection}
            variants={TAB_SLIDE}
            initial="enter"
            animate="center"
            exit="exit"
            transition={TAB_SLIDE_TRANSITION}
          >
            <motion.span
              className="scroll-progress-bar"
              style={{ scaleX: shouldReduceMotion ? directoryScrollYProgress : directoryProgressScaleX }}
              aria-hidden
            />
            <motion.header
              className="directory-header chat-head"
              style={{
                y: shouldReduceMotion ? 0 : directoryHeaderY,
                opacity: shouldReduceMotion ? 1 : directoryHeaderOpacity
              }}
            >
              <div>
                <p className="eyebrow">Kafka Talk</p>
                <h2>채팅</h2>
              </div>
              <div className="chat-head-actions">
                <button
                  type="button"
                  className={chatSearchOpen ? 'chat-icon-button active' : 'chat-icon-button'}
                  onClick={() => setChatSearchOpen((open) => !open)}
                  title={chatSearchOpen ? '검색 닫기' : '검색'}
                  aria-label={chatSearchOpen ? '검색 닫기' : '검색'}
                >
                  {chatSearchOpen ? <X size={19} aria-hidden /> : <Search size={19} aria-hidden />}
                </button>
                <button type="button" className="chat-create-button" onClick={() => openCreateChat('group')} title="새 채팅 만들기" aria-label="새 채팅 만들기">
                  <Plus size={20} aria-hidden />
                </button>
              </div>
            </motion.header>

            {chatSearchOpen ? (
              <div className="chat-search-panel">
                <form className="search-row compact chat-search-input" onSubmit={searchMessages}>
                  <Search size={17} aria-hidden />
                  <input
                    value={messageQuery}
                    onChange={(event) => setMessageQuery(event.target.value)}
                    placeholder="채팅방 또는 대화 내용 검색"
                    autoFocus
                  />
                  {messageQuery && (
                    <button type="button" className="search-clear" onClick={() => setMessageQuery('')} title="지우기">
                      <X size={15} aria-hidden />
                    </button>
                  )}
                </form>
                <SuggestionList suggestions={messageSuggestions} onSelect={chooseMessageSuggestion} />

                {!chatSearchTerm ? (
                  <p className="empty-state">채팅방 이름이나 대화 내용을 검색해 보세요.</p>
                ) : (
                  <>
                    <div className="section-title"><span>채팅방</span><small>{roomSearchResults.length}</small></div>
                    {roomSearchResults.length === 0 ? (
                      <p className="empty-state">일치하는 채팅방이 없어요.</p>
                    ) : (
                      <div className="room-list">
                        {roomSearchResults.map((room) => (
                          <article key={room.id} className={room.id === selectedRoomId ? 'room-item active' : 'room-item'}>
                            <button type="button" className="room-main-button" onClick={() => openRoom(room.id)}>
                              {room.type === 'DIRECT' ? <AtSign size={16} aria-hidden /> : <Hash size={16} aria-hidden />}
                              <span>
                                <strong>{room.name}</strong>
                                <small>{room.type === 'DIRECT' ? '개인 메시지' : `${room.participantCount}명 · ${room.description || '그룹 채팅'}`}</small>
                              </span>
                            </button>
                            {room.unreadCount > 0 && <i className="unread-badge">{room.unreadCount > 99 ? '99+' : room.unreadCount}</i>}
                          </article>
                        ))}
                      </div>
                    )}

                    <div className="section-title"><span>메시지</span><small>Elastic</small></div>
                    <div className="search-results">
                      {searchResults.map((message) => (
                        <button key={message.id} onClick={() => openRoom(message.roomId)}>
                          <span>{message.roomName}</span>
                          <strong>{message.deletedForEveryone ? '삭제된 메시지입니다.' : message.content || message.attachmentName || '첨부 메시지'}</strong>
                          <small>{message.senderName} · {new Date(message.createdAt).toLocaleString()}</small>
                        </button>
                      ))}
                      {searchResults.length === 0 && <p className="empty-state">Enter로 대화 내용을 검색하세요.</p>}
                    </div>
                  </>
                )}
              </div>
            ) : (
              <>
                <div className="chat-filterbar" role="tablist" aria-label="채팅 필터">
                  <button type="button" role="tab" aria-selected={chatFilter === 'all'} className={chatFilter === 'all' ? 'active' : ''} onClick={() => setChatFilter('all')}>
                    전체 <small>{rooms.length}</small>
                  </button>
                  <button type="button" role="tab" aria-selected={chatFilter === 'unread'} className={chatFilter === 'unread' ? 'active' : ''} onClick={() => setChatFilter('unread')}>
                    안읽음 {unreadRoomCount > 0 && <small className="count">{unreadRoomCount}</small>}
                  </button>
                </div>

                <div className="chat-room-list">
                  {visibleChatRooms.length === 0 ? (
                    <p className="empty-state">
                      {chatFilter === 'unread'
                        ? '안 읽은 채팅이 없어요.'
                        : '아직 참여한 채팅이 없어요. 오른쪽 위 + 버튼으로 새 채팅을 시작해보세요.'}
                    </p>
                  ) : (
                    <RoomList
                      rooms={visibleChatRooms}
                      selectedRoomId={selectedRoomId}
                      onSelect={openRoom}
                      onTogglePinned={(room) => updateRoomPreference(room, { pinned: !room.pinned })}
                      onToggleMuted={(room) => updateRoomPreference(room, { muted: !room.muted })}
                    />
                  )}
                </div>
              </>
            )}

            {status && <p className="notice">{status}</p>}
          </motion.section>
        )}
      </AnimatePresence>

      {!selectedRoomId && (
        <nav className="tab-bar" aria-label="주요 탭">
          <button type="button" className={activeTab === 'friends' ? 'active' : ''} onClick={() => switchTab('friends')}>
            <Users size={22} aria-hidden />
            <span>친구</span>
          </button>
          <button type="button" className={activeTab === 'chats' ? 'active' : ''} onClick={() => switchTab('chats')}>
            <MessageCircle size={22} aria-hidden />
            <span>채팅</span>
            {totalUnread > 0 && <i className="tab-badge">{totalUnread > 99 ? '99+' : totalUnread}</i>}
          </button>
          <button type="button" className={activeTab === 'news' ? 'active' : ''} onClick={() => switchTab('news')}>
            <Newspaper size={22} aria-hidden />
            <span>뉴스</span>
          </button>
          <button type="button" className={activeTab === 'shopping' ? 'active' : ''} onClick={() => { switchTab('shopping'); loadShoppingCart(); }}>
            <ShoppingBag size={22} aria-hidden />
            <span>쇼핑</span>
            {(shoppingCart?.totalCount ?? 0) > 0 && <i className="tab-badge">{(shoppingCart?.totalCount ?? 0) > 99 ? '99+' : shoppingCart?.totalCount}</i>}
          </button>
          <button type="button" className={activeTab === 'settings' ? 'active' : ''} onClick={() => switchTab('settings')}>
            <Settings size={22} aria-hidden />
            <span>설정</span>
          </button>
        </nav>
      )}
    </div>
  );
}

function NotificationToastStack({
  toasts,
  onOpen,
  onDismiss
}: {
  toasts: AppToast[];
  onOpen: (toast: AppToast) => void;
  onDismiss: (toastId: string) => void;
}) {
  return (
    <div className="app-toast-stack" aria-live="polite" aria-atomic="false">
      <AnimatePresence initial={false}>
        {toasts.map((toast) => (
          <motion.article
            key={toast.id}
            className="app-toast"
            layout
            initial={{ opacity: 0, y: 34, scale: 0.96 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 24, scale: 0.96 }}
            transition={{ type: 'spring', stiffness: 360, damping: 30, mass: 0.8 }}
          >
            <button
              type="button"
              className="app-toast-content"
              onClick={() => onOpen(toast)}
              aria-label={`${toast.notification.title} 알림 열기`}
            >
              <span className="app-toast-icon"><BellRing size={18} aria-hidden /></span>
              <span className="app-toast-copy">
                <strong>{toast.notification.title || '새 알림'}</strong>
                <small>{toast.notification.body || '새 메시지가 도착했습니다.'}</small>
              </span>
            </button>
            <button
              type="button"
              className="app-toast-close"
              onClick={() => onDismiss(toast.id)}
              title="알림 닫기"
            >
              <X size={16} aria-hidden />
            </button>
          </motion.article>
        ))}
      </AnimatePresence>
    </div>
  );
}

function ScrollRevealSection({
  children,
  className,
  delay,
  disabled
}: {
  children: React.ReactNode;
  className: string;
  delay: number;
  disabled: boolean;
}) {
  if (disabled) {
    return <section className={className}>{children}</section>;
  }

  return (
    <motion.section
      className={className}
      initial={{ opacity: 0, y: 22, scale: 0.985 }}
      whileInView={{ opacity: 1, y: 0, scale: 1 }}
      viewport={{ once: false, amount: 0.22 }}
      transition={{ type: 'spring', stiffness: 210, damping: 26, delay }}
    >
      {children}
    </motion.section>
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

function ProfileAvatar({
  className,
  name,
  imageUrl
}: {
  className: string;
  name: string;
  imageUrl: string | null;
}) {
  const trimmed = name.trim();
  const initials = (trimmed.slice(0, 2) || '?').toUpperCase();
  // 이름에서 안정적인 색상(hue)을 만들어 사람마다 다른 그라데이션 아바타를 준다.
  let hue = 0;
  for (let index = 0; index < trimmed.length; index += 1) {
    hue = (hue * 31 + trimmed.charCodeAt(index)) % 360;
  }
  return (
    <div
      className={`${className} profile-avatar${imageUrl ? '' : ' is-initial'}`}
      style={imageUrl ? undefined : ({ '--avatar-hue': hue } as React.CSSProperties)}
    >
      {imageUrl ? <img src={imageUrl} alt={`${name} 프로필`} /> : <span aria-hidden>{initials}</span>}
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
  onSelect,
  onTogglePinned,
  onToggleMuted
}: {
  rooms: ChatRoom[];
  selectedRoomId: string;
  onSelect: (roomId: string) => void;
  onTogglePinned: (room: ChatRoom) => void;
  onToggleMuted: (room: ChatRoom) => void;
}) {
  const shouldReduceMotion = useReducedMotion();

  if (rooms.length === 0) {
    return <p className="empty-state">아직 대화가 없습니다.</p>;
  }

  if (shouldReduceMotion) {
    return (
      <div className="room-list">
        {rooms.map((room) => (
          <article key={room.id} className={room.id === selectedRoomId ? 'room-item active' : 'room-item'}>
            <button type="button" className="room-main-button" onClick={() => onSelect(room.id)}>
              {room.type === 'DIRECT' ? <AtSign size={16} aria-hidden /> : <Hash size={16} aria-hidden />}
              <span>
                <strong>{room.name}</strong>
                <small>{room.type === 'DIRECT' ? '개인 메시지' : `${room.participantCount}명 · ${room.description || '그룹 채팅'}`}</small>
              </span>
            </button>
            <div className="room-meta-actions">
              {room.unreadCount > 0 && <i className="unread-badge" aria-label={`${room.unreadCount}개의 읽지 않은 메시지`}>{room.unreadCount > 99 ? '99+' : room.unreadCount}</i>}
              <button type="button" className={room.pinned ? 'room-preference-button active' : 'room-preference-button'} onClick={() => onTogglePinned(room)} title={room.pinned ? '채팅방 고정 해제' : '채팅방 고정'}>
                <Pin size={14} aria-hidden />
              </button>
              <button type="button" className={room.muted ? 'room-preference-button active' : 'room-preference-button'} onClick={() => onToggleMuted(room)} title={room.muted ? '알림 켜기' : '알림 끄기'}>
                {room.muted ? <BellOff size={14} aria-hidden /> : <Bell size={14} aria-hidden />}
              </button>
            </div>
          </article>
        ))}
      </div>
    );
  }

  return (
    <LayoutGroup>
      <div className="room-list">
      {rooms.map((room) => (
        <motion.article
          key={room.id}
          className={room.id === selectedRoomId ? 'room-item active' : 'room-item'}
          initial={{ opacity: 0, y: 10 }}
          whileInView={{ opacity: 1, y: 0 }}
          whileHover={{ y: -2, scale: 1.012 }}
          whileTap={{ scale: 0.988 }}
          viewport={{ once: false, amount: 0.35 }}
          transition={{ type: 'spring', stiffness: 320, damping: 24, mass: 0.72 }}
        >
          {room.id === selectedRoomId && (
            <motion.span
              className="room-active-highlight"
              layoutId="room-active-highlight"
              transition={{ type: 'spring', stiffness: 420, damping: 34, mass: 0.82 }}
              aria-hidden
            />
          )}
          <span className="room-hover-aura" aria-hidden />
          <button type="button" className="room-main-button" onClick={() => onSelect(room.id)}>
            <span className="room-kind-icon">
              {room.type === 'DIRECT' ? <AtSign size={16} aria-hidden /> : <Hash size={16} aria-hidden />}
            </span>
            <span>
              <strong>{room.name}</strong>
              <small>{room.type === 'DIRECT' ? '개인 메시지' : `${room.participantCount}명 · ${room.description || '그룹 채팅'}`}</small>
            </span>
          </button>
          <div className="room-meta-actions">
            {room.unreadCount > 0 && <i className="unread-badge" aria-label={`${room.unreadCount}개의 읽지 않은 메시지`}>{room.unreadCount > 99 ? '99+' : room.unreadCount}</i>}
            <motion.button type="button" className={room.pinned ? 'room-preference-button active' : 'room-preference-button'} onClick={() => onTogglePinned(room)} title={room.pinned ? '채팅방 고정 해제' : '채팅방 고정'} whileHover={{ rotate: -8, scale: 1.08 }} whileTap={{ scale: 0.9 }}>
              <Pin size={14} aria-hidden />
            </motion.button>
            <motion.button type="button" className={room.muted ? 'room-preference-button active' : 'room-preference-button'} onClick={() => onToggleMuted(room)} title={room.muted ? '알림 켜기' : '알림 끄기'} whileHover={{ rotate: 8, scale: 1.08 }} whileTap={{ scale: 0.9 }}>
              {room.muted ? <BellOff size={14} aria-hidden /> : <Bell size={14} aria-hidden />}
            </motion.button>
          </div>
        </motion.article>
      ))}
      </div>
    </LayoutGroup>
  );
}

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
