# CHAPTER IV — SYSTEM IMPLEMENTATION

This section documents the technologies and implementation details for SmartJobSearch, maps features to source files, and provides deployment and run instructions. The text below follows the structure from your example but is adapted to this project's architecture (Spring Boot + JPA + static frontend + ML prototypes).

## 4.1 TECHNOLOGIES USED

### 4.1.1 Java-Based Web Stack (documentation-only)
This subsection describes the backend stack conceptually without referring to specific source paths. It is written for inclusion in a formal report.

• Spring Boot framework: the application uses Spring Boot and Spring MVC to provide an embedded web server, dependency injection, and a standard request handling lifecycle. The web layer is implemented with controller components that expose REST endpoints for core flows such as job CRUD, search, user management, file upload/download, and AI-assisted features.

• Controller responsibilities: controllers perform request validation, map request payloads to domain DTOs, and delegate business rules to the service layer. They handle multipart form submissions (for resume uploads), return JSON responses for API clients, and enforce authentication/authorization checks.

• Persistence: the data layer uses a JPA-based approach with a JDBC DataSource to manage connections and transactions. Domain entities persist user profiles, job postings, applications and related metadata. Connection details (JDBC URL, credentials) are supplied through runtime configuration or environment variables.

• File uploads & storage (prototype): resume files and attachments are persisted to a local file store in the prototype. File metadata (original filename, stored path, upload timestamp) is kept in the data store and associated with the corresponding application record. For production, this storage should migrate to an object store (S3/Blob) with signed URLs for downloads.

• Build & packaging: use a standard Java build tool to compile, run tests, and package the application into an executable artifact. Use a build wrapper to make builds reproducible across developer machines and CI.

Security/config note
• Runtime configuration and secrets (database credentials, API keys) must be injected via environment variables or a secrets manager in production. Avoid embedding secrets in configuration files that accompany the code in public repositories.


### 4.1.2 Front-end (HTML, CSS, JavaScript and templates)
The SmartJobSearch frontend is intentionally lightweight and designed for clarity and speed of iteration. The documentation below focuses on technologies and best practices rather than file locations.

• HTML: Pages are structured with semantic HTML elements (header, nav, main, footer). Typical pages include landing/job list, job posting form, job details, user profile, and dashboards. Markup focuses on accessibility and simple, predictable layouts.

• CSS: Use a single source of styling that defines colors, spacing, typography and responsive breakpoints. The project favors a minimalist responsive design; teams may optionally adopt a CSS framework (e.g., Bootstrap or Tailwind) to accelerate component layout and responsiveness.

• JavaScript: Vanilla JavaScript or small modules provide interactivity. Responsibilities include:
  - performing fetch/AJAX requests to the REST API (search, apply, profile updates),
  - client-side validation (resume file size/type checks, required fields),
  - rendering and updating lists (infinite scroll or pagination), and
  - handling session tokens and UX state (login state, loading indicators, error messages).

• Server-side templates: Use lightweight templating (server-rendered snippets) for initial page rendering when SEO or first‑paint speed matters; otherwise prefer JSON APIs and client-side rendering for dynamic flows.

• Progressive enhancement: Design pages so that essential functionality works without JavaScript (basic forms submit), then enhance with client-side scripts for improved UX (live search suggestions, async submissions).

• Pages & components (conceptual):
  - Landing / Job List — search box, filters, job cards, sorting and pagination controls.
  - Job Posting — multipart form to create or edit a job posting, with optional file attachments.
  - Job Details — full description, apply CTA and employer info.
  - Profile — edit skills, availability, and personal details; preview profile completeness.
  - Dashboard — listings for employers to review applicants, with sortable columns and score indicators.

• Accessibility & responsiveness: Follow semantic HTML, use ARIA attributes where necessary, and ensure keyboard navigation and screen-reader compatibility. Test layouts at common breakpoints (mobile/tablet/desktop).

• Build & asset strategy: Keep the frontend small and easy to serve. If introducing a modern frontend stack, build artifacts should be optimized and copied to the deployment package during the CI/CD pipeline.

Security & UX notes
• Enforce client-side input constraints for good UX (file size/type, required fields) but treat server-side validation as authoritative.
• Use HTTPS in production and set security flags on cookies (HttpOnly, Secure, SameSite). Protect endpoints against CSRF and implement rate limits on sensitive endpoints.


### 4.1.3 Back-end (Spring Boot request handling, sessions & runtime behavior)

This subsection explains the backend's runtime behavior using Spring Boot / Spring MVC request handling as a conceptual frame. The text focuses on documentation of responsibilities and runtime considerations rather than implementation details.

