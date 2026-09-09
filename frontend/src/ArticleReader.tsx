import { useEffect, useState } from 'react';
import { motion } from 'motion/react';
import { ExternalLink, X } from 'lucide-react';
import type { NewsItem } from './NewsFeed';

const NEWS_ROOT = '/api/news';

type ArticleBlock = { type: 'p' | 'img'; text: string | null; src: string | null };
type Article = {
  url: string;
  title: string | null;
  siteName: string | null;
  publishedAt: string | null;
  leadImage: string | null;
  blocks: ArticleBlock[];
};

/** ISO 또는 언론사 표기 문자열을 "2026.09.09 13:40" 형태로. 파싱 실패 시 원문 그대로. */
function formatDate(raw: string | null): string | null {
  if (!raw) return null;
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return raw;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/**
 * 인앱 기사 리더. 외부 페이지로 나가지 않고 본문을 앱 안에서 읽는다.
 * 백엔드가 문단/이미지 블록으로 구조화해 주므로 HTML을 직접 주입하지 않는다(XSS 없음).
 * 추출 실패(204)면 원문 링크로 안내한다.
 */
export default function ArticleReader({ item, onClose }: { item: NewsItem; onClose: () => void }) {
  const [article, setArticle] = useState<Article | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setFailed(false);
    setArticle(null);
    fetch(`${NEWS_ROOT}/article?url=${encodeURIComponent(item.url)}`)
      .then((response) => {
        if (response.status === 204) return null; // 추출 불가 → 원문 안내
        if (!response.ok) throw new Error(String(response.status));
        return response.json();
      })
      .then((data: Article | null) => {
        if (!alive) return;
        if (data) setArticle(data);
        else setFailed(true);
      })
      .catch(() => alive && setFailed(true))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [item.url]);

  // Esc 로 닫기 + 배경 스크롤 잠금
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = previousOverflow;
    };
  }, [onClose]);

  const title = article?.title || item.title;
  const press = article?.siteName || item.press;
  const when = formatDate(article?.publishedAt ?? null);

  return (
    <motion.section
      className="reader-overlay"
      role="dialog"
      aria-modal="true"
      aria-label={title}
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: 16 }}
      transition={{ duration: 0.22 }}
    >
      <header className="reader-bar">
        <button type="button" className="reader-close" onClick={onClose} aria-label="닫기">
          <X size={18} aria-hidden />
        </button>
        <span className="reader-bar-press">{press}</span>
        <a className="reader-origin" href={item.url} target="_blank" rel="noreferrer noopener">
          원문 <ExternalLink size={13} aria-hidden />
        </a>
      </header>

      <div className="reader-scroll">
        <article className="reader-article">
          <h1 className="reader-title">{title}</h1>
          <div className="reader-meta">
            {press && <span>{press}</span>}
            {when && <span>{when}</span>}
          </div>

          {loading && (
            <div className="reader-skeleton" aria-hidden>
              {Array.from({ length: 6 }).map((_, index) => (
                <span key={index} className="skeleton-line" />
              ))}
            </div>
          )}

          {!loading && failed && (
            <div className="reader-fallback">
              <p>이 기사는 앱에서 바로 열 수 없어요.</p>
              <a className="reader-fallback-link" href={item.url} target="_blank" rel="noreferrer noopener">
                원문으로 보기 <ExternalLink size={14} aria-hidden />
              </a>
            </div>
          )}

          {!loading && !failed && article && (
            <>
              {article.leadImage && (
                <img className="reader-lead" src={article.leadImage} alt="" loading="lazy" />
              )}
              {article.blocks.map((block, index) =>
                block.type === 'img' && block.src ? (
                  <img key={index} className="reader-img" src={block.src} alt="" loading="lazy" />
                ) : block.text ? (
                  <p key={index} className="reader-p">{block.text}</p>
                ) : null
              )}
              <a className="reader-origin-foot" href={item.url} target="_blank" rel="noreferrer noopener">
                원문에서 계속 보기 <ExternalLink size={14} aria-hidden />
              </a>
            </>
          )}
        </article>
      </div>
    </motion.section>
  );
}
