import { useEffect, useState } from 'react';
import { ExternalLink } from 'lucide-react';

type Preview = { url: string; title?: string; description?: string; image?: string; siteName?: string };

// URL별 결과를 모듈 레벨에서 캐시해 메시지 리렌더/중복 메시지에서 재요청하지 않는다.
const resolved = new Map<string, Preview | null>();
const inflight = new Map<string, Promise<Preview | null>>();

function load(url: string): Promise<Preview | null> {
  if (resolved.has(url)) return Promise.resolve(resolved.get(url) ?? null);
  const existing = inflight.get(url);
  if (existing) return existing;
  const promise = fetch(`/api/news/link-preview?url=${encodeURIComponent(url)}`)
    .then((response) => (response.status === 200 ? response.json() : null))
    .then((data: Preview | null) => {
      resolved.set(url, data);
      inflight.delete(url);
      return data;
    })
    .catch(() => {
      resolved.set(url, null);
      inflight.delete(url);
      return null;
    });
  inflight.set(url, promise);
  return promise;
}

function hostOf(url: string) {
  try {
    return new URL(url).hostname.replace(/^www\./, '');
  } catch {
    return url;
  }
}

// 메시지 본문에서 첫 번째 http(s) 링크를 뽑는다. 뒤따르는 문장부호는 제거.
export function firstMessageUrl(text: string | null | undefined): string | null {
  if (!text) return null;
  const match = text.match(/https?:\/\/[^\s<>()]+/i);
  if (!match) return null;
  return match[0].replace(/[.,!?)\]]+$/, '');
}

export function MessageLinkPreview({ url }: { url: string }) {
  const [preview, setPreview] = useState<Preview | null | undefined>(resolved.get(url));

  useEffect(() => {
    let cancelled = false;
    if (resolved.has(url)) {
      setPreview(resolved.get(url));
      return;
    }
    setPreview(undefined);
    load(url).then((data) => {
      if (!cancelled) setPreview(data);
    });
    return () => {
      cancelled = true;
    };
  }, [url]);

  if (!preview) return null; // 로딩(undefined) 또는 미리보기 없음(null)

  return (
    <a className="link-card" href={preview.url} target="_blank" rel="noreferrer noopener">
      {preview.image && (
        <div className="link-card-thumb">
          <img src={preview.image} alt="" loading="lazy" referrerPolicy="no-referrer" />
        </div>
      )}
      <div className="link-card-body">
        {preview.title && <strong className="link-card-title">{preview.title}</strong>}
        {preview.description && <span className="link-card-desc">{preview.description}</span>}
        <span className="link-card-host">
          {preview.siteName || hostOf(preview.url)}
          <ExternalLink size={12} aria-hidden />
        </span>
      </div>
    </a>
  );
}
