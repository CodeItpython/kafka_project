import { useEffect, useMemo, useRef } from 'react';
import { Canvas, useFrame } from '@react-three/fiber';
import { EffectComposer, Bloom } from '@react-three/postprocessing';
import { RoundedBoxGeometry } from 'three/examples/jsm/geometries/RoundedBoxGeometry.js';
import * as THREE from 'three';
import { heroState, ramp } from './heroState';
import { KeyboardAct, pointer } from './KeyboardAct';

/**
 * 영상 대신 실시간으로 렌더하는 히어로 3막.
 *  1막(0~0.55)  키보드 조립 → 로봇 타건        (KeyboardAct)
 *  2막(0.35~0.8) 타건에서 태어난 빛의 리본이 유리 통로를 타고 올라간다 (Stream)
 *  3막(0.65~1)  리본이 도착한 그래파이트 폰이 켜지고 카메라가 멈춘다  (Phone)
 * 카메라는 스크롤 진행도로 CatmullRom 경로를 따라 이동한다.
 */

const PHONE_POS = new THREE.Vector3(1.05, 3.1, 0);

// 키보드 중앙에서 폰 하단까지 S 자로 오르는 경로
const STREAM_CURVE = new THREE.CatmullRomCurve3([
  new THREE.Vector3(0.2, 0.25, 0.2),
  new THREE.Vector3(-0.5, 1.0, 0.5),
  new THREE.Vector3(0.6, 1.7, -0.3),
  new THREE.Vector3(-0.2, 2.35, 0.2),
  new THREE.Vector3(PHONE_POS.x - 0.1, PHONE_POS.y - 0.85, 0.05)
]);

const CAM_PATH = new THREE.CatmullRomCurve3([
  new THREE.Vector3(1.0, 2.0, 4.9),
  new THREE.Vector3(0.5, 2.1, 4.6),
  new THREE.Vector3(-0.9, 2.6, 4.6),
  new THREE.Vector3(-0.1, 3.0, 4.0),
  new THREE.Vector3(-0.15, 3.05, 3.6)
]);
const LOOK_PATH = new THREE.CatmullRomCurve3([
  new THREE.Vector3(0, 0.35, 0),
  new THREE.Vector3(0, 0.6, 0),
  new THREE.Vector3(0.1, 1.6, 0),
  new THREE.Vector3(0.6, 2.9, 0),
  new THREE.Vector3(PHONE_POS.x - 0.62, PHONE_POS.y, 0)
]);

const pulseVert = /* glsl */ `
  varying vec2 vUv;
  void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }
`;
// u = 튜브 길이 방향(0 시작→1 끝). 진행도만큼만 켜지고, 그 위를 펄스 몇 개가 흐른다.
const pulseFrag = /* glsl */ `
  uniform float uTime;
  uniform float uReach;
  uniform float uFade;
  uniform vec3 uColor;
  varying vec2 vUv;
  void main(){
    float head = smoothstep(uReach, uReach - 0.08, vUv.x);
    float base = 0.22 * head;
    float p1 = pow(max(0.0, 1.0 - abs(fract(vUv.x * 1.0 - uTime * 0.35) - 0.5) * 8.0), 3.0);
    float p2 = pow(max(0.0, 1.0 - abs(fract(vUv.x * 1.0 - uTime * 0.35 + 0.45) - 0.5) * 10.0), 3.0);
    float glow = (base + (p1 + p2 * 0.7) * head) * (1.0 - smoothstep(0.96, 1.0, vUv.x));
    if (glow < 0.01) discard;
    gl_FragColor = vec4(uColor * (0.6 + glow * 2.2), glow * uFade);
  }
`;

