import { Component, ReactNode, useEffect, useMemo, useRef } from 'react';
import { Canvas, useFrame } from '@react-three/fiber';
import { EffectComposer, Bloom } from '@react-three/postprocessing';
import * as THREE from 'three';

// WebGL 미지원/컨텍스트 초기화 실패 시 웰컴 페이지가 깨지지 않도록 방어.
class SceneBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  render() {
    return this.state.failed ? null : this.props.children;
  }
}

// 마우스 위치(정규화 -1..1). Canvas가 pointer-events:none이라 window에서 직접 추적한다.
const pointer = { x: 0, y: 0, tx: 0, ty: 0 };

const prefersReducedMotion =
  typeof window !== 'undefined' &&
  window.matchMedia &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const vertexShader = /* glsl */ `
  uniform float uTime;
  uniform float uSize;
  uniform float uPixelRatio;
  uniform vec2 uMouse;
  uniform float uAmp;
  attribute float aScale;
  attribute float aSeed;
  varying float vMix;
  varying float vGlow;

  // Ashima 3D simplex noise
  vec4 permute(vec4 x){return mod(((x*34.0)+1.0)*x,289.0);}
  vec4 taylorInvSqrt(vec4 r){return 1.79284291400159-0.85373472095314*r;}
  float snoise(vec3 v){
    const vec2 C=vec2(1.0/6.0,1.0/3.0);const vec4 D=vec4(0.0,0.5,1.0,2.0);
    vec3 i=floor(v+dot(v,C.yyy));vec3 x0=v-i+dot(i,C.xxx);
    vec3 g=step(x0.yzx,x0.xyz);vec3 l=1.0-g;vec3 i1=min(g.xyz,l.zxy);vec3 i2=max(g.xyz,l.zxy);
    vec3 x1=x0-i1+1.0*C.xxx;vec3 x2=x0-i2+2.0*C.xxx;vec3 x3=x0-1.0+3.0*C.xxx;
    i=mod(i,289.0);
    vec4 p=permute(permute(permute(i.z+vec4(0.0,i1.z,i2.z,1.0))+i.y+vec4(0.0,i1.y,i2.y,1.0))+i.x+vec4(0.0,i1.x,i2.x,1.0));
    float n_=1.0/7.0;vec3 ns=n_*D.wyz-D.xzx;
    vec4 j=p-49.0*floor(p*ns.z*ns.z);
    vec4 x_=floor(j*ns.z);vec4 y_=floor(j-7.0*x_);
    vec4 x=x_*ns.x+ns.yyyy;vec4 y=y_*ns.x+ns.yyyy;vec4 h=1.0-abs(x)-abs(y);
    vec4 b0=vec4(x.xy,y.xy);vec4 b1=vec4(x.zw,y.zw);
    vec4 s0=floor(b0)*2.0+1.0;vec4 s1=floor(b1)*2.0+1.0;vec4 sh=-step(h,vec4(0.0));
    vec4 a0=b0.xzyw+s0.xzyw*sh.xxyy;vec4 a1=b1.xzyw+s1.xzyw*sh.zzww;
    vec3 p0=vec3(a0.xy,h.x);vec3 p1=vec3(a0.zw,h.y);vec3 p2=vec3(a1.xy,h.z);vec3 p3=vec3(a1.zw,h.w);
    vec4 norm=taylorInvSqrt(vec4(dot(p0,p0),dot(p1,p1),dot(p2,p2),dot(p3,p3)));
    p0*=norm.x;p1*=norm.y;p2*=norm.z;p3*=norm.w;
    vec4 m=max(0.6-vec4(dot(x0,x0),dot(x1,x1),dot(x2,x2),dot(x3,x3)),0.0);m=m*m;
    return 42.0*dot(m*m,vec4(dot(p0,x0),dot(p1,x1),dot(p2,x2),dot(p3,x3)));
  }

  void main(){
    vec3 pos = position;
    vec3 dir = normalize(pos);
    // 시간에 따라 흐르는 노이즈 변위(살아 움직이는 파티클 셸)
    float n = snoise(pos * 0.55 + vec3(uTime * 0.12, uTime * 0.08, aSeed * 4.0));
    float n2 = snoise(pos * 1.4 - vec3(uTime * 0.16));
    pos += dir * (n * 0.55 + n2 * 0.18) * uAmp;

    // 마우스 방향으로 은은한 밀림
    pos.xy += uMouse * (0.35 + aSeed * 0.4);

    vMix = clamp(0.5 + n * 0.6, 0.0, 1.0);
    vGlow = 0.55 + aSeed * 0.9 + max(n, 0.0) * 0.6;

    vec4 mvPosition = modelViewMatrix * vec4(pos, 1.0);
    gl_PointSize = uSize * aScale * uPixelRatio * (1.0 / -mvPosition.z);
    gl_Position = projectionMatrix * mvPosition;
  }
`;

