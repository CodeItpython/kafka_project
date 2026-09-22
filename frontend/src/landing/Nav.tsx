import { motion } from 'motion/react';

export const SECTIONS = [
  { id: 'stream', label: 'STREAM' },
  { id: 'oneapp', label: 'ONE APP' },
  { id: 'film', label: 'DELIVERY' },
  { id: 'room', label: 'ROOM' }
] as const;

type Props = {
  solid: boolean;
  active: string | null;
  onStart: () => void;
  onJump: (id: string) => void;
};

// 히어로 위에서는 투명, 히어로를 벗어나면 불투명 검은 배경. 현재 섹션은 얇은 선으로 표시.
export default function Nav({ solid, active, onStart, onJump }: Props) {
  return (
    <header className={`uc-nav${solid ? ' solid' : ''}`}>
      <a className="uc-brand" href="#top" onClick={(e) => { e.preventDefault(); onJump('top'); }}>KAFKATALK</a>
      <nav aria-label="랜딩 섹션">
        {SECTIONS.map((s) => (
          <button
            key={s.id}
            type="button"
            className={`uc-nav-link${active === s.id ? ' on' : ''}`}
            aria-current={active === s.id ? 'true' : undefined}
            onClick={() => onJump(s.id)}
          >
            {s.label}
            {active === s.id && <motion.span className="uc-nav-line" layoutId="uc-nav-line" aria-hidden />}
          </button>
        ))}
      </nav>
      <button type="button" className="uc-pill solid" onClick={onStart}>시작하기</button>
    </header>
  );
}
