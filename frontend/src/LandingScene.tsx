import { useEffect, useMemo, useRef } from 'react';
import { Canvas, useFrame } from '@react-three/fiber';
import { EffectComposer, Bloom } from '@react-three/postprocessing';
import * as THREE from 'three';

// 랜딩 스크롤 진행도(0..1)를 씬으로 전달하는 공유 상태. WelcomeLanding이 target을 갱신하고
// 씬은 current를 부드럽게 따라가며 파티클 형태(uProgress)를 스크러빙한다.
export const landingScroll = { target: 0, current: 0 };

const prefersReducedMotion =
  typeof window !== 'undefined' &&
  window.matchMedia &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const pointer = { x: 0, y: 0, tx: 0, ty: 0 };

const vertexShader = /* glsl */ `
  uniform float uTime;
  uniform float uSize;
  uniform float uPixelRatio;
  uniform float uProgress;
  uniform vec2 uMouse;
  attribute vec3 aPos0;
  attribute vec3 aPos1;
  attribute vec3 aPos2;
  attribute vec3 aPos3;
  attribute float aScale;
  attribute float aSeed;
  varying float vT;
  varying float vGlow;

  vec3 morph(float p){
    float s = clamp(p, 0.0, 1.0) * 3.0;
    float seg = floor(s);
    float t = smoothstep(0.0, 1.0, fract(s));
    vec3 a; vec3 b;
    if (seg < 0.5) { a = aPos0; b = aPos1; }
    else if (seg < 1.5) { a = aPos1; b = aPos2; }
    else { a = aPos2; b = aPos3; }
    return mix(a, b, t);
  }

  void main(){
    vec3 pos = morph(uProgress);
    float wobble = sin(uTime * 0.75 + aSeed * 6.2831) * 0.07;
    pos += normalize(pos + 0.0001) * wobble;
    pos.xy += uMouse * (0.22 + aSeed * 0.28);
    vT = clamp(uProgress, 0.0, 1.0);
    // 개별 파티클이 이따금 번쩍이는 반짝임(twinkle) — pow로 날카로운 피크
    float twinkle = pow(max(0.0, sin(uTime * 1.7 + aSeed * 43.0)), 14.0);
    vGlow = 0.5 + aSeed * 0.85 + twinkle * 2.2;
    // 전체가 은은하게 숨쉬듯 커졌다 작아지는 크기 브리딩
    float breathe = 1.0 + sin(uTime * 0.9) * 0.06;
    vec4 mv = modelViewMatrix * vec4(pos, 1.0);
    gl_PointSize = uSize * aScale * uPixelRatio * breathe * (1.0 / -mv.z);
    gl_Position = projectionMatrix * mv;
  }
`;

const fragmentShader = /* glsl */ `
  precision highp float;
  uniform vec3 uColorA;
  uniform vec3 uColorB;
  uniform vec3 uColorC;
  uniform vec3 uColorD;
  varying float vT;
  varying float vGlow;

  void main(){
    float d = length(gl_PointCoord - 0.5);
    float alpha = smoothstep(0.5, 0.05, d);
    if (alpha <= 0.001) discard;
    float s = vT * 3.0;
    vec3 col;
    if (s < 1.0) col = mix(uColorA, uColorB, s);
    else if (s < 2.0) col = mix(uColorB, uColorC, s - 1.0);
    else col = mix(uColorC, uColorD, s - 2.0);
    gl_FragColor = vec4(col * vGlow, alpha);
  }
`;

function fract(n: number) {
  return n - Math.floor(n);
}

