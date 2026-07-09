import { useEffect, useRef } from 'react';

type Props = { onScore: (s: number) => void; onGameOver: (s: number) => void };
type Pt = { x: number; y: number };

const CELLS = 20;
const CELL = 16;
const SIZE = CELLS * CELL;

export default function SnakeGame({ onScore, onGameOver }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const cb = useRef({ onScore, onGameOver });
  cb.current = { onScore, onGameOver };
  const st = useRef({
    snake: [{ x: 8, y: 10 }, { x: 7, y: 10 }, { x: 6, y: 10 }] as Pt[],
    dir: { x: 1, y: 0 },
    next: { x: 1, y: 0 },
    food: { x: 14, y: 10 } as Pt,
    score: 0,
    dead: false,
  });

  const setDir = (x: number, y: number) => {
    const s = st.current;
    if (s.dir.x + x === 0 && s.dir.y + y === 0) return; // 반대 방향 금지
    s.next = { x, y };
  };

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    const s = st.current;

    const randFood = (): Pt => {
      let f: Pt;
      do {
        f = { x: Math.floor(Math.random() * CELLS), y: Math.floor(Math.random() * CELLS) };
      } while (s.snake.some((p) => p.x === f.x && p.y === f.y));
      return f;
    };
    const draw = () => {
      ctx.fillStyle = '#0f1023';
      ctx.fillRect(0, 0, SIZE, SIZE);
      ctx.fillStyle = '#ff5c8a';
      ctx.fillRect(s.food.x * CELL + 2, s.food.y * CELL + 2, CELL - 4, CELL - 4);
      s.snake.forEach((p, i) => {
        ctx.fillStyle = i === 0 ? '#a29bff' : '#5b53eb';
        ctx.fillRect(p.x * CELL + 1, p.y * CELL + 1, CELL - 2, CELL - 2);
      });
    };
    const tick = () => {
      if (s.dead) return;
      s.dir = s.next;
      const head = { x: s.snake[0].x + s.dir.x, y: s.snake[0].y + s.dir.y };
      if (head.x < 0 || head.y < 0 || head.x >= CELLS || head.y >= CELLS
          || s.snake.some((p) => p.x === head.x && p.y === head.y)) {
        s.dead = true;
        cb.current.onGameOver(s.score);
        return;
      }
      s.snake.unshift(head);
      if (head.x === s.food.x && head.y === s.food.y) {
        s.score += 10;
        cb.current.onScore(s.score);
        s.food = randFood();
      } else {
        s.snake.pop();
      }
      draw();
    };
    const onKey = (e: KeyboardEvent) => {
      const map: Record<string, [number, number]> = {
        ArrowUp: [0, -1], ArrowDown: [0, 1], ArrowLeft: [-1, 0], ArrowRight: [1, 0],
      };
      const d = map[e.key];
      if (d) { e.preventDefault(); setDir(d[0], d[1]); }
    };
    draw();
    window.addEventListener('keydown', onKey);
    const id = window.setInterval(tick, 120);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.clearInterval(id);
    };
  }, []);

  return (
    <div className="game-play">
      <canvas ref={canvasRef} width={SIZE} height={SIZE} className="game-canvas" />
      <div className="game-dpad" aria-label="조작">
        <button type="button" onClick={() => setDir(0, -1)} aria-label="위">▲</button>
        <div>
          <button type="button" onClick={() => setDir(-1, 0)} aria-label="왼쪽">◀</button>
          <button type="button" onClick={() => setDir(1, 0)} aria-label="오른쪽">▶</button>
        </div>
        <button type="button" onClick={() => setDir(0, 1)} aria-label="아래">▼</button>
      </div>
    </div>
  );
}
