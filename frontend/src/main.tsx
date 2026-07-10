import React, { ClipboardEvent, FormEvent, KeyboardEvent, TouchEvent, UIEvent, WheelEvent, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Client, IMessage } from '@stomp/stompjs';
import { AnimatePresence, LayoutGroup, motion, useReducedMotion, useScroll, useSpring, useTransform } from 'motion/react';
import { useVirtualizer } from '@tanstack/react-virtual';
import {
  ArrowLeft,
  AtSign,
  Bell,
  BellOff,
  BellRing,
  Camera,
  CheckCircle2,
  ChevronRight,
  ChevronLeft,
  Download,
  Copy,
  Hash,
  Image as ImageIcon,
  Film,
  File as FileIcon,
  Gamepad2,
  KeyRound,
  Link2,
  LogOut,
  Mail,
  MessageCircle,
  MessagesSquare,
  MessageSquareDashed,
  MapPin,
  Database,
  Reply,
  SmilePlus,
  Smile,
  Paperclip,
  Pin,
  Plus,
  Pencil,
  RefreshCcw,
  Save,
  Search,
  Send,
  Settings,
  Settings2,
  Share2,
  Swords,
  ShoppingBag,
  ShoppingCart,
  Sparkles,
  Trash2,
  UserPlus,
  Users,
  Newspaper,
  X,
  UserRound,
  Volume2,
  VolumeX,
  Sun,
  Moon,
  Monitor
} from 'lucide-react';
import * as Popover from '@radix-ui/react-popover';
import * as ContextMenu from '@radix-ui/react-context-menu';
import GameOverlay, { GameKey, GameScoreResult } from './games/GameOverlay';
import GameMatchCard, { GameMatchResponse } from './games/GameMatchCard';
import './styles.css';

