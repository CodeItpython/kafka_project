import { useEffect, useMemo, useRef } from 'react';
import { useFrame } from '@react-three/fiber';
import { heroState, ramp } from './heroState';
import { RoundedBoxGeometry } from 'three/examples/jsm/geometries/RoundedBoxGeometry.js';
import * as THREE from 'three';

const prefersReducedMotion =
  typeof window !== 'undefined' &&
  window.matchMedia &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches;

export const pointer = { x: 0, y: 0, tx: 0, ty: 0 };

const U = 0.3; // 1u 키 폭
const GAP = 0.026;
const PITCH = U + GAP;
const CAP_H = 0.17;
const BOARD_U = 16; // 65% 배열 가로 16u

// 독거미(65% / 68키) 배열 — [각인, 폭(u)]
const ROWS: [string, number][][] = [
  [['Esc', 1], ['1', 1], ['2', 1], ['3', 1], ['4', 1], ['5', 1], ['6', 1], ['7', 1], ['8', 1], ['9', 1], ['0', 1], ['-', 1], ['=', 1], ['Back', 2], ['Del', 1]],
  [['Tab', 1.5], ['Q', 1], ['W', 1], ['E', 1], ['R', 1], ['T', 1], ['Y', 1], ['U', 1], ['I', 1], ['O', 1], ['P', 1], ['[', 1], [']', 1], ['\\', 1.5], ['PgUp', 1]],
  [['Caps', 1.75], ['A', 1], ['S', 1], ['D', 1], ['F', 1], ['G', 1], ['H', 1], ['J', 1], ['K', 1], ['L', 1], [';', 1], ["'", 1], ['Enter', 2.25], ['PgDn', 1]],
  [['Shift', 2.25], ['Z', 1], ['X', 1], ['C', 1], ['V', 1], ['B', 1], ['N', 1], ['M', 1], [',', 1], ['.', 1], ['/', 1], ['Shift', 1.75], ['↑', 1], ['End', 1]],
  [['Ctrl', 1.25], ['Win', 1.25], ['Alt', 1.25], ['', 6.25], ['Alt', 1], ['Fn', 1], ['Ctrl', 1], ['←', 1], ['↓', 1], ['→', 1]]
];

const KEY_COUNT = ROWS.reduce((n, r) => n + r.length, 0);
const ATLAS_COLS = 8;
const ATLAS_ROWS = Math.ceil(KEY_COUNT / ATLAS_COLS);
const CELL = 128;

// 독거미 레트로 컬러웨이: 베이지 알파 + 그레이 모디파이어 + 오렌지 Esc/Enter
const C_ALPHA = '#ece6d8';
const C_MOD = '#7b7f86';
const C_ACCENT = '#df5f28';

// 결정적 난수 — 새로고침해도 같은 흩어짐을 보여준다.
function rnd(n: number) {
  const v = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return v - Math.floor(v);
}

const easeOut = (t: number) => 1 - Math.pow(1 - t, 3);

type Key = {
  label: string;
  x: number;
  z: number;
  w: number;
  ex: number;
  ey: number;
  ez: number;
  rx: number;
  ry: number;
  rz: number;
  delay: number;
  accent: boolean;
  mod: boolean;
};

function buildKeys(): Key[] {
  const keys: Key[] = [];
  let i = 0;
  ROWS.forEach((row, r) => {
    let u = 0;
    row.forEach(([label, w], c) => {
      const accent = label === 'Esc' || label === 'Enter';
      keys.push({
        label,
        x: (u + w / 2) * PITCH - (BOARD_U / 2) * PITCH,
        z: (r - 2) * PITCH,
        w,
        // 흩어진 상태: 위쪽 공중에 무작위로 떠 있다
        ex: (rnd(i) - 0.5) * 4.6,
        ey: 0.45 + rnd(i + 91) * 1.25,
        ez: (rnd(i + 17) - 0.5) * 1.4,
        rx: (rnd(i + 33) - 0.5) * 2.4,
        ry: (rnd(i + 57) - 0.5) * 2.4,
        rz: (rnd(i + 71) - 0.5) * 2.4,
        // 행/열 순서대로 시차를 두고 내려앉는다
        delay: (r * 0.075 + c * 0.015 + rnd(i + 5) * 0.05) * 0.6,
        accent,
        mod: !accent && (w > 1 || ['Del', 'PgUp', 'PgDn', 'End', 'Fn', '↑', '↓', '←', '→'].includes(label))
      });
      u += w;
      i += 1;
    });
  });
  return keys;
}

