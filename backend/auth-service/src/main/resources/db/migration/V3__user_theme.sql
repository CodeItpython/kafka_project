-- 사용자 UI 테마 선호(light/dark/system) — 기기 간 동기화를 위해 프로필에 저장
alter table users add column if not exists theme varchar(10) not null default 'system';
