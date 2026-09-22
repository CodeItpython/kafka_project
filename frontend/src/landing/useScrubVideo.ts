import { useEffect, useRef, useState } from 'react';
import { MotionValue, useMotionValueEvent } from 'motion/react';

/**
 * 스크롤 진행도(0..1)로 <video>의 currentTime을 스크러빙한다.
 *
 * 브라우저 seek 알고리즘의 함정을 피하는 규칙:
 *  - readyState < HAVE_METADATA(1) 이거나 seekable 범위가 없으면 대입이 조용히 무시된다 → 가드
 *  - seeking 중 다시 대입하면 진행 중인 seek이 취소된다 → pending 1개만 유지, `seeked` 후 다음 seek 발행(chase 패턴)
 *  - 목표값은 lerp로 따라가되, 멈추면 정확히 수렴시켜 되감기/빨리 감기가 어긋나지 않게 한다
 */
export function useScrubVideo(progress: MotionValue<number>, enabled = true) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [ready, setReady] = useState(false);
  const [buffered, setBuffered] = useState(0); // 0..1
  const [failed, setFailed] = useState(false); // 모든 소스 로드 실패 → poster 만 남긴다
  const target = useRef(0);
  const current = useRef(0);
  const seekPending = useRef(false);
  const seekIssuedAt = useRef(0);
  const lastTick = useRef(0);
  const raf = useRef(0);

  const schedule = () => {
    if (!raf.current) raf.current = requestAnimationFrame(tick);
  };

  const trySeek = () => {
    const video = videoRef.current;
    if (!video || !enabled) return;
    if (video.readyState < 1 || !video.seekable.length || !Number.isFinite(video.duration)) return;
    const now = performance.now();
    // 드물게 seeked 가 오지 않는 경우(모바일 Safari)를 위한 데드락 해제
    if (seekPending.current && now - seekIssuedAt.current > 300) seekPending.current = false;
    if (seekPending.current) return;
    const end = video.seekable.end(video.seekable.length - 1);
    const t = Math.min(end - 0.001, current.current * video.duration);
    if (Math.abs(video.currentTime - t) < 1 / 120) return;
    seekPending.current = true;
    seekIssuedAt.current = now;
    video.currentTime = t;
  };

  const tick = (now: number) => {
    raf.current = 0;
    const diff = target.current - current.current;
    if (Math.abs(diff) < 0.0004) {
      current.current = target.current;
    } else {
      // 프레임 간격에 무관한 감쇠. 빠르게 스크롤하다 멈춰도 곧바로 따라붙는다
      const dt = Math.min(0.05, (now - (lastTick.current || now)) / 1000);
      current.current += diff * (1 - Math.exp(-dt * 14));
    }
    lastTick.current = now;
    trySeek();
    if (current.current !== target.current || seekPending.current) schedule();
  };

  useMotionValueEvent(progress, 'change', (v) => {
    target.current = Math.min(1, Math.max(0, v));
    schedule();
  });

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    const onMeta = () => {
      setReady(true);
      target.current = Math.min(1, Math.max(0, progress.get()));
      schedule(); // 메타데이터가 오면 현재 스크롤 위치 프레임을 즉시 표시
    };
    const onSeeked = () => {
      seekPending.current = false;
      schedule(); // 목표가 이동해 있으면 다음 seek 을 바로 이어 발행
    };
    const onProgress = () => {
      if (!Number.isFinite(video.duration) || !video.buffered.length) return;
      setBuffered(Math.min(1, video.buffered.end(video.buffered.length - 1) / video.duration));
    };
    // <source> 자식을 쓰면 마지막 source 에서 error 가 나고 video 는 NETWORK_NO_SOURCE(3) 가 된다
    const sources = Array.from(video.querySelectorAll('source'));
    const onError = () => {
      if (video.networkState === 3 || video.error) setFailed(true);
    };
    video.addEventListener('loadedmetadata', onMeta);
    video.addEventListener('seeked', onSeeked);
    video.addEventListener('progress', onProgress);
    video.addEventListener('canplaythrough', onProgress);
    video.addEventListener('error', onError);
    sources.forEach((s) => s.addEventListener('error', onError));
    if (video.readyState >= 1) onMeta();
    if (video.networkState === 3) setFailed(true);
    return () => {
      video.removeEventListener('loadedmetadata', onMeta);
      video.removeEventListener('seeked', onSeeked);
      video.removeEventListener('progress', onProgress);
      video.removeEventListener('canplaythrough', onProgress);
      video.removeEventListener('error', onError);
      sources.forEach((s) => s.removeEventListener('error', onError));
      if (raf.current) cancelAnimationFrame(raf.current);
      raf.current = 0;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled]);

  return { videoRef, ready, buffered, failed };
}
