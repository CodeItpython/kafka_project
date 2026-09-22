import { RefObject, useRef } from 'react';
import { motion, MotionValue, useScroll, useTransform } from 'motion/react';
import { MEDIA } from './media';
import LazyVideo from './LazyVideo';

// 펄스의 경로를 따라 하나씩 켜지는 네 단계
const STEPS = [
  { k: '실시간 전송', d: '보낸 즉시 스트림에 실려 같은 방으로.' },
  { k: '읽음 표시', d: '상대 기기에 도착하고 읽힌 순간이 남는다.' },
  { k: '답장 · 반응', d: '어느 말에 대한 답인지, 어떤 반응인지 함께 흐른다.' },
  { k: '재접속 시 이어받기', d: '끊겼다 돌아와도 놓친 대화는 순서대로 이어진다.' }
];

function Step({ p, at, idx, k, d, reduce }: { p: MotionValue<number>; at: number; idx: number; k: string; d: string; reduce: boolean }) {
  // 스크롤이 지날 때 켜진다 (reduced-motion 이면 항상 표시)
  const o = useTransform(p, [at, at + 0.08], [0, 1]);
  return (
    <motion.li style={{ opacity: reduce ? 1 : o }}>
      <span className="uc-idx">{String(idx + 1).padStart(2, '0')}</span>
      <div><h3>{k}</h3><p>{d}</p></div>
    </motion.li>
  );
}

export default function StreamFilm({ container, reduce }: { container: RefObject<HTMLDivElement | null>; reduce: boolean }) {
  const ref = useRef<HTMLElement>(null);
  const { scrollYProgress: p } = useScroll({ container, target: ref, offset: ['start 0.8', 'end 0.6'] });
  const line = useTransform(p, [0.05, 0.95], [0, 1]);
  return (
    <section className="uc-sec uc-film" id="film" ref={ref} aria-labelledby="film-title">
      <div className="uc-film-head">
        <p className="uc-eyebrow"><span className="uc-no">03</span> THE STREAM FILM</p>
        <h2 id="film-title">DELIVERED,<br />NOT SENT</h2>
        <p className="uc-body">보낸 것이 아니라, 도착한 것이다.</p>
      </div>
      <div className="uc-film-stage">
        <LazyVideo container={container} src={MEDIA.stream} className="uc-film-video" reduce={reduce} />
        <ol className="uc-steps" aria-label="전달 과정">
          <motion.i className="uc-steps-line" style={{ scaleY: reduce ? 1 : line }} aria-hidden />
          {STEPS.map((s, i) => <Step key={s.k} p={p} at={0.1 + i * 0.22} idx={i} k={s.k} d={s.d} reduce={reduce} />)}
        </ol>
      </div>
    </section>
  );
}
