# Online Auction Simulator

## 1. Project Overview

Online Auction Simulator is a beginner-friendly Java console project for a second-year OOP course. It models users, products, auctions, and bids, and includes MySQL-backed account registration, login, profile management, and logout. A product has an ID, name, description, and condition. The account features were previously reported database-verified; database-backed checks could not be rerun in the latest review because the required database environment variables were unavailable.

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
- **Static members** — `UserSession` keeps one static current-user reference for the console application; `UserDAO` holds shared hashing constants and a `SecureRandom` instance.
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

Other current support files are `DatabaseConnection.java`, `UserDAO.java`, `UserSession.java`, and `InsufficientBidException.java`. Test/demo files are `DatabaseConnectionTest.java`, `UserRegistrationTest.java`, `UserLoginTest.java`, `UserLogoutTest.java`, `AdminRoleTest.java`, `ProfileManagementTest.java`, `MinimumBidIncrementTest.java`, `BidTimestampTest.java`, `UserNotFoundExceptionTest.java`, `UnauthorizedActionExceptionTest.java`, and `SynchronizationTest.java`.

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

`Auction` stores a `LocalDateTime` end time and a positive finite minimum bid increment. Its backend logic detects expiry, treats an expired auction as closed, and rejects further bids with `AuctionClosedException`. Manual close behavior is preserved. Sellers enter the end time in local time using `yyyy-MM-ddTHH:mm`. The frontend countdown display will be implemented later. For source compatibility, older constructor overloads remain available and use a default minimum increment of `1.00`; constructors without an end-time argument retain their no-expiry behavior. New auctions created through the seller menu require an end time and minimum increment.

The first bid follows the existing positive finite amount validation. For later bids, a bid at or below the current highest remains invalid and throws `InvalidBidException`. A valid bid above the current highest but below `current highest + minimum bid increment` throws `InsufficientBidException`; a bid equal to or above that required amount is accepted. Invalid bidder/amount checks happen before closed/expired checks, which happen before minimum-increment checks. Detailed tie-handling rules are **TBD**.

When a bid is accepted, `Bid` records the current `LocalDateTime` in its constructor. The timestamp is immutable, has no setter, is not requested from the user, and is included in the bid display. Rejected attempts do not create a `Bid` and therefore do not receive a bid timestamp.

### Concurrent bidding

`Auction.placeBid` is a synchronized method. It keeps bid validation, auction status/expiry checks, minimum-increment checks, and adding the accepted bid in one protected operation. Without this, two threads could read the same previous highest bid and both accept amounts that are not valid when considered in sequence. Related state reads, closing, winner selection, and bid-list snapshots also use synchronization so callers see a consistent auction state. `SynchronizationTest` starts several bidder threads on one auction, joins them, reports accepted/rejected attempts, and checks that the stored bids, final highest bid, and winner agree. The test uses in-memory auction data and does not require MySQL. This synchronization is per `Auction`; `AuctionSystem` collections and the static user session remain intended for the current single-threaded console flow.

## 8. User Accounts and Profiles

Account feature status:

- **User registration:** collect a user ID, name, email, password, and role. Exact registration validation rules are **TBD**.
- **Login:** `UserDAO.login` looks up the registered email with a `PreparedStatement`, verifies the entered password against the stored PBKDF2-HMAC-SHA256 hash, and creates a `Buyer`, `Seller`, or `Admin` according to the stored role. Unknown email and incorrect password both return `null`. The session is held in `UserSession`; a failed login does not replace the current user. Phase 3 is reported fully verified and complete.
- **Logout:** `UserSession.logout()` clears the in-memory current user and does not access the database. Phase 4 is reported fully verified and complete.
- **Password handling:** registration is reported complete from Phase 2 and uses PBKDF2-HMAC-SHA256 with a random salt. Login uses the same stored-hash format and is reported verified in Phase 3.
- **Admin account and roles:** `UserDAO` maps the existing `users.role` value `ADMIN` to `Admin`, and registration accepts only `BUYER`, `SELLER`, or `ADMIN`. No separate admin table is used. Admin-only auction monitoring requires that the calling `Admin` object be the active session user. Phase 5 is reported fully verified and complete.
- **Profile management:** `UserDAO.viewCurrentProfile`, `updateCurrentProfile`, and `changeCurrentPassword` operate only on the current session user. Database queries/updates use `PreparedStatement`. A duplicate email update returns `false`; successful name/email changes update the same in-memory user object. Password changes reuse the registration PBKDF2-HMAC-SHA256 salted hash method. `user_id` is immutable and is not an update parameter. The user reports Phase 6 was database-verified; its tests could not be rerun against MySQL in this review environment.

The user record identifies the roles `BUYER`, `SELLER`, and `ADMIN`. Current role-based authorization is limited to protecting admin auction monitoring. No separate Admin database table is used. Profile access is limited to the account ID held by the current session.

`UserNotFoundException` is used when `UserDAO.findUserById` cannot find the requested ID, or when a logged-in user's database profile row is unexpectedly missing during a profile operation. Login intentionally continues to return `null` for an unknown email or incorrect password. `UnauthorizedActionException` is used by `Admin.monitorAuctions` unless the calling Admin object is the active session user. Profile methods still accept no target user ID and use only the immutable ID from the current session, preserving profile isolation. No separate user-to-user profile modification operation exists.

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
- **Auction end time:** Implemented with `LocalDateTime`; the backend treats auctions at or past their end time as expired/closed and rejects bids. Manual closing remains available. The console displays the end time and status; a frontend countdown is postponed.
- **Minimum bid increment:** Implemented as a positive finite `double` stored on each auction. Subsequent bids must be at least the current highest bid plus this increment. Higher bids below that threshold use `InsufficientBidException`; zero/non-increasing/invalid amounts remain `InvalidBidException`.
- **Bid timestamps:** Each accepted `Bid` automatically stores an immutable `LocalDateTime` from its creation time and displays it. No database persistence or user input for bid timestamps is implemented.
- **Concurrent bidding:** `Auction.placeBid` and related auction state operations are synchronized. Concurrent bids are validated and recorded serially per auction; their order depends on thread scheduling, while each accepted bid must still satisfy the existing rules against the latest accepted bid.
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
