import { useCallback, useEffect, useRef, useState } from 'react';
import { motion, useMotionValueEvent, useReducedMotion, useScroll } from 'motion/react';
import Nav, { SECTIONS } from './landing/Nav';
import Hero from './landing/Hero';
import TheStream from './landing/TheStream';
import OneApp from './landing/OneApp';
import StreamFilm from './landing/StreamFilm';
import TheRoom from './landing/TheRoom';
import GetStarted from './landing/GetStarted';
import Footer from './landing/Footer';

// 랜딩은 window 가 아니라 .uc div 가 스크롤 컨테이너다. 각 섹션은 이 ref 를 받아 useScroll/IntersectionObserver 의 root 로 쓴다.
export default function WelcomeLanding({ onStart }: { onStart: () => void }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const heroRef = useRef<HTMLElement>(null);
  // dev 서버에서 ?reduce 로 감소 모션 대체 화면을 미리 볼 수 있다 (프로덕션 빌드에서는 제거됨)
  const reduce = !!useReducedMotion() || (import.meta.env.DEV && new URLSearchParams(window.location.search).has('reduce'));
  const [solid, setSolid] = useState(false);
  const [active, setActive] = useState<string | null>(null);

  const { scrollYProgress, scrollY } = useScroll({ container: containerRef });

  // 히어로를 벗어나면 내비가 불투명해진다
  useMotionValueEvent(scrollY, 'change', (y) => {
    const hero = heroRef.current;
    const root = containerRef.current;
    if (!hero || !root) return;
    setSolid(y > hero.offsetHeight - root.clientHeight - 40);
  });

  // 현재 섹션 표시 (히어로 안에서는 없음)
  useEffect(() => {
    const root = containerRef.current;
    if (!root) return;
    const targets = [heroRef.current, ...SECTIONS.map((s) => document.getElementById(s.id))].filter(Boolean) as HTMLElement[];
    const io = new IntersectionObserver(
      (entries) => {
        const top = entries.filter((e) => e.isIntersecting).sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
        if (top) setActive(top.target === heroRef.current ? null : top.target.id);
      },
      { root, threshold: [0.2, 0.45, 0.7] }
    );
    targets.forEach((el) => io.observe(el));
    return () => io.disconnect();
  }, []);

  const jump = useCallback(
    (id: string) => {
      const root = containerRef.current;
      const el = id === 'top' ? heroRef.current : document.getElementById(id);
      if (!root || !el) return;
      root.scrollTo({ top: el.offsetTop, behavior: reduce ? 'auto' : 'smooth' });
    },
    [reduce]
  );

  return (
    <div className="uc" ref={containerRef}>
      <motion.span className="uc-progress" style={{ scaleX: scrollYProgress }} aria-hidden />
      <Nav solid={solid} active={active} onStart={onStart} onJump={jump} />
      <main>
        <Hero container={containerRef} sectionRef={heroRef} reduce={reduce} onStart={onStart} onExplore={() => jump('stream')} />
        <TheStream container={containerRef} reduce={reduce} />
        <OneApp container={containerRef} reduce={reduce} />
        <StreamFilm container={containerRef} reduce={reduce} />
        <TheRoom container={containerRef} reduce={reduce} />
        <GetStarted onStart={onStart} />
      </main>
      <Footer />
      <div className="uc-grain" aria-hidden />
    </div>
  );
}
