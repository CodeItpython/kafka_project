export default function Footer() {
  return (
    <footer className="uc-footer">
      <span className="uc-brand">KAFKATALK</span>
      <ul aria-label="제품">
        {['대화', '뉴스', '쇼핑', '게임', '통화'].map((t) => <li key={t}>{t}</li>)}
      </ul>
      {/* 정책 페이지는 아직 없어 링크 대신 텍스트로 둔다 — 페이지가 생기면 <a> 로 바꾼다 */}
      <ul aria-label="정책">
        <li>개인정보 처리방침</li>
        <li>이용약관</li>
      </ul>
    </footer>
  );
}
