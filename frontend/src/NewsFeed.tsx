import { useCallback, useEffect, useRef, useState } from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { ExternalLink, Newspaper, RefreshCcw, Search, Share2, X } from 'lucide-react';
import ThumbImage from './ThumbImage';
import YouthPanel from './YouthPanel';

type NewsCategory = { code: string; label: string };
export type NewsItem = {
  id: string;
  title: string;
  url: string;
  press: string | null;
  thumbnail: string | null;
  description: string | null;
};

const NEWS_ROOT = '/api/news';
const DISPLAY = 20;
const MAX_START = 181; // 무한 스크롤 상한(네이버 start 한계 + 과도한 페이징 방지)
const PULL_THRESHOLD = 64;
const MAX_PULL = 92;
const MIN_REFRESH_MS = 600; // 새로고침 스피너 최소 표시 시간(빠른 응답에도 "새로고침한 느낌")

// og:image 지연 로딩 캐시(url → 이미지 URL, 또는 null = 조회했지만 이미지 없음)
const ogImageCache = new Map<string, string | null>();

/**
 * 뉴스 검색 API는 썸네일을 주지 않으므로, 카드가 화면에 보일 때(IntersectionObserver) 링크 프리뷰
 * 엔드포인트로 기사 og:image를 지연 조회해 채운다. 보이는 카드만 요청 + 캐시로 중복/부하를 줄인다.
 */
export function NewsThumb({ url, fallback }: { url: string; fallback: string | null }) {
  const [image, setImage] = useState<string | null>(fallback ?? ogImageCache.get(url) ?? null);
  const ref = useRef<HTMLDivElement>(null);
  const requested = useRef(false);

  useEffect(() => {
    if (fallback) return;
    if (ogImageCache.has(url)) {
      setImage(ogImageCache.get(url) ?? null);
      return;
    }
    const el = ref.current;
    if (!el) return;
    let cancelled = false;
    const observer = new IntersectionObserver(
      (entries) => {
        if (!entries.some((entry) => entry.isIntersecting) || requested.current) return;
        requested.current = true;
        observer.disconnect();
        fetch(`${NEWS_ROOT}/link-preview?url=${encodeURIComponent(url)}`)
          .then((response) => {
            if (response.status === 200) return response.json();
            if (response.status === 204) return null; // 프리뷰 없음(확정) → null 캐시 OK
            throw new Error(String(response.status)); // 4xx/5xx = 일시적 → 캐시하지 않고 재마운트 시 재시도
          })
          .then((data: { image?: string } | null) => {
            const img = data && data.image ? data.image : null;
            ogImageCache.set(url, img);
            if (!cancelled) setImage(img);
          })
          .catch(() => {
            // 일시적 실패는 음수 캐시하지 않는다(백엔드 복구 후 카드가 다시 보일 때 재시도되도록).
          });
      },
      { rootMargin: '200px' }
    );
    observer.observe(el);
    return () => {
      cancelled = true;
      observer.disconnect();
    };
  }, [url, fallback]);

  if (image) {
    return (
      <div className="news-card-thumb">
        <ThumbImage src={image} />
      </div>
    );
  }
  return (
    <div className="news-card-thumb news-card-thumb--empty" ref={ref} aria-hidden>
      <Newspaper aria-hidden />
    </div>
  );
}

// 제목 앞의 [속보]/속보 표기를 감지해 배지로 대체한다.
const BREAKING_RE = /^\s*\[?\s*속보\s*\]?\s*[:·\-]?\s*/;
function isBreaking(title: string) {
  return BREAKING_RE.test(title);
}
function stripBreaking(title: string) {
  return title.replace(BREAKING_RE, '').trim();
}

