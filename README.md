# Memo Lake

여러 AI 에이전트가 **토픽(Topic)별 메모(Memo)** 를 저장하고 읽으면서 정보를 공유하는 서비스입니다.
하나의 Spring Boot 서버가 세 가지 인터페이스를 제공하고, 세 인터페이스는 같은 서비스 계층과 같은 데이터베이스를 사용합니다.

| 인터페이스 | 기본 주소 | 대상 |
|---|---|---|
| REST API | `http://localhost:8080/api/topic` | 에이전트, 외부 프로그램 |
| MCP (Streamable HTTP) | `http://localhost:8080/mcp` | Claude Code, Codex 등 MCP 클라이언트 |
| Web UI | `http://localhost:8080/web/main` (`/`는 여기로 리다이렉트) | 사람 |

```text
REST Controller ─┐
                 │
MCP Tools ───────┼──> TopicService / MemoService ──> Spring Data JPA ──> H2 (dev) / PostgreSQL (prod)
                 │
Web Controller ──┘
```

MCP 도구는 REST API를 HTTP로 다시 호출하지 않고, REST 컨트롤러와 같은 서비스 메서드를 직접 호출합니다.

## 기술 스택

- Java 21, Spring Boot 4.1.2 (Spring MVC, Spring Data JPA / Hibernate 7, Bean Validation, Thymeleaf), Gradle 9.8
- MCP: Spring AI 2.0.2 MCP Server (`spring-ai-starter-mcp-server-webmvc`, 공식 MCP Java SDK 2.1.0 기반), Streamable HTTP 전송
- DB: H2 (개발), PostgreSQL (운영), Flyway 마이그레이션
- 프론트엔드: Thymeleaf 서버 사이드 렌더링, CSS, 약간의 Vanilla JavaScript (없어도 모든 화면이 동작함)

---

## 1. 로컬 개발 환경 실행 (H2)

필요한 것: Gradle을 실행할 JDK 17 이상. 빌드와 실행은 Java 21 툴체인을 사용하며, Java 21 JDK가 없으면 Gradle이 자동으로 내려받습니다.

```bash
./gradlew bootRun
```

- 기본 프로필은 `dev`이며, 내장 H2 데이터베이스를 `./data/memolake.mv.db` 파일에 저장합니다. 따로 설치할 DB는 없습니다.
- 서버는 기본적으로 `127.0.0.1:8080`에만 바인딩됩니다 (같은 PC에서만 접속 가능).
- 브라우저에서 http://localhost:8080/web/main 을 엽니다.

그 밖의 명령:

```bash
./gradlew test                       # 전체 테스트 (H2 인메모리)
./gradlew bootJar                    # build/libs/memolake.jar 생성
java -jar build/libs/memolake.jar    # jar로 실행 (dev 프로필)
SERVER_PORT=9090 ./gradlew bootRun   # 포트 변경
```

## 2. PostgreSQL 환경 실행

`prod` 프로필을 켜고 DB 접속 정보를 환경 변수로 넘깁니다. 스키마는 시작할 때 Flyway가 만듭니다.

```bash
./gradlew bootJar

SPRING_PROFILES_ACTIVE=prod \
DB_HOST=localhost DB_PORT=5432 DB_NAME=memolake \
DB_USERNAME=memolake DB_PASSWORD=secret \
java -jar build/libs/memolake.jar
```

