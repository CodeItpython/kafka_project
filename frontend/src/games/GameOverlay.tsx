import { useCallback, useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { ArrowLeft, X, Swords, ChevronRight, Trophy, Share2, RotateCcw } from 'lucide-react';
import SnakeGame from './SnakeGame';
import TetrisGame from './TetrisGame';
import Game2048 from './Game2048';
import type { GameMatchResponse } from './GameMatchCard';

export type GameKey = 'SNAKE' | 'TETRIS' | 'G2048';

export type GameScoreResult = { bestScore: number; improved: boolean };

type Props = {
  open: boolean;
  onClose: () => void;
  submitScore: (game: GameKey, score: number) => Promise<GameScoreResult | null>;
  loadBests: () => Promise<Record<GameKey, number>>;
  // 턴제 대결 모드: 개인 최고점 대신 대결 라운드로 제출. matchGame이 있으면(상대 차례) 바로 그 게임 시작,
  // 없으면(도전자) 게임을 고른 뒤 onMatchStart로 대결을 생성한다.
  matchMode?: boolean;
  matchGame?: GameKey | null;
  onMatchStart?: (game: GameKey) => Promise<boolean>;
  onMatchEnd?: (game: GameKey, score: number) => Promise<GameMatchResponse | null | void>;
  // 미니게임 허브에서 '게임 대결' 배너를 누르면 대결 모드로 전환.
  onDuel?: () => void;
  myEmail?: string;
  myName?: string;
};

const GAMES: { key: GameKey; name: string; emoji: string; hint: string }[] = [
  { key: 'SNAKE', name: '스네이크', emoji: '🐍', hint: '방향키 / 화면 버튼으로 이동' },
  { key: 'TETRIS', name: '테트리스', emoji: '🧱', hint: '← → 이동 · ↑ 회전 · ↓ 내리기 · Space 바닥' },
  { key: 'G2048', name: '2048', emoji: '🔢', hint: '방향키 / 스와이프로 합치기' },
];

export default function GameOverlay({ open, onClose, submitScore, loadBests, matchMode = false, matchGame = null, onMatchStart, onMatchEnd, onDuel, myEmail = '', myName = '' }: Props) {
  const [selected, setSelected] = useState<GameKey | null>(null);
  const [score, setScore] = useState(0);
  const [runId, setRunId] = useState(0);
  const [over, setOver] = useState<{ score: number; improved: boolean; prevBest: number; match: GameMatchResponse | null } | null>(null);
  const [bests, setBests] = useState<Record<string, number>>({});
  // 부모 리렌더로 콜백 신원이 바뀌어도 오버레이가 초기화되지 않도록 ref로 고정.
  const fns = useRef({ submitScore, loadBests, onMatchStart, onMatchEnd });
  fns.current = { submitScore, loadBests, onMatchStart, onMatchEnd };

  useEffect(() => {
    if (!open) return;
    setScore(0);
    setOver(null);
    if (matchMode) {
      // 상대 차례(matchGame 지정)면 바로 시작, 도전자면 게임 선택 화면.
      if (matchGame) { setSelected(matchGame); setRunId((n) => n + 1); }
      else setSelected(null);
      return;
    }
    setSelected(null);
    fns.current.loadBests().then(setBests).catch(() => {});
  }, [open, matchMode, matchGame]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  const startGame = async (key: GameKey) => {
    if (matchMode && !matchGame) {
      // 도전자: 게임을 고르면 먼저 대결을 생성한 뒤 플레이.
      const ok = await fns.current.onMatchStart?.(key);
      if (ok === false) return;
    }
    setSelected(key);
    setScore(0);
    setOver(null);
    setRunId((n) => n + 1);
  };
  const restart = () => { setScore(0); setOver(null); setRunId((n) => n + 1); };
  const handleShare = useCallback(async (label: string) => {
    const text = `Kafka Talk 미니게임 · ${label}`;
    try {
      if (navigator.share) { await navigator.share({ title: 'Kafka Talk', text }); return; }
      await navigator.clipboard?.writeText(text);
    } catch { /* 취소/미지원 무시 */ }
  }, []);

  const handleScore = useCallback((s: number) => setScore(s), []);
  const handleOver = useCallback((finalScore: number) => {
    const game = selected;
    if (!game) return;
    const prevBest = bests[game] ?? 0;
    if (matchMode) {
      Promise.resolve(fns.current.onMatchEnd?.(game, finalScore))
        .then((match) => setOver({ score: finalScore, improved: false, prevBest, match: (match as GameMatchResponse) ?? null }))
        .catch(() => setOver({ score: finalScore, improved: false, prevBest, match: null }));
      return;
    }
    fns.current.submitScore(game, finalScore).then((res) => {
      setOver({ score: finalScore, improved: !!res?.improved, prevBest, match: null });
      if (res) setBests((prev) => ({ ...prev, [game]: res.bestScore }));
    }).catch(() => setOver({ score: finalScore, improved: false, prevBest, match: null }));
  }, [selected, matchMode, bests]);

  const meta = GAMES.find((g) => g.key === selected);
  const best = selected ? (bests[selected] ?? 0) : 0;

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          className="game-overlay-backdrop"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
        >
          <motion.section
            className="game-overlay"
            role="dialog"
            aria-modal="true"
            aria-label="게임"
            initial={{ y: 24, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            exit={{ y: 24, opacity: 0 }}
            transition={{ type: 'spring', stiffness: 420, damping: 34 }}
            onClick={(e) => e.stopPropagation()}
          >
            <header className="game-overlay-head">
              {selected && !matchMode ? (
                <button type="button" className="game-head-btn" onClick={() => setSelected(null)} aria-label="게임 목록">
                  <ArrowLeft size={18} aria-hidden />
                </button>
              ) : <span className="game-head-btn" aria-hidden />}
              <strong>{matchMode ? '게임 대결' : (meta ? meta.name : '미니게임')}</strong>
              <button type="button" className="game-head-btn" onClick={onClose} aria-label="닫기">
                <X size={18} aria-hidden />
              </button>
            </header>

            {!selected ? (
              <div className="game-picker">
                {!matchMode && onDuel && (
                  <button type="button" className="game-duel-banner" onClick={onDuel}>
                    <span className="game-duel-ico" aria-hidden><Swords size={24} /></span>
                    <span className="game-duel-copy">
                      <strong>게임 대결</strong>
                      <small>친구와 실시간 점수 배틀 🔥</small>
                    </span>
                    <ChevronRight size={20} aria-hidden />
                  </button>
                )}
                <div className="game-card-grid">
                  {GAMES.map((g) => (
                    <button key={g.key} type="button" className="game-card" onClick={() => startGame(g.key)}>
                      <span className="game-card-emoji" aria-hidden>{g.emoji}</span>
                      <strong>{g.name}</strong>
                      {matchMode ? <small>대결 신청</small> : <small>최고 {bests[g.key] ?? 0}</small>}
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              <div className="game-stage">
                <div className="game-scorebar">
                  <span>점수 <strong>{score}</strong></span>
                  <span>최고 <strong>{Math.max(best, score)}</strong></span>
                </div>
                <div className="game-area">
                  {selected === 'SNAKE' && <SnakeGame key={runId} onScore={handleScore} onGameOver={handleOver} />}
                  {selected === 'TETRIS' && <TetrisGame key={runId} onScore={handleScore} onGameOver={handleOver} />}
                  {selected === 'G2048' && <Game2048 key={runId} onScore={handleScore} onGameOver={handleOver} />}
                  {over && (() => {
                    const m = over.match;
                    const done = matchMode && m && m.status === 'DONE';
                    const gameName = meta ? meta.name : '게임';
                    let label = gameName;
                    if (done && m) {
                      const result = m.winnerEmail === null ? '무승부' : (m.winnerEmail === myEmail ? '승리 🎉' : '패배');
                      label = `${gameName} · ${result}`;
                    } else if (matchMode) {
                      label = '점수 제출 완료';
                    }
                    const iAmChallenger = m ? m.challengerEmail === myEmail : true;
                    const myScore = m ? (iAmChallenger ? m.challengerScore : m.opponentScore) ?? over.score : over.score;
                    const oppName = m ? (iAmChallenger ? m.opponentName : m.challengerName) ?? '상대' : '상대';
                    const oppScore = m ? (iAmChallenger ? m.opponentScore : m.challengerScore) ?? 0 : 0;
                    return (
                    <div className="game-over-panel">
                      <span className="game-over-trophy" aria-hidden><Trophy size={38} /></span>
                      <span className="game-over-label">{label}</span>
                      <span className="game-over-score">{over.score.toLocaleString()}</span>
                      {!matchMode && (
                        <span className="game-over-sub">
                          이전 최고 {over.prevBest.toLocaleString()}{over.improved && <span className="game-over-record"> · 신기록!</span>}
                        </span>
                      )}
                      {done && m && (
                        <div className="game-vs">
                          <div className="game-vs-row">
                            <span className="game-vs-who"><span className="game-vs-avatar is-me">{(myName[0] ?? '나')}</span><span className="game-vs-name">나</span></span>
                            <span className="game-vs-score">{Number(myScore).toLocaleString()}</span>
                          </div>
                          <div className="game-vs-row">
                            <span className="game-vs-who"><span className="game-vs-avatar is-opp">{(oppName[0] ?? '상')}</span><span className="game-vs-name" style={{ color: '#9AA3B8' }}>{oppName}</span></span>
                            <span className="game-vs-score" style={{ color: '#9AA3B8' }}>{Number(oppScore).toLocaleString()}</span>
                          </div>
                        </div>
                      )}
                      <div className="game-over-actions">
                        <button type="button" className="game-ghost-btn" onClick={() => handleShare(label)}><Share2 size={17} aria-hidden />공유</button>
                        {matchMode ? (
                          <button type="button" className="game-primary-btn" onClick={onClose}>닫기</button>
                        ) : (
                          <button type="button" className="game-primary-btn" onClick={restart}><RotateCcw size={17} aria-hidden />다시하기</button>
                        )}
                      </div>
                    </div>
                    );
                  })()}
                </div>
                {meta && <p className="game-hint">{meta.hint}</p>}
              </div>
            )}
          </motion.section>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
