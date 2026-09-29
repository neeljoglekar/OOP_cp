# Online Auction Simulator

## 1. Project Overview

Online Auction Simulator is a beginner-friendly Java OOP project for a second-year course, with a console demonstration and a small browser frontend. It models users, products, auctions, and bids, and includes MySQL-backed account registration, login, profile management, and logout. A product has an ID, name, description, and condition. The account features were previously reported database-verified; database-backed checks could not be rerun in this phase because the required database environment variables were unavailable.

## 2. Project Objective

The objective is to demonstrate relevant Java Object-Oriented Programming concepts through a simple auction simulation and account system. Classes should have clear responsibilities, and OOP concepts should be used where they make sense without adding unnecessary complexity.

## 3. Core Functionality

### Account workflow

1. A user registers.
2. A user logs in.
3. The system identifies the logged-in user's role.
4. The user performs actions appropriate to that role.
5. The user can manage their profile.
6. The user logs out.

Registration, login, role handling, profile updates, and logout form the account workflow. The user reports that Phases 1–6 were fully implemented and database-verified. In this review environment, database-backed regression tests could not connect because `AUCTION_DB_URL`, `AUCTION_DB_USERNAME`, and `AUCTION_DB_PASSWORD` are not configured; see the test results below.

### Existing auction workflow

1. A seller lists a product with its name, description, and condition.
2. An auction is created for the product.
   Each auction has a configured end time.
3. Buyers place multiple bids.
4. Bids are validated.
5. The highest valid bid is determined.
6. The auction ends.
7. A winner is determined.
8. Payment-related behavior can be represented through `Payable`.

The auction workflow is implemented and the `Main` demonstration passed in this review. Bid amounts must be positive and finite. After the first bid, a new bid must meet the current highest bid plus the auction's minimum bid increment. Detailed tie-handling rules are **TBD**.

## 4. Planned Features

The auction demonstration and compile were verified in this review. The user reports Phases 1–6, including account and database behavior, as previously verified. Database-backed tests in this review stopped at connection setup because the required environment variables were absent; they must not be treated as newly reverified here.

- [x] **Tested** — Seller lists a product and an auction is created for it.
- [x] **Tested** — Buyers place multiple bids; positive finite amounts above the current highest bid are accepted. `Main` also verifies that a NaN bid is rejected.
- [x] **Tested** — Determine the highest valid bid and auction winner after the auction is closed.
- [x] **Tested** — Auctions carry an end time; expired auctions are unavailable for bidding and are reported as expired.
- [x] **Tested** — Auctions carry a positive finite minimum bid increment; an insufficient higher bid is rejected with `InsufficientBidException`.
- [x] **Tested** — Each accepted `Bid` receives an automatic creation timestamp and displays it; timestamp handling is not entered by the user.
- [x] **Tested** — Concurrent bids on the same auction are synchronized so validation and bid recording happen as one operation.
- [x] **Tested** — A JDK HTTP server exposes health, auction listing/detail, and bid endpoints while using the existing auction objects and rules.
- [x] **Tested (browser UI not verified)** — A seller-only REST endpoint creates in-memory products and auctions from the Seller identity in the caller's REST token; the frontend includes a seller-only creation form and refreshes the auction list after success.
- [x] **Tested (browser multi-tab behavior not manually verified)** — REST login issues a separate bearer token per login; protected REST actions derive the caller and role from that token, and logout invalidates only that token. The frontend keeps its token in per-tab `sessionStorage`.
- [ ] **Implemented; browser flow not verified** — The frontend provides a bid form, selected-auction polling, and a countdown using the existing API. Browser-driven bidding and live-update behavior have not been verified in this environment.
- [ ] **Implemented; database/browser login not verified** — The frontend calls REST login/logout routes, keeps the account in page memory and the per-tab token in `sessionStorage`, and enables bid submission only for a logged-in Buyer. Successful database authentication and browser interaction remain unverified in this environment.
- [ ] **Implemented; database/browser account flows not verified** — The frontend has Buyer/Seller registration and current-user profile view/edit forms backed by REST endpoints. Registration and profile database flows were not verified in this environment because the database connection variables were unavailable; browser interaction was not tested.
- [x] **Tested** — Represent payment-related behavior through `Payable`.
- [x] **Tested** — Products store and expose an ID, name, description, and condition; a seller can list such a product and create an auction for it.
- [x] **Tested** — Invalid bids, closed auctions, insufficient bids, missing auctions, and unauthorized admin monitoring use their separate custom exceptions.
- [ ] **Implemented; database test not run** — `UserNotFoundException` is used for missing user IDs and missing current-profile rows. `UserNotFoundExceptionTest` requires the database environment variables, which were unavailable in the latest review.
- [x] **Tested (reported from Phase 2)** — User registration using MySQL and JDBC. Phase 2 was reported verified by the user; it was not rerun during Phase 3.
- [x] **Tested (reported from Phase 3)** — Login by email using a prepared statement and the existing PBKDF2-HMAC-SHA256 hashes; successful login creates the role-specific user and sets the current session.
- [x] **Tested (reported from Phase 4)** — Logout clears the in-memory current user without changing the database user record.
- [x] **Tested (reported from Phase 2)** — Registration stores password hashes; login uses the same hash format and is reported verified in Phase 3.
- [x] **Tested (reported from Phase 5)** — Admin role uses the shared `users` table; admin-only auction monitoring is protected from Buyer and Seller sessions.
- [x] **Previously verified (user-reported)** — Logged-in users can view/update their own name and email and change their password. Profile operations derive the account ID from the active session; the ID is immutable. Duplicate email updates are rejected. The database scenarios were not rerun successfully in this review because connection variables were unavailable.
- [x] **Tested (reported from Phase 1)** — JDBC-based MySQL connection setup. Connector/J is present and its driver is discoverable when the JAR is on the classpath; the connection was not rechecked during Phase 3 because the database environment variables were unset.
- [x] **Tested (reported from Phase 1)** — MySQL user/account schema. The user reports that `database.sql` was run successfully in MySQL Workbench; it was not reverified during this phase.

Status meanings: **Planned** = accepted for future implementation; **In Progress** = currently being implemented; **Implemented** = coded but not verified; **Tested** = implemented and verified; **Not Planned** = explicitly excluded. Do not describe unverified behavior as tested.

## 5. OOP Concepts

The current implementation demonstrates these concepts in a simple, meaningful way:

- **Classes and Objects** — model users, products, auctions, and bids.
- **Encapsulation** — entity and account state is held in private fields and accessed through methods; `User.userId` is immutable.
- **Constructors** — initialize objects with the information they need.
- **Inheritance** — `Buyer`, `Seller`, and `Admin` extend `User`.
- **Polymorphism** — role-specific `User` subclasses are constructed from stored roles and used through the shared user type; overriding supplies role behavior.
- **Abstraction** — the `Biddable` and `Payable` interfaces describe bid and payment behavior without tying callers to extra frameworks or an abstract class hierarchy.
- **Interfaces** — `Auction` implements `Biddable` and `Buyer` implements `Payable`.
- **Method overriding** — `Buyer`, `Seller`, and `Admin` override role behavior inherited from `User`.
- **Association/composition** — an `Auction` refers to its `Product` and `Seller` and contains its bid history; each `Bid` refers to its `Buyer`.
- **Access modifiers** — model fields are private; public methods expose the needed operations, and the current-user setter is package-private.
- **Static members** — `UserSession` keeps one static current-user reference for the console application. REST browser sessions use a separate server-side token map and do not rely on that global reference. `UserDAO` holds shared hashing constants and a `SecureRandom` instance.
- **Synchronization** — `Auction` synchronizes bid placement, closing, and the reads that inspect mutable auction bidding state.
- **Custom exception handling** — `InvalidBidException`, `AuctionClosedException`, `AuctionNotFoundException`, `InsufficientBidException`, `UserNotFoundException`, and `UnauthorizedActionException` each represent a distinct failure condition.