const fragmentShader = /* glsl */ `
  precision highp float;
  uniform vec3 uColorA;
  uniform vec3 uColorB;
  uniform vec3 uColorC;
  varying float vMix;
  varying float vGlow;

  void main(){
    // 원형 소프트 포인트
    float d = length(gl_PointCoord - 0.5);
    float alpha = smoothstep(0.5, 0.06, d);
    if (alpha <= 0.001) discard;
    vec3 col = mix(uColorA, uColorB, vMix);
    col = mix(col, uColorC, smoothstep(0.7, 1.0, vGlow) * 0.6);
    gl_FragColor = vec4(col * vGlow, alpha);
  }
`;

function ParticleField() {
  const pointsRef = useRef<THREE.Points>(null);
  const groupRef = useRef<THREE.Group>(null);

  const geometry = useMemo(() => {
    const count = 22000;
    const positions = new Float32Array(count * 3);
    const scales = new Float32Array(count);
    const seeds = new Float32Array(count);
    const golden = Math.PI * (3 - Math.sqrt(5));
    const baseR = 2.25;
    for (let i = 0; i < count; i += 1) {
      const t = i / count;
      const inclination = Math.acos(1 - 2 * t);
      const azimuth = golden * i;
      const shell = baseR * (0.82 + Math.random() * 0.5); // 두께감 있는 구 셸
      const sx = Math.sin(inclination) * Math.cos(azimuth);
      const sy = Math.sin(inclination) * Math.sin(azimuth);
      const sz = Math.cos(inclination);
      positions[i * 3] = sx * shell;
      positions[i * 3 + 1] = sy * shell;
      positions[i * 3 + 2] = sz * shell;
      scales[i] = 0.4 + Math.random() * 1.8;
      seeds[i] = Math.random();
    }
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
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
          uSize: { value: 26 },
          uPixelRatio: { value: Math.min(window.devicePixelRatio, 1.75) },
          uMouse: { value: new THREE.Vector2(0, 0) },
          uAmp: { value: prefersReducedMotion ? 0.35 : 1 },
          uColorA: { value: new THREE.Color('#5b53eb') },
          uColorB: { value: new THREE.Color('#8b5cf6') },
          uColorC: { value: new THREE.Color('#67e8f9') }
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
    // 마우스 부드럽게 추적
    pointer.x += (pointer.tx - pointer.x) * Math.min(1, delta * 3);
    pointer.y += (pointer.ty - pointer.y) * Math.min(1, delta * 3);
    (u.uMouse.value as THREE.Vector2).set(pointer.x, pointer.y);
    if (groupRef.current) {
      const speed = prefersReducedMotion ? 0.02 : 0.08;
      groupRef.current.rotation.y += delta * speed;
      // 마우스 패럴랙스
      groupRef.current.rotation.x += (pointer.y * 0.25 - groupRef.current.rotation.x) * Math.min(1, delta * 2);
      const targetY = groupRef.current.rotation.y + pointer.x * 0.0;
      groupRef.current.rotation.y = targetY;
    }
  });

  return (
    <group ref={groupRef}>
      <points ref={pointsRef} geometry={geometry} material={material} />
    </group>
  );
}

export default function WelcomeScene() {
  useEffect(() => {
    const onMove = (event: PointerEvent) => {
      pointer.tx = (event.clientX / window.innerWidth) * 2 - 1;
      pointer.ty = -((event.clientY / window.innerHeight) * 2 - 1);
    };
    window.addEventListener('pointermove', onMove, { passive: true });
    return () => window.removeEventListener('pointermove', onMove);
  }, []);

  return (
    <SceneBoundary>
      <Canvas
        className="welcome-canvas"
        dpr={[1, 1.75]}
        gl={{ antialias: false, alpha: true, powerPreference: 'high-performance' }}
        camera={{ position: [0, 0, 6], fov: 55 }}
      >
        <ParticleField />
        <EffectComposer>
          <Bloom mipmapBlur intensity={1.15} luminanceThreshold={0.02} luminanceSmoothing={0.25} radius={0.85} />
        </EffectComposer>
      </Canvas>
    </SceneBoundary>
  );
}
