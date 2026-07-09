import { useEffect, useRef } from 'react';

type Props = { onScore: (s: number) => void; onGameOver: (s: number) => void };
type Piece = { shape: number[][]; color: string; x: number; y: number };

const COLS = 10;
const ROWS = 20;
const CELL = 15;
const W = COLS * CELL;
const H = ROWS * CELL;

const SHAPES: number[][][] = [
  [[1, 1, 1, 1]],
  [[1, 1], [1, 1]],
  [[0, 1, 0], [1, 1, 1]],
  [[0, 1, 1], [1, 1, 0]],
  [[1, 1, 0], [0, 1, 1]],
  [[1, 0, 0], [1, 1, 1]],
  [[0, 0, 1], [1, 1, 1]],
];
const COLORS = ['#4dd0e1', '#ffd54f', '#ba68c8', '#81c784', '#e57373', '#64b5f6', '#ffb74d'];

function rotateCW(m: number[][]): number[][] {
  const rows = m.length;
  const cols = m[0].length;
  const out: number[][] = Array.from({ length: cols }, () => Array(rows).fill(0));
  for (let y = 0; y < rows; y += 1) for (let x = 0; x < cols; x += 1) out[x][rows - 1 - y] = m[y][x];
  return out;
}

export default function TetrisGame({ onScore, onGameOver }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const cb = useRef({ onScore, onGameOver }); cb.current = { onScore, onGameOver };
  const controls = useRef<{ move: (d: number) => void; rotate: () => void; soft: () => void; hard: () => void } | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const board: string[][] = Array.from({ length: ROWS }, () => Array(COLS).fill(''));
    let piece: Piece | null = null;
    let score = 0;
    let dead = false;
    let dropMs = 620;
    let timer = 0;

    const spawn = (): Piece => {
      const i = Math.floor(Math.random() * SHAPES.length);
      const shape = SHAPES[i];
      return { shape, color: COLORS[i], x: Math.floor((COLS - shape[0].length) / 2), y: 0 };
    };
    const collides = (p: Piece, nx: number, ny: number, shape = p.shape): boolean => {
      for (let y = 0; y < shape.length; y += 1) {
        for (let x = 0; x < shape[y].length; x += 1) {
          if (!shape[y][x]) continue;
          const bx = nx + x;
          const by = ny + y;
          if (bx < 0 || bx >= COLS || by >= ROWS) return true;
          if (by >= 0 && board[by][bx]) return true;
        }
      }
      return false;
    };
    const draw = () => {
      ctx.fillStyle = '#0f1023';
      ctx.fillRect(0, 0, W, H);
      const cell = (x: number, y: number, color: string) => {
        ctx.fillStyle = color;
        ctx.fillRect(x * CELL + 1, y * CELL + 1, CELL - 2, CELL - 2);
      };
      for (let y = 0; y < ROWS; y += 1) for (let x = 0; x < COLS; x += 1) if (board[y][x]) cell(x, y, board[y][x]);
      if (piece) piece.shape.forEach((row, y) => row.forEach((v, x) => { if (v && piece) cell(piece.x + x, piece.y + y, piece.color); }));
    };
    const clearLines = () => {
      let cleared = 0;
      for (let y = ROWS - 1; y >= 0; y -= 1) {
        if (board[y].every((c) => c)) {
          board.splice(y, 1);
          board.unshift(Array(COLS).fill(''));
          cleared += 1;
          y += 1;
        }
      }
      if (cleared) {
        score += [0, 100, 300, 500, 800][cleared];
        cb.current.onScore(score);
        dropMs = Math.max(140, 620 - Math.floor(score / 1000) * 60);
      }
    };
    const lock = () => {
      if (!piece) return;
      piece.shape.forEach((row, y) => row.forEach((v, x) => {
        if (v && piece && piece.y + y >= 0) board[piece.y + y][piece.x + x] = piece.color;
      }));
      clearLines();
      const np = spawn();
      if (collides(np, np.x, np.y)) { dead = true; draw(); cb.current.onGameOver(score); return; }
      piece = np;
    };
    const step = () => {
      if (dead || !piece) return;
      if (!collides(piece, piece.x, piece.y + 1)) piece.y += 1;
      else lock();
      draw();
    };

    controls.current = {
      move: (d) => { if (piece && !collides(piece, piece.x + d, piece.y)) { piece.x += d; draw(); } },
      rotate: () => { if (piece) { const ns = rotateCW(piece.shape); if (!collides(piece, piece.x, piece.y, ns)) { piece.shape = ns; draw(); } } },
      soft: () => { if (piece && !collides(piece, piece.x, piece.y + 1)) { piece.y += 1; draw(); } },
      hard: () => { if (piece) { while (!collides(piece, piece.x, piece.y + 1)) piece.y += 1; lock(); draw(); } },
    };

    const onKey = (e: KeyboardEvent) => {
      if (dead || !controls.current) return;
      if (e.key === 'ArrowLeft') { e.preventDefault(); controls.current.move(-1); }
      else if (e.key === 'ArrowRight') { e.preventDefault(); controls.current.move(1); }
      else if (e.key === 'ArrowDown') { e.preventDefault(); controls.current.soft(); }
      else if (e.key === 'ArrowUp') { e.preventDefault(); controls.current.rotate(); }
      else if (e.key === ' ') { e.preventDefault(); controls.current.hard(); }
    };

    const loop = () => { step(); timer = window.setTimeout(loop, dropMs); };
    piece = spawn();
    draw();
    window.addEventListener('keydown', onKey);
    timer = window.setTimeout(loop, dropMs);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.clearTimeout(timer);
      controls.current = null;
    };
  }, []);

  return (
    <div className="game-play">
      <canvas ref={canvasRef} width={W} height={H} className="game-canvas" />
      <div className="game-dpad tetris" aria-label="조작">
        <button type="button" onClick={() => controls.current?.rotate()} aria-label="회전">⟳</button>
        <div>
          <button type="button" onClick={() => controls.current?.move(-1)} aria-label="왼쪽">◀</button>
          <button type="button" onClick={() => controls.current?.soft()} aria-label="아래">▼</button>
          <button type="button" onClick={() => controls.current?.move(1)} aria-label="오른쪽">▶</button>
        </div>
        <button type="button" onClick={() => controls.current?.hard()} aria-label="바닥으로">⤓</button>
      </div>
    </div>
  );
}