The account implementation uses the existing `User`, `Buyer`, `Seller`, and `Admin` hierarchy. Keep OOP concepts meaningful and do not force them into places where they do not make sense.

## 6. Main Entities / Classes

The following 14 Java files are the **finalized initial project structure**. Additional Java files may be introduced later if required for database functionality.

| File | Responsibility |
|---|---|
| `User.java` | Common user information and behavior shared by users. |
| `Buyer.java` | Represents a buyer who can participate in auctions and place bids. |
| `Seller.java` | Represents a seller who can list products and create auctions. |
| `Admin.java` | Represents an administrator who can manage or monitor the auction system. |
| `Product.java` | Represents an auction product with an ID, name, description, and condition. |
| `Auction.java` | Represents an auction, including its product, status, bids, and auction-related operations. |
| `Bid.java` | Represents a bid placed by a buyer, including the bidder, bid amount, and immutable automatically generated creation timestamp. |
| `Biddable.java` | Interface defining basic behavior related to placing or accepting bids. |
| `Payable.java` | Interface defining basic payment-related behavior after an auction is won. |
| `InvalidBidException.java` | Custom exception for invalid bids. |
| `AuctionClosedException.java` | Custom exception for attempting an auction operation after the auction has closed. |
| `AuctionNotFoundException.java` | Custom exception for attempting to access an auction that does not exist. |
| `UserNotFoundException.java` | Custom exception for an explicitly requested user record that does not exist. |
| `UnauthorizedActionException.java` | Custom exception for an action denied because the current user is not authorized. |
| `AuctionSystem.java` | Manages the overall auction system, including users, products, and auctions. |
| `Main.java` | Entry point of the Java application, used to demonstrate and test the system. |

Database setup has since added `DatabaseConnection.java`, which is responsible only for opening a JDBC connection, and `DatabaseConnectionTest.java`, which is a separate connection check. These additions do not change the finalized initial 14-file structure.

Phase 2 added `UserDAO.java` for registration and password hashing, plus `UserRegistrationTest.java`. Phase 3 added login support to `UserDAO.java`, `UserSession.java` for current-user state, and `UserLoginTest.java` for database-backed login checks. Phase 4 added `UserSession.logout()` and `UserLogoutTest.java`. Phase 5 added admin-only authorization to `Admin.monitorAuctions` and `AdminRoleTest.java`. Phase 6 added profile operations to `UserDAO.java` and `ProfileManagementTest.java`; `User.java` now carries email and has an immutable user ID. Minimum-increment support adds `InsufficientBidException.java` and `MinimumBidIncrementTest.java`; the initial 14-file structure remains unchanged.

The user-lookup and authorization exception support adds `UserNotFoundException.java`, `UnauthorizedActionException.java`, `UserNotFoundExceptionTest.java`, and `UnauthorizedActionExceptionTest.java`. `UserDAO.findUserById` is the explicit ID lookup; unknown-email login retains its existing failure behavior. The DB-backed lookup test requires the existing database environment variables.

`SynchronizationTest.java` is an in-memory concurrency test for `Auction.placeBid`; it does not change the initial Java file structure or database schema.

Other current support files are `DatabaseConnection.java`, `UserDAO.java`, `UserSession.java`, and `InsufficientBidException.java`. Test/demo files are `DatabaseConnectionTest.java`, `UserRegistrationTest.java`, `UserLoginTest.java`, `UserLogoutTest.java`, `AdminRoleTest.java`, `ProfileManagementTest.java`, `MinimumBidIncrementTest.java`, `BidTimestampTest.java`, `UserNotFoundExceptionTest.java`, `UnauthorizedActionExceptionTest.java`, `SynchronizationTest.java`, `RestApiTest.java`, `SellerAuctionCreationTest.java`, `RestAuthenticationTest.java`, `RestAccountManagementTest.java`, and `RestSessionIsolationTest.java`.

The REST API adds `AuctionServer.java` as a separate server entry point, `RestApiTest.java` for auction HTTP checks, `SellerAuctionCreationTest.java` for seller-only auction creation checks, `RestAuthenticationTest.java` for authentication-route checks, `RestAccountManagementTest.java` for registration/profile route checks, and `RestSessionIsolationTest.java` for per-token profile and role checks. `Main.java` remains the console entry point.

## 7. Auction Logic

The existing auction flow is:

1. A seller lists a product with its name, description, and condition.
2. An auction is created for the product.
3. Buyers can place multiple bids.
4. Bids are validated.
5. The highest valid bid is determined.
6. Bidding is unavailable after the auction end time; a seller can also manually close an auction.
7. A winner is determined.
8. Payment-related behavior can be represented through `Payable`.

`Auction` stores a `LocalDateTime` end time and a positive finite minimum bid increment. Its backend logic detects expiry, treats an expired auction as closed, and rejects further bids with `AuctionClosedException`. Manual close behavior is preserved. Sellers enter the end time in local time using `yyyy-MM-ddTHH:mm`. The frontend displays a countdown derived from the API end time; the backend remains authoritative for expiry and bid acceptance. For source compatibility, older constructor overloads remain available and use a default minimum increment of `1.00`; constructors without an end-time argument retain their no-expiry behavior. New auctions created through the seller menu require an end time and minimum increment.

The first bid follows the existing positive finite amount validation. For later bids, a bid at or below the current highest remains invalid and throws `InvalidBidException`. A valid bid above the current highest but below `current highest + minimum bid increment` throws `InsufficientBidException`; a bid equal to or above that required amount is accepted. Invalid bidder/amount checks happen before closed/expired checks, which happen before minimum-increment checks. Detailed tie-handling rules are **TBD**.

When a bid is accepted, `Bid` records the current `LocalDateTime` in its constructor. The timestamp is immutable, has no setter, is not requested from the user, and is included in the bid display. Rejected attempts do not create a `Bid` and therefore do not receive a bid timestamp.

### Concurrent bidding

`Auction.placeBid` is a synchronized method. It keeps bid validation, auction status/expiry checks, minimum-increment checks, and adding the accepted bid in one protected operation. Without this, two threads could read the same previous highest bid and both accept amounts that are not valid when considered in sequence. Related state reads, closing, winner selection, and bid-list snapshots also use synchronization so callers see a consistent auction state. `SynchronizationTest` starts several bidder threads on one auction, joins them, reports accepted/rejected attempts, and checks that the stored bids, final highest bid, and winner agree. The test uses in-memory auction data and does not require MySQL. This synchronization is per `Auction`; `AuctionSystem` collections and the static user session remain intended for the current single-threaded console flow.

## 8. User Accounts and Profiles

Account feature status:

