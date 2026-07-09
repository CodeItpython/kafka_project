import { useCallback, useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { ExternalLink, Plus, RefreshCcw, Search, ShoppingBag, ShoppingCart, Store, TrendingUp, X } from 'lucide-react';
import ThumbImage from './ThumbImage';

type ShoppingCategory = { code: string; label: string };
type PopularKeyword = { rank: number; keyword: string; count: number };
export type ShoppingProduct = {
  productId: string;
  title: string;
  link: string;
  image: string;
  price: number;
  mallName: string | null;
  brand: string | null;
  category: string | null;
};

const SHOP_ROOT = '/api/shopping';
const DISPLAY = 20;
const MAX_START = 161; // 무한 스크롤 상한(네이버 start 한계 + 과도한 페이징 방지)
const PULL_THRESHOLD = 64;
const MAX_PULL = 92;
const MIN_REFRESH_MS = 600; // 새로고침 스피너 최소 표시 시간(빠른 응답에도 "새로고침한 느낌")
const ROLL_INTERVAL = 2800; // 인기검색어 자동 롤링 주기(ms)

const SORTS: { code: string; label: string }[] = [
  { code: 'sim', label: '인기순' },
  { code: 'asc', label: '낮은가격순' },
  { code: 'dsc', label: '높은가격순' },
  { code: 'date', label: '최신순' }
];

function formatPrice(price: number) {
  return price > 0 ? `${price.toLocaleString('ko-KR')}원` : '가격문의';
}

export default function ShoppingFeed({
  onAddToCart,
  onOpenCart,
  cartCount,
  addingId
}: {
  onAddToCart: (product: ShoppingProduct) => void;
  onOpenCart: () => void;
  cartCount: number;
  addingId: string | null;
}) {
  const [categories, setCategories] = useState<ShoppingCategory[]>([]);
  const [active, setActive] = useState<string>('');
  const [sort, setSort] = useState<string>('sim');
  const [items, setItems] = useState<ShoppingProduct[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notConfigured, setNotConfigured] = useState(false);
  const [pull, setPull] = useState(0);

  const [input, setInput] = useState('');
  const [term, setTerm] = useState(''); // 제출된 검색어(비어 있으면 카테고리 피드)
  const [searchOpen, setSearchOpen] = useState(false); // 검색창은 기본 접힘(아이콘) → 클릭 시 펼침
  const [popular, setPopular] = useState<PopularKeyword[]>([]);
  const [popIdx, setPopIdx] = useState(0);
  const [related, setRelated] = useState<string[]>([]); // 연관검색어(검색 시)
  const [suggestions, setSuggestions] = useState<string[]>([]); // 자동완성 후보(입력 중)
  const [suggestOpen, setSuggestOpen] = useState(false);
  const [suggestActive, setSuggestActive] = useState(-1);
  const suppressSuggestRef = useRef(false); // 항목 선택/제출 직후 입력 변경으로 드롭다운이 다시 열리는 것 방지
  const suggestSeqRef = useRef(0); // 진행 중 자동완성 요청 세대 — 닫은 뒤 도착한 stale 응답이 다시 열지 못하게

  const rootRef = useRef<HTMLDivElement>(null);
  const reqRef = useRef(0);
  const nextStartRef = useRef(1);
  const refreshCountRef = useRef(0);
  const loadingRef = useRef(false);
  const loadingMoreRef = useRef(false);
  const refreshingRef = useRef(false);
  const touchStartY = useRef<number | null>(null);
  const wheelAccum = useRef(0);
  const wheelResetTimer = useRef<number | undefined>(undefined);
  const wheelRefreshLatch = useRef(false); // 한 휠 제스처(관성 포함)당 새로고침 1회만 허용

  const searching = term.trim().length > 0;

  useEffect(() => {
    let cancelled = false;
    fetch(`${SHOP_ROOT}/categories`)
      .then((response) => (response.ok ? response.json() : Promise.reject(new Error(String(response.status)))))
      .then((data: ShoppingCategory[]) => {
        if (cancelled || !Array.isArray(data) || data.length === 0) return;
        setCategories(data);
        setActive((current) => current || data[0].code);
      })
      .catch(() => {
        if (!cancelled) setError('쇼핑 서비스에 연결하지 못했습니다.');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // 인기검색어: 최초 로드 + 60초마다 갱신
  useEffect(() => {
    let cancelled = false;
    const load = () =>
      fetch(`${SHOP_ROOT}/popular-keywords`)
        .then((response) => (response.ok ? response.json() : []))
        .then((data: PopularKeyword[]) => {
          if (cancelled || !Array.isArray(data)) return;
          setPopular(data);
          setPopIdx(0);
        })
        .catch(() => {});
    load();
    const timer = window.setInterval(load, 60000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, []);

  // 인기검색어 1→10위 자동 롤링(한 박스 안에서 순환)
  useEffect(() => {
    if (popular.length <= 1) return;
    const timer = window.setInterval(() => {
      setPopIdx((index) => (index + 1) % popular.length);
    }, ROLL_INTERVAL);
    return () => window.clearInterval(timer);
  }, [popular.length]);

  // 연관검색어: 검색어가 제출되면 ES significant_text 결과를 가져온다(카테고리 피드일 땐 비움)
  useEffect(() => {
    const keyword = term.trim();
    if (!keyword) {
      setRelated([]);
      return;
    }
    let cancelled = false;
    fetch(`${SHOP_ROOT}/related?query=${encodeURIComponent(keyword)}&size=8`)
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

  // 자동완성: 입력 중(input) prefix로 후보를 디바운스 조회. 항목 선택/제출 직후에는 건너뛴다.
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
      fetch(`${SHOP_ROOT}/suggest?query=${encodeURIComponent(prefix)}&size=8`)
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

  const loadPage = useCallback(
    (category: string, sortCode: string, keyword: string, startAt: number, mode: 'replace' | 'append', refresh: boolean) => {
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
        setNotConfigured(false);
      }
      const url = keyword
        ? `${SHOP_ROOT}/search?query=${encodeURIComponent(keyword)}&sort=${encodeURIComponent(sortCode)}`
          + `&display=${DISPLAY}&start=${startAt}`
        : `${SHOP_ROOT}/feed?category=${encodeURIComponent(category)}&sort=${encodeURIComponent(sortCode)}`
          + `&display=${DISPLAY}&start=${startAt}${refresh ? '&refresh=true' : ''}`;
      return fetch(url)
        .then(async (response) => {
          if (response.ok) return response.json();
          const body = await response.json().catch(() => null);
          if (response.status === 503 && body?.code === 'NAVER_NOT_CONFIGURED') {
            throw new Error('NAVER_NOT_CONFIGURED');
          }
          throw new Error(String(response.status));
        })
        .then((data: ShoppingProduct[]) => {
          if (myReq !== reqRef.current) return; // 카테고리/정렬/검색으로 무효화된 응답 폐기
          const list = Array.isArray(data) ? data : [];
          if (mode === 'append') {
            setItems((prev) => {
              const seen = new Set(prev.map((p) => p.productId));
              return [...prev, ...list.filter((p) => !seen.has(p.productId))];
            });
          } else {
            setItems(list);
          }
          nextStartRef.current = startAt + DISPLAY;
          setHasMore(list.length >= DISPLAY && startAt + DISPLAY <= MAX_START);
        })
        .catch((err: Error) => {
          if (myReq !== reqRef.current) return;
          if (mode === 'append') {
            setHasMore(false);
            return;
          }
          setItems([]);
          if (err.message === 'NAVER_NOT_CONFIGURED') {
            setNotConfigured(true);
          } else {
            setError('상품을 불러오지 못했습니다.');
          }
        })
        .finally(() => {
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
    },
    []
  );

  // 카테고리/정렬/검색어 변경 → 처음부터 다시
  useEffect(() => {
    if (!active) return;
    nextStartRef.current = 1;
    setHasMore(true);
    setItems([]);
    setPull(0);
    wheelAccum.current = 0;
    loadPage(active, sort, term.trim(), 1, 'replace', false);
  }, [active, sort, term, loadPage]);

  function loadMore() {
    if (!hasMore || loadingRef.current || loadingMoreRef.current || refreshingRef.current || !active) return;
    loadPage(active, sort, term.trim(), nextStartRef.current, 'append', false);
  }

  const triggerRefresh = useCallback(() => {
    if (refreshingRef.current || loadingRef.current || !active) return;
    // 새로고침 중에도 기존 목록을 유지(빈 화면 깜빡임 방지). 새 데이터가 오면 교체된다(stale-while-revalidate).
    setHasMore(true);
    if (term.trim()) {
      loadPage(active, sort, term.trim(), 1, 'replace', true);
      return;
    }
    refreshCountRef.current += 1;
    // 새로고침마다 시작 오프셋을 41→81→121로 순환(초기 1페이지로 안 돌아가게)시켜 매번 새 인기상품 노출(캐시 우회).
    const rotatedStart = (((refreshCountRef.current - 1) % 3) + 1) * (DISPLAY * 2) + 1;
    loadPage(active, sort, '', rotatedStart, 'replace', true);
  }, [active, sort, term, loadPage]);

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

  // 아래로 스크롤 → 더 불러오기
  function handleScroll() {
    const el = rootRef.current;
    if (!el) return;
    if (el.scrollHeight - el.scrollTop - el.clientHeight < 340) {
      loadMore();
    }
  }

  const activeLabel = categories.find((category) => category.code === active)?.label ?? '';
  const sectionTitle = searching ? `‘${term.trim()}’ 검색 결과` : `#${activeLabel || '상품'}`;
  const indicatorHeight = refreshing ? 46 : pull;
  const armed = pull >= PULL_THRESHOLD;
  const current = popular.length > 0 ? popular[popIdx % popular.length] : null;

  return (
    <div
      className="shop-root"
      ref={rootRef}
      onScroll={handleScroll}
      onWheel={handleWheel}
      onTouchStart={handleTouchStart}
      onTouchMove={handleTouchMove}
      onTouchEnd={handleTouchEnd}
      onTouchCancel={handleTouchEnd}
    >
      <div className="shop-pull" style={{ height: `${indicatorHeight}px` }} aria-hidden={indicatorHeight === 0}>
        {indicatorHeight > 0 && (
          <div className={refreshing ? 'shop-pull-inner loading' : 'shop-pull-inner'}>
            <RefreshCcw size={15} aria-hidden />
            <span>{refreshing ? '새 상품 불러오는 중' : armed ? '놓으면 새로고침' : '당겨서 새로고침'}</span>
          </div>
        )}
      </div>

      <header className="shop-hero">
        <div className="shop-hero-copy">
          <span className="shop-hero-eyebrow">NAVER SHOPPING</span>
          <h2>#특가픽</h2>
        </div>
        <div className="shop-hero-actions">
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
          <button type="button" className="shop-cart-button" onClick={onOpenCart} title="장바구니">
            <ShoppingCart size={18} aria-hidden />
            {cartCount > 0 && <span className="shop-cart-badge">{cartCount > 99 ? '99+' : cartCount}</span>}
          </button>
        </div>
      </header>

      <div className="shop-searchbar">
        {searchOpen && (
        <form className="shop-search-form" onSubmit={submitSearch} role="search">
          <Search size={17} aria-hidden />
          <input
            type="search"
            value={input}
            autoFocus
            onChange={(event) => setInput(event.target.value)}
            onFocus={() => { if (suggestions.length > 0) setSuggestOpen(true); }}
            onBlur={() => setSuggestOpen(false)}
            onKeyDown={onSearchKeyDown}
            placeholder="상품을 검색해보세요"
            aria-label="상품 검색"
            enterKeyHint="search"
            role="combobox"
            aria-expanded={suggestOpen}
            aria-controls="shop-suggest-list"
          />
          {input && (
            <button type="button" className="shop-search-clear" onClick={clearSearch} aria-label="검색어 지우기">
              <X size={15} aria-hidden />
            </button>
          )}
          <button type="submit" className="shop-search-submit">검색</button>
          {suggestOpen && suggestions.length > 0 && (
            <ul className="shop-suggest" id="shop-suggest-list" role="listbox" aria-label="검색어 자동완성">
              {suggestions.map((suggestion, index) => (
                <li key={suggestion} role="option" aria-selected={index === suggestActive}>
                  <button
                    type="button"
                    className={index === suggestActive ? 'shop-suggest-item active' : 'shop-suggest-item'}
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

        {current && (
          <div className="shop-popular" tabIndex={0} aria-label="실시간 인기검색어">
            <span className="shop-popular-label"><TrendingUp size={13} aria-hidden /> 인기검색어</span>
            <button type="button" className="shop-popular-view" onClick={() => runSearch(current.keyword)}>
              <AnimatePresence mode="wait" initial={false}>
                <motion.span
                  key={popIdx}
                  className="shop-popular-current"
                  initial={{ y: 14, opacity: 0 }}
                  animate={{ y: 0, opacity: 1 }}
                  exit={{ y: -14, opacity: 0 }}
                  transition={{ duration: 0.32, ease: 'easeOut' }}
                >
                  <b className="shop-popular-rank">{current.rank}</b>
                  <span className="shop-popular-kw">{current.keyword}</span>
                </motion.span>
              </AnimatePresence>
            </button>
            <div className="shop-popular-list" role="listbox" aria-label="인기검색어 1~10위">
              <p className="shop-popular-list-head"><TrendingUp size={13} aria-hidden /> 실시간 인기검색어</p>
              {popular.map((keyword) => (
                <button
                  key={keyword.keyword}
                  type="button"
                  className="shop-popular-item"
                  onClick={() => runSearch(keyword.keyword)}
                >
                  <b className={keyword.rank <= 3 ? 'shop-popular-rank hot' : 'shop-popular-rank'}>{keyword.rank}</b>
                  <span className="shop-popular-kw">{keyword.keyword}</span>
                </button>
              ))}
            </div>
          </div>
        )}

        {searching && related.length > 0 && (
          <div className="shop-related" aria-label="연관검색어">
            <span className="shop-related-label">연관검색어</span>
            <div className="shop-related-chips">
              {related.map((keyword) => (
                <button
                  key={keyword}
                  type="button"
                  className="shop-related-chip"
                  onClick={() => runSearch(keyword)}
                >
                  {keyword}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>

      <div className="shop-catbar" role="tablist" aria-label="쇼핑 카테고리">
        {categories.map((category) => (
          <button
            key={category.code}
            type="button"
            role="tab"
            aria-selected={!searching && active === category.code}
            className={!searching && active === category.code ? 'active' : ''}
            onClick={() => selectCategory(category.code)}
          >
            {category.label}
          </button>
        ))}
      </div>

      <div className="shop-sortbar">
        <strong className="shop-section-title">{sectionTitle}</strong>
        <div className="shop-sorts">
          {SORTS.map((option) => (
            <button
              key={option.code}
              type="button"
              className={sort === option.code ? 'active' : ''}
              onClick={() => setSort(option.code)}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>

      {loading && (
        <div className="shop-grid">
          {Array.from({ length: 6 }).map((_, index) => (
            <div key={index} className="shop-card shop-card--skeleton" aria-hidden>
              <div className="shop-card-thumb" />
              <div className="shop-card-body">
                <span className="skeleton-line" />
                <span className="skeleton-line short" />
              </div>
            </div>
          ))}
        </div>
      )}

      {!loading && notConfigured && (
        <div className="shop-empty">
          <Store size={26} aria-hidden />
          <p>네이버 쇼핑 API 키가 설정되면 상품이 표시됩니다.</p>
          <small>서버 환경변수 <code>NAVER_CLIENT_ID</code> / <code>NAVER_CLIENT_SECRET</code>를 설정하세요.</small>
        </div>
      )}

      {!loading && !notConfigured && error && (
        <div className="shop-empty">
          <p>{error}</p>
          <button type="button" className="news-retry" onClick={() => loadPage(active, sort, term.trim(), 1, 'replace', true)}>
            <RefreshCcw size={15} aria-hidden /> 다시 시도
          </button>
        </div>
      )}

      {!loading && !notConfigured && !error && items.length === 0 && (
        <div className="shop-empty"><p>{searching ? '검색 결과가 없습니다.' : '표시할 상품이 없습니다.'}</p></div>
      )}

      {!loading && !notConfigured && !error && items.length > 0 && (
        <>
          <div className="shop-grid">
            {items.map((item, index) => (
              <motion.div
                key={`${item.productId}-${index}`}
                className="shop-card"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.26, delay: Math.min((index % DISPLAY) * 0.015, 0.25) }}
                whileHover={{ scale: 1.03, transition: { type: 'spring', stiffness: 320, damping: 24 } }}
                whileTap={{ scale: 0.98, transition: { type: 'spring', stiffness: 320, damping: 24 } }}
              >
                <a className="shop-card-thumb" href={item.link} target="_blank" rel="noreferrer noopener" title={item.title}>
                  {item.image ? (
                    <ThumbImage src={item.image} />
                  ) : (
                    <span className="shop-card-thumb--empty" aria-hidden><ShoppingBag aria-hidden /></span>
                  )}
                </a>
                <div className="shop-card-body">
                  <a className="shop-card-title" href={item.link} target="_blank" rel="noreferrer noopener">{item.title}</a>
                  <span className="shop-card-mall">{item.mallName || item.brand || '네이버쇼핑'} <ExternalLink size={11} aria-hidden /></span>
                  <div className="shop-card-foot">
                    <strong className="shop-card-price">{formatPrice(item.price)}</strong>
                    <button
                      type="button"
                      className="shop-add-button"
                      onClick={() => onAddToCart(item)}
                      disabled={addingId === item.productId}
                      title="장바구니 담기"
                    >
                      <Plus size={15} aria-hidden />담기
                    </button>
                  </div>
                </div>
              </motion.div>
            ))}
          </div>
          <div className="shop-more">
            {loadingMore && <span className="shop-more-loading"><RefreshCcw size={14} aria-hidden /> 더 불러오는 중…</span>}
            {!loadingMore && !hasMore && <span className="shop-more-end">{searching ? '검색 결과를 모두 불러왔어요' : '모든 상품을 불러왔어요'}</span>}
          </div>
        </>
      )}
    </div>
  );
}
