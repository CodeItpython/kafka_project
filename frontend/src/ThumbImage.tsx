import { useState } from 'react';

// 썸네일 이미지: 로드 전까지 스켈레톤(시머)을 보여주고, 로드되면 부드럽게 페이드인.
// 이미지가 갑자기 나타나는(팝인) 현상을 없앤다. 뉴스·쇼핑 카드 공용.
export default function ThumbImage({ src }: { src: string }) {
  const [loaded, setLoaded] = useState(false);
  return (
    <>
      <span className={loaded ? 'thumb-skeleton done' : 'thumb-skeleton'} aria-hidden />
      <img
        src={src}
        alt=""
        loading="lazy"
        referrerPolicy="no-referrer"
        className={loaded ? 'thumb-img loaded' : 'thumb-img'}
        onLoad={() => setLoaded(true)}
        onError={() => setLoaded(true)}
      />
    </>
  );
}