- **User registration:** collect a user ID, name, email, password, and role. Exact registration validation rules are **TBD**.
- **Login:** `UserDAO.login` looks up the registered email with a `PreparedStatement`, verifies the entered password against the stored PBKDF2-HMAC-SHA256 hash, and creates a `Buyer`, `Seller`, or `Admin` according to the stored role. Unknown email and incorrect password both return `null`. Console login uses `UserSession`; REST login uses `UserDAO.authenticate` and creates an independent server-side token. Phase 3 is reported fully verified and complete.
- **Logout:** Console `UserSession.logout()` clears the console user's in-memory state. REST `POST /api/logout` invalidates only the bearer token supplied by that caller. Neither logout path changes the database record. Phase 4 is reported fully verified and complete.
- **Password handling:** registration is reported complete from Phase 2 and uses PBKDF2-HMAC-SHA256 with a random salt. Login uses the same stored-hash format and is reported verified in Phase 3.
- **Admin account and roles:** `UserDAO` maps the existing `users.role` value `ADMIN` to `Admin`, and registration accepts only `BUYER`, `SELLER`, or `ADMIN`. No separate admin table is used. Admin-only auction monitoring requires that the calling `Admin` object be the active session user. Phase 5 is reported fully verified and complete.
- **Profile management:** Console DAO methods use `UserSession`; REST DAO overloads accept the user object resolved from the request's valid token. Database queries/updates use `PreparedStatement`. A duplicate email update returns `false`; successful name/email changes update that user's in-memory object. Password changes reuse the registration PBKDF2-HMAC-SHA256 salted hash method. `user_id` is immutable and is not an update parameter. The user reports Phase 6 was database-verified; its tests could not be rerun against MySQL in this review environment.

The user record identifies the roles `BUYER`, `SELLER`, and `ADMIN`. Current role-based authorization protects admin auction monitoring, seller auction creation, and Buyer-only bidding. No separate Admin database table is used. REST profile access is limited to the account ID held by the token's server-side user object.

`UserNotFoundException` is used when `UserDAO.findUserById` cannot find the requested ID, or when a logged-in user's database profile row is unexpectedly missing during a profile operation. Login intentionally continues to return `null` for an unknown email or incorrect password. `UnauthorizedActionException` is used by `Admin.monitorAuctions` unless the calling Admin object is the active session user. Console profile methods use the immutable ID from `UserSession`; REST profile methods use the immutable ID of the user resolved from the bearer token. Neither accepts an arbitrary target user ID, preserving profile isolation. No separate user-to-user profile modification operation exists.

## 9. Data Storage and Database Design

MySQL has been selected as the database, and JDBC has been selected for Java database connectivity. File handling is not the selected persistence approach. The user reports the database setup and Phases 1–6 were verified. A live connection was unavailable during this final review because the database environment variables were absent.

Initial database work will focus on user/account information. Auction, product, and bid persistence may be added later as the project is extended. The SQL setup script is [database.sql](database.sql); its successful execution in MySQL Workbench is user-reported and was not reverified during this phase.

The initial `auction_system` database schema defines a `users` table:

- `user_id INT PRIMARY KEY`
- `name VARCHAR(100) NOT NULL`
- `email VARCHAR(150) NOT NULL UNIQUE`
- `password_hash VARCHAR(255) NOT NULL`
- `role VARCHAR(20) NOT NULL` — intended values: `BUYER`, `SELLER`, `ADMIN`.

The supplied initial column types and constraints are captured in `database.sql`. The current schema has one account table; auction, product, and bid persistence have not been added. Product details and auction end times currently live in in-memory objects, so no product table, auction table, or DAO was added in this phase. Further schema details remain **TBD**.

```text
Java application
        ↓
      JDBC
        ↓
 MySQL database
```

## 10. JDBC Design

JDBC will connect the Java application to MySQL. Keep database access straightforward and suitable for a second-year Java OOP project. Do not introduce a framework or ORM such as Hibernate or Spring.

Connection details, credentials, URL, and configuration method are implementation details and should not be recorded in this README or hardcoded into project documentation.

## 11. Database-Related Java Structure

`DatabaseConnection.java` establishes a JDBC connection; it does not contain registration or login SQL. Its connection URL, username, and password are read from environment variables and are not hardcoded in the source. `DatabaseConnectionTest.java` checks the connection separately from the auction demo and now exits with a failure status when it cannot connect.

MySQL Connector/J is present at `lib/mysql-connector-j-26.7.0/mysql-connector-j-26.7.0.jar`. The JAR contains `com.mysql.cj.jdbc.Driver` and its JDBC service-provider entry. The current execution environment has no `AUCTION_DB_URL`, `AUCTION_DB_USERNAME`, or `AUCTION_DB_PASSWORD` variables, so database-backed tests stop at connection setup. No Maven/Gradle setup is used. A simple DAO-style approach keeps SQL/database operations separate from entity classes; `UserDAO.java` handles account SQL.

## 12. User Roles

- **Buyer (`BUYER`)** — participates in auctions and places bids.
- **Seller (`SELLER`)** — lists products and creates auctions.
- **Admin (`ADMIN`)** — represents an administrator who can manage or monitor the auction system.

These roles are represented by the existing user class hierarchy. Account registration is reported complete. Login identifies the role from the stored record and creates the corresponding subclass; Phases 3–5 are reported verified complete. Admin monitoring requires the current session to contain the same `Admin` object making the call.

## 13. Input and Validation

- Bid amounts must be positive and finite. The first bid uses this rule alone; later bids must be strictly higher than the current highest bid and at least the highest bid plus the auction's minimum increment.
- Detailed tie-handling behavior is **TBD**.
- `InvalidBidException` is used for invalid bids.
- `AuctionClosedException` is used for auction operations attempted after an auction has closed.
- `AuctionNotFoundException` is used for attempts to access an auction that does not exist.
- `InsufficientBidException` is used when a valid bid is above the current high bid but below the required minimum next bid.
- `UserNotFoundException` is used for a requested user ID that has no matching record; unknown-email login retains its non-revealing `null` failure behavior.
- `UnauthorizedActionException` is used when an action is denied to the current user, including admin monitoring by anyone other than the active Admin session object.
- A bid exactly equal to the current highest bid remains invalid; a later bid must be strictly higher and meet the configured increment.
- Registration validation details and email-format validation are **TBD**. Profile names and emails must be nonblank; email uniqueness is enforced by the existing database constraint. Login accepts an email and password; both unknown email and incorrect password fail by returning `null`.

## 14. Final Java File Structure

These 14 files are the finalized initial Java project structure. More files may be added later if database functionality requires them.

```text
OnlineAuctionSimulator/
│
├── User.java
├── Buyer.java
├── Seller.java
├── Admin.java
├── Product.java
├── Auction.java
├── Bid.java
├── Biddable.java
├── Payable.java
├── InvalidBidException.java
├── AuctionClosedException.java
├── AuctionNotFoundException.java
├── AuctionSystem.java
└── Main.java
```

## 15. Current Project Status