function Stream() {
  const pulseRef = useRef<THREE.ShaderMaterial>(null);
  const ringsRef = useRef<THREE.Group>(null);
  const coreGeo = useMemo(() => new THREE.TubeGeometry(STREAM_CURVE, 140, 0.02, 10, false), []);
  const glassGeo = useMemo(() => new THREE.TubeGeometry(STREAM_CURVE, 140, 0.048, 14, false), []);
  const ringGeo = useMemo(() => new THREE.TorusGeometry(0.08, 0.006, 8, 40), []);
  const rings = useMemo(() => [0.22, 0.45, 0.7].map((t) => ({ t, pos: STREAM_CURVE.getPointAt(t), tan: STREAM_CURVE.getTangentAt(t) })), []);
  const uniforms = useMemo(() => ({ uTime: { value: 0 }, uReach: { value: 0 }, uFade: { value: 1 }, uColor: { value: new THREE.Color('#5b86ff') } }), []);
  const glassRef = useRef<THREE.MeshPhysicalMaterial>(null);
  const rootRef = useRef<THREE.Group>(null);

  useEffect(() => () => { coreGeo.dispose(); glassGeo.dispose(); ringGeo.dispose(); }, [coreGeo, glassGeo, ringGeo]);

  useFrame((state) => {
    const p = heroState.current;
    const reach = ramp(p, 0.36, 0.72); // 리본이 키보드에서 폰까지 자라는 구간
    // 폰이 켜진 뒤(0.84~0.96) 스트림은 어둠으로 사라진다
    const fade = 1 - ramp(p, 0.84, 0.96);
    uniforms.uTime.value = state.clock.elapsedTime;
    uniforms.uReach.value = reach;
    uniforms.uFade.value = fade;
    if (glassRef.current) glassRef.current.opacity = 0.16 * fade * ramp(p, 0.34, 0.4);
    if (rootRef.current) rootRef.current.visible = p > 0.33 && fade > 0.01;
    if (ringsRef.current) {
      ringsRef.current.children.forEach((r, i) => {
        // 펄스 머리가 지날 때 한 번 밝아지고(읽음), 이후 은은하게 유지
        const lit = ramp(reach, rings[i].t - 0.02, rings[i].t + 0.04);
        const m = (r as THREE.Mesh).material as THREE.MeshStandardMaterial;
        m.emissiveIntensity = (0.15 + lit * (0.6 + Math.pow(Math.max(0, 1 - Math.abs(reach - rings[i].t) * 14), 2) * 3.5)) * fade;
        r.visible = reach > rings[i].t - 0.1;
      });
    }
  });

  return (
    <group ref={rootRef} visible={false}>
      <mesh geometry={glassGeo}>
        <meshPhysicalMaterial ref={glassRef} color="#7f9ff0" roughness={0.15} metalness={0.1} transmission={0.6} thickness={0.1} transparent opacity={0} depthWrite={false} />
      </mesh>
      <mesh geometry={coreGeo}>
        <shaderMaterial ref={pulseRef} vertexShader={pulseVert} fragmentShader={pulseFrag} uniforms={uniforms} transparent depthWrite={false} blending={THREE.AdditiveBlending} />
      </mesh>
      <group ref={ringsRef}>
        {rings.map((r, i) => (
          <mesh key={i} geometry={ringGeo} position={r.pos} quaternion={new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0, 0, 1), r.tan)}>
            <meshStandardMaterial color="#2a3550" emissive="#3d6dff" emissiveIntensity={0.15} roughness={0.3} metalness={0.8} />
          </mesh>
        ))}
      </group>
    </group>
  );
}

