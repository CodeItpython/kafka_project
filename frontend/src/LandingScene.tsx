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
    float wobble = sin(uTime * 0.7 + aSeed * 6.2831) * 0.05;
    pos += normalize(pos + 0.0001) * wobble;
    pos.xy += uMouse * (0.22 + aSeed * 0.28);
    vT = clamp(uProgress, 0.0, 1.0);
    vGlow = 0.5 + aSeed * 0.85;
    vec4 mv = modelViewMatrix * vec4(pos, 1.0);
    gl_PointSize = uSize * aScale * uPixelRatio * (1.0 / -mv.z);
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

  const geometry = useMemo(() => {
    const count = 16000;
    const pos0 = new Float32Array(count * 3); // 구체
    const pos1 = new Float32Array(count * 3); // 나선 은하 (정면)
    const pos2 = new Float32Array(count * 3); // 물결 커튼
    const pos3 = new Float32Array(count * 3); // 빛나는 코어
    const scales = new Float32Array(count);
    const seeds = new Float32Array(count);
    const golden = Math.PI * (3 - Math.sqrt(5));

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

      // 1) 정면 나선 은하 (x-y 평면)
      const arm = i % 3;
      const gr = 0.25 + 2.35 * Math.sqrt(t);
      const angle = gr * 2.4 + (arm * (Math.PI * 2 / 3)) + rand * 0.5;
      pos1[i * 3] = Math.cos(angle) * gr;
      pos1[i * 3 + 1] = Math.sin(angle) * gr;
      pos1[i * 3 + 2] = (rand2 - 0.5) * 0.5;

      // 2) 물결 커튼 (x-y 그리드 + z 리플)
      const gx = (rand - 0.5) * 5.4;
      const gy = (rand2 - 0.5) * 3.2;
      pos2[i * 3] = gx;
      pos2[i * 3 + 1] = gy;
      pos2[i * 3 + 2] = Math.sin(gx * 1.6) * 0.5 + Math.cos(gy * 1.8) * 0.5;

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
      const spin = prefersReducedMotion ? 0.01 : 0.06;
      groupRef.current.rotation.y += delta * spin;
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
        <Bloom mipmapBlur intensity={1.1} luminanceThreshold={0.02} luminanceSmoothing={0.3} radius={0.85} />
      </EffectComposer>
    </Canvas>
  );
}