function Particles() {
  const groupRef = useRef<THREE.Group>(null);
  const spinRef = useRef(0);

  const geometry = useMemo(() => {
    const count = 16000;
    const pos0 = new Float32Array(count * 3); // 구체
    const pos1 = new Float32Array(count * 3); // 채팅 버블
    const pos2 = new Float32Array(count * 3); // 네트워크 그래프
    const pos3 = new Float32Array(count * 3); // 빛나는 코어
    const scales = new Float32Array(count);
    const seeds = new Float32Array(count);
    const golden = Math.PI * (3 - Math.sqrt(5));

    // 채팅 버블 치수 (둥근 사각형 + 왼쪽 아래 꼬리)
    const bw = 3.6;
    const bh = 2.3;
    const br = 0.68;

    // 네트워크 그래프: 노드 위치와 엣지를 미리 결정적으로 만들어 둔다
    const nodeCount = 24;
    const nodes: number[][] = [];
    for (let n = 0; n < nodeCount; n += 1) {
      const nt = (n + 0.5) / nodeCount;
      const incl = Math.acos(1 - 2 * nt);
      const azi = golden * n * 3.1;
      const nr = 2.2 * (0.55 + fract(Math.sin(n * 91.7) * 7431.3) * 0.45);
      nodes.push([
        Math.sin(incl) * Math.cos(azi) * nr,
        Math.sin(incl) * Math.sin(azi) * nr * 0.72,
        Math.cos(incl) * nr * 0.55
      ]);
    }
    const edges: number[][] = [];
    for (let n = 0; n < nodeCount; n += 1) {
      edges.push([n, (n + 1) % nodeCount]);
      edges.push([n, (n * 7 + 3) % nodeCount]);
    }

    for (let i = 0; i < count; i += 1) {
      const t = i / count;
      const rand = fract(Math.sin(i * 12.9898) * 43758.5453);
      const rand2 = fract(Math.sin(i * 78.233) * 12543.632);

      // 0) 구체 셸
      const incl = Math.acos(1 - 2 * t);
      const azi = golden * i;
      const sr = 2.25 * (0.9 + rand * 0.2);
      pos0[i * 3] = Math.sin(incl) * Math.cos(azi) * sr;
      pos0[i * 3 + 1] = Math.sin(incl) * Math.sin(azi) * sr;
      pos0[i * 3 + 2] = Math.cos(incl) * sr;

      // 1) 채팅 버블 — 둥근 사각형 본체 + 왼쪽 아래 꼬리
      if (i % 11 === 0) {
        // 꼬리: 삼각형 안을 무게중심 좌표로 채운다
        let u = rand;
        let v = rand2;
        if (u + v > 1) { u = 1 - u; v = 1 - v; }
        const t0x = -bw * 0.16, t0y = -bh / 2;
        const t1x = -bw * 0.02, t1y = -bh / 2;
        const t2x = -bw * 0.26, t2y = -bh / 2 - 0.78;
        pos1[i * 3] = t0x + (t1x - t0x) * u + (t2x - t0x) * v;
        pos1[i * 3 + 1] = t0y + (t1y - t0y) * u + (t2y - t0y) * v;
        pos1[i * 3 + 2] = (rand - 0.5) * 0.18;
      } else {
        let bx = (rand - 0.5) * bw;
        let by = (rand2 - 0.5) * bh;
        // 모서리 밖으로 나간 점은 라운드 반경 위로 당겨 둥근 사각형을 만든다
        const qx = Math.abs(bx) - (bw / 2 - br);
        const qy = Math.abs(by) - (bh / 2 - br);
        if (qx > 0 && qy > 0) {
          const d = Math.hypot(qx, qy);
          if (d > br) {
            const s = br / d;
            bx = Math.sign(bx) * (bw / 2 - br + qx * s);
            by = Math.sign(by) * (bh / 2 - br + qy * s);
          }
        }
        pos1[i * 3] = bx;
        pos1[i * 3 + 1] = by;
        pos1[i * 3 + 2] = (rand - 0.5) * 0.22;
      }

      // 2) 네트워크 그래프 — 노드(뭉침) + 엣지(선분)
      if (i % 6 === 0) {
        const nd = nodes[i % nodeCount];
        pos2[i * 3] = nd[0] + (rand - 0.5) * 0.17;
        pos2[i * 3 + 1] = nd[1] + (rand2 - 0.5) * 0.17;
        pos2[i * 3 + 2] = nd[2] + (rand - 0.5) * 0.17;
      } else {
        const e = edges[i % edges.length];
        const a = nodes[e[0]];
        const b = nodes[e[1]];
        pos2[i * 3] = a[0] + (b[0] - a[0]) * rand + (rand2 - 0.5) * 0.045;
        pos2[i * 3 + 1] = a[1] + (b[1] - a[1]) * rand + (rand - 0.5) * 0.045;
        pos2[i * 3 + 2] = a[2] + (b[2] - a[2]) * rand + (rand2 - 0.5) * 0.045;
      }

      // 3) 빛나는 코어 (조밀한 작은 구)
      const cr = 0.85 * (0.5 + rand * 0.5);
      pos3[i * 3] = Math.sin(incl) * Math.cos(azi) * cr;
      pos3[i * 3 + 1] = Math.sin(incl) * Math.sin(azi) * cr;
      pos3[i * 3 + 2] = Math.cos(incl) * cr;

      scales[i] = 0.45 + rand2 * 1.7;
      seeds[i] = rand;
    }

    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(pos0, 3));
    geo.setAttribute('aPos0', new THREE.BufferAttribute(pos0, 3));
    geo.setAttribute('aPos1', new THREE.BufferAttribute(pos1, 3));
    geo.setAttribute('aPos2', new THREE.BufferAttribute(pos2, 3));
    geo.setAttribute('aPos3', new THREE.BufferAttribute(pos3, 3));
    geo.setAttribute('aScale', new THREE.BufferAttribute(scales, 1));
    geo.setAttribute('aSeed', new THREE.BufferAttribute(seeds, 1));
    return geo;
  }, []);

  const material = useMemo(
    () =>
      new THREE.ShaderMaterial({
        vertexShader,
        fragmentShader,
        transparent: true,
        depthWrite: false,
        depthTest: false,
        blending: THREE.AdditiveBlending,
        uniforms: {
          uTime: { value: 0 },
          uProgress: { value: 0 },
          uSize: { value: 24 },
          uPixelRatio: { value: Math.min(window.devicePixelRatio, 1.75) },
          uMouse: { value: new THREE.Vector2() },
          uColorA: { value: new THREE.Color('#5b53eb') },
          uColorB: { value: new THREE.Color('#38bdf8') },
          uColorC: { value: new THREE.Color('#a855f7') },
          uColorD: { value: new THREE.Color('#e6e9ff') }
        }
      }),
    []
  );

  useEffect(() => () => {
    geometry.dispose();
    material.dispose();
  }, [geometry, material]);

  useFrame((state, delta) => {
    const u = material.uniforms;
    u.uTime.value = state.clock.elapsedTime;

    // 스크롤 진행도를 부드럽게 추종 → uProgress
    landingScroll.current += (landingScroll.target - landingScroll.current) * Math.min(1, delta * 4.5);
    u.uProgress.value = landingScroll.current;

    // 마우스 패럴랙스
    pointer.x += (pointer.tx - pointer.x) * Math.min(1, delta * 3);
    pointer.y += (pointer.ty - pointer.y) * Math.min(1, delta * 3);
    (u.uMouse.value as THREE.Vector2).set(pointer.x, pointer.y);

    if (groupRef.current) {
      const spin = prefersReducedMotion ? 0.01 : 0.075;
      spinRef.current += delta * spin;
      // 채팅 버블(진행도 1/3) 구간에선 평평한 형태가 옆으로 서지 않도록 정면으로 수렴시킨다
      const faceFront = Math.max(0, 1 - Math.abs(landingScroll.current - 1 / 3) * 5);
      groupRef.current.rotation.y = spinRef.current * (1 - faceFront);
      groupRef.current.rotation.x += (pointer.y * 0.2 - groupRef.current.rotation.x) * Math.min(1, delta * 2);
    }
    // 스크롤에 따라 카메라 살짝 당겨졌다 물러남 (형태 전환을 강조)
    const z = 6.2 - Math.sin(landingScroll.current * Math.PI) * 1.1;
    state.camera.position.z += (z - state.camera.position.z) * Math.min(1, delta * 3);
  });

  return (
    <group ref={groupRef}>
      <points geometry={geometry} material={material} frustumCulled={false} />
    </group>
  );
}

export default function LandingScene() {
  useEffect(() => {
    const onMove = (event: PointerEvent) => {
      pointer.tx = (event.clientX / window.innerWidth) * 2 - 1;
      pointer.ty = -((event.clientY / window.innerHeight) * 2 - 1);
    };
    window.addEventListener('pointermove', onMove, { passive: true });
    return () => window.removeEventListener('pointermove', onMove);
  }, []);

  return (
    <Canvas
      className="welcome-canvas"
      dpr={[1, 1.75]}
      gl={{ antialias: false, alpha: true, powerPreference: 'high-performance' }}
      camera={{ position: [0, 0, 6.2], fov: 55 }}
    >
      <Particles />
      <EffectComposer>
        <Bloom mipmapBlur intensity={1.45} luminanceThreshold={0.015} luminanceSmoothing={0.28} radius={0.95} />
      </EffectComposer>
    </Canvas>
  );
}