- **Requirements/design:** Established.
- **Initial Java file structure:** Finalized (14 files listed above).
- **Initial auction implementation:** Implemented and tested.
- **Product information:** `Product` stores product ID, name, description, and condition. Seller auction creation collects the description and condition, and auction viewing displays them. Product image handling is intentionally postponed until the frontend stage.
- **Auction end time:** Implemented with `LocalDateTime`; the backend treats auctions at or past their end time as expired/closed and rejects bids. Manual closing remains available. The console displays end time/status, and the frontend displays an API-derived countdown. The backend remains authoritative.
- **Minimum bid increment:** Implemented as a positive finite `double` stored on each auction. Subsequent bids must be at least the current highest bid plus this increment. Higher bids below that threshold use `InsufficientBidException`; zero/non-increasing/invalid amounts remain `InvalidBidException`.
- **Bid timestamps:** Each accepted `Bid` automatically stores an immutable `LocalDateTime` from its creation time and displays it. No database persistence or user input for bid timestamps is implemented.
- **Concurrent bidding:** `Auction.placeBid` and related auction state operations are synchronized. Concurrent bids are validated and recorded serially per auction; their order depends on thread scheduling, while each accepted bid must still satisfy the existing rules against the latest accepted bid.
- **REST API:** `AuctionServer.java` serves the existing in-memory auction system over HTTP. It uses the JDK `HttpServer`; `Auction.java` remains responsible for bid validation, expiry, increment rules, synchronization, and bid recording.
- **Custom exceptions:** `InvalidBidException`, `AuctionClosedException`, `AuctionNotFoundException`, `InsufficientBidException`, `UserNotFoundException`, and `UnauthorizedActionException` are implemented. Bid/auction exceptions are covered by the auction demonstrations and focused tests. The authorization test passed locally; the database-backed user-lookup test compiled but could not run because database environment variables were unavailable.
- **Database technology:** MySQL selected; setup started.
- **Database connectivity technology:** JDBC selected; `DatabaseConnection.java` created.
- **User registration:** Reported verified complete from Phase 2.
- **Login:** Reported fully verified and complete in Phase 3. `UserDAO.login` uses a prepared lookup, verifies the existing PBKDF2-HMAC-SHA256 format, returns the mapped `User` subclass, and records it in `UserSession`.
- **Logout:** Reported fully verified and complete by the user before Phase 5.
- **Password handling:** Registration hashing is reported verified from Phase 2; login verification is reported verified from Phase 3.
- **Admin and roles:** Reported fully verified and complete by the user before Phase 6. The database role remains in the shared `users` table.
- **Profile management:** Implemented in `UserDAO.java` and `User.java`; the user reports Phase 6 database verification passed. During this Phase 7 review, the no-session guard ran, but database-backed profile scenarios could not begin because the three `AUCTION_DB_*` variables are unset.
- **JDBC driver:** Connector/J is present at the path above, and `DriverManager` successfully discovers the driver when the JAR is on the runtime classpath.
- **Phase 2 registration test:** Reported verified complete by the user.
- **Phase 3 login test:** Reported fully verified and complete by the user before Phase 4.
- **Phase 4 logout test:** Reported fully verified and complete by the user before Phase 5.
- **Phase 5 AdminRoleTest:** Reported fully verified and complete by the user before Phase 6.
- **Phase 6 ProfileManagementTest:** The reported isolation assertion was caused by a stale test snapshot: the test compared the Seller record to a pre-update snapshot after intentionally updating that Seller's own profile. The test now retains the Buyer-flow isolation check and captures a fresh Seller snapshot after its own update before checking the Admin flow. All Java sources compiled. Runtime testing stopped before test-user registration because the three `AUCTION_DB_*` variables are unset, so the database-backed assertions remain unverified.
- **Account regression tests:** Registration, login, logout, and admin test programs were attempted after Phase 6 compilation. Each stopped at the initial database connection because the three `AUCTION_DB_*` variables are unset. The logout in-memory check and admin/unsupported-role local checks passed before their database steps.
- **MySQL database/table:** `database.sql` defines the database and initial `users` table. The user reports that these were created in Workbench; they were not reverified during this phase because a JDBC connection was unavailable.
- **Phase 7 compilation:** All Java sources compiled successfully with JDK 25 and the Connector/J JAR on the classpath.
- **Phase 7 auction demo:** `Main` ran successfully, covering multiple bids, highest bid/winner, payment behavior, invalid bid (including NaN), closed auction handling, admin monitoring, and missing-auction handling.
- **Phase 7 database test results:** `DatabaseConnectionTest` — **FAIL**, missing `AUCTION_DB_URL`, `AUCTION_DB_USERNAME`, and `AUCTION_DB_PASSWORD`; `UserRegistrationTest` — **FAIL**, stopped during database connection before registration checks; `UserLoginTest` — **FAIL**, stopped during initial database-backed setup before login checks; `UserLogoutTest` — **PARTIAL**, in-memory logout check passed, then database setup failed for the same missing variables; `AdminRoleTest` — **PARTIAL**, local unsupported-role validation and in-memory admin authorization checks passed, then database setup failed; `ProfileManagementTest` — **PARTIAL**, no-session guard passed, then initial registration setup failed. No DB-backed assertions were completed in this run. The user reports these phases were database-verified previously, but that could not be reproduced here.
- **Phase 7 review fixes:** NaN bids are rejected; `Main` demonstrates that validation. `DatabaseConnectionTest` now returns a nonzero process status on connection failure.
- **Minimum-increment verification:** `MinimumBidIncrementTest` and `Main` demonstrate invalid increment rejection, first bid, below/equal/above-threshold bids, NaN and non-increasing bids, manual close, expiry, winner/payment, and missing-auction handling.
- **Bid timestamp verification:** `BidTimestampTest` verifies timestamps are automatically created within each accepted bid's creation interval, are displayed, and do not change the existing bidding, exception, close/expiry, winner/payment, or missing-auction behavior.
- **User lookup and authorization:** `UserDAO.findUserById` uses a prepared statement and throws `UserNotFoundException` for a missing ID. `Admin.monitorAuctions` throws `UnauthorizedActionException` unless the calling Admin is the active session user. Login failure and current-session profile-isolation behavior remain unchanged.
- **Current exception-phase verification:** All Java sources compiled with JDK 25. `UnauthorizedActionExceptionTest`, `MinimumBidIncrementTest`, `BidTimestampTest`, and `Main` passed. `UserNotFoundExceptionTest`, registration, login, logout, admin database, and profile database checks stopped before database operations because `AUCTION_DB_URL`, `AUCTION_DB_USERNAME`, and `AUCTION_DB_PASSWORD` were not set. Local logout and admin authorization checks passed before the blocked database steps.
- **Synchronization verification:** `SynchronizationTest` starts five concurrent bid attempts and checks that the stored bid sequence respects the minimum increment, the highest bid matches accepted history, and the closed-auction winner matches that bid. Database-backed account tests remain dependent on the three `AUCTION_DB_*` environment variables.
- **Final review verification (2026-09-29):** All Java sources compiled with JDK 25 and Connector/J. `MinimumBidIncrementTest`, `BidTimestampTest`, `UnauthorizedActionExceptionTest`, `SynchronizationTest`, and `Main` passed. `Main` also handled an invalid top-level menu entry and then exited normally. Database-backed tests were not run because the three `AUCTION_DB_*` variables were unavailable.
- **REST API verification (2026-09-29):** All Java sources compiled with JDK 25 and Connector/J, including the `jdk.httpserver` module. `RestApiTest` passed health, CORS preflight, auction list/detail, missing-auction 404, valid bid, shared state across two HTTP clients, invalid and insufficient bid responses, non-buyer authorization, closed/expired auction responses, and winner data. `Main` and the existing in-memory auction tests passed. Database-backed regression tests were not run because the three `AUCTION_DB_*` variables were unavailable.
- **User database schema:** Initial fields, types, and stated constraints are defined in `database.sql`; broader schema details remain undecided.
- **Other undecided details:** Auction duration/timer, detailed tie-handling rules, registration validation, email-format validation, and exact database schema.

## 16. Development Rules

- Inspect the existing files before modifying them.
- Do not unnecessarily rewrite working code.
- Keep the implementation beginner-friendly and suitable for a second-year Java OOP course.
- Use OOP concepts meaningfully.
- Do not introduce unnecessary libraries, frameworks, or ORMs.
- Maintain clear class responsibilities.
- Keep changes focused on the requested feature.
- Test existing functionality after adding a new feature.
- Update this README whenever an important requirement or design decision changes.
- Do not claim a feature is complete until it has been implemented and tested.
- Keep undecided requirements marked as **TBD** rather than guessing.
- Never store account passwords as plain text in the database; use password hashing.

## 17. Change Log

