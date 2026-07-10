import { useCallback, useEffect, useRef, useState } from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { Briefcase, ExternalLink, Newspaper, RefreshCcw, Search, Share2, Sparkles, X } from 'lucide-react';
import { NewsItem, NewsThumb } from './NewsFeed';

const NEWS_ROOT = '/api/news';
const DISPLAY = 20;
const MAX_START = 181; // 뉴스(취업·소식) 무한스크롤 상한 — NewsFeed와 동일

type YouthPolicy = {
  id: string;
  title: string;
  summary: string | null;
  support: string | null;
  keywords: string[];
  category: string | null;
  subCategory: string | null;
  region: string | null;
  agency: string | null;
  minAge: number | null;
  maxAge: number | null;
  applyUrl: string | null;
  refUrl: string | null;
  period: string | null;
};

// 경기 공공일자리(잡아바) 채용공고. 취업 탭에서 정책 카드 스타일을 재사용해 표시.
type YouthJob = {
  id: string;
  title: string;
  org: string | null;
  startDate: string | null;
  endDate: string | null;
  period: string | null;
  url: string | null;
};

type PolicyResponse = { available?: boolean; hasMore?: boolean; items?: YouthPolicy[] };

// 지역 토글 / 세부탭은 클라이언트 상수(쇼핑 탭의 SORTS 방식).
const REGIONS: { code: string; label: string }[] = [
  { code: 'all', label: '전체' },
  { code: 'seoul', label: '서울' },
  { code: 'gyeonggi', label: '경기' }
];
const SEGMENTS: { code: string; label: string; icon: typeof Sparkles }[] = [
  { code: 'policies', label: '정책·혜택', icon: Sparkles },
  { code: 'employment', label: '취업', icon: Briefcase },
  { code: 'news', label: '소식', icon: Newspaper }
];

function ageText(min: number | null, max: number | null): string | null {
  if (min != null && max != null) return `만 ${min}~${max}세`;
  if (min != null) return `만 ${min}세 이상`;
  if (max != null) return `만 ${max}세 이하`;
  return null;
}

/**
 * 뉴스 탭의 '청년' 하위 화면. 상단 지역 토글(서울/경기/전체) + 세부탭(정책·혜택/취업/소식).
 * 정책·혜택은 온통청년 API(/api/news/youth/policies), 취업·소식은 기존 뉴스 검색(/api/news/search)을 재사용한다.
 * NewsFeed 내부(news-feed-root)에 렌더되며 더 불러오기는 버튼 방식(부모 스크롤과 충돌 방지).
 */
