import { Suspense, lazy, useRef } from 'react';
import { motion, useReducedMotion, useScroll, useTransform } from 'motion/react';
import { ArrowRight, ChevronDown, Link2, MessageCircle, Newspaper } from 'lucide-react';

const WelcomeScene = lazy(() => import('./WelcomeScene'));

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
    icon: Link2,
    eyebrow: 'LINK',
    title: '링크는 카드처럼',
    desc: '링크를 보내면 제목·썸네일이 담긴 미리보기 카드로 예쁘게 펼쳐집니다.'
  }
];

const EASE = [0.22, 1, 0.36, 1] as const;

export default function WelcomeLanding({ onStart }: { onStart: () => void }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const heroRef = useRef<HTMLDivElement>(null);
  const reduce = useReducedMotion();

  const { scrollYProgress } = useScroll({ container: containerRef });
  const { scrollYProgress: heroProgress } = useScroll({
    container: containerRef,
    target: heroRef,
    offset: ['start start', 'end start']
  });
  const heroY = useTransform(heroProgress, [0, 1], [0, reduce ? 0 : -140]);
  const heroOpacity = useTransform(heroProgress, [0, 0.75], [1, 0]);
  const heroScale = useTransform(heroProgress, [0, 1], [1, reduce ? 1 : 1.18]);
  const hintOpacity = useTransform(heroProgress, [0, 0.25], [1, 0]);

  return (
    <div className="landing" ref={containerRef}>
      <Suspense fallback={null}>
        <WelcomeScene />
      </Suspense>
      <div className="landing-scrim" aria-hidden />
      <motion.span className="landing-progress" style={{ scaleX: scrollYProgress }} aria-hidden />

      <section className="landing-section landing-hero" ref={heroRef}>
        <motion.div className="landing-hero-inner" style={{ y: heroY, opacity: heroOpacity, scale: heroScale }}>
          <p className="landing-eyebrow">KAFKA TALK</p>
          <h1 className="landing-title">대화가<br />시작되는 곳</h1>
          <p className="landing-lead">친구와의 순간을 가볍게, 끊김 없이.</p>
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
            <motion.div
              className="landing-feature-inner"
              initial={{ y: 56 }}
              whileInView={{ y: 0 }}
              viewport={{ root: containerRef, amount: 0.5, once: false }}
              transition={{ duration: 0.8, ease: EASE }}
            >
              <span className="landing-feature-icon"><Icon size={26} aria-hidden /></span>
              <p className="landing-eyebrow">{feature.eyebrow}</p>
              <h2 className="landing-feature-title">{feature.title}</h2>
              <p className="landing-feature-desc">{feature.desc}</p>
            </motion.div>
          </section>
        );
      })}

      <section className="landing-section landing-cta">
        <motion.div
          className="landing-cta-inner"
          initial={{ y: 40 }}
          whileInView={{ y: 0 }}
          viewport={{ root: containerRef, amount: 0.5, once: false }}
          transition={{ duration: 0.7, ease: EASE }}
        >
          <h2 className="landing-cta-title">이제,<br />시작해볼까요?</h2>
          <p className="landing-lead">테스트 계정으로 바로 체험하거나 로그인하세요.</p>
          <button className="landing-start" type="button" onClick={onStart}>
            시작하기 <ArrowRight size={18} aria-hidden />
          </button>
          <button className="landing-login-link" type="button" onClick={onStart}>
            이미 계정이 있으신가요? 로그인
          </button>
        </motion.div>
      </section>
    </div>
  );
}
