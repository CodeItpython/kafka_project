import { Component, ReactNode, Suspense, lazy, useRef } from 'react';
import { motion, useMotionValueEvent, useReducedMotion, useScroll, useTransform, Variants } from 'motion/react';
import { ArrowRight } from 'lucide-react';
import { landingScroll } from './LandingScene';

const LandingScene = lazy(() => import('./LandingScene'));

// WebGL 미지원/초기화 실패 시에도 랜딩이 깨지지 않도록 방어 (배경만 생략).
class SceneBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  render() {
    return this.state.failed ? null : this.props.children;
  }
}

const NAV = ['대화', '뉴스', '쇼핑', '게임'];

const FEATURES = [
  { no: '01', label: 'REALTIME', title: '보내는 순간,\n그대로 도착합니다.', desc: '1:1도 그룹도 끊김 없이. 읽음·답장·반응까지 대화의 결을 그대로 옮겼습니다.' },
  { no: '02', label: 'NEWS', title: '오늘을 아는\n가장 빠른 방법.', desc: '경제·증시·IT·세계. 카드로 넘겨보고, 마음이 가는 기사는 원문으로 바로 이어집니다.' },
  { no: '03', label: 'COMMERCE', title: '검색부터 장바구니까지,\n대화를 벗어나지 않고.', desc: '카테고리별 인기 상품을 둘러보고 검색해 담으세요. 앱을 옮겨 다닐 필요가 없습니다.' },
  { no: '04', label: 'CONNECT', title: '영상통화와\n음성 메시지까지.', desc: '목소리도 얼굴도 같은 자리에서. 대화를 끊지 않고 그대로 이어집니다.' }
];

const EASE = [0.22, 1, 0.36, 1] as const;
const revealGroup: Variants = { hidden: {}, show: { transition: { staggerChildren: 0.08 } } };
const riseItem: Variants = {
  hidden: { opacity: 0, y: 26 },
  show: { opacity: 1, y: 0, transition: { duration: 0.7, ease: EASE } }
};

export default function WelcomeLanding({ onStart }: { onStart: () => void }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const heroRef = useRef<HTMLDivElement>(null);
  const reduce = useReducedMotion();

  const { scrollYProgress } = useScroll({ container: containerRef });
  // 스크롤 진행도를 3D 씬으로 전달 → 형태가 스크롤에 맞춰 스크러빙된다.
  useMotionValueEvent(scrollYProgress, 'change', (value) => {
    landingScroll.target = value;
  });

  const { scrollYProgress: heroProgress } = useScroll({
    container: containerRef,
    target: heroRef,
    offset: ['start start', 'end start']
  });
  const heroY = useTransform(heroProgress, [0, 1], [0, reduce ? 0 : -70]);
  const heroOpacity = useTransform(heroProgress, [0, 0.7], [1, 0]);

  const group = reduce
    ? {}
    : { variants: revealGroup, initial: 'hidden', whileInView: 'show', viewport: { root: containerRef, amount: 0.4, once: true } };
  const itemV = reduce ? {} : { variants: riseItem };

  return (
    <div className="uc" ref={containerRef}>
      <SceneBoundary>
        <Suspense fallback={null}>
          <LandingScene />
        </Suspense>
      </SceneBoundary>
      <div className="uc-scrim" aria-hidden />
      <div className="uc-rim" aria-hidden />
      <motion.span className="uc-progress" style={{ scaleX: scrollYProgress }} aria-hidden />

      <div className="uc-ticker" aria-hidden>
        <span>REALTIME MESSAGING · NEWS · COMMERCE · VIDEO CALL — ONE APP</span>
      </div>

      <header className="uc-top">
        <span className="uc-brand">KAFKATALK</span>
        <nav className="uc-nav" aria-label="랜딩 내비게이션">
          {NAV.map((n) => <span key={n}>{n}</span>)}
        </nav>
        <button type="button" className="uc-pill solid" onClick={onStart}>시작하기</button>
      </header>

      <section className="uc-hero" ref={heroRef}>
        <motion.div className="uc-hero-copy" style={{ y: heroY, opacity: heroOpacity }}>
          <motion.p className="uc-eyebrow" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6, ease: EASE }}>
            ONE APP
          </motion.p>
          <motion.h1 initial={{ opacity: 0, y: 26 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.85, ease: EASE, delay: 0.08 }}>
            대화부터<br />뉴스, 쇼핑까지.
          </motion.h1>
          <motion.p className="uc-lead" initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.8, ease: EASE, delay: 0.16 }}>
            실시간 메신저에 뉴스와 쇼핑을 더했습니다.<br />앱을 옮겨 다니지 않아도 됩니다.
          </motion.p>
          <motion.div className="uc-actions" initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.8, ease: EASE, delay: 0.24 }}>
            <button type="button" className="uc-pill solid" onClick={onStart}>시작하기 <ArrowRight size={15} aria-hidden /></button>
            <button type="button" className="uc-pill" onClick={onStart}>둘러보기</button>
          </motion.div>
        </motion.div>
      </section>

      {FEATURES.map((f) => (
        <section className="uc-section" key={f.no}>
          <motion.div className="uc-row" {...group}>
            <motion.div className="uc-row-head" {...itemV}>
              <span className="uc-no">{f.no}</span>
              <span className="uc-label">{f.label}</span>
            </motion.div>
            <motion.h2 {...itemV}>
              {f.title.split('\n').map((line, i) => <span key={i}>{line}<br /></span>)}
            </motion.h2>
            <motion.p className="uc-body" {...itemV}>{f.desc}</motion.p>
          </motion.div>
        </section>
      ))}

      <section className="uc-section uc-final">
        <motion.div className="uc-row" {...group}>
          <motion.p className="uc-eyebrow" {...itemV}>GET STARTED</motion.p>
          <motion.h2 {...itemV}>지금 바로<br />시작해보세요.</motion.h2>
          <motion.div className="uc-actions" {...itemV}>
            <button type="button" className="uc-pill solid lg" onClick={onStart}>시작하기 <ArrowRight size={16} aria-hidden /></button>
          </motion.div>
        </motion.div>
      </section>
    </div>
  );
}
