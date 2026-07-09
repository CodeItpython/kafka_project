import { useCallback, useEffect, useRef, useState } from 'react';

type Props = { onScore: (s: number) => void; onGameOver: (s: number) => void };
type Grid = number[][];
type Dir = 'left' | 'right' | 'up' | 'down';

const N = 4;
const empty = (): Grid => Array.from({ length: N }, () => Array(N).fill(0));
const transpose = (g: Grid): Grid => g[0].map((_, c) => g.map((row) => row[c]));
const reverse = (g: Grid): Grid => g.map((row) => row.slice().reverse());

function collapseRow(row: number[]): [number[], number] {
  const nums = row.filter((x) => x);
  let gained = 0;
  for (let i = 0; i < nums.length - 1; i += 1) {
    if (nums[i] === nums[i + 1]) { nums[i] *= 2; gained += nums[i]; nums.splice(i + 1, 1); }
  }
  while (nums.length < N) nums.push(0);
  return [nums, gained];
}
function collapseLeft(g: Grid): [Grid, number, boolean] {
  let gained = 0;
  let moved = false;
  const next = g.map((row) => {
    const [nr, gg] = collapseRow(row);
    gained += gg;
    if (nr.some((v, i) => v !== row[i])) moved = true;
    return nr;
  });
  return [next, gained, moved];
}
function slide(g: Grid, dir: Dir): [Grid, number, boolean] {
  if (dir === 'left') return collapseLeft(g);
  if (dir === 'right') { const [n, gg, m] = collapseLeft(reverse(g)); return [reverse(n), gg, m]; }
  if (dir === 'up') { const [n, gg, m] = collapseLeft(transpose(g)); return [transpose(n), gg, m]; }
  const [n, gg, m] = collapseLeft(reverse(transpose(g)));
  return [transpose(reverse(n)), gg, m];
}
function addRandom(g: Grid): Grid {
  const spots: [number, number][] = [];
  g.forEach((row, r) => row.forEach((v, c) => { if (!v) spots.push([r, c]); }));
  if (!spots.length) return g;
  const [r, c] = spots[Math.floor(Math.random() * spots.length)];
  const next = g.map((row) => row.slice());
  next[r][c] = Math.random() < 0.9 ? 2 : 4;
  return next;
}
function canMove(g: Grid): boolean {
  for (let r = 0; r < N; r += 1) {
    for (let c = 0; c < N; c += 1) {
      if (!g[r][c]) return true;
      if (c < N - 1 && g[r][c] === g[r][c + 1]) return true;
      if (r < N - 1 && g[r][c] === g[r + 1][c]) return true;
    }
  }
  return false;
}

const TILE: Record<number, string> = {
  2: '#eee4da', 4: '#ede0c8', 8: '#f2b179', 16: '#f59563', 32: '#f67c5f', 64: '#f65e3b',
  128: '#edcf72', 256: '#edcc61', 512: '#edc850', 1024: '#edc53f', 2048: '#edc22e',
};

export default function Game2048({ onScore, onGameOver }: Props) {
  const [grid, setGrid] = useState<Grid>(() => addRandom(addRandom(empty())));
  const [score, setScore] = useState(0);
  const gridRef = useRef(grid); gridRef.current = grid;
  const scoreRef = useRef(0); scoreRef.current = score;
  const doneRef = useRef(false);
  const cb = useRef({ onScore, onGameOver }); cb.current = { onScore, onGameOver };
  const touch = useRef<{ x: number; y: number } | null>(null);

  const move = useCallback((dir: Dir) => {
    if (doneRef.current) return;
    const [moved, gained, didMove] = slide(gridRef.current, dir);
    if (!didMove) return;
    const next = addRandom(moved);
    const newScore = scoreRef.current + gained;
    setGrid(next);
    setScore(newScore);
    cb.current.onScore(newScore);
    if (!canMove(next)) { doneRef.current = true; cb.current.onGameOver(newScore); }
  }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const m: Record<string, Dir> = { ArrowLeft: 'left', ArrowRight: 'right', ArrowUp: 'up', ArrowDown: 'down' };
      if (m[e.key]) { e.preventDefault(); move(m[e.key]); }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [move]);

  return (
    <div
      className="game-play"
      onTouchStart={(e) => { touch.current = { x: e.touches[0].clientX, y: e.touches[0].clientY }; }}
      onTouchEnd={(e) => {
        const t = touch.current;
        if (!t) return;
        const dx = e.changedTouches[0].clientX - t.x;
        const dy = e.changedTouches[0].clientY - t.y;
        if (Math.max(Math.abs(dx), Math.abs(dy)) >= 24) {
          if (Math.abs(dx) > Math.abs(dy)) move(dx > 0 ? 'right' : 'left');
          else move(dy > 0 ? 'down' : 'up');
        }
        touch.current = null;
      }}
    >
      <div className="g2048-board">
        {grid.map((row, r) => row.map((v, c) => (
          <div
            key={`${r}-${c}`}
            className="g2048-cell"
            style={v ? { background: TILE[v] || '#3c3a32', color: v <= 4 ? '#776e65' : '#f9f6f2', fontSize: v >= 1024 ? 18 : 22 } : undefined}
          >
            {v || ''}
          </div>
        )))}
      </div>
      <div className="game-dpad" aria-label="조작">
        <button type="button" onClick={() => move('up')} aria-label="위">▲</button>
        <div>
          <button type="button" onClick={() => move('left')} aria-label="왼쪽">◀</button>
          <button type="button" onClick={() => move('right')} aria-label="오른쪽">▶</button>
        </div>
        <button type="button" onClick={() => move('down')} aria-label="아래">▼</button>
      </div>
    </div>
  );
}