| Date | Change | Files affected | Status |
|---|---|---|---|
| 2026-09-29 | Added seller-only in-memory auction creation through `POST /api/auctions`, requiring the active `UserSession` Seller and never accepting seller identity from request data. Added backend request validation/duplicate auction ID handling, a role-gated seller form that refreshes the auction list, and focused REST coverage. No database schema, account logic, or bid rules changed. | `AuctionServer.java`; `AuctionSystem.java`; `SellerAuctionCreationTest.java`; `frontend/index.html`; `frontend/css/style.css`; `frontend/js/app.js`; `README.md` | All Java sources compiled with JDK 25; new seller-creation HTTP test and in-memory REST/auction regression tests passed; database-dependent `AdminRoleTest` portion unavailable because `AUCTION_DB_*` variables were unset; frontend syntax and static references passed; browser UI not verified |
| 2026-09-29 | Added REST registration and current-session profile GET/PUT endpoints using existing `UserDAO`/`UserSession`; public registration allows only Buyer/Seller, profile updates do not accept user ID or role, and account responses exclude password data. Added frontend registration, backend-loaded profile view/edit, optional password change, and REST boundary/database-gated tests. Database schema and existing auction/account classes were not changed. | `AuctionServer.java`; `RestAccountManagementTest.java`; `frontend/index.html`; `frontend/css/style.css`; `frontend/js/app.js`; `README.md` | All Java sources compiled; REST boundary checks, JavaScript syntax, static references, and non-database regression tests passed; DB-backed account cases and browser flow not verified because DB variables/browser were unavailable |
| 2026-09-29 | Added frontend login/logout using `POST /api/login` and `POST /api/logout`, delegating credential verification and session changes to existing `UserDAO`/`UserSession`; added in-memory account display and Buyer-only bid controls using the signed-in account ID; added a focused REST authentication check. Did not change schema, account DAO/session implementation, or auction rules. | `AuctionServer.java`; `RestAuthenticationTest.java`; `frontend/index.html`; `frontend/css/style.css`; `frontend/js/app.js`; `README.md` | Full Java compilation and non-database regression tests passed; login request validation/logout tested; database-backed login was not verified because DB environment variables were unavailable, and browser flow was not run |
| 2026-09-30 | Added per-login REST bearer tokens held in an in-memory token-to-user map, with per-tab frontend `sessionStorage`. Protected profile, logout, bidding, and seller auction creation now derive identity and role from the token; REST login/logout no longer use or mutate the console `UserSession`. Added deterministic session-isolation coverage and updated related REST tests and documentation. | `AuctionServer.java`; `UserDAO.java`; `RestAuthenticationTest.java`; `RestAccountManagementTest.java`; `RestApiTest.java`; `SellerAuctionCreationTest.java`; `RestSessionIsolationTest.java`; `frontend/js/app.js`; `README.md` | JDK 25 compilation passed; `RestSessionIsolationTest`, `SellerAuctionCreationTest`, `RestAuthenticationTest`, non-database `RestAccountManagementTest`, `RestApiTest`, `MinimumBidIncrementTest`, `BidTimestampTest`, `UnauthorizedActionExceptionTest`, `SynchronizationTest`, and `Main` passed. MySQL-backed checks and browser multi-tab behavior were not verified because database variables/browser testing were unavailable. |
| 2026-09-29 | Added one-second polling for the selected auction using its existing detail endpoint, countdown from the returned end time, last-updated/live status, temporary-poll-error feedback, and cleanup on selection change, detail close, and page unload. Polling pauses while a bid request is active. No Java or database files were changed. | `frontend/index.html`; `frontend/css/style.css`; `frontend/js/app.js`; `README.md` | Java compile and requested non-database regression suite passed; JavaScript syntax/reference checks passed; browser polling/countdown not verified |
| 2026-09-29 | Added the auction detail bidding form with temporary buyer-ID entry, POST submission to the existing bid endpoint, friendly HTTP error messages, duplicate-submit prevention, and an authoritative detail refresh after acceptance. No Java or database behavior was changed. | `frontend/index.html`; `frontend/css/style.css`; `frontend/js/app.js`; `README.md` | Java compile, REST and auction regressions passed; browser-driven bidding flow not verified in this environment |
| 2026-09-29 | Added a separate REST API entry point using the JDK `HttpServer`, shared in-memory auction state, health/list/detail/bid endpoints, CORS, HTTP exception mapping, and an in-memory HTTP test. Kept `Main.java`, auction rules, synchronization, and database schema unchanged. | `AuctionServer.java`; `RestApiTest.java`; `README.md` | All Java sources compiled with JDK 25; `RestApiTest` and the requested non-database regressions passed; database tests skipped because environment variables were unavailable |
| 2026-09-29 | Completed a project review of auction/account code, synchronization, test coverage, and README accuracy. Clarified the documented OOP concepts and current supporting/test files; corrected indentation in `UserDAO.viewCurrentProfile` without changing behavior. | `UserDAO.java`; `README.md` | Compile and all available non-database tests passed; database-backed tests not run because environment variables were unavailable |
| 2026-09-29 | Synchronized the complete per-auction bid operation and related state access, closing, and winner operations; added an in-memory concurrent-bidding test and documented the approach. Database schema and account behavior were unchanged. | `Auction.java`; `SynchronizationTest.java`; `README.md` | All Java sources compiled with JDK 25; `SynchronizationTest`, auction regression tests, authorization test, and `Main` passed |
| 2026-09-29 | Added checked `UserNotFoundException` for explicit user-ID lookup and missing current-profile records; added checked `UnauthorizedActionException` for admin monitoring authorization; added focused tests and updated README documentation. Login behavior, session-based profile isolation, and database schema were preserved. | `UserNotFoundException.java`; `UnauthorizedActionException.java`; `UserDAO.java`; `Admin.java`; `Main.java`; `AdminRoleTest.java`; `ProfileManagementTest.java`; `UserNotFoundExceptionTest.java`; `UnauthorizedActionExceptionTest.java`; `README.md` | All Java sources compiled with JDK 25; local authorization, auction tests, and Main passed; DB-backed tests blocked by missing environment variables |
| 2026-09-29 | Added an immutable, automatically generated `LocalDateTime` to each accepted bid; included timestamps in bid display and added focused timestamp verification. Auction validation order and database schema were unchanged. | `Bid.java`; `Main.java`; `BidTimestampTest.java`; `README.md` | All Java sources compiled with JDK 25; `BidTimestampTest`, `MinimumBidIncrementTest`, seller-menu smoke check, and the `Main` demonstration passed |
| 2026-09-29 | Added a positive finite minimum bid increment, `InsufficientBidException`, seller-menu input/display, and demonstrations/tests for increment and existing auction behavior. Kept backward-compatible constructors with a default increment of 1.00; database schema unchanged. | `Auction.java`; `AuctionSystem.java`; `Seller.java`; `Buyer.java`; `Biddable.java`; `InsufficientBidException.java`; `MinimumBidIncrementTest.java`; `Main.java`; `README.md` | Full compilation, focused test, and Main demonstration passed |
| 2026-09-29 | Added auction end times, expiry-aware open/closed status and bid rejection, end-time input/display, and an expired-auction demonstration. Preserved constructors without end-time arguments for compatibility. | `Auction.java`; `AuctionSystem.java`; `Seller.java`; `Main.java`; `README.md` | Compiled and smoke-tested future/past expiry, bidding, manual close, winner/payment, exceptions, and seller menu behavior |
| 2026-09-29 | Added product description and condition fields, accessors, and constructor parameters; updated the seller console flow to collect and display the product details. Kept products in memory; database schema was unchanged. | `Product.java`; `Main.java`; `README.md` | Compiled and smoke-tested product values and auction compatibility |
| 2026-09-28 | Created this README to record the initial requirements and decisions. Java implementation had not started. | `README.md` | README created; implementation not started |
| 2026-09-28 | Corrected the project workflow and scope. Java implementation remained not yet created. | `README.md` | Corrections recorded; implementation not started |
| 2026-09-28 | Finalized the 14-file Java structure, initial class responsibilities, OOP expectations, workflow, and current status. | `README.md` | Structure finalized; implementation and testing not started |
| 2026-09-28 | Created the initial implementation in all 14 Java files. Compilation and execution could not be verified because the JDK launch outside the sandbox was rejected. | All 14 Java files; `README.md` | Source created; compilation and tests not run |
| 2026-09-28 | Compiled all 14 Java files with JDK 25.0.3 and ran `Main` successfully. Updated feature statuses to reflect the demonstration; auction-not-found behavior compiled but was not exercised. | All 14 Java files; `README.md` | Main workflow tested successfully |
| 2026-09-28 | Updated `Main` to demonstrate missing-auction lookup and handling `AuctionNotFoundException`; corrected the bid-validation wording and updated test status. | `Main.java`; `README.md` | All three custom exceptions exercised in the demonstration |
| 2026-09-28 | Selected MySQL and JDBC as the persistence/database approach and added user registration, login/logout, password handling, admin account, and profile management to planned scope. Implementation of these new features has not started. | `README.md` | Account/database features planned; implementation not started |
| 2026-09-28 | Began Step 1: added `database.sql`, `DatabaseConnection.java`, and `DatabaseConnectionTest.java`. All Java sources compiled and `Main` ran successfully; the JDBC test failed because MySQL Connector/J is not on the classpath. The SQL script was not run against a MySQL server. | `database.sql`; `DatabaseConnection.java`; `DatabaseConnectionTest.java`; `README.md` | JDBC connection not verified; Connector/J and MySQL setup still required |
| 2026-09-28 | Began Phase 2: added `UserDAO.java` with required-field/role validation, prepared SQL, subclass creation, duplicate checks, and PBKDF2-HMAC-SHA256 password hashing, plus `UserRegistrationTest.java`. All Java sources compiled and `Main` ran; database registration checks could not start because the database environment variables were absent. | `UserDAO.java`; `UserRegistrationTest.java`; `README.md` | Phase 2 in progress; database tests not completed |
| 2026-09-28 | Added Phase 3 email/password login with prepared lookup and PBKDF2 hash verification, role-specific user creation, and an in-memory current-user session. All Java sources compiled and `Main` ran successfully. Login test execution was blocked because `AUCTION_DB_URL`, `AUCTION_DB_USERNAME`, and `AUCTION_DB_PASSWORD` were unset. | `UserDAO.java`; `UserSession.java`; `UserLoginTest.java`; `README.md` | Login implemented; database-backed tests not verified; Phase 3 incomplete |
| 2026-09-28 | Recorded Phase 3 as fully verified and complete based on the user's confirmation. | `README.md` | Phase 3 complete (user-confirmed) |
| 2026-09-28 | Added `UserSession.logout()` to clear current-user state and `UserLogoutTest.java`. All Java files compiled with JDK 25; the in-memory clearing check passed and `Main` ran successfully. The database-backed logout test could not proceed because database environment variables were unset. | `UserSession.java`; `UserLogoutTest.java`; `README.md` | Logout implemented; database-backed checks incomplete; Phase 4 not yet verified complete |
| 2026-09-28 | Recorded Phase 4 as fully verified and complete based on the user's confirmation. | `README.md` | Phase 4 complete (user-confirmed) |
| 2026-09-28 | Added authorization to `Admin.monitorAuctions`, requiring the calling Admin to be the active session user; updated `Main` to establish the demo Admin session; added `AdminRoleTest.java`. Compilation, local authorization/role-validation checks, and `Main` succeeded. Database-backed ADMIN registration and login checks were blocked by unset connection variables. | `Admin.java`; `Main.java`; `AdminRoleTest.java`; `README.md` | Local checks passed; Phase 5 database checks incomplete |
| 2026-09-28 | Recorded Phase 5 as fully verified and complete based on the user's confirmation. | `README.md` | Phase 5 complete (user-confirmed) |
| 2026-09-28 | Added current-user profile viewing and name/email/password updates in `UserDAO.java`, email storage in the `User` hierarchy, and immutable user IDs. Added `ProfileManagementTest.java`. All Java sources compiled and `Main` ran; the no-session guards passed. Phase 6 and prior account regression tests could not complete database checks because the `AUCTION_DB_*` environment variables were unset. | `User.java`; `Buyer.java`; `Seller.java`; `Admin.java`; `UserDAO.java`; `ProfileManagementTest.java`; `README.md` | Profile code implemented; database-backed tests incomplete; Phase 6 not verified complete |
| 2026-09-28 | Final integration review: rejected non-finite NaN bid amounts and added a demonstration check; made JDBC connection-test failures return a nonzero exit status. Compiled all sources and ran `Main` successfully. Database-backed regression tests could not connect because `AUCTION_DB_URL`, `AUCTION_DB_USERNAME`, and `AUCTION_DB_PASSWORD` were absent. | `Auction.java`; `Main.java`; `DatabaseConnectionTest.java`; `README.md` | Compile and auction demo passed; database regression tests blocked by missing environment configuration |

