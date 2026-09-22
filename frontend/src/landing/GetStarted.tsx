import { ArrowRight } from 'lucide-react';
import { MEDIA } from './media';

export default function GetStarted({ onStart }: { onStart: () => void }) {
  return (
    <section className="uc-sec uc-start" id="start" aria-labelledby="start-title">
      <figure className="uc-start-figure">
        <img src={MEDIA.hero.last} alt="어두운 스튜디오에 떠 있는 KAFKATALK 기기" loading="lazy" decoding="async" />
      </figure>
      <div className="uc-start-copy">
        <p className="uc-eyebrow">GET STARTED</p>
        <h2 id="start-title">대화의 맥박을<br />직접 느껴보세요.</h2>
        <button type="button" className="uc-pill solid lg" onClick={onStart}>지금 시작하기 <ArrowRight size={16} aria-hidden /></button>
      </div>
    </section>
  );
}
