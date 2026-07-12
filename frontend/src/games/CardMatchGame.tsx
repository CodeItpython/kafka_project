import { useCallback, useEffect, useRef, useState } from 'react';

type Props = { onScore: (s: number) => void; onGameOver: (s: number) => void };
type Card = { id: number; symbol: string; matched: boolean };

// 8 distinct symbols → 8 pairs → 4×4 board.
const SYMBOLS = ['🍎', '🍋', '🍇', '🍓', '🥝', '🍑', '🍍', '🥥'];

function shuffledDeck(): Card[] {
  const deck = SYMBOLS.flatMap((symbol) => [symbol, symbol]);
  for (let i = deck.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [deck[i], deck[j]] = [deck[j], deck[i]];
  }
  return deck.map((symbol, id) => ({ id, symbol, matched: false }));
}

/**
 * Memory / card-match game. The "score" reported to the hub is the elapsed time in
 * SECONDS (shorter is better) — the hub formats it as mm:ss and tracks the best time
 * locally. The timer starts on the first flip and stops when all pairs are matched.
 */
export default function CardMatchGame({ onScore, onGameOver }: Props) {
  const [cards, setCards] = useState<Card[]>(() => shuffledDeck());
  const [flipped, setFlipped] = useState<number[]>([]); // face-up, not-yet-matched indices (max 2)
  const [locked, setLocked] = useState(false); // during the mismatch flip-back delay
  const [elapsed, setElapsed] = useState(0);
  const startRef = useRef<number | null>(null);
  const doneRef = useRef(false);

  // Tick the timer (starts on first flip). Report whole seconds to the hub.
  useEffect(() => {
    const id = window.setInterval(() => {
      if (startRef.current != null && !doneRef.current) {
        const secs = Math.floor((Date.now() - startRef.current) / 1000);
        setElapsed(secs);
        onScore(secs);
      }
    }, 250);
    return () => window.clearInterval(id);
  }, [onScore]);

  const flip = useCallback((index: number) => {
    if (locked || doneRef.current) return;
    if (flipped.length >= 2) return;
    const card = cards[index];
    if (!card || card.matched || flipped.includes(index)) return;
    if (startRef.current == null) startRef.current = Date.now();
    setFlipped((current) => (current.includes(index) || current.length >= 2 ? current : [...current, index]));
  }, [locked, flipped, cards]);

  // Resolve a pair once two cards are face up.
  useEffect(() => {
    if (flipped.length !== 2) return;
    const [a, b] = flipped;
    if (cards[a].symbol === cards[b].symbol) {
      setCards((current) => current.map((c, i) => (i === a || i === b ? { ...c, matched: true } : c)));
      setFlipped([]);
    } else {
      setLocked(true);
      const t = window.setTimeout(() => { setFlipped([]); setLocked(false); }, 720);
      return () => window.clearTimeout(t);
    }
  }, [flipped, cards]);

  // All pairs matched → finish with the elapsed time.
  useEffect(() => {
    if (!doneRef.current && cards.length > 0 && cards.every((c) => c.matched)) {
      doneRef.current = true;
      const secs = startRef.current != null ? Math.floor((Date.now() - startRef.current) / 1000) : 0;
      onGameOver(secs);
    }
  }, [cards, onGameOver]);

  const faceUp = (i: number) => cards[i].matched || flipped.includes(i);

  return (
    <div className="game-play">
      <div className="cardmatch-board" aria-label={`카드 매치 · 경과 ${elapsed}초`}>
        {cards.map((c, i) => (
          <button
            key={c.id}
            type="button"
            className={`cardmatch-tile${faceUp(i) ? ' is-up' : ''}${c.matched ? ' is-matched' : ''}`}
            onClick={() => flip(i)}
            disabled={c.matched || doneRef.current}
            aria-label={faceUp(i) ? c.symbol : '뒤집힌 카드'}
          >
            <span className="cardmatch-face">{faceUp(i) ? c.symbol : ''}</span>
          </button>
        ))}
      </div>
    </div>
  );
}
