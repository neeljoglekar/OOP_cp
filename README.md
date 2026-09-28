# Online Auction Simulator

## 1. Project Overview

Online Auction Simulator is a Java project that models users, products, auctions, and bids. Sellers list products, auctions are created, buyers place bids, and the auction determines a winner when it ends. The project is intended to remain beginner-friendly and suitable for a second-year Java OOP course.

## 2. Project Objective

The objective is to demonstrate relevant Java Object-Oriented Programming concepts through a simple auction simulation. Classes should have clear responsibilities, and OOP concepts should be used where they make sense without adding unnecessary complexity.

## 3. Core Functionality

### Confirmed workflow

- A seller lists a product.
- An auction is created for the product.
- Buyers can place multiple bids.
- Bids are validated.
- The highest valid bid is determined.
- The auction ends and a winner is determined.
- Payment-related behavior can be represented through `Payable`.

For this initial implementation, a bid amount must be positive, and each new bid must be higher than the current highest bid. Detailed tie-handling rules are **TBD**.

## 4. Planned Features

The initial Java source has been compiled with JDK 25.0.3 and the demonstration workflow has been run. The statuses below reflect what that run exercised.

- [x] **Tested** — Seller lists a product and an auction is created for it.
- [x] **Tested** — Buyers place multiple bids; positive amounts above the current highest bid are accepted.
- [x] **Tested** — Determine the highest valid bid and auction winner after the auction is closed.
- [x] **Tested** — Represent payment-related behavior through `Payable`.
- [x] **Tested** — Invalid bids and operations on closed auctions use their custom exceptions.
- [x] **Tested** — Auction lookup throws `AuctionNotFoundException` when no matching auction exists.

Status meanings: **Planned** = accepted for future implementation; **In Progress** = currently being implemented; **Implemented (not exercised in the demo)** = source code exists and compiles, but the behavior was not exercised by the demonstration; **Tested** = compiled, run, and behavior was verified; **Not Planned** = explicitly excluded. Do not describe unverified behavior as tested.

## 5. OOP Concepts

The project is expected to demonstrate these concepts in a simple, meaningful way:

- **Classes and Objects** — model users, products, auctions, and bids.
- **Encapsulation** — keep object data and related behavior together, controlling how state is accessed and changed.
- **Constructors** — initialize objects with the information they need.
- **Inheritance** — use where meaningful, including `Buyer`, `Seller`, and `Admin` extending `User`.
- **Polymorphism** — use where it naturally supports the user and auction design.
- **Interfaces** — use `Biddable` for basic bid-related behavior and `Payable` for payment-related behavior after an auction is won.
- **Method overriding** — apply where appropriate to inherited or interface behavior.
- **Custom exception handling** — use `InvalidBidException`, `AuctionClosedException`, and `AuctionNotFoundException` for their stated cases.

Do not force concepts into classes or behavior where they do not make sense.

## 6. Main Entities / Classes

The following 14 Java files form the **finalized initial project structure**. Their responsibilities below are the initial design.

| File | Responsibility |
|---|---|
| `User.java` | Common user information and behavior shared by users. |
| `Buyer.java` | Represents a buyer who can participate in auctions and place bids. |
| `Seller.java` | Represents a seller who can list products and create auctions. |
| `Admin.java` | Represents an administrator who can manage or monitor the auction system. |
| `Product.java` | Represents a product/item being offered for auction. |
| `Auction.java` | Represents an auction, including its product, status, bids, and auction-related operations. |
| `Bid.java` | Represents a bid placed by a buyer, including the bidder and bid amount. |
| `Biddable.java` | Interface defining basic behavior related to placing or accepting bids. |
| `Payable.java` | Interface defining basic payment-related behavior after an auction is won. |
| `InvalidBidException.java` | Custom exception for invalid bids. |
| `AuctionClosedException.java` | Custom exception for attempting an auction operation after the auction has closed. |
| `AuctionNotFoundException.java` | Custom exception for attempting to access an auction that does not exist. |
| `AuctionSystem.java` | Manages the overall auction system, including users, products, and auctions. |
| `Main.java` | Entry point of the Java application, used to demonstrate and test the system. |