• Request lifecycle and controllers: each incoming HTTP request is handled by Spring MVC's dispatching mechanism which maps requests to controller methods. Controllers parse request parameters (JSON body or multipart forms), perform validation, and invoke business logic. Responses follow standard HTTP semantics (status codes, JSON error objects, redirects for non-API flows).

• Multipart handling: the Spring Boot application accepts multipart/form-data for file uploads. Upload handling includes strict server-side validation for allowed MIME types and maximum sizes. Uploaded content is streamed to temporary storage to avoid large memory spikes and then moved to durable storage after validation.

• Session management & authentication: sessions are used to maintain authenticated user state when applicable. Session cookies must be configured with security flags (HttpOnly, Secure, SameSite) and have sensible lifetimes. The system supports token-based authentication for API clients and session cookies for browser flows; both patterns coexist and are validated server-side. Spring Security can be used to centralize these concerns.

• Role-based access control: handlers check user roles/permissions before allowing sensitive actions (posting jobs, downloading applicant materials, approving applications). Authorization failures return clear HTTP error codes and messages.

• Transactions & data integrity: operations that touch multiple resources (persisting an application record and storing a resume file) are executed under transactional boundaries where possible. If a multi-step operation partially fails, compensating logic ensures data consistency (e.g., remove a stored file if DB save fails).

• Filters / interceptors: cross-cutting concerns (authentication, request logging, input sanitization, CORS, rate limiting) are implemented as filters/interceptors that run before and/or after handler execution. Filters ensure consistent security and observability across endpoints.

• Error handling & graceful degradation: the system centralizes exception handling to convert internal errors into structured HTTP error responses. Optional integrations (third-party ML APIs, webhook notifications) are invoked in a best-effort way: failures in those integrations are logged and do not block the primary user request.

• Concurrency & scaling notes: request handlers are designed to be stateless when possible to enable horizontal scaling. Long-running work (model training, bulk embedding computation) is offloaded to background jobs or separate services so the request path stays responsive.

• Observability: instrument handlers with structured logs, request IDs, and metrics (latency, error rates). This enables quick diagnosis of production issues and capacity planning.


### 4.1.4 Database — MySQL (documentation-only)
The SmartJobSearch project uses MySQL for development and can be deployed to managed MySQL services (Amazon RDS/Aurora, Azure Database for MySQL, Cloud SQL) for production.

Below is a concise, developer-friendly listing of the database contents we use in this project (table names and representative columns). This is intended to match the app's current responsibilities (user accounts, job postings, applications, resume handling, ML metadata, auditing).

Primary tables and representative columns (what we are using):
- `users`
  - id (PK), username, email, password_hash, roles, profile_complete, skills, availability, created_at, updated_at
- `jobs`
  - id (PK), employer_id (FK -> users), title, description, location, hours_per_week, duration, tags, visibility, posted_at, closes_at
- `applied_jobs`
  - id (PK), job_id (FK -> jobs), applicant_id (FK -> users), resume_id (FK -> resumes), cover_letter, status, score, applied_at
- `resumes` (file metadata)
  - id (PK), applicant_id (FK -> users), original_filename, storage_key_or_url, mime_type, size_bytes, uploaded_at, access_scope
- `embeddings` (optional metadata)
  - id (PK), source_type (job/profile), source_id, model_version, storage_pointer, created_at
- `audit_logs`
  - id (PK), actor_id (FK -> users), action, resource_type, resource_id, details (json/blob), created_at

Optional/auxiliary tables you may see or add:
- `sessions` — session_id, user_id, created_at, expires_at
- `notifications` — id, user_id, type, payload, status, created_at

Operational & design notes (short):
- Engine & charset: use InnoDB for transactions and `utf8mb4` for full Unicode support.
- Search & scale: use InnoDB indexes for structured queries; for semantic/full-text needs, use an external search/vector store and join results with MySQL metadata.
- Transactions & integrity: wrap multi-step work (DB + file storage + job enqueue) in transactional boundaries when possible and implement compensating steps on partial failures.
- Files & vectors: store large binaries (resumes) in object storage (S3/Blob) and vectors in a vector DB; keep only references in MySQL.
- Connection pooling & backups: configure HikariCP in Spring Boot, schedule regular backups, test restores, and add read replicas for read-heavy workloads.

Security & migration notes (brief):
- Restrict DB access to private networks and enforce TLS. Use least-privilege DB users and audit access to sensitive fields.
- Use Flyway/Liquibase for schema migrations and avoid `hibernate.ddl-auto=update` in production.

