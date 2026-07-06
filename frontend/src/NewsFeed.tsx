import { useEffect, useState } from 'react';
import { motion } from 'motion/react';
import { ExternalLink, Newspaper, RefreshCcw, Share2 } from 'lucide-react';

type NewsCategory = { code: string; label: string };
export type NewsItem = { id: string; title: string; url: string; press: string | null; thumbnail: string | null };

const NEWS_ROOT = '/api/news';

export default function NewsFeed({ onShare }: { onShare?: (item: NewsItem) => void }) {
  const [categories, setCategories] = useState<NewsCategory[]>([]);
  const [active, setActive] = useState<string>('');
  const [items, setItems] = useState<NewsItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetch(`${NEWS_ROOT}/categories`)
      .then((response) => (response.ok ? response.json() : Promise.reject(new Error(String(response.status)))))
      .then((data: NewsCategory[]) => {
        if (cancelled || !Array.isArray(data) || data.length === 0) return;
        setCategories(data);
        setActive((current) => current || data[0].code);
      })
      .catch(() => {
        if (!cancelled) setError('뉴스 서비스에 연결하지 못했습니다.');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!active) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    fetch(`${NEWS_ROOT}/feed?category=${encodeURIComponent(active)}`)
      .then((response) => (response.ok ? response.json() : Promise.reject(new Error(String(response.status)))))
      .then((data: { items: NewsItem[] }) => {
        if (cancelled) return;
        setItems(Array.isArray(data.items) ? data.items : []);
      })
      .catch(() => {
        if (!cancelled) {
          setItems([]);
          setError('뉴스를 불러오지 못했습니다.');
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [active]);

  return (
    <div className="news-feed-root">
      <header className="news-header">
        <h2>뉴스</h2>
        <p className="muted">네이버 뉴스에서 모은 주요 소식이에요.</p>
      </header>

      <div className="news-catbar" role="tablist" aria-label="뉴스 카테고리">
        {categories.map((category) => (
          <button
            key={category.code}
            type="button"
            role="tab"
            aria-selected={active === category.code}
            className={active === category.code ? 'active' : ''}
            onClick={() => setActive(category.code)}
          >
            {category.label}
          </button>
        ))}
      </div>

      {loading && (
        <div className="news-list">
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className="news-card news-card--skeleton" aria-hidden>
              <div className="news-card-thumb" />
              <div className="news-card-body">
                <span className="skeleton-line" />
                <span className="skeleton-line short" />
              </div>
            </div>
          ))}
        </div>
      )}

      {!loading && error && (
        <div className="news-empty">
          <p>{error}</p>
          <button type="button" className="news-retry" onClick={() => setActive((current) => current)}>
            <RefreshCcw size={15} aria-hidden /> 다시 시도
          </button>
        </div>
      )}

      {!loading && !error && items.length === 0 && (
        <div className="news-empty">
          <p>표시할 뉴스가 없습니다.</p>
        </div>
      )}

      {!loading && !error && items.length > 0 && (
        <div className="news-list">
          {items.map((item, index) => (
            <motion.div
              key={item.id}
              className="news-card-wrap"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.28, delay: Math.min(index * 0.02, 0.3) }}
            >
              <a
                className="news-card"
                href={item.url}
                target="_blank"
                rel="noreferrer noopener"
              >
                {item.thumbnail ? (
                  <div className="news-card-thumb">
                    <img src={item.thumbnail} alt="" loading="lazy" referrerPolicy="no-referrer" />
                  </div>
                ) : (
                  <div className="news-card-thumb news-card-thumb--empty" aria-hidden>
                    <Newspaper aria-hidden />
                  </div>
                )}
                <div className="news-card-body">
                  <strong className="news-card-title">{item.title}</strong>
                  <span className="news-card-meta">
                    {item.press ? `${item.press} · ` : ''}네이버뉴스 <ExternalLink size={13} aria-hidden />
                  </span>
                </div>
              </a>
              {onShare && (
                <button
                  type="button"
                  className="news-share-btn"
                  title="채팅으로 공유"
                  aria-label={`${item.title} 채팅으로 공유`}
                  onClick={() => onShare(item)}
                >
                  <Share2 size={15} aria-hidden />
                  <span>공유</span>
                </button>
              )}
            </motion.div>
          ))}
        </div>
      )}
    </div>
  );
}