## 18. REST API Foundation

`AuctionServer.java` is a separate HTTP entry point for browser or other HTTP clients. It uses the JDK's built-in `com.sun.net.httpserver.HttpServer` and the `jdk.httpserver` module. No REST framework or additional dependency is required. The existing console entry point remains `Main.java`.

The server binds to `127.0.0.1` on port `8080` by default. Its initial in-memory demonstration auction has the ID `API-AUCTION-1`.

### Endpoints

| Method and path | Purpose |
|---|---|
| `GET /api/health` | Returns a small health response. |
| `POST /api/login` | Accepts `{"email":"...","password":"..."}`, authenticates through `UserDAO.authenticate`, and returns a new session token plus `userId`, `name`, `email`, and `role`. It never returns the password or password hash. |
| `POST /api/logout` | Requires `Authorization: Bearer <sessionToken>` and invalidates only that token. |
| `POST /api/register` | Accepts user ID, name, email, password, and public role (`BUYER` or `SELLER`); delegates validation, duplicate checks, and password hashing to `UserDAO.registerUser`. Public `ADMIN` registration is rejected. |
| `GET /api/profile` | Requires a bearer token and returns that token's user's ID, name, email, and role; accepts no user ID selector. |
| `PUT /api/profile` | Requires a bearer token and updates only that token's user's name/email and optionally password. The request does not accept user ID or role; duplicate email is rejected. |
| `GET /api/auctions` | Lists the auctions currently held by the server. |
| `POST /api/auctions` | Requires a bearer token for a Seller; seller identity comes from the server-side token map, not the request. |
| `GET /api/auctions/{auctionId}` | Returns details, bids, and winner information for an auction. |
| `POST /api/auctions/{auctionId}/bids` | Attempts to place a bid on an auction. |

The bid request body contains the amount. The server derives the Buyer from the bearer token; any legacy `buyerId` field is ignored for identity, for example:

```json
{
  "amount": 50.0
}
```

Send protected REST requests with `Authorization: Bearer <sessionToken>`. Missing, invalid, or logged-out tokens receive HTTP 401. A successful login creates a distinct unpredictable token held in the server's in-memory session store; the token is returned only to the caller and is not placed in URLs or visible UI. REST session lookup does not use the console's global `UserSession`.

