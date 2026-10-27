import styles from "../pages/Auth.module.css"

// 왼쪽 파란 패널에 보여줄 채팅방 미리보기
const PREVIEW = [
  { who: "민수", initial: "민", text: "로그인 리다이렉트 PR 올렸어요. 리뷰 부탁해요!" },
  {
    who: "민수",
    initial: "민",
    diff: [
      { sign: "-", code: "return null" },
      { sign: "+", code: "const user = await findUser(id)" },
      { sign: "+", code: "return redirectTo(user.home)" },
    ],
  },
  { who: "AI 리뷰", initial: "AI", bot: true, text: "findUser가 null을 돌려줄 수 있어요. user.home 전에 확인이 필요해요." },
  { who: "지은", initial: "지", text: "반영 확인했어요. 머지할게요 👍" },
]

// 폼 뒤에 흐릿하게 깔리는 대화·코드 조각
const GHOSTS = [
  { kind: "add", text: "+ if (!user) throw new NotFoundException()", pos: "g1" },
  { kind: "bubble", text: "@지은 이거 한번 봐줄 수 있어?", pos: "g2" },
  { kind: "hunk", text: "@@ -42,7 +42,9 @@", pos: "g3" },
  { kind: "del", text: "- String token = request.getParameter(\"t\")", pos: "g4" },
  { kind: "bubble", text: "테스트 다 통과했어요", pos: "g5" },
  { kind: "add", text: "+ @Transactional(readOnly = true)", pos: "g6" },
  { kind: "bubble", text: "리뷰 요청 #128", pos: "g7" },
  { kind: "del", text: "- console.log(data)", pos: "g8" },
]

export default function AuthLayout({ children }) {
  return (
    <div className={styles.shell}>
      <aside className={styles.showcase}>
        <a href="/" className={styles.showcaseBrand}>devchat</a>

        <div className={styles.preview} aria-hidden="true">
          <div className={styles.channel}># backend-review</div>
          {PREVIEW.map((m, i) => (
            <div key={i} className={styles.msg} style={{ "--i": i }}>
              <span className={`${styles.avatar} ${m.bot ? styles.avatarBot : ""}`}>{m.initial}</span>
              <div className={styles.msgBody}>
                <span className={styles.msgWho}>{m.who}</span>
                {m.text && <p className={styles.msgText}>{m.text}</p>}
                {m.diff && (
                  <pre className={styles.diff}>
                    {m.diff.map((d, j) => (
                      <code key={j} className={d.sign === "+" ? styles.diffAdd : styles.diffDel}>
                        {d.sign} {d.code}
                      </code>
                    ))}
                  </pre>
                )}
              </div>
            </div>
          ))}
        </div>

        <p className={styles.tagline}>대화하던 그 자리에서 코드 리뷰까지.</p>
      </aside>

      <main className={styles.main}>
        <div className={styles.ghosts} aria-hidden="true">
          {GHOSTS.map((g, i) => (
            <span key={i} className={`${styles.ghost} ${styles[g.kind]} ${styles[g.pos]}`}>{g.text}</span>
          ))}
        </div>

        <a href="/" className={styles.brand}>
          <span className={styles.brandName}><span className={styles.brandMark}>dev</span>chat</span>
        </a>

        {children}
      </main>
    </div>
  )
}
