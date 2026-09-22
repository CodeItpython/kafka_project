import { RefObject, useMemo } from 'react';
import { motion, MotionValue, useScroll, useTransform } from 'motion/react';
import { ArrowRight } from 'lucide-react';
import { MEDIA, prefersLowBandwidth } from './media';
import { useScrubVideo } from './useScrubVideo';

type Props = {
  container: RefObject<HTMLDivElement | null>;
  sectionRef: RefObject<HTMLElement | null>;
  reduce: boolean;
  onStart: () => void;
  onExplore: () => void;
};

// 스크롤 진행률 → 구간별 등장/퇴장 불투명도
function useStage(p: MotionValue<number>, from: number, to: number, fadeIn = 0.06, fadeOut = 0.06) {
  return useTransform(p, [from, from + fadeIn, to - fadeOut, to], [0, 1, 1, 0]);
}

export default function Hero({ container, sectionRef, reduce, onStart, onExplore }: Props) {
  const { scrollYProgress: p } = useScroll({ container, target: sectionRef, offset: ['start start', 'end end'] });
  const { videoRef, ready, buffered, failed } = useScrubVideo(p, !reduce);
  const lowBw = useMemo(prefersLowBandwidth, []);

  // 첫 카피는 0%부터 보이고 30% 직전에 사라진다
  const s1 = useTransform(p, [0, 0.22, 0.3], [1, 1, 0]);
  const s2 = useStage(p, 0.35, 0.65, 0.08, 0.1);
  const s3 = useTransform(p, [0.75, 0.86], [0, 1]);
  const cta = useTransform(p, [0.85, 0.94], [0, 1]);
  const ctaPointer = useTransform(cta, (v) => (v > 0.5 ? 'auto' : 'none'));
  // 보이지 않는 동안엔 탭 순서에서도 빠지도록 visibility 로 함께 숨긴다
  const ctaVisibility = useTransform(cta, (v) => (v > 0.02 ? 'visible' : 'hidden'));
  const y3 = useTransform(p, [0.75, 0.9], [24, 0]);

  // reduced-motion 또는 영상 로드 실패: 3장의 스틸(스트림 내부 / 코어 / 완성 기기)이 진행률에 따라 전환.
  // 영상 실패 시에는 스크롤에 맞춰 아주 느리게 확대해 정지 화면처럼 보이지 않게 한다.
  const stillA = useTransform(p, [0, 0.3, 0.4], [1, 1, 0]);
  const stillB = useTransform(p, [0.3, 0.4, 0.65, 0.75], [0, 1, 1, 0]);
  const stillC = useTransform(p, [0.65, 0.75], [0, 1]);
  const stillScale = useTransform(p, [0, 1], [1.06, 1]);
  const showStills = reduce || failed;

  return (
    <section className="uc-hero" ref={sectionRef} id="top" aria-label="KAFKATALK 소개 영상">
      <div className="uc-hero-sticky">
        {reduce ? null : (
          <>
            <video
              ref={videoRef}
              className="uc-hero-video"
              poster={MEDIA.hero.poster}
              preload="auto"
              muted
              playsInline
              disablePictureInPicture
              aria-hidden
              tabIndex={-1}
            >
              {!lowBw && <source src={MEDIA.hero.webm} type="video/webm" />}
              <source src={lowBw ? MEDIA.hero.mp4Mobile : MEDIA.hero.mp4} type="video/mp4" />
            </video>
            {!failed && buffered < 0.999 && (
              <div className="uc-hero-load" aria-hidden>
                <span>{ready ? 'BUFFERING' : 'LOADING'}</span>
                <i style={{ transform: `scaleX(${Math.max(0.04, buffered)})` }} />
              </div>
            )}
          </>
        )}
        {showStills && (
          <motion.div className="uc-hero-stills" aria-hidden style={{ scale: reduce ? 1 : stillScale }}>
            <motion.img src={MEDIA.hero.stills[0]} alt="" style={{ opacity: stillA }} />
            <motion.img src={MEDIA.hero.stills[1]} alt="" style={{ opacity: stillB }} />
            <motion.img src={MEDIA.hero.stills[2]} alt="" style={{ opacity: stillC }} />
          </motion.div>
        )}
        <div className="uc-hero-shade" aria-hidden />

        <motion.p className="uc-stage uc-stage-1" style={{ opacity: s1 }}>메시지는 먼저 움직인다.</motion.p>
        <motion.p className="uc-stage uc-stage-2" style={{ opacity: s2 }}>수천 개의 흐름이<br />하나의 대화가 된다.</motion.p>

        <motion.div className="uc-stage uc-stage-3" style={{ opacity: s3, y: y3 }}>
          <p className="uc-eyebrow">KAFKATALK</p>
          <h1>연결은,<br />보이지 않는 곳에서<br />시작된다.</h1>
          <p className="uc-lead">실시간 메신저 · 뉴스 · 쇼핑 · 영상통화 — ONE APP</p>
          <motion.div className="uc-actions" style={{ opacity: cta, pointerEvents: ctaPointer, visibility: ctaVisibility }}>
            <button type="button" className="uc-pill solid lg" onClick={onStart}>시작하기 <ArrowRight size={16} aria-hidden /></button>
            <button type="button" className="uc-pill lg" onClick={onExplore}>둘러보기</button>
          </motion.div>
        </motion.div>

        <motion.div className="uc-hero-hint" style={{ opacity: s1 }} aria-hidden>
          <span>SCROLL</span><i />
        </motion.div>
      </div>
    </section>
  );
}
