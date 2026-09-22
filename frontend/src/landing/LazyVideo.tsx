import { RefObject, useEffect, useRef, useState } from 'react';

type Src = { mp4: string; webm: string; poster: string };

// 뷰포트에 가까워질 때만 소스를 붙이는 무음 루프 영상. 히어로 이외의 영상은 전부 이걸 쓴다.
export default function LazyVideo({
  container,
  src,
  className,
  label,
  reduce
}: {
  container: RefObject<HTMLDivElement | null>;
  src: Src;
  className?: string;
  label: string;
  reduce: boolean;
}) {
  const ref = useRef<HTMLVideoElement>(null);
  const [near, setNear] = useState(false);

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

  return (
    <video
      ref={ref}
      className={className}
      poster={src.poster}
      preload="none"
      muted
      loop
      playsInline
      disablePictureInPicture
      aria-label={label}
    >
      {near && !reduce && (
        <>
          <source src={src.webm} type="video/webm" />
          <source src={src.mp4} type="video/mp4" />
        </>
      )}
    </video>
  );
}