The seller auction-creation request is JSON with these fields: `auctionId`, `productId`, `productName`, `description`, `condition`, `endTime` (a future local date/time in Java `LocalDateTime` format), and `minimumBidIncrement` (a positive finite number). The request does not accept a seller ID. A token mapped to a Seller is required; an anonymous caller receives HTTP 401 and a Buyer or Admin receives HTTP 403. A duplicate auction ID returns HTTP 409, and malformed or invalid fields return HTTP 400. Successful creation returns HTTP 201 and the new auction details.

The server returns JSON. Bid validation continues to be performed by the existing auction logic. Invalid bid data is reported as HTTP 400, a closed or expired auction as HTTP 409, a missing auction as HTTP 404, and an unauthorized bidder as HTTP 403. Login returns HTTP 401 for incorrect credentials and HTTP 400 for malformed or empty credentials. CORS is enabled for the initial local development API.

### Shared state and current boundaries

The HTTP server uses one shared `AuctionSystem` instance for all requests, with a small fixed request thread pool. It looks up auctions through `AuctionSystem` and routes bid attempts through `Auction.placeBid`; it does not duplicate bid, expiry, or minimum-increment rules. REST login delegates password verification to `UserDAO.authenticate`, which preserves the console `UserSession`; logout removes only the supplied token. Database account records remain in the existing `users` table; REST sessions use no database table, and products, auctions, and bids are not persisted.

The demonstration auction and other auction state are in memory and reset when the server process stops. Seller-created products and auctions also remain in memory and are not persisted to MySQL. REST session tokens map to users in a concurrent in-memory store; stopping `AuctionServer` removes all sessions. `UserSession` remains for console application behavior and is not used to identify REST callers. The bid endpoint requires a Buyer token and ignores any request-body `buyerId` when choosing the bidder. These endpoints are for local development and should not be exposed as a public service in this state.

### Compile, run, and test on Windows

From the project directory in PowerShell, compile all Java sources with JDK 25 and the existing Connector/J JAR:

```powershell
javac --add-modules jdk.httpserver -cp "lib\mysql-connector-j-26.7.0\mysql-connector-j-26.7.0.jar" -d out *.java
```

Start the API:

```powershell
java --add-modules jdk.httpserver -cp "out;lib\mysql-connector-j-26.7.0\mysql-connector-j-26.7.0.jar" AuctionServer
```

Run the in-memory HTTP checks in a second PowerShell window:

```powershell
java --add-modules jdk.httpserver -cp "out;lib\mysql-connector-j-26.7.0\mysql-connector-j-26.7.0.jar" RestApiTest
java --add-modules jdk.httpserver -cp "out;lib\mysql-connector-j-26.7.0\mysql-connector-j-26.7.0.jar" SellerAuctionCreationTest
```

`RestApiTest` starts the server on an available local port and checks the endpoints without requiring database environment variables. The API's database-backed buyer lookup requires `AUCTION_DB_URL`, `AUCTION_DB_USERNAME`, and `AUCTION_DB_PASSWORD` when a buyer is not already available in the shared in-memory user collection.

`RestAuthenticationTest` checks required/malformed login request handling and verifies that unauthenticated REST logout is rejected without changing the console `UserSession`. `RestSessionIsolationTest` checks token issuance, role-bound identities, protected operations, and independent logout using an in-memory test DAO. A successful database login and real browser flow require MySQL/browser access and were not exercised in the latest run.

`RestAccountManagementTest` checks that public registration rejects `ADMIN` and unsupported roles, registration rejects malformed IDs, profile access requires a valid REST token, and profile update requests cannot supply a user ID or role. Its Buyer/Seller registration, duplicate checks, profile updates, password changes, and cleanup run only when all three `AUCTION_DB_*` variables are configured.

`SellerAuctionCreationTest` verifies seller-only creation through HTTP without MySQL. It checks unauthenticated/Buyer/Admin denial; successful Seller creation and session-derived ownership; duplicate auction IDs; blank required fields; invalid and past end times; zero, negative, and NaN increments; product description/condition and end-time/increment preservation; list/detail visibility; and valid bidding on the created auction.

## 19. Frontend Foundation

The initial frontend is in `frontend/` and uses plain HTML, CSS, and vanilla JavaScript. It does not use a frontend framework or package manager.

```text
frontend/
├── index.html
├── css/
│   └── style.css
└── js/
    └── app.js
```

The dashboard checks `GET http://127.0.0.1:8080/api/health`, then loads `GET /api/auctions`. It displays the API connection status and renders auction cards using the returned product name, description, condition, highest bid, minimum increment, end time, and status. **View auction** requests `GET /api/auctions/{auctionId}` and displays the returned details. The page shows friendly messages for a stopped server, API/response errors, and an empty auction list. Auction and product information shown by the page comes from the API; it is not hardcoded in the frontend.

The frontend includes registration, login/logout, profile viewing/editing, a seller-only auction-creation form, a bid form, selected-auction polling, and an auction countdown. The seller form is shown only when the page's current account role is `SELLER`. It submits auction ID, product ID, name, description, condition, end time, and minimum increment to `POST /api/auctions`; the API authorizes the request using the Seller mapped to its bearer token. The page then reloads the auction list from the API. The request does not contain a seller ID. Product/auction state is in memory and resets when the Java API stops. The Java API must be running separately for account actions, auction data, creation, bidding, polling, and the countdown to work.

### Registration and profile management

Signed-out users can switch between login and registration. Public registration accepts a positive numeric user ID, name, email, password, and either `BUYER` or `SELLER`; the normal form does not offer `ADMIN`, and the API rejects `ADMIN` even if a caller sends it directly. Registration calls `POST /api/register`, which reuses `UserDAO.registerUser` and its existing validation, prepared SQL, duplicate checks, and PBKDF2-HMAC-SHA256 hashing. Successful registration returns the user to the login form without signing them in automatically. Passwords are sent only in request bodies, cleared from the form after submission, and never returned by the API.

When signed in, a user can request `GET /api/profile` to load their current profile from MySQL and open the profile editor. `PUT /api/profile` updates name and email and changes the password only when a non-empty new password is supplied. The browser never sends a user ID or role in the update request. The endpoint resolves the user from the request's valid bearer token and passes that user to the existing `UserDAO` profile methods, so the stable ID and role are not editable through this form. A duplicate email update returns HTTP 409. Profile and registration responses include only user ID, name, email, and role.

Profile access requires a valid server-side REST token and returns HTTP 401 when it is missing or invalid. The frontend stores the opaque token in `sessionStorage` for the current browser tab and reloads the profile from the API when that tab is refreshed. It does not persist passwords. REST identity is separate from the static `UserSession` used by the console application.

### Login and logout

The login form sends the entered email and password only in the JSON body of `POST /api/login`. The Java endpoint authenticates through `UserDAO.authenticate`; on success it returns an unpredictable session token and the basic account fields (`userId`, `name`, `email`, `role`) without password data. The frontend stores the token in per-tab `sessionStorage`; the returned user object stays in JavaScript memory. Incorrect or unknown credentials receive the same friendly failure message. Logout sends `POST /api/logout` with that token, invalidates only that server-side token, and clears the current tab's token and displayed user information.

The REST API keeps a separate in-memory token-to-user map for browser requests. Each successful login receives a distinct token, and every protected request must send `Authorization: Bearer <sessionToken>`. Logout invalidates only the token presented in that tab; other tabs' tokens remain valid. The console `UserSession` remains unchanged and is not used to authenticate REST requests. Tokens disappear when the Java server stops. Cross-tab behavior has not been manually verified in a browser.