// 키캡 각인을 한 장의 아틀라스 캔버스에 그린다(키마다 셀 하나).
function makeLegendTexture(keys: Key[]) {
  const canvas = document.createElement('canvas');
  canvas.width = ATLAS_COLS * CELL;
  canvas.height = ATLAS_ROWS * CELL;
  const ctx = canvas.getContext('2d')!;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  keys.forEach((k, i) => {
    if (!k.label) return;
    const cx = (i % ATLAS_COLS) * CELL + CELL / 2;
    const cy = Math.floor(i / ATLAS_COLS) * CELL + CELL / 2;
    const long = k.label.length > 1;
    ctx.fillStyle = k.accent ? '#f7efe6' : k.mod ? '#e8ecf2' : '#33312c';
    ctx.font = `${long ? 600 : 700} ${long ? 46 : 74}px "Pretendard", system-ui, sans-serif`;
    ctx.fillText(k.label, cx, cy);
  });
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 4;
  return tex;
}

function Keyboard() {
  const groupRef = useRef<THREE.Group>(null);
  const capsRef = useRef<THREE.InstancedMesh>(null);
  const legendRef = useRef<THREE.InstancedMesh>(null);
  const caseRef = useRef<THREE.Mesh>(null);
  const fingersRef = useRef<THREE.InstancedMesh>(null);
  const tipsRef = useRef<THREE.InstancedMesh>(null);
  const dummy = useMemo(() => new THREE.Object3D(), []);
  const legendDummy = useMemo(() => new THREE.Object3D(), []);
  const tipDummy = useMemo(() => new THREE.Object3D(), []);
  const keys = useMemo(buildKeys, []);
  const press = useMemo(() => new Float32Array(KEY_COUNT), []);

  // 체리 프로파일: 윗면을 좁히고 살짝 파낸 키캡
  const capGeo = useMemo(() => {
    const geo = new RoundedBoxGeometry(1, 1, 1, 2, 0.17);
    const p = geo.attributes.position as THREE.BufferAttribute;
    for (let i = 0; i < p.count; i += 1) {
      const y = p.getY(i);
      if (y > 0) {
        const k = (y + 0.5) / 1;
        p.setX(i, p.getX(i) * (1 - 0.2 * k));
        p.setZ(i, p.getZ(i) * (1 - 0.22 * k));
        if (y > 0.44) p.setY(i, y - 0.06 * (1 - Math.abs(p.getZ(i)) / 0.4));
      }
    }
    p.needsUpdate = true;
    geo.computeVertexNormals();
    return geo;
  }, []);

  const legendGeo = useMemo(() => {
    const geo = new THREE.PlaneGeometry(1, 1);
    const cells = new Float32Array(KEY_COUNT * 2);
    for (let i = 0; i < KEY_COUNT; i += 1) {
      cells[i * 2] = i % ATLAS_COLS;
      // 캔버스는 위에서 아래로 그리므로 v축을 뒤집어 맞춘다
      cells[i * 2 + 1] = ATLAS_ROWS - 1 - Math.floor(i / ATLAS_COLS);
    }
    geo.setAttribute('aCell', new THREE.InstancedBufferAttribute(cells, 2));
    return geo;
  }, []);

  const legendTex = useMemo(() => makeLegendTexture(keys), [keys]);
  const legendMat = useMemo(() => {
    const mat = new THREE.MeshBasicMaterial({ map: legendTex, transparent: true, depthWrite: false });
    mat.onBeforeCompile = (shader) => {
      shader.vertexShader = `attribute vec2 aCell;\n${shader.vertexShader}`.replace(
        '#include <uv_vertex>',
        `#include <uv_vertex>
         vMapUv = (uv + aCell) / vec2(${ATLAS_COLS}.0, ${ATLAS_ROWS}.0);`
      );
    };
    return mat;
  }, [legendTex]);

  const caseGeo = useMemo(
    () => new RoundedBoxGeometry((BOARD_U + 0.6) * PITCH, 0.5, 5.7 * PITCH, 3, 0.06),
    []
  );
  const fingerGeo = useMemo(() => new THREE.CapsuleGeometry(0.045, 0.46, 4, 10), []);
  const tipGeo = useMemo(() => new THREE.SphereGeometry(0.062, 12, 10), []);

  // 손가락마다 담당하는 키(가로 위치로 8등분) — 사람이 치듯 자기 구역만 누른다
  const zones = useMemo(() => {
    const board = BOARD_U * PITCH;
    return FINGERS.map((_, f) =>
      keys.reduce<number[]>((acc, k, i) => {
        const band = Math.min(7, Math.max(0, Math.floor(((k.x + board / 2) / board) * 8)));
        if (band === f && k.w <= 1.5 && k.label) acc.push(i);
        return acc;
      }, [])
    );
  }, [keys]);

  // 키캡 색은 한 번만 칠한다 (알파/모디파이어/강조)
  useEffect(() => {
    const mesh = capsRef.current;
    if (!mesh) return;
    const c = new THREE.Color();
    keys.forEach((k, i) => mesh.setColorAt(i, c.set(k.accent ? C_ACCENT : k.mod ? C_MOD : C_ALPHA)));
    if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
  }, [keys]);

  useEffect(() => () => {
    capGeo.dispose();
    legendGeo.dispose();
    legendTex.dispose();
    legendMat.dispose();
    caseGeo.dispose();
    fingerGeo.dispose();
    tipGeo.dispose();
  }, [capGeo, legendGeo, legendTex, legendMat, caseGeo, fingerGeo, tipGeo]);

  useFrame((state, delta) => {
    pointer.x += (pointer.tx - pointer.x) * Math.min(1, delta * 2.5);
    pointer.y += (pointer.ty - pointer.y) * Math.min(1, delta * 2.5);

    const time = state.clock.elapsedTime;
    const p = heroState.current;
    // 1막: 0~32% 조립 → 28~55% 타건 → 55~72% 보드가 아래로 물러난다
    const assemble = ramp(p, 0, 0.32);
    const leave = ramp(p, 0.55, 0.72);

    // 조립이 끝나면 로봇 손이 내려와 타건한다
    const typing = prefersReducedMotion ? 0 : ramp(p, 0.28, 0.34) * (1 - ramp(p, 0.5, 0.56));
    press.fill(0);
    const hands = fingersRef.current;
    const tips = tipsRef.current;
    if (hands && tips) {
      FINGERS.forEach((f, i) => {
        const cand = zones[i];
        if (typing <= 0.001 || cand.length === 0) {
          dummy.scale.setScalar(0);
          dummy.position.set(f.hx, 1.4, f.hz);
          dummy.rotation.set(0, 0, 0);
          dummy.updateMatrix();
          hands.setMatrixAt(i, dummy.matrix);
          tips.setMatrixAt(i, dummy.matrix);
          return;
        }
        const cycle = time * f.rate + f.phase;
        const beat = Math.floor(cycle);
        const ft = cycle - beat;
        const target = cand[Math.floor(rnd(i * 7.3 + beat * 3.1) * cand.length)];
        const key = keys[target];
        const dip = Math.pow(Math.sin(Math.PI * ft), 8);
        press[target] = dip;
        // 전반부에 목표 키 위로 이동하고, 중반에 눌렀다가 돌아온다
        const move = Math.min(1, ft / 0.45);
        const glide = move * move * (3 - 2 * move);
        dummy.position.set(
          f.hx + (key.x - f.hx) * glide,
          0.42 + (1 - typing) * 1.6 - dip * 0.22,
          f.hz + (key.z - f.hz) * glide
        );
        // 키를 향해 앞으로 기울여 손가락처럼 내려찍는다
        dummy.rotation.set(0.42, 0, (f.hx - key.x) * 0.16);
        dummy.scale.setScalar(typing);
        dummy.updateMatrix();
        hands.setMatrixAt(i, dummy.matrix);

        tipDummy.position.copy(dummy.position);
        tipDummy.rotation.copy(dummy.rotation);
        tipDummy.scale.setScalar(1);
        tipDummy.translateY(-0.27);
        tipDummy.scale.setScalar(typing);
        tipDummy.updateMatrix();
        tips.setMatrixAt(i, tipDummy.matrix);
      });
      hands.instanceMatrix.needsUpdate = true;
      tips.instanceMatrix.needsUpdate = true;
    }

    const mesh = capsRef.current;
    const legends = legendRef.current;
    if (mesh && legends) {
      keys.forEach((k, i) => {
        const t = easeOut(Math.min(1, Math.max(0, (assemble - k.delay) / 0.38)));
        // 손가락이 누르고 있는 키만 내려간다
        const push = press[i] * 0.07 * t;
        dummy.position.set(
          k.ex + (k.x - k.ex) * t,
          k.ey + (CAP_H / 2 - k.ey) * t - push,
          k.ez + (k.z - k.ez) * t
        );
        dummy.rotation.set(k.rx * (1 - t), k.ry * (1 - t), k.rz * (1 - t));
        dummy.scale.set(k.w * PITCH - GAP, CAP_H, U);
        dummy.updateMatrix();
        mesh.setMatrixAt(i, dummy.matrix);

        // 각인은 키캡 윗면에 붙어 함께 움직인다
        legendDummy.position.copy(dummy.position);
        legendDummy.rotation.copy(dummy.rotation);
        legendDummy.scale.setScalar(1);
        legendDummy.translateY(CAP_H / 2 + 0.004);
        legendDummy.rotateX(-Math.PI / 2);
        legendDummy.scale.set((k.w * PITCH - GAP) * 0.72, U * 0.72, 1);
        legendDummy.updateMatrix();
        legends.setMatrixAt(i, legendDummy.matrix);
      });
      mesh.instanceMatrix.needsUpdate = true;
      legends.instanceMatrix.needsUpdate = true;
    }

    if (caseRef.current) {
      // 케이스는 키캡보다 먼저 자리를 잡는다
      const pt = easeOut(Math.min(1, assemble / 0.3));
      caseRef.current.position.y = -0.3 - (1 - pt) * 0.35;
      (caseRef.current.material as THREE.MeshStandardMaterial).opacity = 0.55 + pt * 0.45;
    }

    if (groupRef.current) {
      const g = groupRef.current;
      // 제품 컷 각도(상판이 카메라를 향함). 퇴장 구간엔 아래로 물러나며 살짝 눕는다
      g.rotation.x += (0.62 + pointer.y * 0.05 - g.rotation.x) * Math.min(1, delta * 2);
      const yaw = prefersReducedMotion ? 0 : Math.sin(time * 0.18) * 0.12;
      g.rotation.y += (yaw + pointer.x * 0.12 - g.rotation.y) * Math.min(1, delta * 2);
      g.position.y = -leave * 2.6;
      g.scale.setScalar(1 - leave * 0.25);
    }
  });

  return (
    <group ref={groupRef} position={[0, 0, 0]}>
      <mesh ref={caseRef} geometry={caseGeo} position={[0, -0.3, 0]}>
        <meshStandardMaterial color="#1b1e26" roughness={0.35} metalness={0.7} transparent />
      </mesh>
      <instancedMesh ref={capsRef} args={[capGeo, undefined, KEY_COUNT]} frustumCulled={false}>
        <meshStandardMaterial roughness={0.62} metalness={0.04} />
      </instancedMesh>
      <instancedMesh ref={legendRef} args={[legendGeo, legendMat, KEY_COUNT]} frustumCulled={false} />
      <instancedMesh ref={fingersRef} args={[fingerGeo, undefined, FINGERS.length]} frustumCulled={false}>
        <meshStandardMaterial color="#39404f" roughness={0.32} metalness={0.92} />
      </instancedMesh>
      <instancedMesh ref={tipsRef} args={[tipGeo, undefined, FINGERS.length]} frustumCulled={false}>
        <meshStandardMaterial color="#d8e0ee" roughness={0.22} metalness={0.8} emissive="#3d6dff" emissiveIntensity={0.25} />
      </instancedMesh>
    </group>
  );
}

// 로봇 손가락 8개 — 홈 포지션에서 출발해 매 비트마다 자기 구역의 키를 누른다.
const FINGERS = Array.from({ length: 8 }, (_, f) => ({
  hx: (f < 4 ? f - 4.6 : f - 3.4) * PITCH * 1.3,
  hz: PITCH * 0.2,
  phase: f * 0.37 + rnd(f * 3) * 0.4,
  rate: 1.05 + rnd(f * 11) * 0.35
}));

export { Keyboard as KeyboardAct };
