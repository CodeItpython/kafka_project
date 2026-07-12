-- 사용자 알림 설정(방해금지 시간대 + 알림 종류). 기기 간 동기화를 위해 프로필에 저장.
alter table users add column if not exists dnd_enabled boolean not null default false;
alter table users add column if not exists dnd_start integer not null default 22;
alter table users add column if not exists dnd_end integer not null default 8;
alter table users add column if not exists notify_messages boolean not null default true;
alter table users add column if not exists notify_mentions_only boolean not null default false;
alter table users add column if not exists notify_marketing boolean not null default false;
