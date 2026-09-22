import { RefObject, useEffect, useRef, useState } from 'react';

type Src = { mp4: string; webm: string; poster: string };

// 뷰포트에 가까워질 때만 소스를 붙이는 무음 루프 영상(장식). 히어로 이외의 영상은 전부 이걸 쓴다.
// 5초 넘게 움직이는 콘텐츠에는 멈출 수단이 있어야 하므로(WCAG 2.2.2) 작은 토글을 함께 렌더한다.
export default function LazyVideo({
  container,
  src,
  className,
  reduce
}: {
  container: RefObject<HTMLDivElement | null>;
  src: Src;
  className?: string;
  reduce: boolean;
}) {
  const ref = useRef<HTMLVideoElement>(null);
  const [near, setNear] = useState(false);
  const [paused, setPaused] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el || reduce) return;
    const io = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          setNear(true);
          io.disconnect();
        }
      },
      { root: container.current, rootMargin: '60% 0px' }
    );
    io.observe(el);
    return () => io.disconnect();
  }, [container, reduce]);

  useEffect(() => {
    const el = ref.current;
    if (!el || !near) return;
    el.load();
    // 자동재생 정책: muted 이므로 대부분 허용. 저전력 모드 등에서 거절되면 poster 만 남긴다
    el.play().catch(() => {});
  }, [near]);

  const toggle = () => {
    const el = ref.current;
    if (!el) return;
    if (el.paused) {
      el.play().catch(() => {});
      setPaused(false);
    } else {
      el.pause();
      setPaused(true);
    }
  };

  return (
    <div className={`uc-video${className ? ` ${className}` : ''}`}>
      <video ref={ref} poster={src.poster} preload="none" muted loop playsInline disablePictureInPicture aria-hidden tabIndex={-1}>
        {near && !reduce && (
          <>
            <source src={src.webm} type="video/webm" />
            <source src={src.mp4} type="video/mp4" />
          </>
        )}
      </video>
      {near && !reduce && (
        <button type="button" className="uc-video-toggle" onClick={toggle} aria-pressed={paused} aria-label={paused ? '배경 영상 재생' : '배경 영상 일시정지'}>
          {paused ? 'PLAY' : 'PAUSE'}
        </button>
      )}
    </div>
  );
}