Summary
This listing focuses on the concrete tables and representative columns the application currently uses (accounts, job postings, applications, resume metadata, ML pointers, and audits). If you want, I can convert this into a database ER diagram (PlantUML) and/or produce a Flyway baseline migration SQL file matching these tables.

### 4.1.5 Machine learning & prototypes
- The `ml/` folder contains Python scripts for offline experiments: `build_artifacts.py` (compute embeddings from job text), `retrain_reranker.py` (train a reranker model using LightGBM or logistic regression), and `validate_dataset.py`.
- Integration points: controllers and services optionally call an external Recommendation service via the `ML_RECOMMENDER_URL` environment variable; if absent the application falls back to local rule-based scoring and mock embeddings (`CohereApiService.generateMockEmbedding`).

### 4.1.6 Development environment & tooling
- Java (11+ recommended) and Maven; project includes `mvnw` and `mvnw.cmd`.
- Recommended IDEs: IntelliJ IDEA or Eclipse for Java editing and debugging.
- Python environment (for ML): `ml/requirements.txt` lists packages (pandas, sentence-transformers, lightgbm, faiss-cpu, joblib). Use a virtualenv or conda.
- Optional: Docker for containerization and reproducing production-like environments.

### 4.2 APPROACH AND IMPLEMENTATION (documentation-only)

The implementation follows a modular monolith pattern for the prototype: UI, HTTP API, business services and data persistence run in a single deployable process. The approach emphasizes clear separation of concerns so individual components (scoring, recommendation) can be extracted later if needed.

1) HTTP layer (controllers)
- The HTTP layer exposes REST endpoints that perform request validation, map inputs to domain models, and delegate to business services. Endpoints accept JSON or multipart requests (for file uploads) and return JSON responses, following consistent status codes and error formats.

2) Business services
- Services contain application rules and orchestration logic: creating/updating jobs, applying to jobs, computing profile completeness, and integrating with optional ML services. Scoring is implemented so it can run synchronously (rule-based) or be queued for asynchronous model-based scoring.

3) Persistence
- The data layer persists users, jobs, applications and file metadata using a relational datastore. Repositories or DAOs encapsulate queries, pagination and transactional boundaries.

4) File handling
- File handling enforces strict validation (allowed MIME types, max size) and stores metadata with application records. For the prototype, files are stored locally; production should use a managed object store.

5) ML integration pattern
- The application uses a hybrid pattern: offline ML artifacts (embeddings, reranker models) are produced by a separate pipeline and optionally hosted behind a protected scoring API. The running application can call this service when configured; otherwise it falls back to local rule-based behavior and deterministic mock embeddings to stay functional.

6) Error handling and observability
- Standardized JSON error responses, structured logs, and metrics are recommended. Sensitive operations should be instrumented and failures in optional integrations (ML webhooks, external APIs) should be logged but not allowed to block primary user flows.

## 4.2.1 Back-end Logic

This section describes the SmartJobSearch backend implementation in a narrative style suitable for documentation. The backend is implemented with Spring Boot and Spring MVC, organized around controllers (HTTP endpoints), services (business logic), and repositories (data access). The goal is to explain responsibilities, runtime behavior, and representative class-level components in a way that developers can use to understand, maintain, or extend the system.

4.2.1.1 Session management & role-based access

• Authentication and sessions: the application uses Spring Security to authenticate users. For browser-based flows the system typically uses secure session cookies; API clients may use token-based (JWT) authentication where appropriate. Session cookies are configured with HttpOnly, Secure and SameSite flags and sensible expiration times.

• Role-based authorization: roles (for example ROLE_USER and ROLE_EMPLOYER) gate access to sensitive actions such as posting jobs, viewing applicant details, or downloading resumes. Authorization rules are centralized in security configuration rather than sprinkled through controllers.

4.2.1.2 Data access & transactions

• Connection management: the application relies on a Spring-managed DataSource configured via `application.properties` or environment variables. HikariCP is the recommended connection pool implementation for production.

• Persistence: domain entities are mapped with JPA/Hibernate. Repositories (interfaces extending Spring Data JPA) encapsulate common queries, pagination and sorting behavior. Transactional boundaries are declared at the service layer to ensure atomic multi-step operations.

4.2.1.3 Controllers and core request flows

The system organizes HTTP handling into concise controllers that validate input, map to DTOs/entities, and call services. Representative controllers and their responsibilities:

- `AuthController`
  - Handles login, logout and session/token issuance. Validates credentials, issues session cookies or tokens, and returns standardized JSON responses for auth status.

- `JobController` / `PostJobController`
  - Exposes endpoints to create, edit, view and search jobs. Accepts query parameters for filters (q, location, tags, hours, posted_at ranges), returns paginated results, and enforces authorization for employer-specific actions.

