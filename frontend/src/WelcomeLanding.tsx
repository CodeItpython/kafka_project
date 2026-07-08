import { Component, ReactNode, Suspense, lazy, useRef } from 'react';
import { motion, useMotionValueEvent, useReducedMotion, useScroll, useTransform, Variants } from 'motion/react';
import { ArrowRight, ChevronDown, Link2, MessageCircle, Newspaper, ShoppingBag } from 'lucide-react';
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

const FEATURES = [
  {
    icon: MessageCircle,
    eyebrow: 'REALTIME',
    title: '대화가 흐르는 곳',
    desc: '실시간으로 이어지는 1:1·그룹 대화. 읽음, 답장, 반응까지 끊김 없이.'
  },
  {
    icon: Newspaper,
    eyebrow: 'NEWS',
    title: '뉴스도 한눈에',
    desc: '경제·증시·IT·세계까지, 주요 뉴스를 카드로 넘겨보고 원문으로 바로.'
  },
  {
    icon: ShoppingBag,
    eyebrow: 'SHOPPING',
    title: '쇼핑까지 한 곳에서',
    desc: '카테고리별 인기·최저가 상품을 둘러보고, 검색해 장바구니에 바로 담으세요.'
  },
  {
    icon: Link2,
    eyebrow: 'LINK',
    title: '링크는 카드처럼',
    desc: '링크를 보내면 제목·썸네일이 담긴 미리보기 카드로 예쁘게 펼쳐집니다.'
  }
];

const EASE = [0.22, 1, 0.36, 1] as const;

// 등장 애니메이션 — 컨테이너가 자식을 순차(stagger)로 드러낸다.
const revealGroup: Variants = {
  hidden: {},
  show: { transition: { staggerChildren: 0.11, delayChildren: 0.06 } }
};
// 텍스트류: 아래에서 살짝 흐릿하게 떠오름.
const riseItem: Variants = {
  hidden: { opacity: 0, y: 34, filter: 'blur(6px)' },
  show: { opacity: 1, y: 0, filter: 'blur(0px)', transition: { duration: 0.65, ease: EASE } }
};
// 아이콘: 작게 회전한 채 튀어나오는 스프링 팝.
const popIcon: Variants = {
  hidden: { opacity: 0, scale: 0.35, rotate: -35 },
  show: { opacity: 1, scale: 1, rotate: 0, transition: { type: 'spring', stiffness: 260, damping: 15 } }
};

export default function WelcomeLanding({ onStart }: { onStart: () => void }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const heroRef = useRef<HTMLDivElement>(null);
  const reduce = useReducedMotion();

  const { scrollYProgress } = useScroll({ container: containerRef });
  // 스크롤 진행도를 3D 씬으로 전달 → 파티클 형태가 스크롤에 맞춰 스크러빙된다.
  useMotionValueEvent(scrollYProgress, 'change', (value) => {
    landingScroll.target = value;
  });

  const { scrollYProgress: heroProgress } = useScroll({
    container: containerRef,
    target: heroRef,
    offset: ['start start', 'end start']
  });
  const heroY = useTransform(heroProgress, [0, 1], [0, reduce ? 0 : -120]);
  const heroOpacity = useTransform(heroProgress, [0, 0.7], [1, 0]);
  const hintOpacity = useTransform(heroProgress, [0, 0.3], [1, 0]);

  // reduce 모드에선 등장 연출 없이 정적으로 보여준다.
  const groupOnView = reduce
    ? {}
    : { variants: revealGroup, initial: 'hidden', whileInView: 'show', viewport: { root: containerRef, amount: 0.5, once: false } };
  const groupOnMount = reduce ? {} : { variants: revealGroup, initial: 'hidden', animate: 'show' };
  const itemV = reduce ? {} : { variants: riseItem };
  const iconV = reduce ? {} : { variants: popIcon };

  return (
    <div className="landing" ref={containerRef}>
      <SceneBoundary>
        <Suspense fallback={null}>
          <LandingScene />
        </Suspense>
      </SceneBoundary>
      <div className="landing-scrim" aria-hidden />
      <motion.span className="landing-progress" style={{ scaleX: scrollYProgress }} aria-hidden />

      <section className="landing-section landing-hero" ref={heroRef}>
        <motion.div className="landing-hero-inner" style={{ y: heroY, opacity: heroOpacity }} {...groupOnMount}>
          <motion.p className="landing-eyebrow" {...itemV}>KAFKA TALK</motion.p>
          <motion.h1 className="landing-title landing-shimmer" {...itemV}>대화가<br />시작되는 곳</motion.h1>
          <motion.p className="landing-lead" {...itemV}>친구와의 순간을 가볍게, 끊김 없이.</motion.p>
        </motion.div>
        <motion.div className="landing-scroll-hint" style={{ opacity: hintOpacity }} aria-hidden>
          <span>스크롤</span>
          <motion.span
            className="landing-scroll-chevron"
            animate={reduce ? undefined : { y: [0, 8, 0] }}
            transition={{ duration: 1.6, repeat: Infinity, ease: 'easeInOut' }}
          >
            <ChevronDown size={22} />
          </motion.span>
        </motion.div>
      </section>

      {FEATURES.map((feature) => {
        const Icon = feature.icon;
        return (
          <section className="landing-section landing-feature" key={feature.eyebrow}>
            <motion.div className="landing-feature-inner" {...groupOnView}>
              <motion.span className="landing-feature-icon" {...iconV}>
                <Icon size={26} aria-hidden />
              </motion.span>
              <motion.p className="landing-eyebrow" {...itemV}>{feature.eyebrow}</motion.p>
              <motion.h2 className="landing-feature-title landing-shimmer" {...itemV}>{feature.title}</motion.h2>
              <motion.p className="landing-feature-desc" {...itemV}>{feature.desc}</motion.p>
            </motion.div>
          </section>
        );
      })}

      <section className="landing-section landing-cta">
        <motion.div className="landing-cta-inner" {...groupOnView}>
          <motion.h2 className="landing-cta-title landing-shimmer" {...itemV}>이제,<br />시작해볼까요?</motion.h2>
          <motion.p className="landing-lead" {...itemV}>테스트 계정으로 바로 체험하거나 로그인하세요.</motion.p>
          <motion.button
            className="landing-start"
            type="button"
            onClick={onStart}
            {...itemV}
            whileHover={reduce ? undefined : { y: -3, scale: 1.03 }}
            whileTap={reduce ? undefined : { scale: 0.96 }}
          >
            시작하기 <ArrowRight size={18} aria-hidden />
          </motion.button>
          <motion.button className="landing-login-link" type="button" onClick={onStart} {...itemV}>
            이미 계정이 있으신가요? 로그인
          </motion.button>
        </motion.div>
      </section>
    </div>
  );
}