| 환경 변수 | 기본값 | 설명 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | `prod`이면 PostgreSQL 사용 |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `memolake` | PostgreSQL 위치 |
| `DB_USERNAME` / `DB_PASSWORD` | `memolake` / (없음) | PostgreSQL 계정 |
| `SPRING_DATASOURCE_URL` | | JDBC URL 전체를 직접 지정 (위 세 값보다 우선) |
| `DB_POOL_SIZE` | `10` | 커넥션 풀 크기 |
| `SERVER_ADDRESS` | `127.0.0.1` | 바인딩 주소. 외부에서 받으려면 `0.0.0.0` |
| `SERVER_PORT` | `8080` | 포트 |
| `MEMOLAKE_MAX_CONTENT_SIZE` | `1MB` | 메모 최대 크기 (UTF-8 바이트) |
| `MEMOLAKE_ALLOWED_HOSTS` | `localhost,127.0.0.1,::1` | 허용할 Host 이름. `*`는 모두 허용 ([보안](#보안-및-운영)) |
| `MEMOLAKE_ACCESS_TOKEN` | (없음) | 설정하면 모든 요청에 `Authorization: Bearer <token>` 필요 |
| `LOG_FORMAT` | `ecs` | 구조화 로그 형식 (`ecs`, `logstash`, `gelf`) |

`prod` 프로필에서는 로그가 한 줄에 하나씩 JSON(Elastic Common Schema)으로 출력되고, `X-Forwarded-*` 헤더를 인식합니다.

## 3. Docker Compose 실행

애플리케이션과 PostgreSQL 17을 함께 띄웁니다.

```bash
docker compose up -d --build     # 빌드 후 실행
docker compose logs -f app       # 로그 보기
docker compose down              # 중지 (데이터는 postgres-data 볼륨에 남음)
docker compose down -v           # 중지 + 데이터 삭제
```

- 앱은 호스트의 `127.0.0.1:8080`으로만 공개되고, PostgreSQL 포트는 호스트에 공개하지 않습니다.
- 설정은 셸 환경 변수나 같은 폴더의 `.env` 파일로 바꿀 수 있습니다: `DB_PASSWORD`, `MEMOLAKE_PORT`, `MEMOLAKE_ALLOWED_HOSTS`, `MEMOLAKE_ACCESS_TOKEN`.

```dotenv
# .env 예시
DB_PASSWORD=change-me
MEMOLAKE_ACCESS_TOKEN=a-long-random-token
```

---

## 4. REST API

모든 경로는 `/api`로 시작합니다. 시간은 ISO 8601 UTC(`2026-10-27T12:30:00.123456Z`)로 반환합니다.

| 메서드 | 경로 | 설명 | 요청 본문 | 성공 |
|---|---|---|---|---|
| GET | `/api/topic?page=0&size=10` | 토픽 목록 (최근 메모 수정 순) | | 200 |
| POST | `/api/topic` | 토픽 생성 | JSON `{name, description}` | 201 |
| GET | `/api/topic/{topicName}?page=0&size=10` | 토픽 설명 + 메모 목록 (본문 제외) | | 200 |
| GET | `/api/topic/{topicName}/{memoName}` | 메모 전체 조회 | | 200 |
| POST | `/api/topic/{topicName}/{memoName}` | 메모 생성 | `text/plain` | 201 |
| PATCH | `/api/topic/{topicName}/{memoName}` | 메모 **전체 교체** | `text/plain` | 200 |
| PUT | `/api/topic/{topicName}/{memoName}` | 메모 끝에 **내용 추가 (append)** | `text/plain` | 200 |

- `page`는 0부터, `size`는 1~100 (기본 10). 범위를 벗어나면 400입니다.
- 토픽 목록은 `lastMemoModifiedAt DESC`(토픽에 속한 메모의 `updatedAt` 최댓값) 순서이고, 메모가 없는 토픽(`null`)은 맨 뒤, 같은 시간이면 이름순입니다.
- 토픽의 메모 목록은 `updatedAt DESC`, 같은 시간이면 이름순입니다.
- 메모 본문은 `Content-Type: text/plain; charset=UTF-8`로 보냅니다. `charset`이 없으면 UTF-8로 읽습니다. 서버는 본문을 해석하거나 바꾸지 않고 그대로 저장합니다 (텍스트, Markdown, JSON, XML, 코드 모두 문자열로 저장).
- 메모를 쓰는 요청(POST / PATCH / PUT)은 본문 없이 메타데이터만 돌려줍니다: `{name, createdAt, updatedAt, version}`. 자주 append해도 메모 전체를 다시 받지 않게 하기 위해서입니다.
- 메모 응답에는 `ETag: "<version>"` 헤더가 붙습니다.

### PUT은 append입니다 (일반 REST 의미와 다름)

이 API에서 `PUT`은 전체 교체가 아니라 **기존 본문 뒤에 요청 본문을 그대로 붙이는 연산**입니다.

- 서버는 구분자나 개행을 넣지 않습니다. `Hello` + `World` → `HelloWorld`. 새 줄로 붙이려면 본문 앞에 `\n`을 직접 넣으세요.
- **멱등(idempotent)하지 않습니다.** 타임아웃 뒤에 같은 요청을 재시도하면 같은 내용이 두 번 붙을 수 있습니다.
  이를 막으려면 마지막으로 본 버전을 `If-Match`로 함께 보내세요. 첫 요청이 이미 성공했다면 재시도는 `412`로 실패하고 내용이 중복되지 않습니다.
- 동시에 여러 에이전트가 append해도 내용은 하나도 빠지지 않습니다 ([동시성 제어](#8-동시성-제어-방식)).

### 이름 규칙과 URL 인코딩

- 토픽 이름과 메모 이름: **1~100자**, **한글, 영문, 숫자, 공백, 하이픈(`-`), 밑줄(`_`)** 만 사용 가능, 공백으로 시작하거나 끝날 수 없음.
- `/`, `\`, `.`, 제어문자, 그 밖의 기호는 모두 거부합니다 (400).
- 이름은 유니코드 NFC로 정규화합니다 (조합형/완성형 한글이 같은 이름으로 취급됨).
- 토픽 이름은 전체에서 유일하고, 메모 이름은 같은 토픽 안에서만 유일하면 됩니다.
- URL에서는 이름을 경로 세그먼트로 퍼센트 인코딩합니다. 예: `백엔드 프로젝트` → `%EB%B0%B1%EC%97%94%EB%93%9C%20%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8`
- 토픽 설명은 선택 항목이며 최대 1000자입니다.

### curl 예제

```bash
BASE=http://localhost:8080

# 토픽 생성 (201, 이미 있으면 409)
curl -i -X POST "$BASE/api/topic" \
  -H 'Content-Type: application/json' \
  -d '{"name": "backend-project", "description": "백엔드 개발 협업"}'

# 토픽 목록
curl "$BASE/api/topic?page=0&size=10"

# 메모 생성 (201). 개행을 보존하려면 -d 대신 --data-binary 를 쓰세요.
curl -i -X POST "$BASE/api/topic/backend-project/architecture" \
  -H 'Content-Type: text/plain; charset=UTF-8' \
  --data-binary $'# Architecture\nSpring Boot 기반 아키텍처\n'

# 파일 내용으로 메모 생성
curl -X POST "$BASE/api/topic/backend-project/api-design" \
  -H 'Content-Type: text/plain; charset=UTF-8' \
  --data-binary @api-design.md

# 토픽 상세 (메모 이름과 수정 시간만, 본문 없음)
curl "$BASE/api/topic/backend-project?page=0&size=10"

# 메모 조회 (ETag 헤더 = version)
curl -i "$BASE/api/topic/backend-project/architecture"

# 끝에 내용 추가 (PUT = append, 개행 자동 추가 없음)
curl -X PUT "$BASE/api/topic/backend-project/architecture" \
  -H 'Content-Type: text/plain; charset=UTF-8' \
  --data-binary $'\n- 2026-10-27: API 설계 완료'

# 재시도해도 두 번 붙지 않게 하는 append (현재 버전이 2일 때만 추가)
curl -X PUT "$BASE/api/topic/backend-project/architecture" \
  -H 'Content-Type: text/plain; charset=UTF-8' -H 'If-Match: "2"' \
  --data-binary $'\n- 리뷰 요청'

# 전체 교체 (PATCH). 조건 없이 보내면 마지막 쓰기가 이깁니다.
curl -X PATCH "$BASE/api/topic/backend-project/architecture" \
  -H 'Content-Type: text/plain; charset=UTF-8' \
  --data-binary 'World'

# 조건부 전체 교체: 내가 읽은 버전(3)에서 바뀌지 않았을 때만 교체, 아니면 412
curl -i -X PATCH "$BASE/api/topic/backend-project/architecture" \
  -H 'Content-Type: text/plain; charset=UTF-8' -H 'If-Match: "3"' \
  --data-binary 'World'

# 한글/공백 이름은 퍼센트 인코딩
curl "$BASE/api/topic/%EC%97%B0%EA%B5%AC%20%EB%85%B8%ED%8A%B8"
```

응답 예시:

```json
// GET /api/topic
{
  "content": [
    { "name": "backend-project", "description": "백엔드 개발 협업", "lastMemoModifiedAt": "2026-10-27T12:30:00Z" },
    { "name": "research", "description": "", "lastMemoModifiedAt": null }
  ],
  "page": 0, "size": 10, "totalElements": 2, "totalPages": 1
}

// GET /api/topic/backend-project
{
  "name": "backend-project",
  "description": "백엔드 개발 협업",
  "memos": {
    "content": [
      { "name": "architecture", "updatedAt": "2026-10-27T12:30:00Z" },
      { "name": "agent-a-progress", "updatedAt": "2026-10-27T11:20:00Z" }
    ],
    "page": 0, "size": 10, "totalElements": 2, "totalPages": 1
  }
}

// GET /api/topic/backend-project/architecture
{
  "name": "architecture",
  "content": "# Architecture\nSpring Boot 기반 아키텍처",
  "createdAt": "2026-10-27T10:00:00Z",
  "updatedAt": "2026-10-27T12:30:00Z",
  "version": 3
}

// PUT / PATCH / POST 메모 응답
{ "name": "architecture", "createdAt": "2026-10-27T10:00:00Z", "updatedAt": "2026-10-27T12:31:00Z", "version": 4 }
```

### 오류 응답

모든 오류는 같은 형식입니다. `code`는 REST와 MCP가 공통으로 사용합니다.

```json
{ "code": "MEMO_NOT_FOUND", "message": "Memo not found: 'x' in topic 'backend-project'", "timestamp": "2026-10-27T12:30:00Z" }
```

| HTTP | code | 상황 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 잘못된 이름, 페이지 값, JSON, If-Match, UTF-8이 아닌 본문, NUL(U+0000) 문자 포함 |
| 401 | `UNAUTHORIZED` | 접근 토큰이 설정됐는데 없거나 틀림 |
| 403 | `FORBIDDEN` | 허용되지 않은 Host, 다른 사이트(Origin)에서 온 브라우저 쓰기 요청 |
| 404 | `TOPIC_NOT_FOUND` / `MEMO_NOT_FOUND` / `NOT_FOUND` | 토픽 없음 / 메모 없음 / 없는 경로 |
| 405 | `METHOD_NOT_ALLOWED` | 지원하지 않는 메서드 |
| 409 | `TOPIC_ALREADY_EXISTS` / `MEMO_ALREADY_EXISTS` | 이름 중복 |
| 412 | `VERSION_CONFLICT` | `If-Match`(또는 MCP `expectedVersion`)와 현재 버전이 다름 |
| 413 | `CONTENT_TOO_LARGE` | 메모가 최대 크기(기본 1MB, UTF-8 바이트)를 넘음. append는 합친 결과 크기로 판단 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 메모 쓰기에 `text/plain`이 아님, 토픽 생성에 JSON이 아님 |
| 500 | `INTERNAL_ERROR` | 서버 오류 (내부 정보는 응답에 넣지 않음) |

---

## 5. MCP 클라이언트 연결

- 엔드포인트: `http://localhost:8080/mcp`
- 전송 방식: **Streamable HTTP** (JSON-RPC 2.0, 세션은 `Mcp-Session-Id` 헤더). stdio나 구형 SSE 전송은 제공하지 않습니다.
- 구현: Spring AI 2.0.2 MCP Server (WebMVC, 동기 서버) + 공식 MCP Java SDK 2.1.0. 같은 Spring Boot 프로세스 안에서 실행됩니다.
- 서버 이름: `memo-lake`. 초기화 응답의 `instructions`에 사용법 요약이 들어 있습니다.

| 도구 | REST 대응 | 입력 | 종류 |
|---|---|---|---|
| `list_topics` | `GET /api/topic` | `page?`, `size?` | 읽기 전용 |
| `create_topic` | `POST /api/topic` | `name`, `description?` | 쓰기 |
| `get_topic` | `GET /api/topic/{topic}` | `topicName`, `page?`, `size?` | 읽기 전용 |
| `get_memo` | `GET /api/topic/{topic}/{memo}` | `topicName`, `memoName` | 읽기 전용 |
| `create_memo` | `POST .../{memo}` | `topicName`, `memoName`, `content` | 쓰기 |
| `replace_memo` | `PATCH .../{memo}` | `topicName`, `memoName`, `content`, `expectedVersion?` | 쓰기 (덮어쓰기) |
| `append_memo` | `PUT .../{memo}` | `topicName`, `memoName`, `content`, `expectedVersion?` | 쓰기 (비멱등) |

- 읽기 도구에는 `readOnlyHint: true`, 쓰기 도구에는 `readOnlyHint: false`가 붙고, `replace_memo`는 `destructiveHint: true`, `append_memo`는 `idempotentHint: false`입니다.
- 성공 결과는 REST 응답과 같은 JSON을 텍스트 콘텐츠로 돌려줍니다.
- 실패하면 `isError: true`와 함께 REST와 같은 오류 객체를 텍스트(JSON)와 `structuredContent` 양쪽에 담아 돌려줍니다.

```json
{ "isError": true,
  "content": [{ "type": "text", "text": "{\"code\":\"VERSION_CONFLICT\",\"message\":\"Version conflict: expected version 2 but current version is 3. ...\",\"timestamp\":\"...\"}" }],
  "structuredContent": { "code": "VERSION_CONFLICT", "message": "...", "timestamp": "..." } }
```

Streamable HTTP를 지원하는 일반 MCP 클라이언트 설정 예:

```json
{
  "mcpServers": {
    "memo-lake": { "type": "http", "url": "http://localhost:8080/mcp" }
  }
}
```

접근 토큰(`MEMOLAKE_ACCESS_TOKEN`)을 켰다면 `Authorization: Bearer <token>` 헤더를 추가합니다.

## 6. Claude Code에 등록

```bash
# 현재 프로젝트에서만 사용 (기본 scope: local)
claude mcp add --transport http memo-lake http://localhost:8080/mcp

# 모든 프로젝트에서 사용
claude mcp add --transport http --scope user memo-lake http://localhost:8080/mcp

# 접근 토큰을 켠 서버
claude mcp add --transport http memo-lake http://localhost:8080/mcp \
  --header "Authorization: Bearer $MEMOLAKE_ACCESS_TOKEN"

claude mcp list      # 연결 상태 확인
```

팀이 함께 쓰도록 저장소에 넣으려면 프로젝트 루트의 `.mcp.json`에 적습니다 (`--scope project`로 추가해도 같은 파일이 만들어집니다).

```json
{
  "mcpServers": {
    "memo-lake": { "type": "http", "url": "http://localhost:8080/mcp" }
  }
}
```

Claude Code 세션 안에서 `/mcp`로 도구 목록을 확인할 수 있습니다.

## 7. Codex에 등록

```bash
codex mcp add memo-lake --url http://localhost:8080/mcp
```

또는 `~/.codex/config.toml`에 직접 적습니다.

```toml
[mcp_servers.memo-lake]
url = "http://localhost:8080/mcp"
# 접근 토큰을 켠 경우: 토큰이 들어 있는 환경 변수 이름
# bearer_token_env_var = "MEMOLAKE_ACCESS_TOKEN"
```

---

## 8. 동시성 제어 방식

여러 에이전트가 같은 메모를 동시에 수정하는 상황을 전제로 설계했습니다. 모든 규칙은 서비스 계층(`MemoService`)에 있어 REST, MCP, 웹이 똑같이 따릅니다.

**Append: 행 잠금으로 직렬화**

1. 트랜잭션을 시작하고 대상 메모 행을 잠그며 읽습니다 (JPA `PESSIMISTIC_WRITE`).
   PostgreSQL에서는 `SELECT ... FOR NO KEY UPDATE`, H2에서는 `SELECT ... FOR UPDATE`가 실행됩니다.
2. 기존 본문 뒤에 내용을 붙이고, `updatedAt`과 `version`을 갱신한 뒤 커밋합니다.
3. 같은 메모에 대한 다른 쓰기는 잠금이 풀릴 때까지 기다렸다가 **최신 본문**을 읽어 이어 붙입니다.

그래서 `Hello`에 A가 ` AAA`, B가 ` BBB`를 동시에 붙이면 결과는 `Hello AAA BBB` 또는 `Hello BBB AAA`이고 어느 쪽도 사라지지 않습니다.
잠금은 행 단위라 다른 메모에 대한 쓰기나 읽기(조회)는 막지 않습니다.

**Replace: 버전 기반 낙관적 동시성 제어**

- 모든 메모에는 `version`이 있고(생성 시 1), 내용이 바뀔 때마다 1씩 늘어납니다 (JPA `@Version`).
- 클라이언트는 읽을 때 받은 버전을 수정 요청에 함께 보냅니다: REST는 `If-Match: "3"`, MCP는 `expectedVersion: 3`, 웹 에디터는 폼의 숨은 필드.
  현재 버전과 다르면 아무것도 바꾸지 않고 `412 VERSION_CONFLICT`를 돌려줍니다. 클라이언트는 다시 읽고, 합친 뒤, 다시 시도하면 됩니다.
- 버전 비교와 쓰기는 append와 같은 행 잠금 안에서 실행되므로, 두 요청이 같은 버전으로 동시에 와도 정확히 하나만 성공합니다.
- **조건 없이 보낸 전체 교체는 "마지막 쓰기가 이기는(last write wins)" 정책입니다.** 그 사이에 다른 에이전트가 쓴 내용은 덮어써집니다. 공유 메모를 덮어쓸 때는 버전 조건을 쓰세요.
- append도 같은 방식으로 버전 조건을 걸 수 있습니다 (선택). 재시도 시 중복 추가를 막는 용도입니다.

**Replace와 Append가 동시에 오면** 두 연산 모두 같은 행 잠금을 쓰므로 한 번에 하나씩 실행됩니다.
`Hello`에 append ` AAA`와 replace `World`가 동시에 오면 결과는 `World`(append 다음 replace) 또는 `World AAA`(replace 다음 append)입니다.

**생성 경쟁**: 같은 이름을 동시에 만들면 DB 유니크 제약 때문에 하나만 성공하고 나머지는 409를 받습니다.

**시간**: `createdAt`/`updatedAt`은 UTC로 마이크로초까지 저장합니다. 수정 시각이 같더라도 `version`으로 변경 여부를 구분할 수 있습니다.

이 동작들은 자동 테스트로 확인합니다 (`MemoConcurrencyTest`, `McpServerTest`): 16개 스레드 × 25번 동시 append, 같은 버전으로 8개 동시 교체, replace/append 동시 실행, MCP와 REST를 섞은 동시 append. 전체 테스트는 H2와 PostgreSQL 17 양쪽에서 통과합니다.

## 9. 데이터베이스 스키마

`src/main/resources/db/migration/V1__create_topic_and_memo.sql` (H2 PostgreSQL 모드와 PostgreSQL에서 그대로 실행됨)

```sql
CREATE TABLE topic (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100)                NOT NULL,
    description VARCHAR(1000)               NOT NULL DEFAULT '',
    created_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_topic_name UNIQUE (name)
);

CREATE TABLE memo (
    id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    topic_id   BIGINT                      NOT NULL,
    name       VARCHAR(100)                NOT NULL,
    content    TEXT                        NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version    BIGINT                      NOT NULL,
    CONSTRAINT fk_memo_topic FOREIGN KEY (topic_id) REFERENCES topic (id),
    CONSTRAINT uk_memo_topic_name UNIQUE (topic_id, name)
);

CREATE INDEX idx_memo_topic_updated ON memo (topic_id, updated_at DESC, name);
```

```text
topic (1) ──< memo (N)        memo.topic_id → topic.id
```

| 조회 | 사용하는 인덱스 |
|---|---|
| 토픽 이름으로 조회 | `uk_topic_name` |
| 토픽 + 메모 이름으로 조회 | `uk_memo_topic_name (topic_id, name)` |
| 토픽의 메모 최신순 목록 | `idx_memo_topic_updated` (인덱스 순서 그대로 읽고 LIMIT) |
| 최근 수정된 토픽 목록 | 토픽마다 `idx_memo_topic_updated`에서 `MAX(updated_at)`를 인덱스 한 번으로 구함 |

- 토픽 목록은 DB에서 `ORDER BY (SELECT MAX(updated_at) ...) DESC NULLS LAST, name` 와 `LIMIT/OFFSET`으로 정렬과 페이징을 끝냅니다. 메모를 메모리로 읽어 정렬하지 않습니다.
- 목록 조회는 필요한 열만 고르는 DTO 프로젝션 쿼리 하나로 처리하므로 N+1 문제가 없고, 메모 본문(`content`)은 메모 단건 조회에서만 읽습니다.
- PostgreSQL 17에서 토픽 1,000개와 메모 100,000개로 확인했을 때, 두 목록 쿼리 모두 `idx_memo_topic_updated` 인덱스만 읽었고(Index Only Scan) 토픽 목록 첫 페이지는 약 2ms 걸렸습니다.
- 본문은 PostgreSQL `TEXT`에 원문 그대로 저장합니다. JSON 메모도 JSON 타입이 아니라 문자열입니다.

---

## 웹 화면

| 경로 | 화면 |
|---|---|
| `/web/main` | 서비스 소개, 최근 수정된 토픽 3개, 모든 토픽 보기 |
| `/web/topic?page=` | 토픽 목록 (10개씩, 최근 메모 수정 순), 새 토픽 만들기 |
| `/web/topic/{topic}?page=` | 토픽 설명, 메모 목록 (10개씩, 최근 수정 순), 메모 발행 |
| `/web/topic/{topic}/{memo}` | 메모 본문, 생성/수정 시간, 버전, 수정 / 토픽으로 돌아가기 |
| `/web/topic/{topic}/{memo}/edit` | 공통 에디터. 메모가 있으면 수정(전체 교체), 없으면 새로 작성 |

- 본문은 `<pre>`에 줄바꿈과 공백을 그대로 보여 주며, Thymeleaf가 HTML을 이스케이프하므로 메모 속 HTML/JavaScript는 실행되지 않습니다.
  추가로 Content-Security-Policy 헤더가 인라인 스크립트를 막습니다.
- 줄바꿈 켜기/끄기, JSON 메모의 정렬 보기, 복사 버튼을 제공합니다 (JavaScript가 없으면 원문만 보입니다). 시간은 브라우저의 지역 시간으로 표시됩니다.
- 에디터는 저장할 때 읽어 온 버전을 함께 보냅니다. 그 사이 에이전트가 메모를 바꿨다면 저장하지 않고 충돌을 알려 주며, 작성 중이던 내용은 그대로 남습니다.
- 브라우저는 textarea의 줄바꿈을 CRLF로 보내므로, 웹 에디터에서 저장한 메모의 줄바꿈은 LF(`\n`)로 바꿔 저장합니다. REST와 MCP로 저장한 본문은 바꾸지 않습니다.

## 보안 및 운영

사용자 계정과 로그인은 이번 버전의 범위가 아닙니다. 인증 없이 메모를 읽고 쓸 수 있으므로 기본값을 "로컬 전용"으로 두고, 운영 배포 시 접근을 제한할 수 있게 해 두었습니다.

- **로컬 바인딩**: 기본 `server.address=127.0.0.1`. Docker Compose도 `127.0.0.1:8080`에만 포트를 공개합니다.
- **Host 허용 목록** (`MEMOLAKE_ALLOWED_HOSTS`, 기본 `localhost,127.0.0.1,::1`): 다른 Host 헤더로 온 요청은 403. 로컬 서버를 노리는 DNS rebinding 공격을 막습니다.
  다른 이름이나 IP로 접속하게 하려면 그 이름을 추가하고, 앞단 프록시가 Host를 검사한다면 `*`로 둘 수 있습니다.
- **Origin 검사**: 브라우저가 보낸 쓰기 요청(GET 외의 메서드)과 모든 MCP 요청은 같은 출처(Origin)여야 합니다. 다른 웹사이트가 사용자의 브라우저를 통해 메모를 쓰는 것(CSRF)을 막습니다. Origin 헤더를 보내지 않는 에이전트/CLI에는 영향이 없습니다.
- **선택적 공유 토큰** (`MEMOLAKE_ACCESS_TOKEN`): 설정하면 모든 요청(REST, MCP, 웹)에 `Authorization: Bearer <token>`이 필요합니다. 브라우저는 HTTP Basic 창에서 사용자 이름은 아무거나, 비밀번호에 토큰을 넣으면 됩니다. 토큰은 HTTPS(프록시 등) 위에서 사용하세요.
- **입력 검증**: 이름 규칙, 설명 길이, 페이지 범위, 본문 크기, 엄격한 UTF-8 디코딩. 본문은 정해진 바이트 수까지만 읽으므로 큰 요청을 통째로 메모리에 올리지 않습니다.
- **SQL Injection**: 모든 쿼리는 JPA/JPQL 파라미터 바인딩을 사용하고 문자열로 SQL을 만들지 않습니다.
- **로그**: 요청마다 메서드, 경로, 상태, 소요 시간만 남깁니다. 요청/응답 본문(메모 내용)과 헤더(토큰)는 기록하지 않습니다. 쓰기 이벤트는 토픽, 메모 이름, 버전, 바이트 수만 기록합니다. `prod`는 JSON 구조화 로그입니다.
- **설정 주입**: DB 연결 정보와 위 설정은 모두 환경 변수로 넣을 수 있습니다.

## 그 밖의 정책

- 메모 최대 크기는 `memolake.memo.max-content-size`(기본 `1MB`, UTF-8 바이트 기준)로 바꿀 수 있습니다. 웹 에디터 폼은 URL 인코딩 때문에 본문보다 최대 3배 커지므로, 크기를 키우면 `server.tomcat.max-http-form-post-size`(기본 4MB)도 3배 이상으로 맞춰 주세요.
- PostgreSQL `TEXT`는 NUL 문자(U+0000)를 저장할 수 없어서, 어느 DB에서든 NUL이 들어 있는 본문은 400으로 거부합니다.
- 삭제 API는 요구 범위에 없어 제공하지 않습니다.

## 프로젝트 구조

```text
src/main/java/com/memolake/
├── MemoLakeApplication.java
├── config/                     AppConfig (Clock), MemoLakeProperties (memolake.*)
├── domain/
│   ├── topic/                  Topic 엔티티, TopicRepository, TopicSummary 프로젝션
│   └── memo/                   Memo 엔티티, MemoRepository (행 잠금 쿼리), MemoSummary 프로젝션
├── application/                TopicService, MemoService (모든 비즈니스 규칙), NameRules, Paging, dto/
├── interfaces/
│   ├── rest/                   TopicController, MemoController, ApiExceptionHandler, 본문 리더, ETag
│   ├── mcp/                    MemoLakeMcpTools (7개 도구), McpToolResults (결과/오류 변환)
│   └── web/                    WebController, 폼, WebExceptionHandler
└── common/
    ├── exception/              ErrorCode, MemoLakeException, ErrorResponse (REST/MCP/웹 공통)
    └── web/                    HttpSecurityFilter, RequestLoggingFilter, AppErrorController
src/main/resources/
├── application.yml, application-dev.yml, application-prod.yml
├── db/migration/               Flyway SQL
├── templates/                  Thymeleaf 화면
└── static/                     app.css, app.js
src/test/java/com/memolake/     REST, MCP, 웹, 동시성, 보안, 단위 테스트
```

## 테스트

```bash
./gradlew test
```

H2 인메모리 DB로 실행합니다. 같은 테스트를 PostgreSQL에서 돌리려면 DB를 가리키는 환경 변수를 넘기면 됩니다.

```bash
docker run -d --rm --name memolake-pg -e POSTGRES_DB=memolake -e POSTGRES_USER=memolake \
  -e POSTGRES_PASSWORD=memolake -p 127.0.0.1:55432:5432 postgres:17-alpine
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:55432/memolake \
SPRING_DATASOURCE_USERNAME=memolake SPRING_DATASOURCE_PASSWORD=memolake \
  ./gradlew cleanTest test
docker stop memolake-pg
```

| 테스트 | 내용 |
|---|---|
| `TopicApiTest` | 토픽 생성, 중복 409, 이름 검증, 목록 정렬(최근 메모 수정, null 마지막, 이름 2차 정렬), 페이징, 토픽 상세 |
| `MemoApiTest` | 메모 생성/조회/전체 교체/append, 중복, 404, 줄바꿈·특수문자·JSON·Markdown 원문 보존, If-Match/412, 413, 415, UTF-8 검증 |
| `HttpErrorResponseTest` | 실제 HTTP에서 404/405/415도 공통 오류 형식, 브라우저에는 HTML 오류 화면 |
| `MemoConcurrencyTest` | 동시 append 유실 없음, 같은 버전 동시 교체 중 하나만 성공, replace+append 동시 실행, 동시 생성 |
| `McpServerTest` | 공식 SDK 클라이언트로 `/mcp` 연결, 도구 목록·힌트, 7개 도구, REST와 데이터 공유, 구조화 오류, MCP+REST 동시 append |
| `WebPageTest` | 메인/목록/상세 렌더링, 페이징, XSS 이스케이프, 토픽·메모 생성, 수정, 충돌 처리, 교차 출처 차단 |
| `AccessTokenTest` | 공유 토큰(Bearer, Basic), Host 허용 목록 |
| `NameRulesTest`, `Utf8LengthTest` | 이름 규칙, NFC 정규화, UTF-8 크기 계산 |
