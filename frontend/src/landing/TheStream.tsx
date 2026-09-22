import { motion, Variants } from 'motion/react';
import { RefObject } from 'react';

// 수치는 코드베이스에서 확인된 것만 (backend/settings.gradle, chat-service ChatService/ChatKafkaConfig)
const STATS = [
  { n: '06', unit: 'SERVICES', desc: 'auth · news · chat · shopping · signaling · order — 독립 배포되는 여섯 개의 서비스' },
  { n: '04', unit: 'REALTIME CHANNELS', desc: '메시지 · 읽음 · 전달 상태 · 게임. STOMP over WebSocket 으로 방마다 열린 네 갈래' },
  { n: '01', unit: 'STREAM', desc: 'Kafka 토픽 하나로 흐르는 대화. Transactional Outbox 와 재시도·DLT 로 유실 없이' }
];

const ITEMS = [
  { k: '실시간 전달', d: '보내는 순간 같은 방의 모든 기기에 도착합니다. 새로 고침도, 기다림도 없습니다.' },
  { k: '읽음 · 답장 · 반응', d: '누가 읽었는지, 어느 말에 답했는지, 어떤 반응을 남겼는지 — 대화의 결이 그대로 남습니다.' },
  { k: '그룹 대화', d: '하나의 메시지가 여러 사람에게 동시에. 참여자가 늘어도 순서는 흐트러지지 않습니다.' }
];

const EASE = [0.22, 1, 0.36, 1] as const;
const group: Variants = { hidden: {}, show: { transition: { staggerChildren: 0.1 } } };
const rise: Variants = { hidden: { opacity: 0, y: 18 }, show: { opacity: 1, y: 0, transition: { duration: 0.8, ease: EASE } } };

export default function TheStream({ container, reduce }: { container: RefObject<HTMLDivElement | null>; reduce: boolean }) {
  const v = reduce ? {} : { variants: group, initial: 'hidden', whileInView: 'show', viewport: { root: container, amount: 0.3, once: true } };
  const item = reduce ? {} : { variants: rise };
  return (
    <section className="uc-sec uc-stream" id="stream" aria-labelledby="stream-title">
      <motion.div className="uc-grid" {...v}>
        <motion.div className="uc-col-head" {...item}>
          <p className="uc-eyebrow"><span className="uc-no">01</span> THE STREAM</p>
          <h2 id="stream-title">BUILT ON<br />THE STREAM</h2>
          <p className="uc-body">한 통의 메시지는 한 번에 도착하지 않는다.<br />수천 개의 흐름이 정확한 순간에 만날 때 대화가 된다.</p>
        </motion.div>
        <div className="uc-col-body">
          <dl className="uc-stats">
            {STATS.map((s) => (
              <motion.div className="uc-stat" key={s.unit} {...item}>
                <dt><b>{s.n}</b><span>{s.unit}</span></dt>
                <dd>{s.desc}</dd>
              </motion.div>
            ))}
          </dl>
          <ul className="uc-catalog">
            {ITEMS.map((it, i) => (
              <motion.li key={it.k} {...item}>
                <span className="uc-idx">{String(i + 1).padStart(2, '0')}</span>
                <div><h3>{it.k}</h3><p>{it.d}</p></div>
              </motion.li>
            ))}
          </ul>
        </div>
      </motion.div>
    </section>
  );
}