### Bidding UI

Selecting **View auction** loads the selected auction from `GET /api/auctions/{auctionId}` and displays its product information, highest bid, current high bidder when available, increment, end time, status, and winner when determined. The detail panel displays the logged-in Buyer account that will be used and asks only for a bid amount. A signed-out user is asked to log in as a buyer; a logged-in Seller or Admin may view auctions but cannot submit bids through the frontend.

On submission, the browser sends `POST /api/auctions/{auctionId}/bids` with JSON shaped as `{"amount": 50.0}` and the current bearer token in the `Authorization` header. The backend resolves the Buyer from the token; a request-body `buyerId` is never trusted as identity. The frontend checks that a Buyer is logged in and the amount is present, while the Java API and `Auction.placeBid` remain responsible for bid validity, bidder authorization, auction state, minimum increment, expiry, synchronization, and recording the bid. After a successful response, the frontend fetches the selected auction details again and displays the authoritative result from the server; it does not calculate the new highest bid itself. The submit button is disabled during the request to prevent repeated submissions.

The default `AuctionServer` demo creates a sample auction but does not seed an account. Logging in requires a registered account in MySQL and configured `AUCTION_DB_*` environment variables. A bid's logged-in Buyer ID must resolve to a Buyer in the API's in-memory `AuctionSystem` or to a Buyer record in MySQL.

HTTP responses are mapped to short user-facing messages: invalid or malformed bid (`400`), insufficient increment (`400`), closed/expired auction (`409`), missing auction or buyer (`404`), unauthorized bidder (`403`), incorrect login (`401`), server failure (`500`), or a connection failure. Raw Java stack traces and password data are not shown in the UI.

### Seller auction creation

When the current page account is a Seller, the account area shows a form for auction ID, product ID, product name, description, condition, end time, and minimum bid increment. The page sends these values as JSON to `POST /api/auctions`; it never sends a seller ID. The Java API requires a valid bearer token mapped to a `Seller`, checks required text fields, parses the end time and requires it to be in the future, checks that the increment is positive and finite, and rejects a duplicate auction ID. It then calls the existing `Seller` and `AuctionSystem` methods to create the product/auction and reloads the auction list. These records exist in memory only. Browser rendering and browser-driven seller creation have not been verified here.

### Live updates and countdown

After an auction is selected, the frontend polls only `GET /api/auctions/{auctionId}` approximately once per second. Selecting another auction clears the old interval before polling the new selection; closing the detail panel and leaving the page also stop it. Polling requests do not overlap, and polling pauses while a bid is being submitted and refreshed. A temporary poll failure keeps the last displayed auction data, shows a small retrying status, and the next interval tries again. Multiple browser windows see updates through the same running Java API; the API remains the source of truth and `Auction.placeBid()` continues to synchronize bid placement.

The selected auction countdown is calculated from the API-provided `LocalDateTime` and updated by the same one-second timer. The API value has no timezone offset, so browsers interpret the local date/time using their own local timezone. When the countdown reaches zero, the display says **Auction ended**; actual auction expiry and whether a bid is accepted are still determined by the Java server. WebSockets are not used.

### Run the Java API

From the project directory in PowerShell, compile and start the API as described in [REST API Foundation](#18-rest-api-foundation):

```powershell
javac --add-modules jdk.httpserver -cp "lib\mysql-connector-j-26.7.0\mysql-connector-j-26.7.0.jar" -d out *.java
java --add-modules jdk.httpserver -cp "out;lib\mysql-connector-j-26.7.0\mysql-connector-j-26.7.0.jar" AuctionServer
```

### Run the frontend

Serve the `frontend` folder as static files. Python's standard-library server is sufficient; no Node.js or npm setup is needed. Open a second PowerShell window in the project directory and run:

```powershell
python -m http.server 5500 --directory frontend --bind 127.0.0.1
```

Then open `http://127.0.0.1:5500/` in a browser. Keep both the Java API and the static server running while using the page. The Java API allows CORS for this local development setup.

**Verification (2026-09-29):** During Frontend Phase 3, all Java sources compiled with JDK 25 and Connector/J. `RestApiTest`, `MinimumBidIncrementTest`, `BidTimestampTest`, `UnauthorizedActionExceptionTest`, `SynchronizationTest`, and `Main` passed. `node --check frontend/js/app.js` passed; all static document element references were checked and the CSS/JS asset paths exist. The database environment variables were unavailable, so database-backed tests were skipped. Browser rendering, cross-window polling, countdown display, and browser-driven bidding could not be verified in this environment. The documented static-server command uses Python 3, which was not installed here.

**Frontend Phase 4 verification (2026-09-29):** All Java sources compiled with JDK 25 and Connector/J. `RestAuthenticationTest` passed empty/malformed login request handling and logout session clearing. `RestApiTest`, `MinimumBidIncrementTest`, `BidTimestampTest`, `UnauthorizedActionExceptionTest`, `SynchronizationTest`, and `Main` passed. `node --check frontend/js/app.js` passed, 25 direct HTML element references were checked, and CSS/JavaScript asset paths exist. Database-backed authentication tests were skipped because the three `AUCTION_DB_*` variables were unavailable. Successful account login and browser rendering were not verified.

**Frontend Phase 5 verification (2026-09-29):** All Java sources compiled with JDK 25 and Connector/J. `RestAccountManagementTest` passed its non-database checks: public `ADMIN`/unsupported role rejection, invalid registration ID rejection, unauthenticated profile denial, and rejection of profile update requests containing user ID or role. Its database-backed Buyer/Seller registration, duplicate checks, profile updates, and password-change scenarios were skipped because `AUCTION_DB_URL`, `AUCTION_DB_USERNAME`, and `AUCTION_DB_PASSWORD` were unavailable. The existing `DatabaseConnectionTest`, `UserRegistrationTest`, `UserLoginTest`, `UserLogoutTest`, `AdminRoleTest`, `ProfileManagementTest`, and `UserNotFoundExceptionTest` were also run; each stopped at database connection setup with `Set AUCTION_DB_URL, AUCTION_DB_USERNAME, and AUCTION_DB_PASSWORD before connecting to MySQL.` `RestAuthenticationTest`, `RestApiTest`, `MinimumBidIncrementTest`, `BidTimestampTest`, `UnauthorizedActionExceptionTest`, `SynchronizationTest`, and `Main` passed. JavaScript syntax passed; 51 direct HTML element references and CSS/JavaScript asset paths were checked. Browser rendering and registration/profile workflows were not browser-tested. No schema change was made.

**Frontend Phase 6 verification (2026-09-29):** All Java sources compiled with JDK 25 and Connector/J. `SellerAuctionCreationTest` passed authorization for anonymous/Buyer/Admin callers, Seller creation, session-derived seller identity, duplicate auction ID rejection, invalid/blank fields, invalid/past end time, non-positive/NaN increment rejection, list/detail visibility, and valid bidding on the created auction. `RestApiTest`, `RestAuthenticationTest`, the available checks in `RestAccountManagementTest`, `MinimumBidIncrementTest`, `BidTimestampTest`, `UnauthorizedActionExceptionTest`, `SynchronizationTest`, and `Main` passed. `AdminRoleTest` passed its in-memory role/authorization checks but stopped at its database registration portion because `AUCTION_DB_URL`, `AUCTION_DB_USERNAME`, and `AUCTION_DB_PASSWORD` are unset. `node --check frontend/js/app.js` passed and 64 direct HTML element references resolved. Browser display and seller form interaction were not verified. No database schema or account/auction bidding rules were changed.
