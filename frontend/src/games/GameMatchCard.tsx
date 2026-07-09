import type { ReactElement } from 'react';
import { Gamepad2, X } from 'lucide-react';
import type { GameKey } from './GameOverlay';

export type GameMatchResponse = {
  id: number;
  roomId: string;
  game: GameKey;
  status: 'WAITING_CHALLENGER' | 'WAITING_OPPONENT' | 'DONE';
  challengerEmail: string;
  challengerName: string;
  challengerScore: number | null;
  opponentEmail: string | null;
  opponentName: string | null;
  opponentScore: number | null;
  winnerEmail: string | null;
};

const GAME_NAMES: Record<GameKey, string> = { SNAKE: '스네이크', TETRIS: '테트리스', G2048: '2048' };

type Props = {
  match: GameMatchResponse | null;
  myEmail: string;
  onPlay: (match: GameMatchResponse) => void;
  onDismiss: () => void;
};

export default function GameMatchCard({ match, myEmail, onPlay, onDismiss }: Props) {
  if (!match) return null;
  const gameName = GAME_NAMES[match.game] ?? '게임';
  const iAmChallenger = match.challengerEmail === myEmail;

  let body: ReactElement;
  if (match.status === 'DONE') {
    const winner = match.winnerEmail === null
      ? '무승부'
      : `${match.winnerEmail === match.challengerEmail ? match.challengerName : match.opponentName} 승리 🏆`;
    body = (
      <div className="gmc-body">
        <span className="gmc-title">🏁 {gameName} 대결 결과</span>
        <span className="gmc-scores">
          {match.challengerName} {match.challengerScore ?? 0} : {match.opponentScore ?? 0} {match.opponentName ?? '상대'}
        </span>
        <strong className="gmc-winner">{winner}</strong>
      </div>
    );
  } else if (match.status === 'WAITING_OPPONENT') {
    body = (
      <div className="gmc-body">
        <span className="gmc-title">🎮 {gameName} 대결</span>
        <span className="gmc-scores">{match.challengerName}님 {match.challengerScore ?? 0}점</span>
        {iAmChallenger ? (
          <span className="gmc-wait">상대의 도전을 기다리는 중…</span>
        ) : (
          <button type="button" className="gmc-play-btn" onClick={() => onPlay(match)}>내 차례 플레이</button>
        )}
      </div>
    );
  } else {
    body = (
      <div className="gmc-body">
        <span className="gmc-title">🎮 {gameName} 대결 준비 중</span>
        <span className="gmc-wait">{match.challengerName}님이 플레이 중…</span>
      </div>
    );
  }

  return (
    <div className="game-match-card">
      <Gamepad2 size={18} aria-hidden className="gmc-icon" />
      {body}
      <button type="button" className="gmc-dismiss" onClick={onDismiss} aria-label="대결 카드 닫기"><X size={15} aria-hidden /></button>
    </div>
  );
}
