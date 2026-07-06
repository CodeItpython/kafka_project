import { useEffect, useState } from 'react';
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
  const [error, setError] = useState<string | null>(null);
  const [notConfigured, setNotConfigured] = useState(false);

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

  useEffect(() => {
    if (!active) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    setNotConfigured(false);
    fetch(`${SHOP_ROOT}/feed?category=${encodeURIComponent(active)}&sort=${encodeURIComponent(sort)}&display=30`)
      .then(async (response) => {
        if (response.ok) return response.json();
        const body = await response.json().catch(() => null);
        if (response.status === 503 && body?.code === 'NAVER_NOT_CONFIGURED') {
          throw new Error('NAVER_NOT_CONFIGURED');
        }
        throw new Error(String(response.status));
      })
      .then((data: ShoppingProduct[]) => {
        if (cancelled) return;
        setItems(Array.isArray(data) ? data : []);
      })
      .catch((err: Error) => {
        if (cancelled) return;
        setItems([]);
        if (err.message === 'NAVER_NOT_CONFIGURED') {
          setNotConfigured(true);
        } else {
          setError('상품을 불러오지 못했습니다.');
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [active, sort]);

  const activeLabel = categories.find((category) => category.code === active)?.label ?? '';

  return (
    <div className="shop-root">
      <header className="shop-hero">
        <div className="shop-hero-copy">
          <span className="shop-hero-eyebrow">NAVER SHOPPING</span>
          <h2>#특가픽</h2>
          <p>카테고리별 인기·최저가 상품을 모았어요.</p>
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
          <button type="button" className="news-retry" onClick={() => setSort((current) => current)}>
            <RefreshCcw size={15} aria-hidden /> 다시 시도
          </button>
        </div>
      )}

      {!loading && !notConfigured && !error && items.length === 0 && (
        <div className="shop-empty"><p>표시할 상품이 없습니다.</p></div>
      )}

      {!loading && !notConfigured && !error && items.length > 0 && (
        <div className="shop-grid">
          {items.map((item, index) => (
            <motion.div
              key={`${item.productId}-${index}`}
              className="shop-card"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.26, delay: Math.min(index * 0.015, 0.25) }}
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
      )}
    </div>
  );
}
