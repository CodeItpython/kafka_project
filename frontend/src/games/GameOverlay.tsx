import { useCallback, useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { ArrowLeft, RefreshCcw, X } from 'lucide-react';
import SnakeGame from './SnakeGame';
import TetrisGame from './TetrisGame';
import Game2048 from './Game2048';

export type GameKey = 'SNAKE' | 'TETRIS' | 'G2048';

export type GameScoreResult = { bestScore: number; improved: boolean };

type Props = {
  open: boolean;
  onClose: () => void;
  submitScore: (game: GameKey, score: number) => Promise<GameScoreResult | null>;
  loadBests: () => Promise<Record<GameKey, number>>;
};

const GAMES: { key: GameKey; name: string; emoji: string; hint: string }[] = [
  { key: 'SNAKE', name: '스네이크', emoji: '🐍', hint: '방향키 / 화면 버튼으로 이동' },
  { key: 'TETRIS', name: '테트리스', emoji: '🧱', hint: '← → 이동 · ↑ 회전 · ↓ 내리기 · Space 바닥' },
  { key: 'G2048', name: '2048', emoji: '🔢', hint: '방향키 / 스와이프로 합치기' },
];

export default function GameOverlay({ open, onClose, submitScore, loadBests }: Props) {
  const [selected, setSelected] = useState<GameKey | null>(null);
  const [score, setScore] = useState(0);
  const [runId, setRunId] = useState(0);
  const [over, setOver] = useState<{ score: number; improved: boolean } | null>(null);
  const [bests, setBests] = useState<Record<string, number>>({});
  // 부모 리렌더로 콜백 신원이 바뀌어도 오버레이가 초기화되지 않도록 ref로 고정.
  const fns = useRef({ submitScore, loadBests });
  fns.current = { submitScore, loadBests };

  useEffect(() => {
    if (!open) return;
    setSelected(null);
    setScore(0);
    setOver(null);
    fns.current.loadBests().then(setBests).catch(() => {});
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  const startGame = (key: GameKey) => {
    setSelected(key);
    setScore(0);
    setOver(null);
    setRunId((n) => n + 1);
  };
  const restart = () => { setScore(0); setOver(null); setRunId((n) => n + 1); };

  const handleScore = useCallback((s: number) => setScore(s), []);
  const handleOver = useCallback((finalScore: number) => {
    const game = selected;
    if (!game) return;
    fns.current.submitScore(game, finalScore).then((res) => {
      setOver({ score: finalScore, improved: !!res?.improved });
      if (res) setBests((prev) => ({ ...prev, [game]: res.bestScore }));
    }).catch(() => setOver({ score: finalScore, improved: false }));
  }, [selected]);

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
              {selected ? (
                <button type="button" className="game-head-btn" onClick={() => setSelected(null)} aria-label="게임 목록">
                  <ArrowLeft size={18} aria-hidden />
                </button>
              ) : <span className="game-head-btn" aria-hidden />}
              <strong>{meta ? meta.name : '게임'}</strong>
              <button type="button" className="game-head-btn" onClick={onClose} aria-label="닫기">
                <X size={18} aria-hidden />
              </button>
            </header>

            {!selected ? (
              <div className="game-picker">
                {GAMES.map((g) => (
                  <button key={g.key} type="button" className="game-card" onClick={() => startGame(g.key)}>
                    <span className="game-card-emoji" aria-hidden>{g.emoji}</span>
                    <strong>{g.name}</strong>
                    <small>최고 {bests[g.key] ?? 0}</small>
                  </button>
                ))}
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
                  {over && (
                    <div className="game-over-panel">
                      <strong>게임 오버</strong>
                      <span className="game-over-score">{over.score}점</span>
                      {over.improved && <span className="game-over-best">🎉 최고 점수 갱신!</span>}
                      <div className="game-over-actions">
                        <button type="button" className="game-primary-btn" onClick={restart}><RefreshCcw size={15} aria-hidden /> 다시하기</button>
                        <button type="button" className="game-ghost-btn" onClick={() => setSelected(null)}>다른 게임</button>
                      </div>
                    </div>
                  )}
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