- `AppliedJobsController`
  - Manages application flows: submit application (with resume), list applications for a job or an applicant (with filtering and paging), and update application status. For submission the controller orchestrates file storage and a DB save in a transactional pattern (or uses compensating actions if the file store is not transactional).

- `FileController`
  - Handles secure download and preview of uploaded resumes. Enforces access control (only authorized employers or the applicant can download), streams files efficiently, and returns appropriate content-type headers.

- `ProfileController` / `UserController`
  - Manages profile updates, skills, availability, and profile completeness scoring; delegates scoring and suggestion generation to `ProfileScoringService`.

4.2.1.4 File uploads and storage

• Multipart handling: Spring Boot’s multipart configuration streams uploads to temporary files; controllers validate file size and MIME type before persisting metadata.

• Prototype storage: resume files are stored under `uploads/resumes/` in the prototype. The `resumes` table keeps metadata (original filename, storage key or path, MIME type, size, uploaded_at). For production, the recommended pattern is to store binaries in object storage (S3/Azure Blob) and keep signed URLs or storage keys in MySQL.

4.2.1.5 Error handling and validation

• Centralized exception handling: use `@ControllerAdvice` to map exceptions to structured JSON error responses with appropriate HTTP status codes. This keeps controllers focused on happy‑path logic.

• Input validation: request DTOs use JSR-380 Bean Validation annotations (e.g., `@NotBlank`, `@Size`, `@Email`) and controllers enforce any domain-level constraints.

4.2.1.6 Representative data flows and examples

Example: applying to a job (happy path)
1. Client `POST /api/jobs/{id}/apply` with multipart form (resume file + cover letter).
2. `AppliedJobsController` validates the request and delegates to `AppliedJobService`.
3. `AppliedJobService` opens a transaction, saves application metadata to the `applied_jobs` table, stores file metadata in the `resumes` table, and either uploads the file to local storage or enqueues an async job to move it to object storage. If file storage fails after the DB save, the service uses a compensating action to delete the DB rows or marks the application as needing file re-sync.
4. The service enqueues a scoring task (optional) and returns a 201 Created response with the application id.

Example: job search with filters
1. Client `GET /api/jobs?q=barista&location=city&page=1`.
2. `JobController` validates parameters and delegates to `JobRepository` or a search service.
3. Results are returned as a paginated JSON object including total counts and result items.

4.2.1.7 Component locations (where to find the code)

- Controllers: `src/main/java/com/example/smartjobsearch/controller/`
- Services: `src/main/java/com/example/smartjobsearch/service/`
- Repositories: `src/main/java/com/example/smartjobsearch/repo/`
- File storage (prototype): `uploads/resumes/`

4.2.1.8 Operational notes

- Use InnoDB with `utf8mb4` for the MySQL schema to support transactions and full Unicode.
- Keep large binaries out of MySQL; store only references. Consider a vector store for embeddings and join results with MySQL metadata.
- Configure HikariCP pool sizing according to workload and enable regular backups and tested restores.
- Use Flyway or Liquibase for schema migrations and avoid `hibernate.ddl-auto=update` in production.

If you want I can convert these textual flows into a more formal list of class responsibilities (one‑line per controller/service) or generate a PlantUML diagram that shows the key entities and flows.

## 4.2.2 Front-end structure (mapped from Student Support webapps)

The prototype frontend lives under `src/main/resources/static/` and `templates/`. Pages such as `home.html`, `jobpost.html`, `profile.html`, and `dashboard.html` correspond to the Student Support `homePage.html` and JSP pages but are plain HTML/Thymeleaf templates in this project. Key elements:

• Landing / home (`home.html`): job search box, filters, job cards and paging controls. Uses `api-utils.js` for fetch wrappers and session handling.
• Job posting (`jobpost.html`): multipart form to create/edit a job posting (handled by `PostJobController`).
• Apply / admissions-like page (`Appliedjobs.html` / employer dashboard): employers can review applicants for a job (analogous to the Student Support admissions view). This page calls `/api/jobs/{id}/applications` to fetch filtered lists of applicants with scores.
• Login / profile: `login.html` and `profile.html` manage authentication and profile editing; the server redirects or returns tokens depending on the client flow.

Client-side behavior mirrors the Student Support descriptions but implemented with simple static pages and fetch APIs rather than JSPs: client-side validation for file size/type, progressive enhancement, and accessible layout.

## 4.2.3 Deployment (Spring Boot instead of Tomcat WAR)

