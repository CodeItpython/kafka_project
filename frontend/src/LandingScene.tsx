import { useEffect, useMemo, useRef } from 'react';
import { Canvas, useFrame } from '@react-three/fiber';
import { EffectComposer, Bloom } from '@react-three/postprocessing';
import * as THREE from 'three';

// 랜딩 스크롤 진행도(0..1)를 씬으로 전달하는 공유 상태. WelcomeLanding이 target을 갱신하고
// 씬은 current를 부드럽게 따라가며 지오메트리 회전/카메라를 스크러빙한다.
export const landingScroll = { target: 0, current: 0 };

const prefersReducedMotion =
  typeof window !== 'undefined' &&
  window.matchMedia &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const pointer = { x: 0, y: 0, tx: 0, ty: 0 };

/**
 * 단일 지오메트리 + 광원 하나. 색은 브랜드 블루 한 포인트뿐이고, 형태는
 * 면이 각진 이십면체 하나. 광원이 주위를 돌며 면을 훑어(raking light) 분위기를 만든다.
 */
function Monolith() {
  const meshRef = useRef<THREE.Mesh>(null);
  const lightRef = useRef<THREE.PointLight>(null);

  const geometry = useMemo(() => new THREE.IcosahedronGeometry(2.05, 1), []);
  const material = useMemo(
    () =>
      new THREE.MeshStandardMaterial({
        color: '#141a33',
        metalness: 0.5,
        roughness: 0.38,
        emissive: new THREE.Color('#3d6dff'),
        emissiveIntensity: 0.24,
        flatShading: true
      }),
    []
  );

  useEffect(() => () => {
    geometry.dispose();
    material.dispose();
  }, [geometry, material]);

  useFrame((state, delta) => {
    // 스크롤 진행도를 부드럽게 추종
    landingScroll.current += (landingScroll.target - landingScroll.current) * Math.min(1, delta * 4.5);
    const progress = landingScroll.current;

    // 마우스 패럴랙스
    pointer.x += (pointer.tx - pointer.x) * Math.min(1, delta * 3);
    pointer.y += (pointer.ty - pointer.y) * Math.min(1, delta * 3);

    const mesh = meshRef.current;
    if (mesh) {
      mesh.rotation.y += delta * (prefersReducedMotion ? 0.02 : 0.11);
      // 스크롤이 진행될수록 형태가 더 굴러가며 다른 면을 보여준다
      mesh.rotation.x = progress * Math.PI * 0.55 + pointer.y * 0.16;
      mesh.rotation.z = pointer.x * 0.1;
    }

    // 광원이 형태 주위를 돌며 면을 훑는다
    const light = lightRef.current;
    if (light) {
      const a = state.clock.elapsedTime * (prefersReducedMotion ? 0.05 : 0.26);
      light.position.set(Math.cos(a) * 4.4, 2.2 + Math.sin(a * 0.7) * 1.4, Math.sin(a) * 4.4);
    }

    const z = 6.4 - Math.sin(progress * Math.PI) * 0.9;
    state.camera.position.z += (z - state.camera.position.z) * Math.min(1, delta * 3);
  });

  return (
    <>
      <ambientLight intensity={0.35} />
      {/* 이 씬의 유일한 색 — 브랜드 블루. 면을 훑으며 입체를 만든다 */}
      <pointLight ref={lightRef} color="#3d6dff" intensity={520} distance={24} decay={2} />
      {/* 반대편 화이트 림 — 면 경계가 읽히도록 */}
      <directionalLight position={[-5, -2, -3]} intensity={1.4} color="#cfe0ff" />
      <mesh ref={meshRef} geometry={geometry} material={material} />
    </>
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
      gl={{ antialias: true, alpha: true, powerPreference: 'high-performance' }}
      camera={{ position: [0, 0, 6.4], fov: 55 }}
    >
      <Monolith />
      <EffectComposer>
        {/* 파티클 가산합성이 아니라 밝은 하이라이트만 번지도록 임계값을 올린다 */}
        <Bloom mipmapBlur intensity={1.25} luminanceThreshold={0.32} luminanceSmoothing={0.3} radius={0.9} />
      </EffectComposer>
    </Canvas>
  );
}
