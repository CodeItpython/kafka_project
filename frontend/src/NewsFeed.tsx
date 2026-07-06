import { useCallback, useEffect, useRef, useState } from 'react';
import { motion } from 'motion/react';
import { ExternalLink, Newspaper, RefreshCcw, Share2 } from 'lucide-react';

type NewsCategory = { code: string; label: string };
export type NewsItem = { id: string; title: string; url: string; press: string | null; thumbnail: string | null };

const NEWS_ROOT = '/api/news';
const PULL_THRESHOLD = 64;
const MAX_PULL = 96;

export default function NewsFeed({ onShare }: { onShare?: (item: NewsItem) => void }) {
  const [categories, setCategories] = useState<NewsCategory[]>([]);
  const [active, setActive] = useState<string>('');
  const [items, setItems] = useState<NewsItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pull, setPull] = useState(0);

  const rootRef = useRef<HTMLDivElement>(null);
  const touchStartY = useRef<number | null>(null);
  const wheelAccum = useRef(0);
  const refreshingRef = useRef(false);

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

  const runFeed = useCallback((category: string, refresh: boolean) => {
    if (!category) return Promise.resolve();
    if (refresh) setRefreshing(true);
    else setLoading(true);
    setError(null);
    const suffix = refresh ? '&refresh=true' : '';
    return fetch(`${NEWS_ROOT}/feed?category=${encodeURIComponent(category)}${suffix}`)
      .then((response) => (response.ok ? response.json() : Promise.reject(new Error(String(response.status)))))
      .then((data: { items: NewsItem[] }) => {
        setItems(Array.isArray(data.items) ? data.items : []);
      })
      .catch(() => {
        setError('뉴스를 불러오지 못했습니다.');
        if (!refresh) setItems([]);
      })
      .finally(() => {
        setLoading(false);
        setRefreshing(false);
      });
  }, []);

  useEffect(() => {
    if (!active) return;
    let cancelled = false;
    setItems([]);
    runFeed(active, false).finally(() => {
      if (cancelled) return;
    });
    // 카테고리 전환 시 당김 상태 초기화
    setPull(0);
    wheelAccum.current = 0;
    return () => {
      cancelled = true;
    };
  }, [active, runFeed]);

  const triggerRefresh = useCallback(() => {
    if (refreshingRef.current || !active) return;
    refreshingRef.current = true;
    setPull(0);
    wheelAccum.current = 0;
    runFeed(active, true).finally(() => {
      refreshingRef.current = false;
    });
  }, [active, runFeed]);

  // 데스크톱: 최상단에서 위로 휠 → 당김/새로고침
  function handleWheel(event: React.WheelEvent<HTMLDivElement>) {
    const el = rootRef.current;
    if (!el || el.scrollTop > 0 || refreshingRef.current) return;
    if (event.deltaY < 0) {
      wheelAccum.current += -event.deltaY;
      setPull(Math.min(wheelAccum.current * 0.6, MAX_PULL));
      if (wheelAccum.current > PULL_THRESHOLD * 1.6) {
        triggerRefresh();
      }
    } else {
      wheelAccum.current = 0;
      if (pull !== 0) setPull(0);
    }
  }

  function handleTouchStart(event: React.TouchEvent<HTMLDivElement>) {
    const el = rootRef.current;
    touchStartY.current = el && el.scrollTop <= 0 ? event.touches[0].clientY : null;
  }
  function handleTouchMove(event: React.TouchEvent<HTMLDivElement>) {
    const el = rootRef.current;
    if (touchStartY.current === null || !el || el.scrollTop > 0 || refreshingRef.current) return;
    const dy = event.touches[0].clientY - touchStartY.current;
    if (dy > 0) setPull(Math.min(dy * 0.5, MAX_PULL));
  }
  function handleTouchEnd() {
    if (touchStartY.current === null) return;
    touchStartY.current = null;
    if (pull >= PULL_THRESHOLD) triggerRefresh();
    else setPull(0);
  }

  const indicatorHeight = refreshing ? 46 : pull;
  const armed = pull >= PULL_THRESHOLD;

  return (
    <div
      className="news-feed-root"
      ref={rootRef}
      onWheel={handleWheel}
      onTouchStart={handleTouchStart}
      onTouchMove={handleTouchMove}
      onTouchEnd={handleTouchEnd}
      onTouchCancel={handleTouchEnd}
    >
      <div className="news-pull" style={{ height: `${indicatorHeight}px` }} aria-hidden={indicatorHeight === 0}>
        {indicatorHeight > 0 && (
          <div className={refreshing ? 'news-pull-inner loading' : 'news-pull-inner'}>
            <RefreshCcw size={15} aria-hidden />
            <span>{refreshing ? '새로고침 중' : armed ? '놓으면 새로고침' : '당겨서 새로고침'}</span>
          </div>
        )}
      </div>

      <header className="news-header">
        <h2>뉴스</h2>
        <p className="muted">네이버 뉴스에서 모은 주요 소식이에요. 위로 당기면 새로고침돼요.</p>
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
          <button type="button" className="news-retry" onClick={() => runFeed(active, true)}>
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
              initial={{ y: 10 }}
              animate={{ y: 0 }}
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