export default function YouthPanel({ onShare }: { onShare?: (item: NewsItem) => void }) {
  const reduceMotion = useReducedMotion();
  const [region, setRegion] = useState('all');
  const [segment, setSegment] = useState('policies');
  const [policyQuery, setPolicyQuery] = useState(''); // 제출된 정책 검색어
  const [queryInput, setQueryInput] = useState('');

  const [policies, setPolicies] = useState<YouthPolicy[]>([]);
  const [available, setAvailable] = useState(true);
  const [news, setNews] = useState<NewsItem[]>([]);
  const [jobs, setJobs] = useState<YouthJob[]>([]);
  const [empShowingJobs, setEmpShowingJobs] = useState(false); // 취업 탭이 채용공고를 보여주는 중(아니면 뉴스 폴백)

  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);

  const reqRef = useRef(0);
  const pageRef = useRef(1); // 정책 페이지
  const startRef = useRef(1); // 뉴스 start 오프셋
  const jobsPageRef = useRef(1); // 채용공고 페이지
  const empShowingJobsRef = useRef(false); // 로직용(렌더용 state와 동기화)
  const loadingRef = useRef(false);
  const loadingMoreRef = useRef(false);

  const newsQuery = useCallback((seg: string, reg: string) => {
    const base = seg === 'employment' ? '청년 취업' : '청년';
    const prefix = reg === 'seoul' ? '서울 ' : reg === 'gyeonggi' ? '경기 ' : '';
    return `${prefix}${base}`;
  }, []);

  const load = useCallback((seg: string, reg: string, mode: 'replace' | 'append', kw: string) => {
    const myReq = mode === 'replace' ? ++reqRef.current : reqRef.current;
    if (mode === 'append') {
      loadingMoreRef.current = true;
      setLoadingMore(true);
    } else {
      loadingRef.current = true;
      setLoading(true);
      setError(null);
    }
    const stale = () => myReq !== reqRef.current; // 세그먼트/지역 변경으로 무효화된 응답 폐기
    const getJson = (url: string) =>
      fetch(url).then((r) => (r.ok ? r.json() : Promise.reject(new Error(String(r.status)))));
    const showJobs = (value: boolean) => {
      empShowingJobsRef.current = value;
      setEmpShowingJobs(value);
    };

    const applyPolicies = (data: PolicyResponse & { items?: YouthPolicy[] }) => {
      if (stale()) return;
      const items = Array.isArray(data.items) ? data.items : [];
      setAvailable(data.available !== false);
      if (mode === 'append') {
        setPolicies((prev) => {
          const seen = new Set(prev.map((p) => p.id));
          return [...prev, ...items.filter((p) => !seen.has(p.id))];
        });
      } else {
        setPolicies(items);
      }
      pageRef.current = (mode === 'replace' ? 1 : pageRef.current) + 1;
      setHasMore(Boolean(data.hasMore));
    };

    const applyJobs = (data: { hasMore?: boolean; items?: YouthJob[] }) => {
      if (stale()) return;
      showJobs(true);
      const items = Array.isArray(data.items) ? data.items : [];
      if (mode === 'append') {
        setJobs((prev) => {
          const seen = new Set(prev.map((j) => j.id));
          return [...prev, ...items.filter((j) => !seen.has(j.id))];
        });
      } else {
        setJobs(items);
      }
      jobsPageRef.current = (mode === 'replace' ? 1 : jobsPageRef.current) + 1;
      setHasMore(Boolean(data.hasMore));
    };

    const applyNews = (data: { items?: NewsItem[] }) => {
      if (stale()) return;
      showJobs(false);
      const items = Array.isArray(data.items) ? data.items : [];
      if (mode === 'append') {
        setNews((prev) => {
          const seen = new Set(prev.map((n) => n.id));
          return [...prev, ...items.filter((n) => !seen.has(n.id))];
        });
      } else {
        setNews(items);
      }
      startRef.current = (mode === 'replace' ? 1 : startRef.current) + DISPLAY;
      setHasMore(items.length >= DISPLAY && startRef.current <= MAX_START);
    };

    const policyUrl = () => {
      const page = mode === 'replace' ? 1 : pageRef.current;
      return `${NEWS_ROOT}/youth/policies?region=${encodeURIComponent(reg)}&page=${page}&size=${DISPLAY}`
        + (kw ? `&keyword=${encodeURIComponent(kw)}` : '');
    };
    const jobsUrl = () => {
      const page = mode === 'replace' ? 1 : jobsPageRef.current;
      return `${NEWS_ROOT}/youth/jobs?page=${page}&size=${DISPLAY}`;
    };
    const newsUrl = () => {
      const start = mode === 'replace' ? 1 : startRef.current;
      return `${NEWS_ROOT}/search?query=${encodeURIComponent(newsQuery(seg, reg))}&start=${start}&display=${DISPLAY}`;
    };

    const run = async () => {
      if (seg === 'policies') {
        applyPolicies(await getJson(policyUrl()));
        return;
      }
      if (seg === 'employment') {
        if (mode === 'append') {
          if (empShowingJobsRef.current) applyJobs(await getJson(jobsUrl()));
          else applyNews(await getJson(newsUrl()));
          return;
        }
        // 첫 로드: 경기/전체는 잡아바 채용공고 우선, 미구성(available:false)·서울·오류면 뉴스로 폴백.
        if (reg !== 'seoul') {
          try {
            const data = await getJson(jobsUrl());
            if (stale()) return;
            if (data.available !== false) {
              applyJobs(data);
              return;
            }
          } catch {
            // 채용공고 API 미배포/오류 → 뉴스 폴백
          }
        }
        applyNews(await getJson(newsUrl()));
        return;
      }
      // 소식
      applyNews(await getJson(newsUrl()));
    };

    return run()
      .catch(() => {
        if (stale() || mode === 'append') return; // 일시적 오류로 더보기를 영구 중단시키지 않음
        setError('불러오지 못했습니다.');
        if (seg === 'policies') setPolicies([]);
        else if (empShowingJobsRef.current) setJobs([]);
        else setNews([]);
      })
      .finally(() => {
        if (stale()) return;
        loadingRef.current = false;
        loadingMoreRef.current = false;
        setLoading(false);
        setLoadingMore(false);
      });
  }, [newsQuery]);

  // 세부탭/지역 변경 → 처음부터 다시
  useEffect(() => {
    pageRef.current = 1;
    startRef.current = 1;
    jobsPageRef.current = 1;
    setHasMore(false);
    if (segment === 'policies') setPolicies([]);
    else { setNews([]); setJobs([]); }
    load(segment, region, 'replace', policyQuery);
  }, [segment, region, policyQuery, load]);

  function loadMore() {
    if (!hasMore || loadingRef.current || loadingMoreRef.current) return;
    load(segment, region, 'append', policyQuery);
  }

  const isPolicies = segment === 'policies';
  const showingJobs = segment === 'employment' && empShowingJobs;
  const empty = isPolicies ? policies.length === 0 : showingJobs ? jobs.length === 0 : news.length === 0;

  return (
    <div className="youth-panel">
      <div className="youth-regionbar" role="group" aria-label="지역 선택">
        {REGIONS.map((r) => (
          <button
            key={r.code}
            type="button"
            className={region === r.code ? 'youth-region-chip active' : 'youth-region-chip'}
            aria-pressed={region === r.code}
            onClick={() => setRegion(r.code)}
          >
            {r.label}
          </button>
        ))}
      </div>

      <div className="news-catbar youth-segbar" role="tablist" aria-label="청년 세부 탭">
        {SEGMENTS.map((seg) => {
          const isActive = segment === seg.code;
          const Icon = seg.icon;
          return (
            <button
              key={seg.code}
              type="button"
              role="tab"
              aria-selected={isActive}
              className={isActive ? 'active' : ''}
              onClick={() => setSegment(seg.code)}
            >
              {isActive && (
                <motion.span
                  className="news-cat-pill"
                  layoutId="youth-seg-pill"
                  transition={reduceMotion ? { duration: 0 } : { type: 'spring', stiffness: 520, damping: 36, mass: 0.7 }}
                  aria-hidden
                />
              )}
              <span className="news-cat-label"><Icon size={14} aria-hidden /> {seg.label}</span>
            </button>
          );
        })}
      </div>

      {isPolicies && (
        <form className="youth-search" role="search" onSubmit={(event) => { event.preventDefault(); setPolicyQuery(queryInput.trim()); }}>
          <Search size={15} aria-hidden />
          <input
            type="search"
            value={queryInput}
            onChange={(event) => setQueryInput(event.target.value)}
            placeholder="정책 검색 (예: 적금, 창업, 주거)"
            aria-label="청년 정책 검색"
            enterKeyHint="search"
          />
          {queryInput && (
            <button type="button" className="youth-search-clear" onClick={() => { setQueryInput(''); setPolicyQuery(''); }} aria-label="검색어 지우기">
              <X size={14} aria-hidden />
            </button>
          )}
          <button type="submit" className="youth-search-submit">검색</button>
        </form>
      )}

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
          <button type="button" className="news-retry" onClick={() => load(segment, region, 'replace', policyQuery)}>
            <RefreshCcw size={15} aria-hidden /> 다시 시도
          </button>
        </div>
      )}

      {!loading && !error && isPolicies && !available && (
        <div className="news-empty">
          <p>청년 정책 데이터를 준비 중입니다.</p>
          <span className="youth-empty-hint">온통청년 API 키(YOUTH_API_KEY) 설정 후 표시됩니다.</span>
        </div>
      )}

      {!loading && !error && available && empty && (
        <div className="news-empty">
          <p>표시할 {isPolicies ? '정책이' : showingJobs ? '채용공고가' : '소식이'} 없습니다.</p>
        </div>
      )}

      {!loading && !error && isPolicies && policies.length > 0 && (
        <>
          <div className="youth-policy-list">
            {policies.map((policy, index) => {
              const age = ageText(policy.minAge, policy.maxAge);
              return (
                <motion.article
                  key={`${policy.id}-${index}`}
                  className="youth-policy-card"
                  initial={{ y: 10, opacity: 0 }}
                  animate={{ y: 0, opacity: 1 }}
                  transition={{ duration: 0.28, delay: Math.min((index % DISPLAY) * 0.02, 0.3) }}
                >
                  <div className="youth-policy-head">
                    {policy.category && <span className="youth-policy-badge">{policy.category}</span>}
                    {policy.region && <span className="youth-policy-region">{policy.region}</span>}
                  </div>
                  <strong className="youth-policy-title">{policy.title}</strong>
                  {policy.summary && <p className="youth-policy-summary">{policy.summary}</p>}
                  {policy.support && <p className="youth-policy-support">{policy.support}</p>}
                  <div className="youth-policy-meta">
                    {age && <span>👤 {age}</span>}
                    {policy.agency && <span>🏛 {policy.agency}</span>}
                    {policy.period && <span>🗓 {policy.period}</span>}
                  </div>
                  {policy.keywords.length > 0 && (
                    <div className="youth-policy-tags">
                      {policy.keywords.slice(0, 6).map((keyword) => (
                        <span key={keyword} className="youth-policy-tag">#{keyword}</span>
                      ))}
                    </div>
                  )}
                  {(policy.applyUrl || policy.refUrl) && (
                    <a
                      className="youth-policy-apply"
                      href={policy.applyUrl || policy.refUrl || '#'}
                      target="_blank"
                      rel="noreferrer noopener"
                    >
                      자세히 보기 <ExternalLink size={13} aria-hidden />
                    </a>
                  )}
                </motion.article>
              );
            })}
          </div>
          {renderMore()}
        </>
      )}

      {!loading && !error && showingJobs && jobs.length > 0 && (
        <>
          <div className="youth-policy-list">
            {jobs.map((job, index) => (
              <motion.article
                key={`${job.id}-${index}`}
                className="youth-policy-card"
                initial={{ y: 10, opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                transition={{ duration: 0.28, delay: Math.min((index % DISPLAY) * 0.02, 0.3) }}
              >
                <div className="youth-policy-head">
                  <span className="youth-policy-badge">채용</span>
                  <span className="youth-policy-region">경기</span>
                </div>
                <strong className="youth-policy-title">{job.title}</strong>
                <div className="youth-policy-meta">
                  {job.org && <span>🏛 {job.org}</span>}
                  {job.period && <span>🗓 {job.period}</span>}
                </div>
                {job.url && (
                  <a className="youth-policy-apply" href={job.url} target="_blank" rel="noreferrer noopener">
                    자세히 보기 <ExternalLink size={13} aria-hidden />
                  </a>
                )}
              </motion.article>
            ))}
          </div>
          {renderMore()}
        </>
      )}

      {!loading && !error && !isPolicies && !showingJobs && news.length > 0 && (
        <>
          <div className="news-list">
            {news.map((item, index) => (
              <motion.div
                key={`${item.id}-${index}`}
                className="news-card-wrap"
                initial={{ y: 10 }}
                animate={{ y: 0 }}
                transition={{ duration: 0.28, delay: Math.min((index % DISPLAY) * 0.02, 0.3) }}
              >
                <a className="news-card" href={item.url} target="_blank" rel="noreferrer noopener">
                  <NewsThumb url={item.url} fallback={item.thumbnail} />
                  <div className="news-card-body">
                    <strong className="news-card-title">{item.title}</strong>
                    {item.description && <p className="news-card-desc">{item.description}</p>}
                    <span className="news-card-meta">네이버뉴스 <ExternalLink size={13} aria-hidden /></span>
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
          {renderMore()}
        </>
      )}
    </div>
  );

  function renderMore() {
    if (loadingMore) {
      return (
        <div className="shop-more">
          <span className="shop-more-loading"><RefreshCcw size={14} aria-hidden /> 더 불러오는 중…</span>
        </div>
      );
    }
    if (hasMore) {
      return (
        <div className="shop-more">
          <button type="button" className="youth-more-btn" onClick={loadMore}>더 보기</button>
        </div>
      );
    }
    return (
      <div className="shop-more">
        <span className="shop-more-end">모두 불러왔어요</span>
      </div>
    );
  }
}
