#!/usr/bin/env bash
# 랜딩 영상 QA 프레임 추출 + 스크러빙용 재인코딩.
#
#   scripts/landing-media.sh qa   <src.mp4> <outdir>      # 0/25/50/75/100% 프레임 5장 → outdir/qa_1..5.jpg + ffprobe 요약
#   scripts/landing-media.sh hero <src.mp4>               # hero.mp4 / hero.webm / hero-720.mp4 / hero.jpg / hero-last.jpg / hero-still-{a,b,c}.jpg
#   scripts/landing-media.sh loop <src.mp4> <name>        # <name>.mp4 / <name>.webm / <name>.jpg  (stream, room)
#
# 출력 위치: frontend/public/media/landing/
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=${OUT:-frontend/public/media/landing}
mkdir -p "$OUT"

need() { command -v "$1" >/dev/null 2>&1 || { echo "$1 이 필요합니다 (brew install ffmpeg)" >&2; exit 1; }; }
need ffmpeg; need ffprobe

frames_of() { ffprobe -v error -select_streams v:0 -count_frames -show_entries stream=nb_read_frames -of csv=p=0 "$1"; }

qa() {
  local src=$1 dir=$2; mkdir -p "$dir"
  local n; n=$(frames_of "$src")
  echo "frames=$n"
  ffprobe -v error -select_streams v:0 -show_entries stream=codec_name,width,height,r_frame_rate,duration,nb_frames -of default=nw=1 "$src"
  local i=1
  for pct in 0 0.25 0.5 0.75 1; do
    local idx; idx=$(python3 -c "n=$n;p=$pct;print(max(0,min(n-1,round((n-1)*p))))")
    ffmpeg -loglevel error -y -i "$src" -vf "select=eq(n\,$idx)" -fps_mode vfr -frames:v 1 -q:v 2 "$dir/qa_$i.jpg"
    i=$((i+1))
  done
  # 키프레임 개수(스크러빙 부드러움 지표)
  echo "keyframes=$(ffprobe -v error -select_streams v:0 -skip_frame nokey -show_entries frame=pts_time -of csv=p=0 "$src" | wc -l | tr -d ' ')"
  ls -la "$dir"/qa_*.jpg
}

# 스크러빙용 H.264: 짧은 GOP(6), 씬컷 키프레임 금지, faststart, 무음
h264() { # src out scale
  local scale=${3:-}
  local vf=("-pix_fmt" "yuv420p"); [ -n "$scale" ] && vf=("-vf" "scale=$scale" "-pix_fmt" "yuv420p")
  ffmpeg -loglevel error -y -i "$1" -an "${vf[@]}" -c:v libx264 -profile:v high -preset slow -crf 18 \
    -g 6 -keyint_min 6 -sc_threshold 0 -movflags +faststart "$2"
}
vp9() { # src out [scale]
  local vf=("-pix_fmt" "yuv420p"); [ -n "${3:-}" ] && vf=("-vf" "scale=$3" "-pix_fmt" "yuv420p")
  ffmpeg -loglevel error -y -i "$1" -an "${vf[@]}" -c:v libvpx-vp9 -b:v 0 -crf 30 -g 6 -row-mt 1 "$2"; }
poster() { ffmpeg -loglevel error -y -i "$1" -frames:v 1 -q:v 2 "$2"; }
frame_at() { ffmpeg -loglevel error -y -ss "$2" -i "$1" -frames:v 1 -q:v 2 "$3"; }

hero() {
  local src=$1
  local dur; dur=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$src")
  h264 "$src" "$OUT/hero.mp4" "1920:-2:flags=lanczos"
  h264 "$src" "$OUT/hero-720.mp4" "1280:-2"
  vp9  "$src" "$OUT/hero.webm" "1920:-2:flags=lanczos"
  poster "$OUT/hero.mp4" "$OUT/hero.jpg"
  ffmpeg -loglevel error -y -sseof -0.05 -i "$OUT/hero.mp4" -frames:v 1 -q:v 2 "$OUT/hero-last.jpg"
  # reduced-motion 스틸: 스트림 내부(12%) / 코어(58%) / 완성 기기(마지막)
  frame_at "$OUT/hero.mp4" "$(python3 -c "print($dur*0.12)")" "$OUT/hero-still-a.jpg"
  frame_at "$OUT/hero.mp4" "$(python3 -c "print($dur*0.58)")" "$OUT/hero-still-b.jpg"
  cp "$OUT/hero-last.jpg" "$OUT/hero-still-c.jpg"
  ls -la "$OUT"/hero*
}

loop() {
  local src=$1 name=$2
  h264 "$src" "$OUT/$name.mp4"
  vp9  "$src" "$OUT/$name.webm"
  poster "$OUT/$name.mp4" "$OUT/$name.jpg"
  ls -la "$OUT/$name".*
}

case "${1:-}" in
  qa)   qa "$2" "$3" ;;
  hero) hero "$2" ;;
  loop) loop "$2" "$3" ;;
  *) sed -n '2,9p' "$0"; exit 2 ;;
esac
