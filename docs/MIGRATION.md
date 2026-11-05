# Migration notes: `adminRoutes.js` and `userRoutes.js` to Spring Boot

Status of this code: **written but not compiled and not run.** The environment it was written in
could not reach Maven Central and only had JDK 21 (your `pom.xml` needs 25). See "Build and test".

## Endpoint mapping

| Express | Spring Boot | Access | Success | Errors |
|---|---|---|---|---|
| `POST /admin/register` | `AdminController.register` | public | 201 `{message, admin}` | 400 `{message}` |
| `POST /admin/login` | `AdminController.login` | public | 200 `{message, token}` (1 day) | 404 `"User Not Found"`, 400 `"Invalid Password"` (bare JSON strings, as in Node) |
| `GET /admin/users` | `AdminController.getAllUsers` | admin | 200 `[users]` | |
| `GET /admin/problems` | `AdminController.getAllProblems` | admin | 200 `{problems}` | |
| `GET /admin/teams` | `AdminController.getAllTeams` | admin | **always 501** (see below) | |
| `POST /admin/create-event` | `AdminController.createEvent` | admin | 201 `{message, event}` | 400 `{error}`, 409 duplicate name |
| `POST /user/register` | `UserController.register` | public | 201 `{message, user}` | 400 `{error}` |
| `POST /user/updatepassword` | `UserController.updatePassword` | **public (as in Node)** | 200 `{message}` | 400, 404 |
| `POST /user/login` | `UserController.login` | public | 200 `{message, isVerified, token, user}` (2 h) | 400 `{error}`; **501** `{status:"ended", token, user}` after the event ended |
| `POST /user/logout` | `UserController.logout` | any valid token | 200 `{message}` | |
| `GET /user/profile` | `UserController.getProfile` | any valid token | 200 `{user}` | |
| `GET /user/gethistory` | `UserController.getHistory` | any valid token | 200 `[submissions]`, newest first | 500 if the token has no `team_id` |

Token errors (same as the two Express middlewares):

| Case | `/admin/**` | `/user/logout`, `/profile`, `/gethistory` |
|---|---|---|
| no token | 403 `Access denied. No token provided.` | 403 `Access denied` |
| expired token | 400 `Invalid token` | 401 `Token expired. Please log in again.` |
| bad signature / garbage | 400 `Invalid token` | 403 `Invalid token. Please log in again.` |
| valid token, role is not `admin` | 403 `Access only For Admins` | n/a |

## Classes

| Class | Job |
|---|---|
| `controller/AdminController`, `UserController` | URLs and HTTP status codes only; they call the services |
| `service/AdminService`, `UserService` | All validation and business rules, password hashing, token creation |
| `repository/UserRepository` | Users and admins (same `user` table). `EventRepository`, `ProblemRepository`, `SubmissionRepository` for the other tables |
| `entity/User, Event, Problem, Submission` | Map the existing tables `user`, `events`, `problems`, `submissions`. Foreign keys are plain number columns |
| `dto/*` | JSON bodies. `UserResponse` has no password field. JSON names match Node (`isjunior`, `event_id`, `team_id`, ...) |
| `security/SecurityConfig` | Which URLs are public / admin-only / any-token, error responses, CORS, BCrypt |
| `security/JwtService`, `JwtAuthFilter`, `JwtUser`, `TokenException` | Create and check HS256 tokens, read the `Authorization: Bearer` header |
| `exception/ApiException`, `GlobalExceptionHandler` | Services throw `ApiException`; one place turns exceptions into JSON. Unexpected errors give `{"error":"Internal server error"}`, details only go to the log |

## Differences from the Node behaviour, and why

1. **`GET /admin/teams` returns 501.** `models/index.js` has `// const Team = require('./Team'); // REMOVED`, so `Team` is
   `undefined` and the Node endpoint throws on every call (HTTP 500). There is no Team table to read, and the task says not
   to invent logic. If you want it to list the `leaderboards` table instead, say so.
2. **`GET /admin/users` and `GET /admin/problems` return only columns that exist.** The Node code selects `email`, `created_at`,
   `event_id` (users) and `created_at`, `test_case_path` (problems). None of them exist in the models or in `database/postgres.sql`,
   so in Node these two endpoints fail with a database error on every call. Spring returns the existing columns.
3. **`email` is required by `/admin/register` but not stored.** There is no `email` column in `user` (Sequelize silently ignored it).
4. **Admin register no longer returns the password hash** (Node returned the whole row). Same for every other response.
5. **Error responses do not contain `details: error.message`** (internal details), and `/user/updatepassword` no longer echoes the
   request body (`received`), which contained the new password.
6. Unexpected failures answer 500 `{"error":"Internal server error"}` (Node sometimes used 400 with the message, for example in user login).
7. Duplicate event name: 409 instead of a 500 from the database.
8. Required-field checks treat whitespace-only text as empty.
9. `/user/gethistory` with a token that has no `team_id` (for example an admin token): Node threw (500). Kept as 500 on purpose,
   because a `null` team id would otherwise match submissions that have no team.
10. `repository/AdminRepository.java` from your structure was **not created**: admins and users are rows of the same table, so
    `UserRepository` serves both. A second repository for the same entity would be an unused class.
11. `Express` treated `/user/login/` and `/user/login` the same; Spring 6+ does not match the trailing slash.

## Things in the Node app that are kept, but you should look at

- **`POST /user/updatepassword` needs no token and no old password.** Anyone who knows a username (including an admin's) can
  set a new password for it. Kept because this is a migration; I recommend fixing it.
- **`POST /admin/register` is public.** Anyone can create an admin account.
- `/admin/login` does not check that the user is an admin (a normal user gets a token whose role is `USER`; admin URLs then reject it).
- `/admin/register` does not check for a duplicate username, `/user/register` does. The `user` table has no unique constraint on `username`.
- Not migrated because they are not in the two route files: `getAllSubmissions`, `startEvent`, `endEvent` in `adminController.js`
  (they have no route in Node either).

## Configuration changes

`pom.xml`: added `com.nimbusds:nimbus-jose-jwt:9.47` (JWT), and test-scope `spring-security-test` and `h2`. Nothing else changed
(Redis starter, Lombok and the compiler plugin settings are untouched).

`application.properties`: the database URL, user and password now come from `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
(your file contained the placeholder `YOUR_MYSQL_PASSWORD`); added `jwt.secret=${JWT_SECRET}`,
`hibernate.jdbc.time_zone=UTC` and `app.cors.allowed-origins` (same origins as `server.js`, plus `FRONTEND_URL`).
`spring.jpa.hibernate.ddl-auto=update` is unchanged. It only adds missing tables and columns and never drops anything, but it
may create tables from the four entities if your MySQL database is empty. Once your schema exists, `validate` is safer.

`JWT_SECRET` must be the same value as in Node (so existing tokens still work) and at least 32 characters. A shorter secret
works in `jsonwebtoken` but the Java library refuses to sign or verify with it.

## Build and test

```bash
export DB_PASSWORD=your_mysql_password
export JWT_SECRET=the_same_secret_as_the_node_backend   # 32+ characters
mvn test                 # tests use an in-memory H2 database, no MySQL needed
mvn spring-boot:run      # needs MySQL running with the database from DB_URL
```

Postman: import `docs/postman_collection.json`. Run "Login admin" / "Login user" first; they save the token into the
collection variables used by the other requests. The user login needs an event whose `start_time` is in the past.
