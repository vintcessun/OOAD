# Real teacher course integration

Archived verification for the previous pin: [REAL-CENTERS-VERIFICATION.md](REAL-CENTERS-VERIFICATION.md).

## Source / ownership

Pinned MIT upstream: `https://gitee.com/mingqcn/course-project-mgt.git`, commit
`e9a6dff454cc6af23297458320f9c58d469850c9`. Original MIT notice is
`LICENSE.upstream` and remains `LICENSE` in the prepared source/runtime image.
The reference checkout is read-only. The prepare command archives approved tracked
paths from the fixed commit, then applies reviewable patches and the overlay. It never
archives the root or copies `选课名单.xlsx`; it removes every exported `.xlsx` and `.env*`.


```bash
export JAVA_HOME='/d/Software/Eclipse Adoptium/jdk-21.0.7.6-hotspot'
export COURSE_SOURCE='/c/Users/vintces/Downloads/rbac1-context-extracted/course-project-mgt-reference'
# Omit COURSE_SOURCE to fetch the pinned Gitee commit reproducibly.
bash tools/install-clonefactory.sh # unchanged teacher processor; install once
bash tools/prepare-course.sh
bash course-integration/tests/run-domain-tests.sh
(cd course-integration/frontend && npm ci && npm run build)
bash course-integration/build-images.sh
```

The prepared tree is under .cache/course-source/prepared. The teacher build uses JDK 21,
Spring Boot 4.1.2, Lombok 1.18.48, and CloneFactory 0.0.2.
The RBAC modules use the root project dependency management. Maven settings route
dependency resolution through credential-free Maven Central.

## Identity, course login, and audit

- Both centers implement the eight teacher identity operations and two audit operations.
  Directory queries require the course audience, the admin identity role, and API permission TEC.
- The same-origin edge sends `/auth/*` to the center and `POST /login` to the course backend.
  The teacher service checks its local student or teacher roster before requesting a central token.
  Other course requests use a verified JWT, current central operation decision, and local course
  object authorization. /api is the browser business prefix.
- The integration profile loads API capabilities and effective course role codes from the private
  center projection. A course leader can copy without CC, while a nonleader must have CC.
- The six patches apply to the new teacher pin. The native after-commit audit callbacks feed the
  integration confirmation filter, which combines them with scoped snapshots and sends a
  single redacted write per request. The filter uses the same request ID for its bounded retry.
- The live two-center runner uses the production Nginx routes and checks login, logout, directory access, department paging, the
  teacher/student account binding, and the real course audit read and write path.

The real runner requires Docker and Node 22.19 or newer for per-process TLS trust.
Set REAL_OPENSSL to the OpenSSL executable path if it is outside PATH.

## Audit guarantees and the unavoidable window

`X-Request-ID` is accepted only as a UUID (otherwise generated), echoed in the response,
and used unchanged for the external audit request. A transport timeout gets at most
one finite, synchronous retry of the **same immutable body and requestId**. Center
requestId idempotency is the authoritative duplicate guard. HTTP 4xx/5xx are not
retried. Missing/non-successful acknowledgements produce HTTP503
`AUDIT_UNCONFIRMED`, not an audited-success response.

Course and center databases are **not one transaction**. The domain transaction can
commit before audit acknowledgement, or the center can commit before its response is
lost. A crash in that interval can leave committed business data and absent/unconfirmed
audit. There is intentionally no MQ, outbox, background retry, distributed transaction,
or claim that business replay is idempotent. Do not blindly replay the entire business
request after 503; reconcile by requestId and inspect the business state. The HTTP test
explicitly demonstrates the committed-business / audit-unconfirmed case instead of
pretending a test can eliminate this window. `X-Audit-State` distinguishes confirmed,
unconfirmed and non-audited operation families. Course/term administrative mutations
are not falsely labelled as one of the four unrelated teacher audit event families.

## HTTP verification and dedicated fixture

`tests/fixture.sql` contains **only synthetic m6-* users** and fixed IDs 610001/610002.
Do not apply it to a shared/old/production database. The boundary runner hardcodes its
own `m6-course-mysql` container and `course_integration_boundary` database, checks real
Teacher/Student subject mappings, runs the real executable/JPA/services, and simulates
**only** the central HTTP boundary with an ephemeral RS256 keypair. No CourseService,
controller, repository, or core domain mock is used.

```bash
export COURSE_LOG='C:/Users/vintces/Downloads/m6-course-boundary-server.log'
# Default jar is .cache/course-source/prepared/backend/target/...jar
node course-integration/tests/http-boundary.mjs
COURSE_STRATEGY=ddd node course-integration/tests/http-boundary.mjs
```

This covers: real term creation, teacher course submission, administrator review,
leader/no-CC and nonleader/CC copy branches, student-leader join approval, and oral
score mutation/audit readback. It additionally exercises wrong aud/issuer/signature,
expired JWT, denied operation with no authority call, forged JWT authorities,
untrusted service-key header, timeout-after-center-commit requestId deduplication, and
post-domain-commit audit failure. This is an **HTTP boundary test**, not proof that the
separately deployed real center has passed end-to-end acceptance.

`tests/live-six-api.mjs` performs the same six real API classes through the actual
same-origin gateway (default8090), using five real central user tokens from env:
`COURSE_ADMIN_TOKEN`, `COURSE_LEADER_TOKEN`, `COURSE_CC_TEACHER_TOKEN`,
`COURSE_NO_CC_TEACHER_TOKEN`, `COURSE_STUDENT_LEADER_TOKEN`. It refuses execution
without `COURSE_DEDICATED_DB_CONFIRM=course_integration`. Provision corresponding
synthetic subjects/central authorities in the dedicated integration deployment first.
The live runner never obtains or transmits the internal audit service key.

The preserved upstream suite requires MySQL. Its default `mysql` hostname is not a
local Windows server. The dedicated loopback runner uses its own MySQL8 container,
small pools and a test-only `sslMode=DISABLED` URL to avoid intermittent Docker-local
TLS handshake termination. Production TLS policy is not downgraded by this setting.

## Deploy / frontend

`build-images.sh` emits `ooad/course-backend:m6` and `ooad/course-frontend:m6` (override
`COURSE_IMAGE_PREFIX`). Backend Docker context includes only the built teacher jar
and MIT license via `.dockerignore`. No roster or build tool enters the runtime image.
The frontend sends student and teacher login to `POST /login` and administrator login to the center `POST /auth/login` with the course audience. It reads roles from `/auth/me` and calls course operations under `/api/`. Menus do not grant server authority. Only the
user bearer is stored in sessionStorage, never a service key. There is no fake dataset.

The full deployment edge sends `/login` and `/api/login` to the course backend. It sends
`/auth/*`, JWKS, and the other course `/api/*` requests to the center. The center checks
operation permissions before forwarding the course API request. The edge serves the
Vite bundle under `/course/assets/`.
`swarm-course.yaml` provides private-network service entries with no published course
backend port. Attach to gateway network via COURSE_NETWORK and set actual issuer/JWK.
Secrets `audit-service-key` and `course-db-password` mount via configtree. BaseURL,
issuer, JWK and key are configuration, not source credentials. The sample does not
create or repurpose an existing database, network, gateway, or center.
