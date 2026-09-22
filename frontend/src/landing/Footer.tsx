export default function Footer() {
  return (
    <footer className="uc-footer">
      <span className="uc-brand">KAFKATALK</span>
      <ul aria-label="제품">
        {['대화', '뉴스', '쇼핑', '게임', '통화'].map((t) => <li key={t}>{t}</li>)}
      </ul>
      <ul aria-label="정책">
        <li><a href="#privacy" onClick={(e) => e.preventDefault()}>개인정보 처리방침</a></li>
        <li><a href="#terms" onClick={(e) => e.preventDefault()}>이용약관</a></li>
      </ul>
    </footer>
  );
}
