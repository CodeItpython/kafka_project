import { RefObject } from 'react';
import { MEDIA } from './media';
import LazyVideo from './LazyVideo';

// 마우스를 올리거나 포커스하면 짧은 설명이 나타난다 (팝업 없음)
const ITEMS = [
  { k: '영상통화', d: '대화방에서 바로 1:1 통화로. 브라우저 안에서 얼굴을 봅니다.' },
  { k: '음성 메시지', d: '길게 눌러 말하고, 놓으면 보냅니다. 파형으로 남습니다.' },
  { k: '링크 미리보기', d: '붙여넣은 링크는 제목과 이미지가 담긴 카드로 펼쳐집니다.' },
  { k: '다크 모드', d: '기기 설정을 따르거나 직접 고릅니다. 계정에 저장됩니다.' }
];

export default function TheRoom({ container, reduce }: { container: RefObject<HTMLDivElement | null>; reduce: boolean }) {
  return (
    <section className="uc-sec uc-room" id="room" aria-labelledby="room-title">
      <LazyVideo container={container} src={MEDIA.room} className="uc-room-video" label="기기 화면 위 통화가 시작되는 장면" reduce={reduce} />
      <div className="uc-room-shade" aria-hidden />
      <div className="uc-grid uc-room-grid">
        <div className="uc-col-head">
          <p className="uc-eyebrow"><span className="uc-no">04</span> THE ROOM</p>
          <h2 id="room-title">NOT A CHAT.<br />A ROOM.</h2>
          <p className="uc-body">목소리도 얼굴도 같은 자리에서.</p>
        </div>
        <ul className="uc-col-body uc-room-list">
          {ITEMS.map((it) => (
            <li key={it.k}>
              <button type="button" className="uc-room-item" aria-describedby={`room-${it.k}`}>
                <span>{it.k}</span>
                <small id={`room-${it.k}`}>{it.d}</small>
              </button>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