/** 사람이 읽기 쉬운 파일 크기(파일 첨부 칩용). */
function formatBytes(bytes: number): string {
  if (!bytes || bytes <= 0) return '';
  const units = ['B', 'KB', 'MB', 'GB'];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${unit > 0 && value < 10 ? value.toFixed(1) : Math.round(value)}${units[unit]}`;
}

import NewsFeed, { NewsItem } from './NewsFeed';
import ShoppingFeed, { ShoppingProduct } from './ShoppingFeed';
import { MessageLinkPreview, firstMessageUrl } from './LinkPreview';
import WelcomeLanding from './WelcomeLanding';
import { playSound, isSoundEnabled, setSoundEnabled } from './sound';
import { Theme, loadTheme, applyTheme, saveThemeLocal, watchSystemTheme } from './theme';

// 앱 마운트 전 테마를 먼저 적용해 깜빡임(FOUC)을 줄인다.
applyTheme(loadTheme());

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
  theme?: Theme;
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
// 메시지 시간 포맷터는 한 번만 생성(렌더마다 메시지 수만큼 Intl 생성 방지)
const MESSAGE_TIME_FORMAT = new Intl.DateTimeFormat('ko-KR', { hour: 'numeric', minute: '2-digit' });

// 배송 상태 라벨(순수 함수) — App 밖 모듈 스코프에 둬 행마다 prop으로 넘기지 않고
// MessageRow 내부에서 직접 계산한다.
function deliveryStatusLabel(message: ChatMessage) {
  if (message.readCount > 0 || message.deliveryStatus === 'READ') {
    return `읽음 ${Math.max(message.readCount, 1)}`;
  }
  if (message.deliveredCount > 0 || message.deliveryStatus === 'DELIVERED') {
    return `전달됨 ${Math.max(message.deliveredCount, 1)}`;
  }
  return '전송됨';
}

// 반응 배열이 렌더에 영향 주는 필드 기준으로 같은지 판정(withReadCounts의 참조 보존용).
function reactionsShallowEqual(a: MessageReaction[], b: MessageReaction[]) {
  if (a === b) return true;
  if (a.length !== b.length) return false;
  for (let index = 0; index < a.length; index += 1) {
    if (
      a[index].emoji !== b[index].emoji ||
      a[index].count !== b[index].count ||
      a[index].reactedByMe !== b[index].reactedByMe
    ) {
      return false;
    }
  }
  return true;
}

function samePresence(a: RoomPresence, b: RoomPresence) {
  return a.onlineUsers.length === b.onlineUsers.length
    && a.typingUsers.length === b.typingUsers.length
    && a.onlineUsers.every((user, index) => user === b.onlineUsers[index])
    && a.typingUsers.every((user, index) => user === b.typingUsers[index]);
}
// 컴포저 이모지 피커: 자주 쓰는 이모지 그리드
const COMPOSER_EMOJIS = [
  '😀', '😄', '😁', '😆', '😅', '😂', '🙂', '😉',
  '😊', '😍', '😘', '😗', '😎', '🤩', '🥳', '🤔',
  '😐', '😴', '😢', '😭', '😤', '😱', '😳', '🥺',
  '👍', '👎', '👏', '🙌', '🙏', '💪', '👌', '✌️',
  '❤️', '🧡', '💛', '💚', '💙', '💜', '🔥', '✨',
  '🎉', '🎊', '💯', '✅', '❓', '❗', '👀', '🚀'
];

const SAMPLE_USERS = [
  { email: 'user@example.com', name: '건우' },
  { email: 'minji@example.com', name: '민지' },
  { email: 'junho@example.com', name: '준호' },
  { email: 'seoyeon@example.com', name: '서연' },
  { email: 'hyejin@example.com', name: '혜진' }
];

// 알림 상대시간 (방금 전 / N분 전 / N시간 전 / 어제 / N일 전)
function formatNotifTime(iso: string) {
  const diff = Date.now() - new Date(iso).getTime();
  const min = Math.floor(diff / 60000);
  if (min < 1) return '방금 전';
  if (min < 60) return `${min}분 전`;
  const hour = Math.floor(min / 60);
  if (hour < 24) return `${hour}시간 전`;
  const day = Math.floor(hour / 24);
  return day === 1 ? '어제' : `${day}일 전`;
}

// 알림 종류별 아이콘 타일 색(디자인의 컬러 타일)
function notifTint(type: string): string {
  const t = (type || '').toUpperCase();
  if (t.includes('MENTION')) return 'notif-ico--blue';
  if (t.includes('FRIEND') || t.includes('INVITE')) return 'notif-ico--indigo';
  if (t.includes('GAME') || t.includes('MATCH') || t.includes('DUEL')) return 'notif-ico--pink';
  return 'notif-ico--green';
}

// 숫자 인증코드를 한 자리씩 칸에 나눠 입력받는 OTP 인풋. 자체 ref로 칸 이동/붙여넣기/
// 백스페이스 이동을 처리하고, 값은 부모의 문자열 하나로 제어된다.
function OtpInput({ length, value, onChange, autoFocus, disabled }: {
  length: number;
  value: string;
  onChange: (next: string) => void;
  autoFocus?: boolean;
  disabled?: boolean;
}) {
  const cellRefs = useRef<Array<HTMLInputElement | null>>([]);
  const digits = Array.from({ length }, (_, index) => value[index] ?? '');

  function focusCell(index: number) {
    const clamped = Math.min(Math.max(index, 0), length - 1);
    requestAnimationFrame(() => cellRefs.current[clamped]?.focus());
  }

  function commit(next: string, focusIndex?: number) {
    onChange(next.replace(/\D/g, '').slice(0, length));
    if (focusIndex !== undefined) focusCell(focusIndex);
  }

  function handleChange(index: number, raw: string) {
    const numeric = raw.replace(/\D/g, '');
    if (!numeric) {
      commit(`${value.slice(0, index)}${value.slice(index + 1)}`, index);
      return;
    }
    const next = digits.slice();
    numeric.slice(0, length - index).split('').forEach((digit, offset) => {
      next[index + offset] = digit;
    });
    commit(next.join(''), Math.min(index + numeric.length, length - 1));
  }

  function handleKeyDown(index: number, event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Backspace' && !digits[index] && index > 0) {
      event.preventDefault();
      commit(`${value.slice(0, index - 1)}${value.slice(index)}`, index - 1);
    } else if (event.key === 'ArrowLeft' && index > 0) {
      event.preventDefault();
      focusCell(index - 1);
    } else if (event.key === 'ArrowRight' && index < length - 1) {
      event.preventDefault();
      focusCell(index + 1);
    }
  }

  function handlePaste(event: ClipboardEvent<HTMLInputElement>) {
    event.preventDefault();
    const pasted = event.clipboardData.getData('text').replace(/\D/g, '').slice(0, length);
    commit(pasted, Math.min(pasted.length, length - 1));
  }

  return (
    <div className="otp-input" role="group" aria-label={`${length}자리 인증코드`}>
      {digits.map((digit, index) => (
        <input
          key={index}
          ref={(element) => { cellRefs.current[index] = element; }}
          className="otp-cell"
          value={digit}
          inputMode="numeric"
          autoComplete={index === 0 ? 'one-time-code' : 'off'}
          maxLength={1}
          disabled={disabled}
          autoFocus={autoFocus && index === 0}
          aria-label={`${index + 1}번째 자리`}
          onChange={(event) => handleChange(index, event.target.value)}
          onKeyDown={(event) => handleKeyDown(index, event)}
          onPaste={handlePaste}
          onFocus={(event) => event.target.select()}
        />
      ))}
    </div>
  );
}

function App() {
  const [mode, setMode] = useState<Mode>('login');
  // 회원가입 모달(동의 → 이메일 인증 → 비밀번호 → 완료)
  const [signupOpen, setSignupOpen] = useState(false);
  const [signupStep, setSignupStep] = useState(1);
  const [agreeTerms, setAgreeTerms] = useState(false);
  const [agreePrivacy, setAgreePrivacy] = useState(false);
  const [agreeMarketing, setAgreeMarketing] = useState(false);
  const [signupEmail, setSignupEmail] = useState('');
  const [signupCode, setSignupCode] = useState('');
  const [signupCodeSent, setSignupCodeSent] = useState(false);
  const [signupName, setSignupName] = useState('');
  const [signupPassword, setSignupPassword] = useState('');
  const [signupPasswordConfirm, setSignupPasswordConfirm] = useState('');
  const [signupResult, setSignupResult] = useState<AuthResponse | null>(null);
  const [signupBusy, setSignupBusy] = useState(false);
  const [signupError, setSignupError] = useState('');
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
  const lastTypingSentRef = useRef(0);
  const [roomQuery, setRoomQuery] = useState('');
  const [contactQuery, setContactQuery] = useState('');
  const [messageQuery, setMessageQuery] = useState('');
  const [chatFilter, setChatFilter] = useState<'all' | 'unread'>('all');
  const [createChatOpen, setCreateChatOpen] = useState(false);
  const [createChatMode, setCreateChatMode] = useState<'group' | 'direct'>('group');
  const [chatSearchOpen, setChatSearchOpen] = useState(false);
  const [notifCenterOpen, setNotifCenterOpen] = useState(false);
  const [searchResults, setSearchResults] = useState<ChatMessage[]>([]);
  const [roomSuggestions, setRoomSuggestions] = useState<SearchSuggestion[]>([]);
  const [messageSuggestions, setMessageSuggestions] = useState<SearchSuggestion[]>([]);
  const [presence, setPresence] = useState<RoomPresence>({ onlineUsers: [], typingUsers: [] });
  const [attachment, setAttachment] = useState<File | null>(null);
  const [attachmentPreview, setAttachmentPreview] = useState('');
  const attachPhotoRef = useRef<HTMLInputElement>(null);
  const attachVideoRef = useRef<HTMLInputElement>(null);
  const attachFileRef = useRef<HTMLInputElement>(null);
  const [gameOpen, setGameOpen] = useState(false);
  const [activeMatch, setActiveMatch] = useState<GameMatchResponse | null>(null);
  const [matchSession, setMatchSession] = useState<{ matchId: number | null; game: GameKey | null } | null>(null);
  const matchIdRef = useRef<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [connected, setConnected] = useState(false);
  const [activeTab, setActiveTab] = useState<HomeTab>('chats');
  const [tabDirection, setTabDirection] = useState(0);
  // 탭 전환: 이동 방향(순서 기준)을 기록해 슬라이드 방향 결정 후 탭 변경
  const switchTab = useCallback((next: HomeTab) => {
    if (next === activeTab) return;
    setTabDirection(TAB_ORDER.indexOf(next) >= TAB_ORDER.indexOf(activeTab) ? 1 : -1);
    setActiveTab(next);
    playSound('tab');
  }, [activeTab]);
  const [authStage, setAuthStage] = useState<'landing' | 'login'>('landing');
  const [conversationSummary, setConversationSummary] = useState<ConversationSummary | null>(null);
  const [notifications, setNotifications] = useState<UserNotification[]>([]);
  const [notificationTopic, setNotificationTopic] = useState('');
  const [notificationUnreadCount, setNotificationUnreadCount] = useState(0);
  const [soundOn, setSoundOn] = useState(isSoundEnabled);
  const toggleSound = useCallback(() => {
    setSoundOn((prev) => {
      const next = !prev;
      setSoundEnabled(next);
      return next;
    });
  }, []);
  const [theme, setThemeState] = useState<Theme>(loadTheme);
  // 테마 변경 시 <html data-theme> 적용 + 로컬 캐시
  useEffect(() => {
    applyTheme(theme);
    saveThemeLocal(theme);
  }, [theme]);
  // 'system'일 때 OS 다크 전환 실시간 반영
  useEffect(() => watchSystemTheme(() => theme), [theme]);
  // 로그인/부트스트랩 시 서버 프로필의 테마로 동기화
  useEffect(() => {
    if (user?.theme) setThemeState(user.theme);
  }, [user?.theme]);
  function changeTheme(next: Theme) {
    setThemeState(next);
    setUser((current) => (current ? { ...current, theme: next } : current));
    playSound('toggle');
    // 로그인 상태면 프로필(백엔드)에 저장해 기기 간 동기화
    if (token) {
      request<UserProfile>('/users/me/theme', { method: 'PATCH', body: JSON.stringify({ theme: next }) }).catch(() => undefined);
    }
  }
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
  const [roomMenuOpen, setRoomMenuOpen] = useState(false); // 채팅방 설정 드로어
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
  // 가상 리스트: 실제 스크롤 컨테이너는 .message-list, 이 spacer가 전체 높이를 차지하고
  // 화면에 보이는 행만 절대배치로 렌더한다.
  const virtualSpacerRef = useRef<HTMLDivElement | null>(null);
  const [messageListScrollMargin, setMessageListScrollMargin] = useState(0);
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
  // 현재 열려 있는 방 id를 렌더마다 최신값으로 유지 — loadMessages의 비동기 응답이
  // 도착했을 때 그새 방을 바꿨는지 판별해 stale 응답이 다른 방을 덮어쓰지 않게 한다.
  const selectedRoomIdRef = useRef(selectedRoomId);
  selectedRoomIdRef.current = selectedRoomId;
  const messagesRef = useRef(messages);
  messagesRef.current = messages;
  const [mediaViewer, setMediaViewer] = useState<{ images: ChatMessage[]; index: number } | null>(null);
  const openMediaViewer = useCallback((message: ChatMessage) => {
    const images = messagesRef.current.filter((m) => !m.deletedForEveryone && m.attachmentUrl && (m.attachmentType == null || m.attachmentType.startsWith('image/')));
    const index = Math.max(0, images.findIndex((m) => m.id === message.id));
    setMediaViewer({ images, index });
  }, []);
  // 인터벌/구독 effect가 이 값들 때문에 재생성되지 않도록 렌더마다 최신값을 ref에 보관해 읽는다.
  const contactQueryRef = useRef(contactQuery);
  contactQueryRef.current = contactQuery;
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
  const inviteOptions = useMemo(() => {
    // 렌더마다(입력 한 글자마다) O(연락처×참가자) 이중 필터 + toLowerCase가 돌던 것을
    // 참가자 이메일 Set으로 O(연락처+참가자)로 낮추고 useMemo로 고정.
    const participantEmails = new Set(roomParticipants.map((participant) => participant.email.toLowerCase()));
    return contacts.filter((contact) => !participantEmails.has(contact.email.toLowerCase()));
  }, [contacts, roomParticipants]);
  const isAdmin = user?.role === 'ADMIN';
  // 채팅방 설정 드로어의 "사진·파일·링크" — 로드된 메시지에서 첨부(이미지/파일)와 링크를 모은다.
  const roomAttachments = useMemo(() => {
    const photos: ChatMessage[] = [];
    const files: ChatMessage[] = [];
    const links: { id: string; url: string; label: string }[] = [];
    for (const message of messages) {
      if (message.deletedForEveryone) continue;
      if (message.attachmentUrl) {
        if (!message.attachmentType || message.attachmentType.startsWith('image/')) photos.push(message);
        else files.push(message);
      }
      const url = firstMessageUrl(message.content);
      if (url) links.push({ id: message.id, url, label: (message.content ?? '').replace(url, '').trim() || url });
    }
    return { photos, files, links };
  }, [messages]);
  const latestMessageId = messages[messages.length - 1]?.id ?? '';
  const emailCodeDigits = Array.from({ length: EMAIL_CODE_LENGTH }, (_, index) => code[index] ?? '');
  const pullRefreshProgress = messagesRefreshing ? 1 : Math.min(1, pullRefreshDistance / 58);
  const pullRefreshVisible = messagesRefreshing || pullRefreshDistance > 0;

  const title = useMemo(() => {
    if (mode === 'register') return '계정 만들기';
    if (mode === 'email') return '이메일 인증 로그인';
    return '다시 오신 걸 환영해요';
  }, [mode]);

  // 메시지 목록 가상 스크롤. 화면에 보이는 행 + overscan 만 DOM에 mount 되어
  // 방에 메시지가 많아도 렌더/레이아웃 비용이 일정하게 유지된다.
  // 행 높이는 가변(텍스트/이미지/반응)이라 measureElement로 실제 높이를 측정한다.
  const messageVirtualizer = useVirtualizer({
    count: messages.length,
    getScrollElement: () => messageListRef.current,
    estimateSize: () => 72,
    getItemKey: (index) => messages[index]?.id ?? index,
    overscan: 8,
    // spacer 위(패딩·풀리프레시·요약카드·빈상태)의 높이를 좌표계에서 보정.
    scrollMargin: messageListScrollMargin
  });

  // spacer 위 컨텐츠 높이가 바뀔 때만 scrollMargin을 다시 측정한다.
  useLayoutEffect(() => {
    const spacer = virtualSpacerRef.current;
    const nextMargin = spacer ? spacer.offsetTop : 0;
    setMessageListScrollMargin((prev) => (prev === nextMargin ? prev : nextMargin));
  }, [pullRefreshVisible, conversationSummary, selectedRoomId, messages.length === 0]);

  // 채팅 메시지 행(MessageRow)에 넘기는 핸들러들이 안정적인 참조를 갖도록
  // request/withReadCounts/readableError도 useCallback으로 고정한다. 이들이
  // 매 렌더 새 함수였다면 핸들러 useCallback deps가 매번 바뀌어 memo가 무력화된다.
  const request = useCallback(async <T,>(path: string, init?: RequestInit): Promise<T> => {
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
  }, [token]);

  const readableError = useCallback((error: unknown, fallback = '잠시 후 다시 시도해주세요.') => {
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
  }, []);

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
    playSound('success');
  }

  function openSignup() {
    setSignupStep(1);
    setAgreeTerms(false);
    setAgreePrivacy(false);
    setAgreeMarketing(false);
    setSignupEmail('');
    setSignupCode('');
    setSignupCodeSent(false);
    setSignupName('');
    setSignupPassword('');
    setSignupPasswordConfirm('');
    setSignupResult(null);
    setSignupError('');
    setSignupOpen(true);
  }

  async function sendSignupCode() {
    if (!signupEmail.trim()) {
      setSignupError('이메일을 입력해주세요.');
      return;
    }
    setSignupBusy(true);
    setSignupError('');
    try {
      await request<void>('/auth/email/code', { method: 'POST', body: JSON.stringify({ email: signupEmail.trim() }) });
      setSignupCodeSent(true);
    } catch (error) {
      setSignupError(readableError(error, '인증코드를 보내지 못했습니다.'));
    } finally {
      setSignupBusy(false);
    }
  }

  async function verifySignupEmail() {
    const verificationCode = signupCode.replace(/\D/g, '').slice(0, EMAIL_CODE_LENGTH);
    if (verificationCode.length !== EMAIL_CODE_LENGTH) {
      setSignupError('6자리 인증코드를 입력해주세요.');
      return;
    }
    setSignupBusy(true);
    setSignupError('');
    try {
      await request<void>('/auth/email/verify', { method: 'POST', body: JSON.stringify({ email: signupEmail.trim(), code: verificationCode }) });
      setSignupStep(3);
    } catch (error) {
      setSignupError(readableError(error, '이메일 인증에 실패했습니다.'));
    } finally {
      setSignupBusy(false);
    }
  }

  async function completeSignup() {
    if (!signupName.trim()) {
      setSignupError('이름을 입력해주세요.');
      return;
    }
    if (signupPassword.length < 8) {
      setSignupError('비밀번호는 8자 이상이어야 합니다.');
      return;
    }
    if (signupPassword !== signupPasswordConfirm) {
      setSignupError('비밀번호가 일치하지 않습니다.');
      return;
    }
    setSignupBusy(true);
    setSignupError('');
    try {
      const data = await request<AuthResponse>('/auth/register', {
        method: 'POST',
        body: JSON.stringify({ email: signupEmail.trim(), password: signupPassword, name: signupName.trim() })
      });
      setSignupResult(data);
      setSignupStep(4);
    } catch (error) {
      setSignupError(readableError(error, '가입에 실패했습니다.'));
    } finally {
      setSignupBusy(false);
    }
  }

  function finishSignup() {
    if (signupResult) saveSession(signupResult);
    setSignupOpen(false);
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
    playSound('tap');
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

  // 알림 소켓 effect가 매 방 전환마다 재연결되지 않도록, 최신 showInAppNotification을 ref로 읽는다.
  const showInAppNotificationRef = useRef(showInAppNotification);
  showInAppNotificationRef.current = showInAppNotification;

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

  // withReadCounts가 의존하므로 먼저 선언한다(useCallback deps 참조는 렌더 시점에
  // 평가되어 TDZ에 걸리기 때문). user가 바뀔 때만 새 참조가 만들어진다.
  const normalizeReactions = useCallback((reactions: MessageReaction[]) => {
    if (!user) {
      return reactions;
    }
    return reactions.map((reaction) => ({
      ...reaction,
      reactedByMe: reaction.reactorEmails.some((email) => email.toLowerCase() === user.email.toLowerCase())
    }));
    // user.email만 읽으므로 email 기준으로 고정 — 프로필 저장 등으로 user 객체 참조만
    // 바뀔 때 withReadCounts→핸들러들이 연쇄로 새 참조가 돼 목록 전체가 리렌더되던 것 방지.
  }, [user?.email]);

  const withReadCounts = useCallback((items: ChatMessage[], receipts: ReadReceipt[]) => {
    // 수신확인 타임스탬프를 한 번만 epoch로 파싱한다(기존엔 메시지×수신확인마다 new Date 2회 →
    // O(M×R)의 Date 파싱). 이제 파싱은 M+R회.
    const readerEpochs: { email: string; epoch: number }[] = [];
    for (const receipt of receipts) {
      if (!receipt.lastReadAt) continue;
      readerEpochs.push({ email: receipt.email.toLowerCase(), epoch: new Date(receipt.lastReadAt).getTime() });
    }
    return items.map((message) => {
      const senderLower = message.senderEmail.toLowerCase();
      const messageEpoch = new Date(message.createdAt).getTime();
      let readCount = 0;
      for (const reader of readerEpochs) {
        if (reader.email !== senderLower && reader.epoch >= messageEpoch) {
          readCount += 1;
        }
      }
      const deliveredCount = message.deliveredCount ?? 0;
      const deliveryStatus = readCount > 0
        ? 'READ' as const
        : deliveredCount > 0 || message.deliveryStatus === 'DELIVERED'
          ? 'DELIVERED' as const
          : 'SENT' as const;
      const reactions = normalizeReactions(message.reactions ?? []);
      // 값이 그대로면 같은 객체 참조를 반환 — 수신확인 브로드캐스트(누군가 읽을 때마다 발생)마다
      // 변화 없는 행까지 새 객체가 돼 MessageRow(memo) 전체가 리렌더되던 문제를 막는다.
      if (
        message.readCount === readCount &&
        message.deliveredCount === deliveredCount &&
        message.deliveryStatus === deliveryStatus &&
        Array.isArray(message.reactions) &&
        reactionsShallowEqual(message.reactions, reactions)
      ) {
        return message;
      }
      return {
        ...message,
        readCount,
        deliveredCount,
        deliveryStatus,
        reactions
      };
    });
  }, [normalizeReactions]);

  function applyReadReceipts(receipts: ReadReceipt[]) {
    readReceiptsRef.current = receipts;
    setReadReceipts(receipts);
    setMessages((current) => withReadCounts(current, receipts));
  }

  async function loadReadReceipts(roomId: string) {
    if (!roomId) return;
    const data = await request<RoomReadSummary>(`/chat/rooms/${roomId}/read-receipts`);
    // 응답 대기 중 방을 바꿨다면 stale 수신확인이 현재 방 메시지에 섞이지 않게 무시한다
    // (loadMessages가 이 함수를 await하므로 방 전환 경쟁의 나머지 절반을 여기서 막는다).
    if (selectedRoomIdRef.current !== roomId) return;
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
    // 응답을 기다리는 사이 방을 바꿨다면(빠른 방 전환, 전송 후 350ms 지연 재조회 등)
    // 이 결과는 stale이므로 현재 방 메시지를 덮어쓰지 않는다.
    if (selectedRoomIdRef.current !== roomId) return;
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
    // 가상 스크롤에서는 마지막 메시지가 아직 mount/측정 전이라 scrollHeight가 추정치일 수
    // 있으므로 virtualizer.scrollToIndex로 마지막 항목을 정확히 하단 정렬한다.
    if (messages.length > 0) {
      messageVirtualizer.scrollToIndex(messages.length - 1, {
        align: 'end',
        behavior: behavior === 'smooth' ? 'smooth' : 'auto'
      });
      return;
    }
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
    // 2.5초 폴링이 매번 새 객체를 주므로, 내용이 같으면 setState를 건너뛰어
    // 불필요한 전체 리렌더(메시지 목록 포함)를 막는다.
    setPresence((prev) => samePresence(prev, data) ? prev : data);
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
      playSound('send');
      clearAttachment();
      setTimeout(() => loadMessages(selectedRoomId), 350);
    } catch (error) {
      setDraft(content);
      setReplyTarget(replyTarget);
      setStatus(readableError(error, '메시지 전송에 실패했습니다.'));
    }
  }

  const hideMessageForMe = useCallback(async (message: ChatMessage) => {
    try {
      await request<ChatMessage>(`/chat/rooms/${message.roomId}/messages/${message.id}/me`, { method: 'DELETE' });
      setMessages((current) => current.filter((item) => item.id !== message.id));
      setSearchResults((current) => current.filter((item) => item.id !== message.id));
    } catch (error) {
      setStatus(readableError(error, '메시지를 삭제하지 못했습니다.'));
    }
  }, [request, readableError]);

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

  const deleteMessageForEveryone = useCallback(async (message: ChatMessage) => {
    try {
      const updated = await request<ChatMessage>(`/chat/rooms/${message.roomId}/messages/${message.id}/everyone`, { method: 'DELETE' });
      const normalized = withReadCounts([updated], readReceiptsRef.current)[0];
      setMessages((current) => current.map((item) => item.id === normalized.id ? normalized : item));
      setSearchResults((current) => current.filter((item) => item.id !== updated.id));
    } catch (error) {
      setStatus(readableError(error, '모두에게 삭제하지 못했습니다.'));
    }
  }, [request, withReadCounts, readableError]);

  const startEditingMessage = useCallback((message: ChatMessage) => {
    setReplyTarget(null);
    setEditingMessageId(message.id);
    setEditingDraft(message.content);
  }, []);

  const cancelEditingMessage = useCallback(() => {
    setEditingMessageId('');
    setEditingDraft('');
  }, []);

  // 저장할 내용을 인자로 받는다 — editingDraft(App state)를 클로저로 캡처하면
  // 편집 중 키 입력마다 editMessage 참조가 바뀌어 모든 행이 리렌더된다.
  const editMessage = useCallback(async (message: ChatMessage, rawContent: string) => {
    const content = rawContent.trim();
    if (!content) {
      setStatus('수정할 메시지 내용을 입력해주세요.');
      return;
    }
    if (content === (message.content ?? '').trim()) {
      cancelEditingMessage();
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
  }, [request, withReadCounts, readableError, cancelEditingMessage]);

  const toggleMessageReaction = useCallback(async (message: ChatMessage, emoji: string) => {
    try {
      const updated = await request<ChatMessage>(`/chat/rooms/${message.roomId}/messages/${message.id}/reactions`, {
        method: 'POST',
        body: JSON.stringify({ emoji })
      });
      const normalized = withReadCounts([updated], readReceiptsRef.current)[0];
      setMessages((current) => current.map((item) => item.id === normalized.id ? normalized : item));
      playSound('reaction');
    } catch (error) {
      setStatus(readableError(error, '반응을 저장하지 못했습니다.'));
    }
  }, [request, withReadCounts, readableError]);

  const copyMessageText = useCallback(async (text: string) => {
    if (!text) return;
    try {
      if (!navigator.clipboard) throw new Error('clipboard unavailable');
      await navigator.clipboard.writeText(text);
      setStatus('메시지를 복사했어요.');
    } catch {
      setStatus('복사할 수 없어요. 메시지를 길게 눌러 선택해 주세요.');
    }
  }, []);

  function selectAttachment(file: File | null) {
    clearAttachment();
    if (!file) return;
    // 종류별 용량 상한(서버 ChatService.maxAttachmentBytes와 동일): 이미지 10 · 동영상 50 · 파일 25MB
    const isImage = file.type.startsWith('image/');
    const isVideo = file.type.startsWith('video/');
    const maxMb = isImage ? 10 : isVideo ? 50 : 25;
    if (file.size > maxMb * 1024 * 1024) {
      setStatus(`첨부 파일이 너무 큽니다. 최대 ${maxMb}MB까지 가능합니다.`);
      return;
    }
    setAttachment(file);
    // 이미지·동영상만 로컬 미리보기 URL 생성(그 외 파일은 칩으로 표시).
    setAttachmentPreview(isImage || isVideo ? URL.createObjectURL(file) : '');
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
        .then(() => loadContacts(contactQueryRef.current))
        .catch(() => undefined);
    }, 30000);
    return () => window.clearInterval(timer);
    // contactQuery는 ref로 읽는다 — deps에 넣으면 검색 입력 한 글자마다 인터벌이 재설정돼
    // 30초 하트비트가 타이핑 중 한 번도 못 나가고 프레즌스가 오프라인으로 새던 문제 방지.
  }, [token, user?.email]);

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
    let rafId = 0;
    let secondRafId = 0;
    let timeoutId = 0;
    const runScroll = () => {
      const messageList = messageListRef.current;
      // 방 전환은 AnimatePresence mode="wait"라 이전 화면이 빠져나간 뒤에야
      // 대화창이 마운트된다. 강제 스크롤(방 열기)일 때는 목록이 마운트될
      // 때까지 재시도해, force 플래그가 헛되이 소비돼 첫 메시지에 머무는 것을 막는다.
      if (!messageList) {
        if (force && attempts < 40) {
          attempts += 1;
          rafId = window.requestAnimationFrame(runScroll);
        }
        return;
      }
      scrollLatestMessageIntoView(behavior);
      secondRafId = window.requestAnimationFrame(() => scrollLatestMessageIntoView(behavior));
      if (force) {
        forceLatestMessageScrollRef.current = false;
        // 첨부 이미지 로딩 등으로 뒤늦게 높이가 늘어나는 경우 대비(사용자가
        // 위로 스크롤하지 않았을 때만 다시 맨 아래로 고정).
        timeoutId = window.setTimeout(() => {
          if (shouldStickToLatestMessageRef.current) {
            scrollLatestMessageIntoView('auto');
          }
        }, 160);
      }
    };
    rafId = window.requestAnimationFrame(runScroll);
    // 방 전환/새 메시지로 effect가 재실행되거나 언마운트될 때 대기 중인 rAF 재시도 루프와
    // 지연 타이머를 취소한다(중첩 루프 누적·언마운트 후 실행 방지).
    return () => {
      if (rafId) window.cancelAnimationFrame(rafId);
      if (secondRafId) window.cancelAnimationFrame(secondRafId);
      if (timeoutId) window.clearTimeout(timeoutId);
    };
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
    // 키 입력마다 typing=true POST를 보내지 않고, 마지막 전송 후 1.5s 지났을 때만 전송.
    // (입력 종료 감지용 typing=false 타이머는 매 입력마다 갱신해 인디케이터 동작은 동일)
    const now = Date.now();
    if (now - lastTypingSentRef.current > 1500) {
      lastTypingSentRef.current = now;
      pushTyping(true);
    }
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
          let isNew = false;
          setMessages((current) => {
            if (current.some((item) => item.id === message.id)) {
              return current.map((item) => item.id === message.id ? messageWithReadState : item);
            }
            isNew = true;
            return [...current, messageWithReadState];
          });
          if (user && message.senderEmail.toLowerCase() !== user.email.toLowerCase()) {
            markRoomRead(selectedRoomId).catch(() => undefined);
            if (isNew) playSound('receive');
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
        client.subscribe(`/topic/rooms/${selectedRoomId}/game`, (frame: IMessage) => {
          setActiveMatch(JSON.parse(frame.body) as GameMatchResponse);
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

  // 방 진입 시 최근 게임 대결 상태를 불러온다(브로드캐스트는 위 STOMP /game 구독으로 갱신).
  useEffect(() => {
    if (!selectedRoomId) { setActiveMatch(null); return; }
    let cancelled = false;
    request<GameMatchResponse | null>(`/chat/rooms/${selectedRoomId}/game-matches/latest`)
      .then((m) => { if (!cancelled) setActiveMatch(m ?? null); })
      .catch(() => { if (!cancelled) setActiveMatch(null); });
    return () => { cancelled = true; };
  }, [selectedRoomId]);

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
          // 알림 토픽은 방과 무관하게 전역이다. selectedRoomId/showInAppNotification을 deps에 두면
          // 방 전환마다 소켓이 끊겼다 재연결(핸드셰이크)돼 재연결 창에서 알림이 유실되므로 ref로 읽는다.
          const currentRoomId = selectedRoomIdRef.current;
          setNotifications((current) => [notification, ...current.filter((item) => item.id !== notification.id)].slice(0, 30));
          setNotificationUnreadCount((current) => current + (notification.read ? 0 : 1));
          if (notification.targetRoomId !== currentRoomId) {
            setRooms((current) => current.map((room) => room.id === notification.targetRoomId ? { ...room, unreadCount: room.unreadCount + 1 } : room));
            // 열려 있지 않은 방의 알림만 소리로 알린다(열린 방 메시지는 'receive'가 이미 재생됨).
            playSound('notify');
          }
          if (notification.targetRoomId && notification.targetMessageId && notification.targetRoomId !== currentRoomId) {
            markMessageDelivered(notification.targetRoomId, notification.targetMessageId).catch(() => undefined);
          }
          showInAppNotificationRef.current(notification);
        });
      }
    });
    client.activate();
    return () => {
      client.deactivate();
    };
  }, [notificationTopic, token]);

  if (!user) {
    if (authStage === 'landing') {
      return <WelcomeLanding onStart={() => setAuthStage('login')} />;
    }
    return (
      <main className="auth-shell auth-shell--login">
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

          <form className="auth-form" onSubmit={submitPasswordFlow}>
            <label>이메일<input value={email} onChange={(event) => setEmail(event.target.value)} type="email" required /></label>
            <label>비밀번호<input value={password} onChange={(event) => setPassword(event.target.value)} type="password" minLength={8} required /></label>
            <button className="primary-button" disabled={loading}><KeyRound size={18} aria-hidden />로그인</button>
          </form>

          <div className="auth-divider"><span>또는</span></div>

          <div className="auth-alt-actions">
            <button type="button" className="signup-cta" onClick={openSignup}>이메일로 회원가입</button>
            <button className="kakao-button" type="button" onClick={() => { window.location.href = `${API_ROOT}/auth/oauth/kakao/authorize`; }}>
              <span className="kakao-mark" aria-hidden>
                <svg viewBox="0 0 24 24" width="18" height="18"><path fill="currentColor" d="M12 3C6.48 3 2 6.58 2 11c0 2.87 1.9 5.39 4.76 6.8-.21.79-.76 2.85-.87 3.29-.14.55.2.54.43.39.18-.12 2.85-1.94 4-2.72.53.08 1.09.12 1.68.12 5.52 0 10-3.58 10-8S17.52 3 12 3z"/></svg>
              </span>
              카카오로 로그인
            </button>
            <button className="naver-button" type="button" onClick={() => { window.location.href = `${API_ROOT}/auth/oauth/naver/authorize`; }}><span className="naver-mark" aria-hidden>N</span>네이버로 로그인</button>
          </div>
          {status && <p className="notice">{status}</p>}

          <AnimatePresence>
            {signupOpen && (
              <motion.div className="signup-backdrop" role="presentation" onClick={() => setSignupOpen(false)}
                initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                <motion.div className="signup-modal" role="dialog" aria-modal="true" aria-label="회원가입"
                  onClick={(event) => event.stopPropagation()}
                  initial={{ opacity: 0, y: 18, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: 18, scale: 0.98 }}
                  transition={{ type: 'spring', stiffness: 320, damping: 30 }}>
                  <header className="signup-modal-head">
                    <div className="signup-steps" aria-hidden>
                      {[1, 2, 3, 4].map((step) => (
                        <span key={step} className={step === signupStep ? 'active' : step < signupStep ? 'done' : ''} />
                      ))}
                    </div>
                    <button type="button" className="signup-close" onClick={() => setSignupOpen(false)} aria-label="닫기"><X size={18} aria-hidden /></button>
                  </header>

                  <AnimatePresence mode="wait">
                    {signupStep === 1 && (
                      <motion.div key="s1" className="signup-step" initial={{ opacity: 0, x: 16 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -16 }} transition={{ duration: 0.18 }}>
                        <h2>약관에 동의해 주세요</h2>
                        <p className="signup-sub">서비스 이용을 위해 아래 약관 동의가 필요해요.</p>
                        <label className="agree-row agree-all">
                          <input type="checkbox" checked={agreeTerms && agreePrivacy && agreeMarketing}
                            onChange={(event) => { const value = event.target.checked; setAgreeTerms(value); setAgreePrivacy(value); setAgreeMarketing(value); }} />
                          <span>전체 동의</span>
                        </label>
                        <label className="agree-row">
                          <input type="checkbox" checked={agreeTerms} onChange={(event) => setAgreeTerms(event.target.checked)} />
                          <span><b>[필수]</b> 서비스 이용약관 동의</span>
                        </label>
                        <label className="agree-row">
                          <input type="checkbox" checked={agreePrivacy} onChange={(event) => setAgreePrivacy(event.target.checked)} />
                          <span><b>[필수]</b> 개인정보 수집·이용 동의</span>
                        </label>
                        <label className="agree-row">
                          <input type="checkbox" checked={agreeMarketing} onChange={(event) => setAgreeMarketing(event.target.checked)} />
                          <span><b className="optional">[선택]</b> 마케팅 정보 수신 동의</span>
                        </label>
                        <div className="signup-actions">
                          <button type="button" className="primary-button" disabled={!agreeTerms || !agreePrivacy} onClick={() => { setSignupError(''); setSignupStep(2); }}>다음</button>
                        </div>
                      </motion.div>
                    )}

                    {signupStep === 2 && (
                      <motion.div key="s2" className="signup-step" initial={{ opacity: 0, x: 16 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -16 }} transition={{ duration: 0.18 }}>
                        <h2>이메일을 인증해 주세요</h2>
                        <p className="signup-sub">가입에 사용할 이메일로 6자리 인증코드를 보내드려요.</p>
                        <div className="signup-email-row">
                          <input type="email" value={signupEmail} onChange={(event) => setSignupEmail(event.target.value)} placeholder="you@example.com" />
                          <button type="button" className="ghost-button" onClick={sendSignupCode} disabled={signupBusy || !signupEmail.trim()}>{signupCodeSent ? '재발송' : '코드 받기'}</button>
                        </div>
                        {signupCodeSent && (
                          <>
                            <OtpInput length={EMAIL_CODE_LENGTH} value={signupCode} onChange={setSignupCode} autoFocus />
                            <p className="signup-sub">메일함에서 인증코드를 확인하세요.</p>
                          </>
                        )}
                        <div className="signup-actions">
                          <button type="button" className="ghost-button" onClick={() => { setSignupError(''); setSignupStep(1); }}>이전</button>
                          <button type="button" className="primary-button" disabled={signupBusy || !signupCodeSent || signupCode.length !== EMAIL_CODE_LENGTH} onClick={verifySignupEmail}>인증하고 다음</button>
                        </div>
                      </motion.div>
                    )}

                    {signupStep === 3 && (
                      <motion.div key="s3" className="signup-step" initial={{ opacity: 0, x: 16 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -16 }} transition={{ duration: 0.18 }}>
                        <h2>비밀번호를 설정해 주세요</h2>
                        <p className="signup-sub"><b>{signupEmail}</b> 인증 완료! 계정 정보를 입력하세요.</p>
                        <label className="signup-field">이름<input value={signupName} onChange={(event) => setSignupName(event.target.value)} placeholder="이름" /></label>
                        <label className="signup-field">비밀번호<input type="password" value={signupPassword} onChange={(event) => setSignupPassword(event.target.value)} placeholder="8자 이상" /></label>
                        <label className="signup-field">비밀번호 확인<input type="password" value={signupPasswordConfirm} onChange={(event) => setSignupPasswordConfirm(event.target.value)} placeholder="다시 입력" /></label>
                        <div className="signup-actions">
                          <button type="button" className="ghost-button" onClick={() => { setSignupError(''); setSignupStep(2); }}>이전</button>
                          <button type="button" className="primary-button" disabled={signupBusy} onClick={completeSignup}>가입 완료</button>
                        </div>
                      </motion.div>
                    )}

                    {signupStep === 4 && (
                      <motion.div key="s4" className="signup-step signup-done" initial={{ opacity: 0, scale: 0.96 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.2 }}>
                        <div className="signup-done-badge"><CheckCircle2 size={44} aria-hidden /></div>
                        <h2>가입이 완료되었어요 🎉</h2>
                        <p className="signup-sub">{signupResult?.user?.name ?? ''}님, 환영해요!</p>
                        <button type="button" className="primary-button" onClick={finishSignup}>시작하기</button>
                      </motion.div>
                    )}
                  </AnimatePresence>

                  {signupError && <p className="signup-error">{signupError}</p>}
                </motion.div>
              </motion.div>
            )}
          </AnimatePresence>
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
      <MediaViewer
        viewer={mediaViewer}
        onClose={() => setMediaViewer(null)}
        onIndexChange={(index) => setMediaViewer((current) => (current ? { ...current, index } : current))}
      />
      <AnimatePresence>
        {notifCenterOpen && (
          <motion.div
            className="notif-center-backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => setNotifCenterOpen(false)}
          >
            <motion.section
              className="notif-center"
              role="dialog"
              aria-modal="true"
              aria-label="알림"
              initial={{ x: '100%' }}
              animate={{ x: 0 }}
              exit={{ x: '100%' }}
              transition={{ type: 'spring', stiffness: 300, damping: 34 }}
              onClick={(event) => event.stopPropagation()}
            >
              <header className="notif-center-head">
                <button type="button" className="notif-center-back" onClick={() => setNotifCenterOpen(false)} aria-label="닫기"><ArrowLeft size={20} aria-hidden /></button>
                <div>
                  <p className="eyebrow">NOTIFICATIONS</p>
                  <h2>알림</h2>
                </div>
                <button type="button" className="notif-center-readall" onClick={markAllNotificationsRead} disabled={notificationUnreadCount === 0}>모두 읽음</button>
              </header>
              <div className="notif-center-list">
                {notifications.length === 0 ? (
                  <div className="notif-center-empty">
                    <span className="notif-center-empty-ico" aria-hidden><Bell size={40} /></span>
                    <strong>새 알림이 없어요</strong>
                    <p>멘션·새 메시지·친구 요청이 오면<br />여기에 모아서 보여드릴게요.</p>
                  </div>
                ) : (() => {
                  const todayStart = new Date();
                  todayStart.setHours(0, 0, 0, 0);
                  const groups = [
                    { label: '오늘', items: notifications.filter((n) => new Date(n.createdAt).getTime() >= todayStart.getTime()) },
                    { label: '이전', items: notifications.filter((n) => new Date(n.createdAt).getTime() < todayStart.getTime()) }
                  ].filter((group) => group.items.length > 0);
                  return groups.map((group) => (
                    <div key={group.label} className="notif-group">
                      <div className="notif-group-label">{group.label}</div>
                      {group.items.map((n) => {
                        const upper = (n.type || '').toUpperCase();
                        const icon = upper.includes('MENTION') ? <AtSign size={19} aria-hidden />
                          : upper.includes('FRIEND') || upper.includes('INVITE') ? <UserPlus size={19} aria-hidden />
                          : upper.includes('GAME') || upper.includes('MATCH') || upper.includes('DUEL') ? <Swords size={19} aria-hidden />
                          : <MessageCircle size={19} aria-hidden />;
                        return (
                          <button
                            key={n.id}
                            type="button"
                            className={n.read ? 'notif-row' : 'notif-row unread'}
                            onClick={() => { if (n.targetRoomId) { openRoom(n.targetRoomId); setNotifCenterOpen(false); } }}
                          >
                            <span className={`notif-ico ${notifTint(n.type)}`} aria-hidden>{icon}</span>
                            <span className="notif-row-body">
                              <span className="notif-row-title">{n.title}{!n.read && <i className="notif-dot" aria-hidden />}</span>
                              <span className="notif-row-text">{n.body}</span>
                              <span className="notif-row-time">{formatNotifTime(n.createdAt)}</span>
                            </span>
                          </button>
                        );
                      })}
                    </div>
                  ));
                })()}
              </div>
            </motion.section>
          </motion.div>
        )}
      </AnimatePresence>
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

            <section className="panel-section theme-panel">
              <div className="section-title"><span>화면 테마</span></div>
              <div className="theme-seg" role="group" aria-label="화면 테마">
                <button type="button" className={theme === 'light' ? 'active' : ''} aria-pressed={theme === 'light'} onClick={() => changeTheme('light')}>
                  <Sun size={16} aria-hidden />라이트
                </button>
                <button type="button" className={theme === 'dark' ? 'active' : ''} aria-pressed={theme === 'dark'} onClick={() => changeTheme('dark')}>
                  <Moon size={16} aria-hidden />다크
                </button>
                <button type="button" className={theme === 'system' ? 'active' : ''} aria-pressed={theme === 'system'} onClick={() => changeTheme('system')}>
                  <Monitor size={16} aria-hidden />시스템
                </button>
              </div>
            </section>

            <section className="panel-section sound-panel">
              <div className="settings-row static">
                <span className="settings-row-label">
                  {soundOn ? <Volume2 size={16} aria-hidden /> : <VolumeX size={16} aria-hidden />}
                  효과음
                </span>
                <span className="settings-row-value">{soundOn ? '켜짐' : '꺼짐'}</span>
                <button
                  type="button"
                  className={soundOn ? 'sound-switch on' : 'sound-switch'}
                  role="switch"
                  aria-checked={soundOn}
                  aria-label="효과음 켜기/끄기"
                  onClick={toggleSound}
                >
                  <span className="sound-switch-knob" aria-hidden />
                </button>
              </div>
            </section>

            {isAdmin && (
              <section className="panel-section dlt-panel">
                <div className="dlt-head">
                  <div>
                    <p className="dlt-eyebrow">ADMIN · KAFKA</p>
                    <h3 className="dlt-title">데드레터(DLT)</h3>
                  </div>
                  <span className="dlt-badge">ADMIN</span>
                </div>
                <div className="dlt-topic"><Database size={15} aria-hidden />{dltTopic || 'chat.messages.DLT'}</div>
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
                  <button type="button" className="dlt-replay-btn" onClick={() => replayDltMessages(false)} disabled={dltLoading || dltMessages.length === 0}>
                    <RefreshCcw size={15} aria-hidden />
                    선택 재처리
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
        {roomMenuOpen && selectedRoom && (
          <motion.div
            className="room-drawer-backdrop"
            role="presentation"
            onClick={() => setRoomMenuOpen(false)}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
          >
            <motion.aside
              className="room-drawer"
              role="dialog"
              aria-modal="true"
              aria-label="채팅방 설정"
              onClick={(event) => event.stopPropagation()}
              initial={{ x: '100%' }}
              animate={{ x: 0 }}
              exit={{ x: '100%' }}
              transition={{ type: 'spring', stiffness: 320, damping: 34 }}
            >
              <header className="room-drawer-head">
                <strong>채팅방 설정</strong>
                <button type="button" className="room-drawer-close" onClick={() => setRoomMenuOpen(false)} aria-label="닫기"><X size={18} aria-hidden /></button>
              </header>

              <div className="room-drawer-body">
                <div className="room-drawer-info">
                  <h3>{selectedRoom.name}</h3>
                  <p>{selectedRoom.type === 'GROUP' ? `참여자 ${roomParticipants.length}명` : '1:1 대화'}</p>
                </div>

                <section className="room-drawer-section">
                  <h4><Users size={14} aria-hidden /> 참여자</h4>
                  <div className="room-drawer-participants">
                    {roomParticipants.map((participant) => (
                      <span key={participant.email} className={participant.online ? 'participant-chip online' : 'participant-chip'}>
                        <ProfileAvatar className="tiny-avatar" name={participant.name} imageUrl={participant.profileImageUrl} />
                        <strong>{participant.name}</strong>
                        {participant.owner && <small>방장</small>}
                      </span>
                    ))}
                  </div>
                  {selectedRoom.type === 'GROUP' && (
                    <form className="invite-form" onSubmit={inviteParticipant}>
                      <select value={inviteEmail} onChange={(event) => setInviteEmail(event.target.value)} disabled={inviteOptions.length === 0}>
                        <option value="">친구 초대</option>
                        {inviteOptions.map((contact) => <option key={contact.email} value={contact.email}>{contact.name} · {contact.email}</option>)}
                      </select>
                      <button type="submit" disabled={!inviteEmail || loading} title="친구 초대"><UserPlus size={16} aria-hidden /></button>
                    </form>
                  )}
                </section>

                <section className="room-drawer-section">
                  <h4><Camera size={14} aria-hidden /> 사진{roomAttachments.photos.length > 0 && <span className="room-drawer-count">{roomAttachments.photos.length}</span>}</h4>
                  {roomAttachments.photos.length > 0 ? (
                    <div className="room-drawer-photos">
                      {roomAttachments.photos.slice(0, 12).map((message) => (
                        <a key={message.id} href={message.attachmentUrl ?? '#'} target="_blank" rel="noreferrer noopener" title={message.attachmentName ?? '사진'}>
                          <img src={message.attachmentUrl ?? ''} alt="" loading="lazy" referrerPolicy="no-referrer" />
                        </a>
                      ))}
                    </div>
                  ) : (
                    <p className="room-drawer-empty">주고받은 사진이 없어요.</p>
                  )}
                </section>

                <section className="room-drawer-section">
                  <h4><Link2 size={14} aria-hidden /> 링크{roomAttachments.links.length > 0 && <span className="room-drawer-count">{roomAttachments.links.length}</span>}</h4>
                  {roomAttachments.links.length > 0 ? (
                    <ul className="room-drawer-files">
                      {roomAttachments.links.map((link) => (
                        <li key={link.id}>
                          <a href={link.url} target="_blank" rel="noreferrer noopener" title={link.label}>
                            <Link2 size={13} aria-hidden />
                            <span>{link.label}</span>
                          </a>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="room-drawer-empty">주고받은 링크가 없어요.</p>
                  )}
                </section>

                {roomAttachments.files.length > 0 && (
                  <section className="room-drawer-section">
                    <h4><Paperclip size={14} aria-hidden /> 파일<span className="room-drawer-count">{roomAttachments.files.length}</span></h4>
                    <ul className="room-drawer-files">
                      {roomAttachments.files.map((message) => (
                        <li key={message.id}>
                          <a href={message.attachmentUrl ?? '#'} target="_blank" rel="noreferrer noopener">
                            <Paperclip size={13} aria-hidden />
                            <span>{message.attachmentName ?? '파일'}</span>
                          </a>
                        </li>
                      ))}
                    </ul>
                  </section>
                )}
              </div>

              <div className="room-drawer-actions">
                <button type="button" onClick={() => { clearRoomMessagesForMe(); setRoomMenuOpen(false); }} disabled={messages.length === 0}>
                  <Trash2 size={16} aria-hidden /> 대화창 비우기
                </button>
                <button
                  type="button"
                  className="danger"
                  onClick={() => { setRoomMenuOpen(false); if (selectedRoom.type === 'GROUP') { leaveSelectedRoom(); } else { setRoomDeleteConfirmOpen(true); } }}
                  disabled={loading}
                >
                  <LogOut size={16} aria-hidden /> 채팅방 나가기
                </button>
              </div>
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
                <button className="back-button" type="button" onClick={() => { setSelectedRoomId(''); cancelEditingMessage(); }} title="채팅방 목록으로 돌아가기">
                  <ArrowLeft size={19} aria-hidden />
                  <span>채팅방 목록</span>
                </button>
              </div>
              <div>
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
                <button className="soft-action-button summary-action" onClick={summarizeConversation} disabled={!selectedRoomId || summaryLoading} type="button" aria-label="AI 대화 요약">
                  <Sparkles size={15} aria-hidden />{summaryLoading ? '요약 중…' : 'AI 요약'}
                </button>
                <button className="ghost-icon-button" onClick={() => setRoomMenuOpen(true)} disabled={!selectedRoomId} title="채팅방 설정" aria-label="채팅방 설정"><Settings size={18} aria-hidden /></button>
              </div>
            </header>


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
                    <Sparkles size={14} aria-hidden />
                    <span>대화 요약</span>
                    <small>{conversationSummary.model} · {conversationSummary.messageCount}개</small>
                  </div>
                  <p>{conversationSummary.summary}</p>
                </section>
              )}
              {messages.length === 0 && <p className="empty-state">아직 메시지가 없습니다.</p>}
              <div
                ref={virtualSpacerRef}
                className="message-virtual-spacer"
                style={{ position: 'relative', width: '100%', height: `${messageVirtualizer.getTotalSize()}px`, flexShrink: 0 }}
              >
                {messageVirtualizer.getVirtualItems().map((virtualRow) => {
                  const message = messages[virtualRow.index];
                  if (!message) return null;
                  const isMine = message.senderEmail?.toLowerCase() === user.email?.toLowerCase();
                  const isEditing = editingMessageId === message.id;
                  return (
                    <div
                      key={virtualRow.key}
                      data-index={virtualRow.index}
                      ref={messageVirtualizer.measureElement}
                      className="message-virtual-row"
                      style={{
                        position: 'absolute',
                        top: 0,
                        left: 0,
                        width: '100%',
                        transform: `translateY(${virtualRow.start - messageVirtualizer.options.scrollMargin}px)`
                      }}
                    >
                      <MessageRow
                        message={message}
                        isMine={isMine}
                        isEditing={isEditing}
                        animateOnMount={false}
                        // 편집 중인 행에만 editingDraft를 넘겨 편집 키 입력이 다른 행을 리렌더하지 않게 한다.
                        editingDraft={isEditing ? editingDraft : undefined}
                        onEditDraftChange={setEditingDraft}
                        onSaveEdit={editMessage}
                        onCancelEdit={cancelEditingMessage}
                        onStartEdit={startEditingMessage}
                        onToggleReaction={toggleMessageReaction}
                        onReply={setReplyTarget}
                        onHide={hideMessageForMe}
                        onDeleteForEveryone={deleteMessageForEveryone}
                        onCopy={copyMessageText}
                        onOpenMedia={openMediaViewer}
                      />
                    </div>
                  );
                })}
              </div>
              {presence.typingUsers.length > 0 && (
                <div className="typing-indicator" aria-live="polite" aria-label={`${presence.typingUsers.join(', ')}님이 입력 중`}>
                  <span className="typing-dots" aria-hidden><i /><i /><i /></span>
                </div>
              )}
            </div>

            {activeMatch && !gameOpen && !matchSession && (
              <GameMatchCard
                match={activeMatch}
                myEmail={user.email}
                onPlay={(m) => { matchIdRef.current = m.id; setMatchSession({ matchId: m.id, game: m.game }); }}
                onDismiss={() => setActiveMatch(null)}
              />
            )}
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
              {attachment && (
                <div className="attachment-preview">
                  {attachment.type.startsWith('image/') ? (
                    <img src={attachmentPreview} alt={attachment.name} />
                  ) : attachment.type.startsWith('video/') ? (
                    <video src={attachmentPreview} muted playsInline />
                  ) : (
                    <span className="attachment-file-ic"><FileIcon size={20} aria-hidden /></span>
                  )}
                  <span>{attachment.name}</span>
                  <button type="button" onClick={clearAttachment} title="첨부 제거"><X size={16} aria-hidden /></button>
                </div>
              )}
              <Popover.Root>
                <Popover.Trigger asChild>
                  <button type="button" className="attach-button" title="첨부 / 게임" aria-label="첨부 메뉴 열기" disabled={!selectedRoomId}>
                    <Plus size={20} aria-hidden />
                  </button>
                </Popover.Trigger>
                <Popover.Portal>
                  <Popover.Content className="attach-menu" side="top" align="start" sideOffset={10} collisionPadding={12}>
                    <div className="attach-grid">
                      <Popover.Close asChild>
                        <button type="button" className="attach-tile" onClick={() => attachPhotoRef.current?.click()}>
                          <span className="attach-ic attach-ic-photo"><ImageIcon size={20} aria-hidden /></span>
                          사진
                        </button>
                      </Popover.Close>
                      <Popover.Close asChild>
                        <button type="button" className="attach-tile" onClick={() => attachVideoRef.current?.click()}>
                          <span className="attach-ic attach-ic-video"><Film size={20} aria-hidden /></span>
                          동영상
                        </button>
                      </Popover.Close>
                      <Popover.Close asChild>
                        <button type="button" className="attach-tile" onClick={() => attachFileRef.current?.click()}>
                          <span className="attach-ic attach-ic-file"><FileIcon size={20} aria-hidden /></span>
                          파일
                        </button>
                      </Popover.Close>
                    </div>
                    <div className="attach-divider" aria-hidden />
                    <div className="attach-grid">
                      <Popover.Close asChild>
                        <button type="button" className="attach-tile" onClick={() => setGameOpen(true)}>
                          <span className="attach-ic attach-ic-game"><Gamepad2 size={20} aria-hidden /></span>
                          게임
                        </button>
                      </Popover.Close>
                      <Popover.Close asChild>
                        <button type="button" className="attach-tile" onClick={() => { matchIdRef.current = null; setMatchSession({ matchId: null, game: null }); }}>
                          <span className="attach-ic attach-ic-duel"><Swords size={20} aria-hidden /></span>
                          게임 대결
                        </button>
                      </Popover.Close>
                      <Popover.Close asChild>
                        <button type="button" className="attach-tile" onClick={() => setStatus('위치 공유는 준비 중이에요.')}>
                          <span className="attach-ic attach-ic-location"><MapPin size={20} aria-hidden /></span>
                          위치
                        </button>
                      </Popover.Close>
                    </div>
                  </Popover.Content>
                </Popover.Portal>
              </Popover.Root>
              <input ref={attachPhotoRef} type="file" accept="image/*" style={{ display: 'none' }} onChange={(event) => { selectAttachment(event.target.files?.[0] ?? null); event.target.value = ''; }} />
              <input ref={attachVideoRef} type="file" accept="video/*" style={{ display: 'none' }} onChange={(event) => { selectAttachment(event.target.files?.[0] ?? null); event.target.value = ''; }} />
              <input ref={attachFileRef} type="file" style={{ display: 'none' }} onChange={(event) => { selectAttachment(event.target.files?.[0] ?? null); event.target.value = ''; }} />
              <input value={draft} onChange={(event) => setDraft(event.target.value)} placeholder={presence.typingUsers.length > 0 ? `${presence.typingUsers[0]}님이 입력 중` : '메시지를 입력하세요'} disabled={!selectedRoomId} />
              <Popover.Root>
                <Popover.Trigger asChild>
                  <button type="button" className="composer-emoji-button" title="이모지" aria-label="이모지 넣기" disabled={!selectedRoomId}>
                    <Smile size={20} aria-hidden />
                  </button>
                </Popover.Trigger>
                <Popover.Portal>
                  <Popover.Content className="emoji-picker" side="top" align="end" sideOffset={10} collisionPadding={12}>
                    <div className="emoji-picker-head">자주 쓰는 이모지</div>
                    <div className="emoji-grid">
                      {COMPOSER_EMOJIS.map((emoji) => (
                        <button key={emoji} type="button" className="emoji-cell" onClick={() => setDraft((current) => current + emoji)}>{emoji}</button>
                      ))}
                    </div>
                  </Popover.Content>
                </Popover.Portal>
              </Popover.Root>
              <button disabled={!selectedRoomId || (!draft.trim() && !attachment)} title="메시지 보내기"><Send size={18} aria-hidden /></button>
            </form>
            <GameOverlay
              open={gameOpen || !!matchSession}
              onClose={() => { setGameOpen(false); setMatchSession(null); matchIdRef.current = null; }}
              submitScore={(game: GameKey, sc: number) => request<GameScoreResult>('/chat/games/scores', { method: 'POST', body: JSON.stringify({ game, score: sc }) })}
              loadBests={async () => {
                const list = await request<Array<{ game: GameKey; bestScore: number }>>('/chat/games/scores/me');
                const map = {} as Record<GameKey, number>;
                (list || []).forEach((s) => { map[s.game] = s.bestScore; });
                return map;
              }}
              matchMode={!!matchSession}
              matchGame={matchSession?.game ?? null}
              onDuel={() => { setGameOpen(false); matchIdRef.current = null; setMatchSession({ matchId: null, game: null }); }}
              onMatchStart={async (game: GameKey) => {
                const m = await request<GameMatchResponse>(`/chat/rooms/${selectedRoomId}/game-matches`, { method: 'POST', body: JSON.stringify({ game }) });
                matchIdRef.current = m.id;
                return true;
              }}
              onMatchEnd={async (game: GameKey, sc: number) => {
                const id = matchSession?.matchId ?? matchIdRef.current;
                if (!id) return;
                await request(`/chat/rooms/${selectedRoomId}/game-matches/${id}/rounds`, { method: 'POST', body: JSON.stringify({ score: sc }) });
              }}
            />
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
                  className="chat-icon-button chat-notif-button"
                  onClick={() => setNotifCenterOpen(true)}
                  title="알림"
                  aria-label={notificationUnreadCount > 0 ? `알림 ${notificationUnreadCount}개` : '알림'}
                >
                  {notificationUnreadCount > 0 ? <BellRing size={19} aria-hidden /> : <Bell size={19} aria-hidden />}
                  {notificationUnreadCount > 0 && <i className="chat-notif-badge">{notificationUnreadCount > 99 ? '99+' : notificationUnreadCount}</i>}
                </button>
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
                    chatFilter === 'unread' ? (
                      <p className="empty-state">안 읽은 채팅이 없어요.</p>
                    ) : (
                      <div className="chat-empty-state">
                        <span className="chat-empty-ico" aria-hidden><MessageSquareDashed size={42} /></span>
                        <strong>아직 참여한 채팅이 없어요</strong>
                        <p>친구를 초대하거나 새 채팅방을 만들어<br />대화를 시작해보세요.</p>
                        <button type="button" className="chat-empty-cta" onClick={() => openCreateChat('group')}>
                          <Plus size={18} aria-hidden />새 채팅 시작하기
                        </button>
                      </div>
                    )
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
            <span className="tab-ico"><UserRound size={22} aria-hidden /></span>
            <span>친구</span>
          </button>
          <button type="button" className={activeTab === 'chats' ? 'active' : ''} onClick={() => switchTab('chats')}>
            <span className="tab-ico"><MessagesSquare size={22} aria-hidden />{totalUnread > 0 && <i className="tab-badge">{totalUnread > 99 ? '99+' : totalUnread}</i>}</span>
            <span>채팅</span>
          </button>
          <button type="button" className={activeTab === 'news' ? 'active' : ''} onClick={() => switchTab('news')}>
            <span className="tab-ico"><Newspaper size={22} aria-hidden /></span>
            <span>뉴스</span>
          </button>
          <button type="button" className={activeTab === 'shopping' ? 'active' : ''} onClick={() => { switchTab('shopping'); loadShoppingCart(); }}>
            <span className="tab-ico"><ShoppingBag size={22} aria-hidden />{(shoppingCart?.totalCount ?? 0) > 0 && <i className="tab-badge">{(shoppingCart?.totalCount ?? 0) > 99 ? '99+' : shoppingCart?.totalCount}</i>}</span>
            <span>쇼핑</span>
          </button>
          <button type="button" className={activeTab === 'settings' ? 'active' : ''} onClick={() => switchTab('settings')}>
            <span className="tab-ico"><Settings2 size={22} aria-hidden /></span>
            <span>설정</span>
          </button>
        </nav>
      )}
    </div>
  );
}

// 미디어 뷰어: 채팅방 이미지 풀스크린 + 상단바(닫기/카운트/저장) + 하단 필름스트립
function MediaViewer({ viewer, onClose, onIndexChange }: {
  viewer: { images: ChatMessage[]; index: number } | null;
  onClose: () => void;
  onIndexChange: (index: number) => void;
}) {
  const open = !!viewer;
  const images = viewer?.images ?? [];
  const index = viewer?.index ?? 0;
  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
      else if (event.key === 'ArrowLeft' && index > 0) onIndexChange(index - 1);
      else if (event.key === 'ArrowRight' && index < images.length - 1) onIndexChange(index + 1);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, index, images.length, onClose, onIndexChange]);
  const current = images[index];
  return (
    <AnimatePresence>
      {open && current && (
        <motion.div
          className="media-viewer"
          role="dialog"
          aria-modal="true"
          aria-label="사진 보기"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
        >
          <div className="media-viewer-top" onClick={(event) => event.stopPropagation()}>
            <button type="button" className="media-viewer-btn" onClick={onClose} aria-label="닫기"><X size={20} aria-hidden /></button>
            <span className="media-viewer-count">{index + 1} / {images.length}</span>
            <a className="media-viewer-btn" href={current.attachmentUrl ?? '#'} target="_blank" rel="noreferrer" download={current.attachmentName ?? undefined} aria-label="저장"><Download size={20} aria-hidden /></a>
          </div>
          <div className="media-viewer-stage" onClick={(event) => event.stopPropagation()}>
            {index > 0 && <button type="button" className="media-viewer-nav prev" onClick={() => onIndexChange(index - 1)} aria-label="이전 사진"><ChevronLeft size={26} aria-hidden /></button>}
            <img key={current.id} className="media-viewer-img" src={current.attachmentUrl ?? ''} alt={current.attachmentName ?? ''} />
            {index < images.length - 1 && <button type="button" className="media-viewer-nav next" onClick={() => onIndexChange(index + 1)} aria-label="다음 사진"><ChevronRight size={26} aria-hidden /></button>}
          </div>
          {images.length > 1 && (
            <div className="media-viewer-strip" onClick={(event) => event.stopPropagation()}>
              {images.map((m, i) => (
                <button key={m.id} type="button" className={i === index ? 'media-thumb active' : 'media-thumb'} onClick={() => onIndexChange(i)} aria-label={`${i + 1}번째 사진`}>
                  <img src={m.attachmentUrl ?? ''} alt="" />
                </button>
              ))}
            </div>
          )}
        </motion.div>
      )}
    </AnimatePresence>
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

type MessageRowProps = {
  message: ChatMessage;
  isMine: boolean;
  isEditing: boolean;
  editingDraft?: string;
  // 가상 스크롤에서는 행이 스크롤 시마다 mount/unmount 되므로 진입 애니메이션을 끈다.
  animateOnMount?: boolean;
  onEditDraftChange: (value: string) => void;
  onSaveEdit: (message: ChatMessage, content: string) => void;
  onCancelEdit: () => void;
  onStartEdit: (message: ChatMessage) => void;
  onToggleReaction: (message: ChatMessage, emoji: string) => void;
  onReply: (message: ChatMessage) => void;
  onHide: (message: ChatMessage) => void;
  onDeleteForEveryone: (message: ChatMessage) => void;
  onCopy: (text: string) => void;
  onOpenMedia: (message: ChatMessage) => void;
};

// 메시지 한 줄. React.memo로 감싸 props가 바뀐 행만 리렌더한다.
// composer의 draft(App state)는 여기로 전달되지 않으므로 입력 중 목록이 리렌더되지 않고,
// editingDraft는 편집 중인 행에만 넘겨 편집 키 입력도 그 행만 리렌더한다.
// 핸들러들은 App에서 useCallback으로 안정화돼 있어야 memo가 실제로 동작한다.
const MessageRow = React.memo(function MessageRow({
  message,
  isMine,
  isEditing,
  editingDraft,
  animateOnMount = true,
  onEditDraftChange,
  onSaveEdit,
  onCancelEdit,
  onStartEdit,
  onToggleReaction,
  onReply,
  onHide,
  onDeleteForEveryone,
  onCopy,
  onOpenMedia
}: MessageRowProps) {
  const isDeleted = message.deletedForEveryone;
  const linkUrl = isDeleted ? null : firstMessageUrl(message.content);
  // 링크 미리보기 카드가 붙는 경우, 본문에서 원본 URL은 숨긴다(카드가 링크를 담으므로 중복+말풍선 비대 방지).
  const displayText = linkUrl && message.content ? message.content.replace(linkUrl, '').trim() : message.content;
  const deliveryLabel = deliveryStatusLabel(message);
  const draftValue = editingDraft ?? '';
  const hasMyReaction = (emoji: string) => message.reactions.some((reaction) => reaction.emoji === emoji && reaction.reactedByMe);
  return (
    <ContextMenu.Root>
      <ContextMenu.Trigger asChild disabled={isDeleted || isEditing}>
        <motion.div
          className={isMine ? 'chat-row mine' : 'chat-row'}
          initial={animateOnMount ? { opacity: 0, y: 8 } : false}
          animate={{ opacity: 1, y: 0 }}
        >
          {!isMine && <strong className="bubble-sender">{message.senderName}</strong>}
          {isEditing && !isDeleted ? (
            <div className="message-edit-panel">
              <textarea value={draftValue} onChange={(event) => onEditDraftChange(event.target.value)} maxLength={2000} autoFocus />
              <div>
                <button type="button" onClick={onCancelEdit}>취소</button>
                <button type="button" onClick={() => onSaveEdit(message, draftValue)} disabled={!draftValue.trim()}>저장</button>
              </div>
            </div>
          ) : (
            <div className="bubble-cluster">
              <article className="chat-bubble">
                {isDeleted ? (
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
                      message.attachmentType?.startsWith('image/') ? (
                        <button type="button" className="message-media" onClick={() => onOpenMedia(message)} title="사진 크게 보기">
                          <img src={message.attachmentUrl} alt={message.attachmentName ?? '첨부 이미지'} />
                        </button>
                      ) : message.attachmentType?.startsWith('video/') ? (
                        <video className="message-media message-video" src={message.attachmentUrl} controls preload="metadata" />
                      ) : (
                        <a className="message-file-chip" href={message.attachmentUrl} target="_blank" rel="noreferrer" download={message.attachmentName ?? undefined}>
                          <FileIcon size={18} aria-hidden />
                          <span className="message-file-meta">
                            <strong>{message.attachmentName ?? '파일'}</strong>
                            {typeof message.attachmentSize === 'number' && message.attachmentSize > 0 && <small>{formatBytes(message.attachmentSize)}</small>}
                          </span>
                        </a>
                      )
                    )}
                    {displayText && <p>{displayText}</p>}
                    {linkUrl && <MessageLinkPreview url={linkUrl} />}
                    {message.editedAt && <span className="edited-label">수정됨</span>}
                    {message.reactions.length > 0 && (
                      <div className="reaction-strip" aria-label="메시지 반응">
                        {message.reactions.map((reaction) => (
                          <button
                            key={`${message.id}-${reaction.emoji}`}
                            type="button"
                            className={reaction.reactedByMe ? 'active' : ''}
                            onClick={() => onToggleReaction(message, reaction.emoji)}
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
              <time className="bubble-time">{MESSAGE_TIME_FORMAT.format(new Date(message.createdAt))}</time>
              {!isDeleted && (
                <div className="message-actions">
                  <button className="msg-quick-btn" type="button" title="답장" aria-label="답장" onClick={() => onReply(message)}>
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
                            className={hasMyReaction(emoji) ? 'active' : ''}
                            onClick={() => onToggleReaction(message, emoji)}
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
      {!isDeleted && !isEditing && (
        <ContextMenu.Portal>
          <ContextMenu.Content className="message-context-menu" collisionPadding={12}>
            <div className="context-reactions" aria-label="빠른 반응">
              {QUICK_REACTIONS.map((emoji) => (
                <ContextMenu.Item
                  key={`${message.id}-ctx-${emoji}`}
                  className={hasMyReaction(emoji) ? 'ctx-emoji active' : 'ctx-emoji'}
                  onSelect={() => onToggleReaction(message, emoji)}
                >
                  {emoji}
                </ContextMenu.Item>
              ))}
            </div>
            <ContextMenu.Separator className="context-sep" />
            {isMine && (
              <div className="context-read-state">{deliveryLabel}</div>
            )}
            {message.content && (
              <ContextMenu.Item className="ctx-item" onSelect={() => onCopy(message.content ?? '')}><Copy size={14} aria-hidden />복사</ContextMenu.Item>
            )}
            {isMine && message.content && (
              <ContextMenu.Item className="ctx-item" onSelect={() => onStartEdit(message)}><Pencil size={14} aria-hidden />수정</ContextMenu.Item>
            )}
            <ContextMenu.Item className="ctx-item" onSelect={() => onReply(message)}><Reply size={14} aria-hidden />답장</ContextMenu.Item>
            <ContextMenu.Item className="ctx-item" onSelect={() => onHide(message)}>나에게 삭제</ContextMenu.Item>
            {isMine && (
              <ContextMenu.Item className="ctx-item danger" onSelect={() => onDeleteForEveryone(message)}><Trash2 size={14} aria-hidden />모두에게 삭제</ContextMenu.Item>
            )}
          </ContextMenu.Content>
        </ContextMenu.Portal>
      )}
    </ContextMenu.Root>
  );
});

const ROOM_TINTS = [
  { bg: '#EAF0FF', fg: '#3D6DFF' },
  { bg: '#FFF0E9', fg: '#F97316' },
  { bg: '#E8FAF0', fg: '#16A34A' },
  { bg: '#F3EEFF', fg: '#7C5CFF' },
  { bg: '#FFF0F3', fg: '#F04452' }
];
function roomTint(name: string) {
  let hash = 0;
  for (let i = 0; i < name.length; i += 1) hash = (hash * 31 + name.charCodeAt(i)) >>> 0;
  return ROOM_TINTS[hash % ROOM_TINTS.length];
}
const ROOM_TIME_FORMAT = new Intl.DateTimeFormat('ko-KR', { hour: 'numeric', minute: '2-digit' });
const ROOM_WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
function formatRoomTime(iso: string | null) {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  const now = new Date();
  const startToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const startThat = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
  const diffDays = Math.round((startToday - startThat) / 86400000);
  if (diffDays <= 0) return ROOM_TIME_FORMAT.format(date);
  if (diffDays === 1) return '어제';
  if (diffDays < 7) return `${ROOM_WEEKDAYS[date.getDay()]}요일`;
  return `${date.getMonth() + 1}월 ${date.getDate()}일`;
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
  if (rooms.length === 0) {
    return <p className="empty-state">아직 대화가 없습니다.</p>;
  }

  const renderRoom = (room: ChatRoom) => {
    const tint = roomTint(room.name);
    const isGroup = room.type === 'GROUP';
    return (
      <article key={room.id} className={room.id === selectedRoomId ? 'room-item active' : 'room-item'}>
        <button type="button" className="room-main-button" onClick={() => onSelect(room.id)}>
          <span
            className={isGroup ? 'room-avatar room-avatar--group' : 'room-avatar'}
            style={isGroup ? undefined : { background: tint.bg, color: tint.fg }}
            aria-hidden
          >
            {isGroup ? <Hash size={18} /> : (room.name || '?').trim().slice(0, 2)}
          </span>
          <span className="room-body">
            <span className="room-line">
              <strong>{room.name}</strong>
              <span className="room-time">{formatRoomTime(room.lastMessageAt)}</span>
            </span>
            <span className="room-line">
              <small className="room-preview">{room.type === 'DIRECT' ? '개인 메시지' : `${room.participantCount}명 · ${room.description || '그룹 채팅'}`}</small>
              <span className="room-meta">
                {room.muted && <BellOff size={15} aria-hidden className="room-muted-ico" />}
                {room.unreadCount > 0 && <i className="unread-badge" aria-label={`${room.unreadCount}개의 읽지 않은 메시지`}>{room.unreadCount > 99 ? '99+' : room.unreadCount}</i>}
              </span>
            </span>
          </span>
        </button>
        <div className="room-actions">
          <button type="button" className={room.pinned ? 'room-preference-button active' : 'room-preference-button'} onClick={() => onTogglePinned(room)} title={room.pinned ? '채팅방 고정 해제' : '채팅방 고정'}>
            <Pin size={14} aria-hidden />
          </button>
          <button type="button" className={room.muted ? 'room-preference-button active' : 'room-preference-button'} onClick={() => onToggleMuted(room)} title={room.muted ? '알림 켜기' : '알림 끄기'}>
            {room.muted ? <BellOff size={14} aria-hidden /> : <Bell size={14} aria-hidden />}
          </button>
        </div>
      </article>
    );
  };

  const pinned = rooms.filter((room) => room.pinned);
  const others = rooms.filter((room) => !room.pinned);

  return (
    <div className="room-list">
      {pinned.length > 0 && (
        <>
          <div className="room-section-head"><Pin size={12} aria-hidden />고정됨</div>
          {pinned.map(renderRoom)}
          {others.length > 0 && <div className="room-section-head">전체</div>}
        </>
      )}
      {others.map(renderRoom)}
    </div>
  );
}

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
