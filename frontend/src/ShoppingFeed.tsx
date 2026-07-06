import { useCallback, useEffect, useRef, useState } from 'react';
import { motion } from 'motion/react';
import { ExternalLink, Plus, RefreshCcw, ShoppingBag, ShoppingCart, Store } from 'lucide-react';

type ShoppingCategory = { code: string; label: string };
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

  const loadFeed = useCallback((category: string, sortCode: string, startAt: number, mode: 'replace' | 'append', refresh: boolean) => {
    const myReq = mode === 'replace' ? ++reqRef.current : reqRef.current;
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
    const url = `${SHOP_ROOT}/feed?category=${encodeURIComponent(category)}&sort=${encodeURIComponent(sortCode)}`
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
        if (myReq !== reqRef.current) return; // 카테고리/정렬/새로고침으로 무효화된 응답 폐기
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
        refreshingRef.current = false;
        setLoading(false);
        setLoadingMore(false);
        setRefreshing(false);
        setPull(0);
        wheelAccum.current = 0;
      });
  }, []);

  // 카테고리/정렬 변경 → 처음부터 다시
  useEffect(() => {
    if (!active) return;
    nextStartRef.current = 1;
    setHasMore(true);
    setItems([]);
    setPull(0);
    wheelAccum.current = 0;
    loadFeed(active, sort, 1, 'replace', false);
  }, [active, sort, loadFeed]);

  function loadMore() {
    if (!hasMore || loadingRef.current || loadingMoreRef.current || refreshingRef.current || !active) return;
    loadFeed(active, sort, nextStartRef.current, 'append', false);
  }

  const triggerRefresh = useCallback(() => {
    if (refreshingRef.current || loadingRef.current || !active) return;
    refreshCountRef.current += 1;
    // 새로고침마다 시작 오프셋을 순환시켜 "새로운 인기상품"을 노출(캐시도 우회).
    const rotatedStart = ((refreshCountRef.current % 4) * DISPLAY * 2) + 1; // 1, 41, 81, 121
    setItems([]);
    setHasMore(true);
    loadFeed(active, sort, rotatedStart, 'replace', true);
  }, [active, sort, loadFeed]);

  // 데스크톱: 최상단에서 위로 휠 → 당김/새로고침
  function handleWheel(event: React.WheelEvent<HTMLDivElement>) {
    const el = rootRef.current;
    if (!el || el.scrollTop > 0 || refreshingRef.current) return;
    if (event.deltaY < 0) {
      wheelAccum.current += -event.deltaY;
      setPull(Math.min(wheelAccum.current * 0.6, MAX_PULL));
      if (wheelAccum.current > PULL_THRESHOLD * 1.6) {
        triggerRefresh();
        return;
      }
      // 휠은 손 떼는 이벤트가 없으므로, 스크롤이 멈추면 곧바로 당김을 원위치로 되돌린다.
      if (wheelResetTimer.current) window.clearTimeout(wheelResetTimer.current);
      wheelResetTimer.current = window.setTimeout(() => {
        if (!refreshingRef.current) {
          wheelAccum.current = 0;
          setPull(0);
        }
      }, 140);
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

  // 아래로 스크롤 → 더 불러오기
  function handleScroll() {
    const el = rootRef.current;
    if (!el) return;
    if (el.scrollHeight - el.scrollTop - el.clientHeight < 340) {
      loadMore();
    }
  }

  const activeLabel = categories.find((category) => category.code === active)?.label ?? '';
  const indicatorHeight = refreshing ? 46 : pull;
  const armed = pull >= PULL_THRESHOLD;

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
          <p>카테고리별 인기·최저가 상품을 모았어요. 위로 당기면 새로고침돼요.</p>
        </div>
        <button type="button" className="shop-cart-button" onClick={onOpenCart} title="장바구니">
          <ShoppingCart size={20} aria-hidden />
          {cartCount > 0 && <span className="shop-cart-badge">{cartCount > 99 ? '99+' : cartCount}</span>}
        </button>
      </header>

      <div className="shop-catbar" role="tablist" aria-label="쇼핑 카테고리">
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

      <div className="shop-sortbar">
        <strong className="shop-section-title">#{activeLabel || '상품'}</strong>
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
          <button type="button" className="news-retry" onClick={() => loadFeed(active, sort, 1, 'replace', true)}>
            <RefreshCcw size={15} aria-hidden /> 다시 시도
          </button>
        </div>
      )}

      {!loading && !notConfigured && !error && items.length === 0 && (
        <div className="shop-empty"><p>표시할 상품이 없습니다.</p></div>
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
              >
                <a className="shop-card-thumb" href={item.link} target="_blank" rel="noreferrer noopener" title={item.title}>
                  {item.image ? (
                    <img src={item.image} alt="" loading="lazy" referrerPolicy="no-referrer" />
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
            {!loadingMore && !hasMore && <span className="shop-more-end">모든 상품을 불러왔어요</span>}
          </div>
        </>
      )}
    </div>
  );
}
