import { motion, Variants } from 'motion/react';
import { RefObject } from 'react';

// 기존 WelcomeLanding FEATURES 카피 재사용 + GAMES 추가
const FEATURES = [
  { no: '01', label: 'REALTIME', title: '보내는 순간, 그대로 도착합니다.', desc: '1:1도 그룹도 끊김 없이. 읽음·답장·반응까지 대화의 결을 그대로 옮겼습니다.' },
  { no: '02', label: 'NEWS', title: '오늘을 아는 가장 빠른 방법.', desc: '경제·증시·IT·세계. 카드로 넘겨보고, 마음이 가는 기사는 원문으로 바로 이어집니다.' },
  { no: '03', label: 'COMMERCE', title: '검색부터 장바구니까지, 대화를 벗어나지 않고.', desc: '카테고리별 인기 상품을 둘러보고 검색해 담으세요. 앱을 옮겨 다닐 필요가 없습니다.' },
  { no: '04', label: 'CONNECT', title: '영상통화와 음성 메시지까지.', desc: '목소리도 얼굴도 같은 자리에서. 대화를 끊지 않고 그대로 이어집니다.' },
  { no: '05', label: 'GAMES', title: '대화방 안에서 바로 하는 미니게임.', desc: '스네이크·테트리스·2048로 같은 방 친구와 점수를 겨룹니다. 결과는 대화에 그대로 남습니다.' }
];

const EASE = [0.22, 1, 0.36, 1] as const;
const rise: Variants = { hidden: { opacity: 0, y: 16 }, show: { opacity: 1, y: 0, transition: { duration: 0.75, ease: EASE } } };

export default function OneApp({ container, reduce }: { container: RefObject<HTMLDivElement | null>; reduce: boolean }) {
  const item = (i: number) =>
    reduce
      ? {}
      : { variants: rise, initial: 'hidden', whileInView: 'show', viewport: { root: container, amount: 0.5, once: true }, transition: { delay: i * 0.03 } };
  return (
    <section className="uc-sec uc-oneapp" id="oneapp" aria-labelledby="oneapp-title">
      <div className="uc-grid">
        <div className="uc-col-head">
          <p className="uc-eyebrow"><span className="uc-no">02</span> ONE APP</p>
          <h2 id="oneapp-title">EVERYTHING<br />IN ONE<br />CONVERSATION</h2>
        </div>
        <ol className="uc-col-body uc-features">
          {FEATURES.map((f, i) => (
            <motion.li key={f.no} {...item(i)}>
              <span className="uc-feat-meta"><span className="uc-no">{f.no}</span><span>{f.label}</span></span>
              <h3>{f.title}</h3>
              <p>{f.desc}</p>
            </motion.li>
          ))}
        </ol>
      </div>
    </section>
  );
}
