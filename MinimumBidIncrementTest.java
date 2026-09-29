import java.time.LocalDateTime;

public class MinimumBidIncrementTest {
    public static void main(String[] args) throws Exception {
        Seller seller = new Seller("S1", "Test Seller");
        Buyer firstBuyer = new Buyer("B1", "First Buyer");
        Buyer secondBuyer = new Buyer("B2", "Second Buyer");
        Product product = new Product("P1", "Test product", "A product for the test.", "Good");
        LocalDateTime endTime = LocalDateTime.now().plusHours(1);
        Auction auction = new Auction("A1", product, seller, endTime, 50.00);

        check(auction.getMinimumBidIncrement() == 50.00, "Minimum increment was not stored.");
        check(endTime.equals(auction.getEndTime()), "Auction end time was not stored.");
        checkInvalidIncrement(seller, product, 0.0);
        checkInvalidIncrement(seller, product, -1.0);
        checkInvalidIncrement(seller, product, Double.NaN);
        checkInvalidIncrement(seller, product, Double.POSITIVE_INFINITY);
        checkInvalidIncrement(seller, product, Double.NEGATIVE_INFINITY);

        firstBuyer.placeBid(auction, 500.00);
        check(auction.getHighestValidBid().getAmount() == 500.00,
                "The first valid bid was not accepted.");
        checkInsufficientBid(secondBuyer, auction, 520.00);
        checkInsufficientBid(secondBuyer, auction, 549.00);
        secondBuyer.placeBid(auction, 550.00);
        check(auction.getHighestValidBid().getAmount() == 550.00,
                "A bid exactly at the minimum required amount was not accepted.");
        firstBuyer.placeBid(auction, 600.00);
        check(auction.getHighestValidBid().getAmount() == 600.00,
                "A bid above the minimum required amount was not accepted.");

        checkInvalidBid(firstBuyer, auction, 600.00);
        checkInvalidBid(firstBuyer, auction, 0.0);
        checkInvalidBid(firstBuyer, auction, -5.0);
        checkInvalidBid(firstBuyer, auction, Double.NaN);
        checkInvalidBid(firstBuyer, auction, Double.POSITIVE_INFINITY);
        try {
            auction.placeBid(null, 700.00);
            throw new AssertionError("A bid with no buyer was accepted.");
        } catch (InvalidBidException expected) {
            // Expected for a missing bidder.
        }

        auction.closeAuction();
        check(auction.isClosed(), "Manual close did not close the auction.");
        check(auction.getWinner() == firstBuyer, "Winner determination changed.");
        firstBuyer.payForAuction(auction);
        checkInvalidBid(secondBuyer, auction, Double.NaN);
        checkClosedBid(secondBuyer, auction, 700.00);

        Auction expiredAuction = new Auction("A2", product, seller,
                LocalDateTime.now().minusSeconds(1), 50.00);
        check(expiredAuction.isExpired(), "A past-end-time auction was not expired.");
        check(expiredAuction.isClosed(), "An expired auction was not treated as closed.");
        checkClosedBid(secondBuyer, expiredAuction, 100.00);

        Auction expiringWithBid = new Auction("A4", product, seller,
                LocalDateTime.now().plusSeconds(1), 50.00);
        firstBuyer.placeBid(expiringWithBid, 500.00);
        Thread.sleep(1100);
        check(expiringWithBid.isExpired(), "Auction did not expire at its configured end time.");
        checkClosedBid(secondBuyer, expiringWithBid, 520.00);

        Auction defaultAuction = new Auction("A3", product, seller);
        check(defaultAuction.getMinimumBidIncrement() == 1.00,
                "The backward-compatible constructor default changed.");
        check(defaultAuction.getEndTime().equals(LocalDateTime.MAX),
                "The backward-compatible constructor expiry behavior changed.");

        AuctionSystem system = new AuctionSystem();
        try {
            system.getAuction("MISSING");
            throw new AssertionError("Missing auction lookup did not throw.");
        } catch (AuctionNotFoundException expected) {
            // Existing missing-auction behavior is preserved.
        }

        System.out.println("MinimumBidIncrementTest passed.");
    }

    private static void checkInvalidIncrement(Seller seller, Product product, double increment) {
        try {
            new Auction("INVALID", product, seller, LocalDateTime.MAX, increment);
            throw new AssertionError("Invalid minimum increment was accepted: " + increment);
        } catch (IllegalArgumentException expected) {
            // Expected for zero, negative, NaN, or infinite increments.
        }
    }

    private static void checkInsufficientBid(Buyer buyer, Auction auction, double amount)
            throws InvalidBidException, AuctionClosedException {
        try {
            buyer.placeBid(auction, amount);
            throw new AssertionError("Insufficient bid was accepted: " + amount);
        } catch (InsufficientBidException expected) {
            // Expected when the bid is above the current bid but below the required increment.
        }
    }

    private static void checkInvalidBid(Buyer buyer, Auction auction, double amount)
            throws AuctionClosedException, InsufficientBidException {
        try {
            buyer.placeBid(auction, amount);
            throw new AssertionError("Invalid bid was accepted: " + amount);
        } catch (InvalidBidException expected) {
            // Expected for invalid amounts and bids that do not exceed the current high bid.
        }
    }

    private static void checkClosedBid(Buyer buyer, Auction auction, double amount)
            throws InvalidBidException, InsufficientBidException {
        try {
            buyer.placeBid(auction, amount);
            throw new AssertionError("A bid was accepted after the auction closed or expired.");
        } catch (AuctionClosedException expected) {
            // Expected for a manually closed or expired auction.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
