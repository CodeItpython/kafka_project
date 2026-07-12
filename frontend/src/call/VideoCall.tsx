import { useCallback, useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { Client, IMessage } from '@stomp/stompjs';
import { Phone, PhoneOff, PhoneIncoming, Video, VideoOff, Mic, MicOff, SwitchCamera } from 'lucide-react';
import { playSound } from '../sound';

export type CallPeer = { email: string; name: string; image: string | null };
export type StartCall = (peer: CallPeer, roomId: string | null) => void;

type SignalType =
  | 'INVITE' | 'ACCEPT' | 'REJECT' | 'CANCEL'
  | 'OFFER' | 'ANSWER' | 'ICE' | 'HANGUP';

type CallSignal = {
  type: SignalType;
  toEmail: string;
  fromEmail?: string;
  fromName?: string;
  fromImage?: string | null;
  roomId?: string | null;
  video?: boolean;
  // RTCSessionDescriptionInit for OFFER/ANSWER, RTCIceCandidateInit for ICE.
  data?: RTCSessionDescriptionInit | RTCIceCandidateInit | null;
};

// idle → no call; outgoing → I called, waiting for accept; incoming → someone is
// calling me; active → both accepted, media negotiating/flowing.
type CallStatus = 'outgoing' | 'incoming' | 'active';

type ActiveCall = {
  status: CallStatus;
  peer: CallPeer;
  roomId: string | null;
  initiator: boolean;
};

type Props = {
  token: string;
  user: { email: string; name: string; profileImageUrl: string | null };
  // Hands the parent a startCall() it can trigger from the conversation header.
  onExposeStart: (start: StartCall) => void;
};

const ICE_SERVERS: RTCIceServer[] = [{ urls: 'stun:stun.l.google.com:19302' }];
const MEDIA: MediaStreamConstraints = { video: true, audio: true };

function Avatar({ peer, size }: { peer: CallPeer; size: number }) {
  if (peer.image) {
    return <img className="call-avatar" src={peer.image} alt="" style={{ width: size, height: size }} />;
  }
  return (
    <span className="call-avatar call-avatar-fallback" style={{ width: size, height: size, fontSize: size * 0.4 }}>
      {peer.name.slice(0, 2)}
    </span>
  );
}

export default function VideoCall({ token, user, onExposeStart }: Props) {
  const [call, setCall] = useState<ActiveCall | null>(null);
  const [rtcConnected, setRtcConnected] = useState(false);
  const [micOn, setMicOn] = useState(true);
  const [camOn, setCamOn] = useState(true);
  const [callSecs, setCallSecs] = useState(0);
  const [facing, setFacing] = useState<'user' | 'environment'>('user');
  const [error, setError] = useState<string | null>(null);

  const clientRef = useRef<Client | null>(null);
  const pcRef = useRef<RTCPeerConnection | null>(null);
  const localStreamRef = useRef<MediaStream | null>(null);
  const remoteStreamRef = useRef<MediaStream | null>(null);
  const callRef = useRef<ActiveCall | null>(null);
  const pendingIceRef = useRef<RTCIceCandidateInit[]>([]);
  const localVideoRef = useRef<HTMLVideoElement | null>(null);
  const remoteVideoRef = useRef<HTMLVideoElement | null>(null);

  callRef.current = call;

  const send = useCallback((signal: CallSignal) => {
    clientRef.current?.publish({ destination: '/app/call/signal', body: JSON.stringify(signal) });
  }, []);

  // Tear down media + peer connection but leave the socket alive for the next call.
  const teardown = useCallback(() => {
    pcRef.current?.close();
    pcRef.current = null;
    localStreamRef.current?.getTracks().forEach((t) => t.stop());
    localStreamRef.current = null;
    remoteStreamRef.current = null;
    pendingIceRef.current = [];
    setRtcConnected(false);
    setMicOn(true);
    setCamOn(true);
  }, []);

  const endCall = useCallback((notifyPeer: boolean) => {
    const current = callRef.current;
    if (notifyPeer && current) {
      send({ type: 'HANGUP', toEmail: current.peer.email });
    }
    teardown();
    setCall(null);
  }, [send, teardown]);

  const createPeerConnection = useCallback((peerEmail: string) => {
    const pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });
    pc.onicecandidate = (event) => {
      if (event.candidate) {
        send({ type: 'ICE', toEmail: peerEmail, data: event.candidate.toJSON() });
      }
    };
    pc.ontrack = (event) => {
      const stream = event.streams[0] ?? new MediaStream([event.track]);
      remoteStreamRef.current = stream;
      if (remoteVideoRef.current) remoteVideoRef.current.srcObject = stream;
      setRtcConnected(true);
    };
    pc.onconnectionstatechange = () => {
      if (pc.connectionState === 'connected') setRtcConnected(true);
      if (pc.connectionState === 'failed' || pc.connectionState === 'closed') {
        setRtcConnected(false);
      }
    };
    pcRef.current = pc;
    return pc;
  }, [send]);

  // Acquire mic+camera, attach to the peer connection. Returns false on failure.
  const acquireMedia = useCallback(async (pc: RTCPeerConnection) => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia(MEDIA);
      localStreamRef.current = stream;
      if (localVideoRef.current) localVideoRef.current.srcObject = stream;
      stream.getTracks().forEach((track) => pc.addTrack(track, stream));
      return true;
    } catch {
      setError('카메라·마이크를 사용할 수 없어요. 권한을 확인해주세요.');
      return false;
    }
  }, []);

  const startCall = useCallback<StartCall>((peer, roomId) => {
    if (callRef.current) return; // already in/ringing a call
    setError(null);
    // Show the outgoing UI immediately, then get media. Only ring the callee once
    // our own camera/mic is ready — if it fails we bail without ever inviting them.
    setCall({ status: 'outgoing', peer, roomId, initiator: true });
    const pc = createPeerConnection(peer.email);
    acquireMedia(pc).then((ok) => {
      if (!ok) { teardown(); setCall(null); return; }
      send({
        type: 'INVITE', toEmail: peer.email, roomId, video: true,
        fromName: user.name, fromImage: user.profileImageUrl,
      });
    });
  }, [acquireMedia, createPeerConnection, send, teardown, user.name, user.profileImageUrl]);

  const acceptCall = useCallback(async () => {
    const current = callRef.current;
    if (!current || current.status !== 'incoming') return;
    // Switch to the in-call view right away (shows "연결 중…"), then get media.
    setCall({ ...current, status: 'active' });
    const pc = createPeerConnection(current.peer.email);
    const ok = await acquireMedia(pc);
    if (!ok) { endCall(true); return; }
    send({ type: 'ACCEPT', toEmail: current.peer.email });
  }, [acquireMedia, createPeerConnection, endCall, send]);

  const rejectCall = useCallback(() => {
    const current = callRef.current;
    if (current) send({ type: 'REJECT', toEmail: current.peer.email });
    teardown();
    setCall(null);
  }, [send, teardown]);

  const drainPendingIce = useCallback(async () => {
    const pc = pcRef.current;
    if (!pc) return;
    const pending = pendingIceRef.current;
    pendingIceRef.current = [];
    for (const candidate of pending) {
      try { await pc.addIceCandidate(candidate); } catch { /* stale candidate */ }
    }
  }, []);

  const handleSignal = useCallback(async (signal: CallSignal) => {
    const current = callRef.current;
    const pc = pcRef.current;
    switch (signal.type) {
      case 'INVITE': {
        if (current) { send({ type: 'REJECT', toEmail: signal.fromEmail! }); return; } // busy
        playSound('notify');
        setCall({
          status: 'incoming',
          peer: { email: signal.fromEmail!, name: signal.fromName || signal.fromEmail!, image: signal.fromImage ?? null },
          roomId: signal.roomId ?? null,
          initiator: false,
        });
        return;
      }
      case 'CANCEL': {
        if (current && current.status === 'incoming') { teardown(); setCall(null); }
        return;
      }
      case 'REJECT': {
        if (current && current.initiator) { setError('상대가 통화를 거절했어요.'); teardown(); setCall(null); }
        return;
      }
      case 'ACCEPT': {
        // Callee accepted; as the caller, create and send the WebRTC offer.
        if (!pc || !current || !current.initiator) return;
        setCall({ ...current, status: 'active' });
        const offer = await pc.createOffer();
        await pc.setLocalDescription(offer);
        send({ type: 'OFFER', toEmail: current.peer.email, data: offer });
        return;
      }
      case 'OFFER': {
        if (!pc || !current) return;
        await pc.setRemoteDescription(new RTCSessionDescription(signal.data as RTCSessionDescriptionInit));
        await drainPendingIce();
        const answer = await pc.createAnswer();
        await pc.setLocalDescription(answer);
        send({ type: 'ANSWER', toEmail: current.peer.email, data: answer });
        return;
      }
      case 'ANSWER': {
        if (!pc) return;
        await pc.setRemoteDescription(new RTCSessionDescription(signal.data as RTCSessionDescriptionInit));
        await drainPendingIce();
        return;
      }
      case 'ICE': {
        const candidate = signal.data as RTCIceCandidateInit;
        if (pc && pc.remoteDescription) {
          try { await pc.addIceCandidate(candidate); } catch { /* stale */ }
        } else {
          pendingIceRef.current.push(candidate); // buffer until remoteDescription is set
        }
        return;
      }
      case 'HANGUP': {
        if (current) { teardown(); setCall(null); }
        return;
      }
    }
  }, [drainPendingIce, send, teardown]);

  const handleSignalRef = useRef(handleSignal);
  handleSignalRef.current = handleSignal;

  // Global signaling socket — must stay connected on any screen so INVITEs arrive
  // even outside the room. Keep deps minimal (like the notification socket) and
  // read the current handler through a ref so a re-render never tears it down.
  useEffect(() => {
    if (!token || !user.email) return;
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const client = new Client({
      brokerURL: `${protocol}//${window.location.host}/ws-signal/websocket`,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
      onConnect: () => {
        client.subscribe('/user/queue/call', (frame: IMessage) => {
          handleSignalRef.current(JSON.parse(frame.body) as CallSignal);
        });
      },
    });
    clientRef.current = client;
    client.activate();
    return () => {
      clientRef.current = null;
      client.deactivate();
    };
  }, [token, user.email]);

  useEffect(() => { onExposeStart(startCall); }, [onExposeStart, startCall]);

  // On unmount (e.g. logout tears down the authed tree) stop the camera/mic and
  // close the peer connection. teardown() is stable, so this runs only at unmount —
  // the socket effect above deliberately leaves media alone since it re-runs on
  // token/user changes.
  useEffect(() => () => teardown(), [teardown]);

  // Attach media streams whenever the in-call view (re)mounts its <video> tags.
  useEffect(() => {
    if (localVideoRef.current && localStreamRef.current) localVideoRef.current.srcObject = localStreamRef.current;
    if (remoteVideoRef.current && remoteStreamRef.current) remoteVideoRef.current.srcObject = remoteStreamRef.current;
  }, [call?.status]);

  useEffect(() => {
    if (!error) return;
    const t = window.setTimeout(() => setError(null), 3200);
    return () => window.clearTimeout(t);
  }, [error]);

  // Give up an unanswered outgoing call after 30s so the caller isn't stuck ringing.
  useEffect(() => {
    if (call?.status !== 'outgoing') return;
    const t = window.setTimeout(() => {
      if (callRef.current?.status === 'outgoing') {
        setError('상대가 응답하지 않았어요.');
        endCall(true);
      }
    }, 30000);
    return () => window.clearTimeout(t);
  }, [call?.status, endCall]);

  const toggleMic = () => {
    const stream = localStreamRef.current;
    if (!stream) return;
    const next = !micOn;
    stream.getAudioTracks().forEach((t) => { t.enabled = next; });
    setMicOn(next);
  };
  const toggleCamera = () => {
    const stream = localStreamRef.current;
    if (!stream) return;
    const next = !camOn;
    stream.getVideoTracks().forEach((t) => { t.enabled = next; });
    setCamOn(next);
  };

  // 전면/후면 카메라 전환: 새 트랙을 얻어 sender에 교체(replaceTrack)한다.
  // 카메라가 하나뿐인 기기(데스크톱)에선 실패할 수 있어 안내만 띄운다.
  const switchCamera = async () => {
    const pc = pcRef.current;
    const stream = localStreamRef.current;
    if (!pc || !stream) return;
    const next = facing === 'user' ? 'environment' : 'user';
    try {
      const cam = await navigator.mediaDevices.getUserMedia({ video: { facingMode: next }, audio: false });
      const track = cam.getVideoTracks()[0];
      if (!track) return;
      const sender = pc.getSenders().find((s) => s.track && s.track.kind === 'video');
      if (sender) await sender.replaceTrack(track);
      stream.getVideoTracks().forEach((t) => { stream.removeTrack(t); t.stop(); });
      track.enabled = camOn;
      stream.addTrack(track);
      if (localVideoRef.current) localVideoRef.current.srcObject = stream;
      setFacing(next);
    } catch {
      setError('카메라를 전환할 수 없어요.');
    }
  };

  // 연결되면 통화 시간 카운트업.
  useEffect(() => {
    if (!rtcConnected) { setCallSecs(0); return; }
    const start = Date.now();
    const id = window.setInterval(() => setCallSecs(Math.floor((Date.now() - start) / 1000)), 1000);
    return () => window.clearInterval(id);
  }, [rtcConnected]);

  const durationLabel = `${String(Math.floor(callSecs / 60)).padStart(2, '0')}:${String(callSecs % 60).padStart(2, '0')}`;

  return (
    <>
      <AnimatePresence>
        {error && (
          <motion.div
            className="call-error"
            initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 12 }}
          >
            {error}
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {call && (
          <motion.div
            className="call-overlay"
            role="dialog" aria-modal="true" aria-label="영상 통화"
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
          >
            {call.status === 'active' ? (
              <div className="call-stage">
                <video ref={remoteVideoRef} className="call-remote" autoPlay playsInline />
                {!rtcConnected && (
                  <div className="call-connecting">
                    <Avatar peer={call.peer} size={96} />
                    <strong>{call.peer.name}</strong>
                    <span>연결 중…</span>
                  </div>
                )}
                <div className="call-topbar">
                  <strong>{call.peer.name}</strong>
                  <span className="call-status">
                    <span className={`call-dot${rtcConnected ? ' on' : ''}`} aria-hidden />
                    {rtcConnected ? `${durationLabel} · 연결됨` : '연결 중…'}
                  </span>
                </div>
                <video ref={localVideoRef} className={`call-local${camOn ? '' : ' is-off'}`} autoPlay playsInline muted />
                <div className="call-controls">
                  <div className="call-control-bar">
                    <button type="button" className={`call-ctrl${micOn ? '' : ' is-off'}`} onClick={toggleMic}>
                      <span className="call-ctrl-ico">{micOn ? <Mic size={22} aria-hidden /> : <MicOff size={22} aria-hidden />}</span>
                      <span className="call-ctrl-label">음소거</span>
                    </button>
                    <button type="button" className={`call-ctrl${camOn ? '' : ' is-off'}`} onClick={toggleCamera}>
                      <span className="call-ctrl-ico">{camOn ? <Video size={22} aria-hidden /> : <VideoOff size={22} aria-hidden />}</span>
                      <span className="call-ctrl-label">카메라</span>
                    </button>
                    <button type="button" className="call-ctrl" onClick={switchCamera}>
                      <span className="call-ctrl-ico"><SwitchCamera size={22} aria-hidden /></span>
                      <span className="call-ctrl-label">전환</span>
                    </button>
                    <button type="button" className="call-ctrl call-hangup" onClick={() => endCall(true)}>
                      <span className="call-ctrl-ico"><PhoneOff size={22} aria-hidden /></span>
                      <span className="call-ctrl-label">종료</span>
                    </button>
                  </div>
                </div>
              </div>
            ) : (
              <div className="call-ringing">
                <span className="call-ring-badge" aria-hidden>
                  {call.status === 'incoming' ? <PhoneIncoming size={22} /> : <Phone size={22} />}
                </span>
                <Avatar peer={call.peer} size={112} />
                <strong className="call-ring-name">{call.peer.name}</strong>
                {/* Authenticated identity — the display name above is caller-supplied and could be spoofed. */}
                <span className="call-ring-email">{call.peer.email}</span>
                <span className="call-ring-sub">
                  {call.status === 'incoming' ? '영상 통화 수신 중…' : '영상 통화 거는 중…'}
                </span>
                <div className="call-ring-actions">
                  {call.status === 'incoming' ? (
                    <>
                      <button type="button" className="call-ring-btn call-decline" onClick={rejectCall} aria-label="거절">
                        <PhoneOff size={26} aria-hidden />
                      </button>
                      <button type="button" className="call-ring-btn call-accept" onClick={acceptCall} aria-label="수락">
                        <Video size={26} aria-hidden />
                      </button>
                    </>
                  ) : (
                    <button type="button" className="call-ring-btn call-decline" onClick={() => endCall(true)} aria-label="취소">
                      <PhoneOff size={26} aria-hidden />
                    </button>
                  )}
                </div>
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}