// 화면 텍스처: 추상 말풍선·카드 (글자 없음)
function makeScreenTexture() {
  const c = document.createElement('canvas');
  c.width = 512; c.height = 1024;
  const g = c.getContext('2d')!;
  g.fillStyle = '#05070f'; g.fillRect(0, 0, c.width, c.height);
  const grad = g.createRadialGradient(256, 620, 40, 256, 620, 520);
  grad.addColorStop(0, 'rgba(61,109,255,0.35)'); grad.addColorStop(1, 'rgba(61,109,255,0)');
  g.fillStyle = grad; g.fillRect(0, 0, c.width, c.height);
  const rr = (x: number, y: number, w: number, h: number, r: number, fill: string) => {
    g.fillStyle = fill; g.beginPath(); g.roundRect(x, y, w, h, r); g.fill();
  };
  g.filter = 'blur(6px)';
  rr(60, 300, 250, 74, 30, 'rgba(140,175,255,0.75)');
  rr(200, 400, 250, 74, 30, 'rgba(230,238,255,0.85)');
  rr(60, 500, 380, 150, 26, 'rgba(120,150,220,0.55)');
  rr(90, 540, 60, 60, 30, 'rgba(255,255,255,0.7)');
  rr(170, 555, 200, 16, 8, 'rgba(255,255,255,0.7)');
  rr(170, 585, 150, 16, 8, 'rgba(255,255,255,0.5)');
  rr(160, 690, 290, 74, 30, 'rgba(140,175,255,0.75)');
  g.filter = 'none';
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

function Phone() {
  const groupRef = useRef<THREE.Group>(null);
  const screenMat = useRef<THREE.MeshStandardMaterial>(null);
  const sweepRef = useRef<THREE.PointLight>(null);
  const bodyGeo = useMemo(() => new RoundedBoxGeometry(0.74, 1.54, 0.075, 4, 0.085), []);
  const screenTex = useMemo(makeScreenTexture, []);
  useEffect(() => () => { bodyGeo.dispose(); screenTex.dispose(); }, [bodyGeo, screenTex]);

  useFrame((state) => {
    const p = heroState.current;
    const reveal = ramp(p, 0.62, 0.8);   // 폰이 어둠에서 떠오른다
    const wake = ramp(p, 0.74, 0.86);    // 리본 도착 → 화면 점등
    const settle = ramp(p, 0.86, 1.0);   // 마지막 구간 정지
    const g = groupRef.current;
    if (g) {
      g.visible = reveal > 0.001;
      g.position.set(PHONE_POS.x, PHONE_POS.y - (1 - reveal) * 0.6, PHONE_POS.z);
      const t = state.clock.elapsedTime;
      const sway = (1 - settle) * 0.05;
      g.rotation.set(-0.08 + Math.sin(t * 0.5) * sway, -0.42 + Math.sin(t * 0.37) * sway * 2, 0.16);
      g.scale.setScalar(0.85 + reveal * 0.15);
    }
    if (screenMat.current) screenMat.current.emissiveIntensity = wake * 1.35;
    if (sweepRef.current) {
      // 흰 스트립 조명이 테두리를 한 번 훑는다
      const s = ramp(p, 0.8, 0.94);
      sweepRef.current.intensity = Math.sin(s * Math.PI) * 6;
      sweepRef.current.position.set(PHONE_POS.x - 0.6 + s * 1.2, PHONE_POS.y + 0.9 - s * 1.8, 0.45);
    }
  });

  return (
    <group ref={groupRef} visible={false}>
      <mesh geometry={bodyGeo}>
        <meshStandardMaterial color="#262c3a" roughness={0.3} metalness={0.85} />
      </mesh>
      <mesh position={[0, 0, 0.039]}>
        <planeGeometry args={[0.67, 1.45]} />
        <meshStandardMaterial ref={screenMat} color="#000000" emissive="#ffffff" emissiveMap={screenTex} emissiveIntensity={0} roughness={0.15} metalness={0} />
      </mesh>
      <pointLight ref={sweepRef} color="#ffffff" intensity={0} distance={2.2} decay={2} />
      <pointLight position={[-1.2, 0.6, 1.4]} color="#3d6dff" intensity={9} distance={4} decay={2} />
    </group>
  );
}

function Rig() {
  const look = useMemo(() => new THREE.Vector3(), []);
  const pos = useMemo(() => new THREE.Vector3(), []);
  useFrame((state, delta) => {
    // 목표 진행도로 부드럽게 수렴 (빠른 스크롤 뒤에도 곧 따라붙는다)
    heroState.current += (heroState.target - heroState.current) * Math.min(1, delta * 6);
    const p = heroState.current;
    CAM_PATH.getPointAt(p, pos);
    LOOK_PATH.getPointAt(p, look);
    // 세로 화면(aspect<1)은 수평 시야가 좁다: 카메라를 뒤로 빼고, 마지막엔 폰을 가운데·위쪽에 둔다
    const narrow = THREE.MathUtils.clamp((1.05 - state.viewport.aspect) / 0.55, 0, 1);
    pos.z += narrow * 1.6;
    pos.y -= narrow * 0.35; // 세로에선 카메라를 조금 낮춰 보드가 화면 가운데로
    look.x += narrow * 0.62 * ramp(p, 0.6, 0.9);
    look.y -= narrow * 0.55 * ramp(p, 0.6, 0.9);
    state.camera.position.lerp(pos, Math.min(1, delta * 8));
    state.camera.lookAt(look);
  });
  return null;
}

export default function HeroScene() {
  useEffect(() => {
    const onMove = (event: PointerEvent) => {
      pointer.tx = (event.clientX / window.innerWidth) * 2 - 1;
      pointer.ty = -((event.clientY / window.innerHeight) * 2 - 1);
    };
    window.addEventListener('pointermove', onMove, { passive: true });
    return () => window.removeEventListener('pointermove', onMove);
  }, []);
  return (
    <div className="uc-hero-3d" aria-hidden>
      <Canvas dpr={[1, 1.6]} gl={{ antialias: true, alpha: true, powerPreference: 'high-performance' }} camera={{ position: [1.0, 2.0, 4.9], fov: 42 }}>
        <ambientLight intensity={0.4} />
        <directionalLight position={[3, 6, 4]} intensity={1.8} color="#dfe8ff" />
        <pointLight position={[-4, 2.5, 3]} intensity={30} color="#3d6dff" />
        <pointLight position={[3.5, -0.5, 3]} intensity={6} color="#ff7a2f" />
        <Rig />
        <KeyboardAct />
        <Stream />
        <Phone />
        <EffectComposer>
          <Bloom mipmapBlur intensity={0.9} luminanceThreshold={0.55} luminanceSmoothing={0.35} radius={0.85} />
        </EffectComposer>
      </Canvas>
    </div>
  );
}