This project is packaged and run as a Spring Boot application (fat JAR) rather than a WAR deployed to an external Tomcat. Typical development and deployment flows:

- Development (PowerShell):

```pwsh
# Run with the embedded Maven wrapper
.\mvnw.cmd spring-boot:run

# Or build and run the packaged jar
.\mvnw.cmd -DskipTests package
java -jar target\*.jar
```

- Configuration and env vars: supply JDBC URL and credentials via environment variables (the app reads `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`) or via a secure secrets manager in production. Also set `ML_RECOMMENDER_URL` and third-party keys as environment variables.

- Production: build a Docker image containing the jar and run it behind a reverse proxy/load balancer (HTTPS terminated at the edge). Use managed MySQL or cloud RDS/Aurora as the backing datastore, HikariCP for pooling, Flyway for schema migrations, and a central secrets store for credentials.

Summary mapping
— The Student Support servlets (admissionDB, scholarshipDB, DBConnect, JSP pages) translate to this project’s Spring MVC controllers (`*Controller`), Spring Data JPA repositories, services, static HTML/Thymeleaf templates, and central application configuration. The result is more testable, configuration-driven, and easier to secure/scale than a raw servlet + WAR approach.

### 4.2.2 Frontend structure (detailed)

- Location: `src/main/resources/static/` contains `home.html`, `jobpost.html`, `profile.html`, `dashboard.html`, `api-utils.js`, `styles.css`, and other assets.
- Interactions:
  - `api-utils.js` centralizes fetch wrappers that attach session tokens and handle JSON errors.
  - The job search page calls `GET /api/jobs?q=...` and renders results client-side.
  - The job posting page uses a multipart form to `POST /api/post-job` (via `PostJobController`).
- UX notes: forms include client-side validation (JavaScript) and rely on server-side validation for security.

### 4.2.3 Deployment

Development run (Windows PowerShell):

```pwsh
# Run with embedded Maven wrapper
.\mvnw.cmd spring-boot:run

# Or build and run the packaged jar
.\mvnw.cmd -DskipTests package
java -jar target\*.jar
```

Environment variables (examples):

```pwsh
$env:COHERE_API_KEY="<your-key>"
$env:ML_RECOMMENDER_URL="http://localhost:8000/recommender"
$env:SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/smartjob"
$env:SPRING_DATASOURCE_USERNAME="user"
$env:SPRING_DATASOURCE_PASSWORD="pass"
```

Production recommendations:
- Use a managed DB (Postgres), enable connection pooling, and run Flyway/Liquibase for schema migrations (remove `hibernate.ddl-auto=update`).
- Move resume storage to S3 (or Azure Blob) and store signed URLs for downloads.
- Run the app in Docker and orchestrate with Kubernetes / Azure App Service / AWS ECS for scaling.
- Deploy the ML pipeline as a separate service (FastAPI + Uvicorn) that consumes training artifacts from `ml/artifacts/` and exposes a protected scoring API.

## 4.3 IMPLEMENTATION MAP: SOURCE → FEATURE

-- Job posting and editing: endpoints and service methods handle creation, validation and lifecycle transitions for job postings.
-- Job search & listing: search endpoints support keyword and filter-based queries with pagination and sorting.
-- User auth/profile: authentication and profile update endpoints support account management and profile completeness features.
-- Apply & resume handling: application endpoints accept applications with resume uploads, persist metadata, and trigger scoring workflows.
-- Profile scoring: the system provides a completeness score and generated suggestion text; it supports both rule-based scoring and optional model-assisted scoring.
-- ML artifacts: an offline pipeline produces embeddings and reranker artifacts used by an optional scoring/ranking service.

## 4.4 QUALITY, TESTING & VERIFICATION (brief)

- Unit tests: Java unit tests live in `test/java/...` (run with `mvnw test`). Add targeted unit tests around scoring and repository queries.
- Manual test cases used during development: job create/search, multipart resume upload, profile scoring, download resume access control, and ML webhook failures.
- Security sanity checks: validate file types, enforce auth checks on download endpoints, and avoid sending PII to external APIs by default.

## 4.5 OUTCOMES & SCREENSHOTS

Replace with project screenshots as needed. Example outcomes to capture:

- Home page showing job list and filters.
- Job posting form.
- Application flow with resume upload confirmation.
- Employer dashboard listing applicants with scores.

---

If you want, I can now:
1) embed the PlantUML-generated diagrams (PNGs) into this chapter,
2) generate an OpenAPI spec from the controller sources and add it to `docs/appendices/openapi.yaml`, or
3) create a short test script that runs the key endpoints locally and validates responses.

Tell me which of these next steps you want and I'll proceed.