export default function NewsFeed({ onShare }: { onShare?: (item: NewsItem) => void }) {
  const reduceMotion = useReducedMotion();
  const [categories, setCategories] = useState<NewsCategory[]>([]);
  const [active, setActive] = useState<string>('');
  const [items, setItems] = useState<NewsItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pull, setPull] = useState(0);

  const [input, setInput] = useState('');
  const [term, setTerm] = useState(''); // 제출된 검색어(비어 있으면 카테고리 피드)
  const [searchOpen, setSearchOpen] = useState(false); // 검색창은 기본 접힘(아이콘) → 클릭 시 펼침
  const [related, setRelated] = useState<string[]>([]); // 연관검색어
  const [suggestions, setSuggestions] = useState<string[]>([]); // 자동완성 후보(입력 중)
  const [suggestOpen, setSuggestOpen] = useState(false);
  const [suggestActive, setSuggestActive] = useState(-1);
  const suppressSuggestRef = useRef(false); // 항목 선택/제출 직후 재열림 방지
  const suggestSeqRef = useRef(0); // 진행 중 자동완성 요청 세대 — 닫은 뒤 도착한 stale 응답이 다시 열지 못하게
  const searching = term.trim().length > 0;

  const rootRef = useRef<HTMLDivElement>(null);
  const reqRef = useRef(0);
  const nextStartRef = useRef(1);
  const refreshCountRef = useRef(0); // 새로고침마다 시작 오프셋을 순환시켜 새 기사 노출
  const loadingRef = useRef(false);
  const loadingMoreRef = useRef(false);
  const refreshingRef = useRef(false);
  const touchStartY = useRef<number | null>(null);
  const wheelAccum = useRef(0);
  const wheelResetTimer = useRef<number | undefined>(undefined);
  const wheelRefreshLatch = useRef(false); // 한 휠 제스처(관성 포함)당 새로고침 1회만 허용

  useEffect(() => {
    let cancelled = false;
    fetch(`${NEWS_ROOT}/categories`)
      .then((response) => (response.ok ? response.json() : Promise.reject(new Error(String(response.status)))))
      .then((data: NewsCategory[]) => {
        if (cancelled || !Array.isArray(data) || data.length === 0) return;
        // '청년'은 백엔드 뉴스 카테고리(enum)가 아니라 커스텀 패널이므로 프론트에서 칩만 덧붙인다.
        setCategories([...data, { code: 'youth', label: '청년' }]);
        setActive((current) => current || data[0].code);
      })
      .catch(() => {
        if (!cancelled) setError('뉴스 서비스에 연결하지 못했습니다.');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const loadPage = useCallback((category: string, keyword: string, startAt: number, mode: 'replace' | 'append', refresh: boolean) => {
    if (!category && !keyword) return Promise.resolve();
    const myReq = mode === 'replace' ? ++reqRef.current : reqRef.current;
    const startedAt = Date.now(); // 새로고침 스피너 최소 표시 시간 계산용
    if (mode === 'append') {
      loadingMoreRef.current = true;
      setLoadingMore(true);
    } else if (refresh) {
      refreshingRef.current = true;
      setRefreshing(true);
    } else {
      loadingRef.current = true;
      setLoading(true);
      setError(null);
    }
    const url = keyword
      ? `${NEWS_ROOT}/search?query=${encodeURIComponent(keyword)}&start=${startAt}&display=${DISPLAY}`
      : `${NEWS_ROOT}/feed?category=${encodeURIComponent(category)}`
        + `&start=${startAt}&display=${DISPLAY}${refresh ? '&refresh=true' : ''}`;
    return fetch(url)
      .then((response) => (response.ok ? response.json() : Promise.reject(new Error(String(response.status)))))
      .then((data: { items: NewsItem[] }) => {
        if (myReq !== reqRef.current) return; // 카테고리/새로고침으로 무효화된 응답 폐기
        const list = Array.isArray(data.items) ? data.items : [];
        if (mode === 'append') {
          setItems((prev) => {
            const seen = new Set(prev.map((n) => n.id));
            return [...prev, ...list.filter((n) => !seen.has(n.id))];
          });
        } else {
          setItems(list);
        }
        nextStartRef.current = startAt + DISPLAY;
        setHasMore(list.length >= DISPLAY && startAt + DISPLAY <= MAX_START);
      })
      .catch(() => {
        if (myReq !== reqRef.current) return;
        if (mode === 'append') {
          // 일시적 오류로 무한 스크롤을 영구 중단시키지 않는다 — hasMore를 유지해 다음 스크롤에 재시도.
          return;
        }
        setError('뉴스를 불러오지 못했습니다.');
        setItems([]);
      })
      .finally(() => {
        if (myReq !== reqRef.current) return; // 무효화된(오래된) 응답은 최신 요청의 로딩 상태를 건드리지 않음
        loadingRef.current = false;
        loadingMoreRef.current = false;
        setLoading(false);
        setLoadingMore(false);
        wheelAccum.current = 0;
        if (refresh) {
          // 로딩이 빨리 끝나도 스피너를 최소 시간만큼 유지(남은 시간만 지연). 더 오래 걸리면 즉시 종료.
          const remaining = Math.max(0, MIN_REFRESH_MS - (Date.now() - startedAt));
          window.setTimeout(() => {
            refreshingRef.current = false;
            setRefreshing(false);
            setPull(0);
          }, remaining);
        } else {
          refreshingRef.current = false;
          setRefreshing(false);
          setPull(0);
        }
      });
  }, []);

  // 카테고리/검색어 변경 → 처음부터 다시
  useEffect(() => {
    if (!active) return;
    const keyword = term.trim();
    // '청년' 탭(검색 중이 아닐 때)은 YouthPanel이 자체적으로 데이터를 불러오므로 카테고리 피드 로딩을 스킵한다.
    if (active === 'youth' && !keyword) {
      setItems([]);
      setError(null);
      setLoading(false);
      setHasMore(false);
      return;
    }
    nextStartRef.current = 1;
    setHasMore(true);
    setItems([]);
    setPull(0);
    wheelAccum.current = 0;
    loadPage(active, keyword, 1, 'replace', false);
    return () => {
      if (wheelResetTimer.current) window.clearTimeout(wheelResetTimer.current);
    };
  }, [active, term, loadPage]);

  // 연관검색어: 검색어가 제출되면 ES significant_text 결과를 가져온다(카테고리 피드일 땐 비움)
  useEffect(() => {
    const keyword = term.trim();
    if (!keyword) {
      setRelated([]);
      return;
    }
    let cancelled = false;
    fetch(`${NEWS_ROOT}/related?query=${encodeURIComponent(keyword)}&size=8`)
      .then((response) => (response.ok ? response.json() : []))
      .then((data: string[]) => {
        if (!cancelled && Array.isArray(data)) setRelated(data);
      })
      .catch(() => {
        if (!cancelled) setRelated([]);
      });
    return () => {
      cancelled = true;
    };
  }, [term]);

  // 자동완성: 입력 중(input) prefix로 후보를 디바운스 조회. 선택/제출 직후에는 건너뛴다.
  useEffect(() => {
    if (suppressSuggestRef.current) {
      suppressSuggestRef.current = false;
      return;
    }
    const prefix = input.trim();
    if (!prefix) {
      setSuggestions([]);
      setSuggestOpen(false);
      return;
    }
    let cancelled = false;
    const seq = suggestSeqRef.current;
    const timer = window.setTimeout(() => {
      fetch(`${NEWS_ROOT}/suggest?query=${encodeURIComponent(prefix)}&size=8`)
        .then((response) => (response.ok ? response.json() : []))
        .then((data: string[]) => {
          // 검색 제출/선택으로 닫힌 뒤 도착한 응답은 무시(엔터 후 드롭다운 재노출 방지).
          if (cancelled || seq !== suggestSeqRef.current || !Array.isArray(data)) return;
          setSuggestions(data);
          setSuggestOpen(data.length > 0);
          setSuggestActive(-1);
        })
        .catch(() => {
          if (!cancelled) setSuggestions([]);
        });
    }, 200);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [input]);

  function loadMore() {
    if (!hasMore || loadingRef.current || loadingMoreRef.current || refreshingRef.current || !active) return;
    loadPage(active, term.trim(), nextStartRef.current, 'append', false);
  }

  const triggerRefresh = useCallback(() => {
    if (refreshingRef.current || loadingRef.current || !active) return;
    // 청년 패널은 카테고리 피드가 아니므로 당겨서 새로고침 대상에서 제외(불필요한 /feed 호출 방지).
    if (active === 'youth' && !term.trim()) return;
    // 새로고침 중에도 기존 목록을 유지(빈 화면 깜빡임 방지). 새 데이터가 오면 교체된다(stale-while-revalidate).
    setHasMore(true);
    if (term.trim()) {
      nextStartRef.current = 1 + DISPLAY;
      loadPage(active, term.trim(), 1, 'replace', true);
      return;
    }
    refreshCountRef.current += 1;
    // 새로고침마다 시작 오프셋을 41→81→121로 순환(초기 1페이지로 안 돌아가게)시켜 매번 새 기사 노출(캐시 우회).
    const rotatedStart = (((refreshCountRef.current - 1) % 3) + 1) * (DISPLAY * 2) + 1;
    loadPage(active, '', rotatedStart, 'replace', true);
  }, [active, term, loadPage]);

  function closeSuggest() {
    suppressSuggestRef.current = true;
    suggestSeqRef.current += 1; // 진행 중 자동완성 요청 무효화(닫은 뒤 재노출 방지)
    setSuggestOpen(false);
    setSuggestActive(-1);
  }
  function submitSearch(event: React.FormEvent) {
    event.preventDefault();
    closeSuggest();
    setTerm(input.trim());
  }
  function runSearch(keyword: string) {
    closeSuggest();
    setInput(keyword);
    setTerm(keyword);
    rootRef.current?.scrollTo({ top: 0 });
  }
  function pickSuggestion(keyword: string) {
    closeSuggest();
    setInput(keyword);
    setTerm(keyword);
    rootRef.current?.scrollTo({ top: 0 });
  }
  function onSearchKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (!suggestOpen || suggestions.length === 0) return;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setSuggestActive((index) => (index + 1) % suggestions.length);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setSuggestActive((index) => (index - 1 + suggestions.length) % suggestions.length);
    } else if (event.key === 'Enter' && suggestActive >= 0) {
      event.preventDefault();
      pickSuggestion(suggestions[suggestActive]);
    } else if (event.key === 'Escape') {
      setSuggestOpen(false);
      setSuggestActive(-1);
    }
  }
  function clearSearch() {
    closeSuggest();
    setSuggestions([]);
    setInput('');
    setTerm('');
  }
  function selectCategory(code: string) {
    closeSuggest();
    setSuggestions([]);
    setInput('');
    setTerm('');
    setActive(code);
  }

  // 데스크톱: 최상단에서 위로 휠 → 당김/새로고침
  function handleWheel(event: React.WheelEvent<HTMLDivElement>) {
    const el = rootRef.current;
    if (!el || el.scrollTop > 0) return;
    if (event.deltaY >= 0) {
      wheelAccum.current = 0;
      wheelRefreshLatch.current = false;
      if (pull !== 0) setPull(0);
      return;
    }
    // 관성 휠은 손 뗀 뒤에도 계속 들어오므로, 상향 휠마다 리셋 타이머를 갱신해
    // 관성이 완전히 멈춘 뒤(180ms)에만 누적/래치를 해제한다 → 한 제스처당 새로고침 1회.
    if (wheelResetTimer.current) window.clearTimeout(wheelResetTimer.current);
    wheelResetTimer.current = window.setTimeout(() => {
      wheelAccum.current = 0;
      wheelRefreshLatch.current = false;
      setPull(0);
    }, 180);
    if (refreshingRef.current || wheelRefreshLatch.current) return;
    wheelAccum.current += -event.deltaY;
    setPull(Math.min(wheelAccum.current * 0.6, MAX_PULL));
    if (wheelAccum.current > PULL_THRESHOLD * 1.6) {
      wheelRefreshLatch.current = true;
      wheelAccum.current = 0;
      triggerRefresh();
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

  // 아래로 스크롤 → 더 불러오기(쇼핑 탭과 동일한 서버 페이지네이션)
  function handleScroll() {
    const el = rootRef.current;
    if (!el) return;
    if (el.scrollHeight - el.scrollTop - el.clientHeight < 340) {
      loadMore();
    }
  }

  const indicatorHeight = refreshing ? 46 : pull;
  const armed = pull >= PULL_THRESHOLD;

  return (
    <div
      className="news-feed-root"
      ref={rootRef}
      onScroll={handleScroll}
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
            <span>{refreshing ? '새 뉴스 불러오는 중' : armed ? '놓으면 새로고침' : '당겨서 새로고침'}</span>
          </div>
        )}
      </div>

      <header className="news-hero">
        <div className="news-hero-copy">
          <span className="news-hero-eyebrow">NAVER NEWS</span>
          <h2>#헤드라인</h2>
        </div>
        <button
          type="button"
          className={searchOpen ? 'feed-search-toggle active' : 'feed-search-toggle'}
          onClick={() => { if (searchOpen) { setSearchOpen(false); clearSearch(); } else { setSearchOpen(true); } }}
          title={searchOpen ? '검색 닫기' : '검색'}
          aria-label={searchOpen ? '검색 닫기' : '검색'}
          aria-expanded={searchOpen}
        >
          {searchOpen ? <X size={18} aria-hidden /> : <Search size={18} aria-hidden />}
        </button>
      </header>

      {(searchOpen || (searching && related.length > 0)) && (
      <div className="news-searchbar">
        {searchOpen && (
        <form className="news-search-form" onSubmit={submitSearch} role="search">
          <Search size={16} aria-hidden />
          <input
            type="search"
            value={input}
            autoFocus
            onChange={(event) => setInput(event.target.value)}
            onFocus={() => { if (suggestions.length > 0) setSuggestOpen(true); }}
            onBlur={() => setSuggestOpen(false)}
            onKeyDown={onSearchKeyDown}
            placeholder="뉴스 검색"
            aria-label="뉴스 검색"
            enterKeyHint="search"
            role="combobox"
            aria-expanded={suggestOpen}
            aria-controls="news-suggest-list"
          />
          {input && (
            <button type="button" className="news-search-clear" onClick={clearSearch} aria-label="검색어 지우기">
              <X size={15} aria-hidden />
            </button>
          )}
          <button type="submit" className="news-search-submit">검색</button>
          {suggestOpen && suggestions.length > 0 && (
            <ul className="news-suggest" id="news-suggest-list" role="listbox" aria-label="검색어 자동완성">
              {suggestions.map((suggestion, index) => (
                <li key={suggestion} role="option" aria-selected={index === suggestActive}>
                  <button
                    type="button"
                    className={index === suggestActive ? 'news-suggest-item active' : 'news-suggest-item'}
                    onMouseDown={(event) => event.preventDefault()}
                    onMouseEnter={() => setSuggestActive(index)}
                    onClick={() => pickSuggestion(suggestion)}
                  >
                    <Search size={14} aria-hidden />
                    <span>{suggestion}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </form>
        )}
        {searching && related.length > 0 && (
          <div className="news-related" aria-label="연관검색어">
            <span className="news-related-label">연관검색어</span>
            <div className="news-related-chips">
              {related.map((keyword) => (
                <button key={keyword} type="button" className="news-related-chip" onClick={() => runSearch(keyword)}>
                  {keyword}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
      )}

      <div className="news-catbar" role="tablist" aria-label="뉴스 카테고리">
        {categories.map((category) => {
          const isActive = !searching && active === category.code;
          return (
            <button
              key={category.code}
              type="button"
              role="tab"
              aria-selected={isActive}
              className={isActive ? 'active' : ''}
              onClick={() => selectCategory(category.code)}
            >
              {isActive && (
                <motion.span
                  className="news-cat-pill"
                  layoutId="news-cat-pill"
                  transition={reduceMotion ? { duration: 0 } : { type: 'spring', stiffness: 520, damping: 36, mass: 0.7 }}
                  aria-hidden
                />
              )}
              <span className="news-cat-label">{category.label}</span>
            </button>
          );
        })}
      </div>

      {active === 'youth' && !searching ? (
        <YouthPanel onShare={onShare} />
      ) : (
      <>
      {loading && (
        <div className="news-list">
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className="news-card news-card--skeleton" aria-hidden>
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
          <button type="button" className="news-retry" onClick={() => loadPage(active, term.trim(), 1, 'replace', true)}>
            <RefreshCcw size={15} aria-hidden /> 다시 시도
          </button>
        </div>
      )}

      {!loading && !error && items.length === 0 && (
        <div className="news-empty">
          <p>{searching ? '검색 결과가 없습니다.' : '표시할 뉴스가 없습니다.'}</p>
        </div>
      )}

      {!loading && !error && items.length > 0 && (
        <>
          <div className="news-list">
            {items.map((item, index) => {
              const breaking = isBreaking(item.title);
              const displayTitle = breaking ? stripBreaking(item.title) : item.title;
              // 헤드라인(비검색) 첫 항목은 큰 피처드 히어로 카드로 강조.
              const featured = index === 0 && !searching;
              return (
                <motion.div
                  key={`${item.id}-${index}`}
                  className={featured ? 'news-card-wrap news-card-wrap--featured' : 'news-card-wrap'}
                  initial={{ y: 10 }}
                  animate={{ y: 0 }}
                  transition={{ duration: 0.28, delay: Math.min((index % DISPLAY) * 0.02, 0.3) }}
                  whileHover={{ scale: 1.02, transition: { type: 'spring', stiffness: 320, damping: 24 } }}
                  whileTap={{ scale: 0.99, transition: { type: 'spring', stiffness: 320, damping: 24 } }}
                >
                  <a className={featured ? 'news-card news-card--featured' : 'news-card'} href={item.url} target="_blank" rel="noreferrer noopener">
                    <div className="news-card-thumb-wrap">
                      <NewsThumb url={item.url} fallback={item.thumbnail} />
                      {breaking && <span className="news-breaking-badge">속보</span>}
                    </div>
                    <div className="news-card-body">
                      <strong className="news-card-title">{displayTitle}</strong>
                      {item.description && <p className="news-card-desc">{item.description}</p>}
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
              );
            })}
          </div>
          <div className="shop-more">
            {loadingMore && <span className="shop-more-loading"><RefreshCcw size={14} aria-hidden /> 더 불러오는 중…</span>}
            {!loadingMore && !hasMore && <span className="shop-more-end">{searching ? '검색 결과를 모두 불러왔어요' : '모든 뉴스를 불러왔어요'}</span>}
          </div>
        </>
      )}
      </>
      )}
    </div>
  );
}