## 7. Auction Logic

The agreed high-level workflow is:

1. A seller lists a product.
2. An auction is created for the product.
3. Buyers can place multiple bids.
4. Bids are validated.
5. The highest valid bid is determined.
6. The auction ends.
7. A winner is determined.
8. Payment-related behavior can be represented through `Payable`.

For this initial implementation, a bid amount must be positive, and each new bid must be higher than the current highest bid. Auction duration/timer and detailed tie-handling rules are **TBD**.

## 8. Data Storage

Database storage versus file handling is **TBD**. Neither approach has been selected or implemented.

## 9. User Roles

- **Buyer** — participates in auctions and places bids.
- **Seller** — lists products and creates auctions.
- **Admin** — manages or monitors the auction system.

These roles are represented in the finalized initial file structure.

## 10. Input and Validation

- Bids are validated before determining the highest valid bid.
- A bid amount must be positive and higher than the current highest bid.
- Detailed tie-handling behavior is **TBD**.
- `InvalidBidException` is designated for invalid bids.
- `AuctionClosedException` is designated for auction operations attempted after an auction has closed.
- `AuctionNotFoundException` is designated for attempts to access an auction that does not exist.

## 11. Final Java File Structure

The initial Java project structure is finalized as these 14 files:

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

## 12. Current Project Status

- **Requirements/design:** Established.
- **Java file structure:** Finalized (14 files listed above).
- **Java implementation:** Created in the finalized 14 files.
- **Testing:** All 14 Java files compiled with JDK 25.0.3 and `Main` ran successfully. The demonstration exercised product listing, auction creation, multiple bids, highest-bid and winner determination, payment behavior, and all three custom exceptions: `InvalidBidException`, `AuctionClosedException`, and `AuctionNotFoundException`.
- **Database/file storage:** TBD.
- **Other undecided details:** Auction duration/timer and detailed tie-handling rules.

## 13. Development Rules

- Inspect the existing files before modifying them.
- Do not unnecessarily rewrite working code.
- Keep the implementation beginner-friendly and suitable for a second-year Java OOP course.
- Use OOP concepts meaningfully.
- Do not introduce unnecessary libraries or frameworks.
- Maintain clear class responsibilities.
- Keep changes focused on the requested feature.
- Test existing functionality after adding a new feature.
- Update this README whenever an important requirement or design decision changes.
- Do not claim a feature is complete until it has been implemented and tested.
- Keep undecided requirements marked as **TBD** rather than guessing.

## 14. Change Log

| Date | Change | Files affected | Status |
|---|---|---|---|
| 2026-09-28 | Created this README to record the initial requirements and decisions. Java implementation had not started. | `README.md` | README created; implementation not started |
| 2026-09-28 | Corrected the project workflow and scope. Java implementation remained not yet created. | `README.md` | Corrections recorded; implementation not started |
| 2026-09-28 | Finalized the 14-file Java structure, initial class responsibilities, OOP expectations, workflow, and current status. | `README.md` | Structure finalized; implementation and testing not started |
| 2026-09-28 | Created the initial implementation in all 14 Java files. Compilation and execution could not be verified because the JDK launch outside the sandbox was rejected. | All 14 Java files; `README.md` | Source created; compilation and tests not run |
| 2026-09-28 | Compiled all 14 Java files with JDK 25.0.3 and ran `Main` successfully. Updated feature statuses to reflect the demonstration; auction-not-found behavior compiled but was not exercised. | All 14 Java files; `README.md` | Main workflow tested successfully |
| 2026-09-28 | Updated `Main` to demonstrate missing-auction lookup and handling `AuctionNotFoundException`; corrected the bid-validation wording and updated test status. | `Main.java`; `README.md` | All three custom exceptions exercised in the demonstration |
