import java.time.LocalDateTime;

public class BidTimestampTest {
    public static void main(String[] args) throws Exception {
        Seller seller = new Seller("TIMESTAMP-SELLER", "Timestamp Seller");
        Buyer firstBuyer = new Buyer("TIMESTAMP-BUYER-1", "First Buyer");
        Buyer secondBuyer = new Buyer("TIMESTAMP-BUYER-2", "Second Buyer");
        Product product = new Product("TIMESTAMP-PRODUCT", "Test product",
                "A product used to verify automatic bid timestamps.", "Good");
        Auction auction = new Auction("TIMESTAMP-AUCTION", product, seller,
                LocalDateTime.now().plusHours(1), 10.00);

        LocalDateTime firstStart = LocalDateTime.now();
        Bid firstBid = firstBuyer.placeBid(auction, 100.00);
        LocalDateTime firstEnd = LocalDateTime.now();
        check(firstBid.getTimestamp() != null, "A new bid has no timestamp.");
        check(inRange(firstBid.getTimestamp(), firstStart, firstEnd),
                "First bid timestamp is outside its creation interval.");
        check(firstBid.getBidder() == firstBuyer && firstBid.getAmount() == 100.00,
                "Bidder or amount behavior changed.");

        Thread.sleep(5);
        LocalDateTime secondStart = LocalDateTime.now();
        Bid secondBid = secondBuyer.placeBid(auction, 110.00);
        LocalDateTime secondEnd = LocalDateTime.now();
        check(inRange(secondBid.getTimestamp(), secondStart, secondEnd),
                "Second bid timestamp is outside its creation interval.");
        check(!secondBid.getTimestamp().isBefore(firstBid.getTimestamp()),
                "Second bid timestamp precedes the first bid timestamp.");
        check(auction.getBids().size() == 2, "Accepted bids were not stored.");
        check(firstBid.toString().contains(firstBid.getTimestamp().toString()),
                "Bid display does not include its timestamp.");

        checkInvalidBid(firstBuyer, auction, Double.NaN);
        checkInsufficientBid(firstBuyer, auction, 115.00);
        auction.closeAuction();
        checkClosedBid(firstBuyer, auction, 120.00);
        check(auction.getWinner() == secondBuyer, "Winner behavior changed.");
        secondBuyer.payForAuction(auction);

        Auction expiredAuction = new Auction("TIMESTAMP-EXPIRED", product, seller,
                LocalDateTime.now().minusSeconds(1), 10.00);
        checkClosedBid(firstBuyer, expiredAuction, 100.00);

        AuctionSystem system = new AuctionSystem();
        try {
            system.getAuction("MISSING-TIMESTAMP-AUCTION");
            throw new AssertionError("Missing auction lookup did not throw.");
        } catch (AuctionNotFoundException expected) {
            // Existing missing-auction behavior is preserved.
        }

        System.out.println("BidTimestampTest passed.");
    }

    private static boolean inRange(LocalDateTime value, LocalDateTime start, LocalDateTime end) {
        return !value.isBefore(start) && !value.isAfter(end);
    }

    private static void checkInvalidBid(Buyer buyer, Auction auction, double amount)
            throws AuctionClosedException, InsufficientBidException {
        try {
            buyer.placeBid(auction, amount);
            throw new AssertionError("Invalid bid was accepted.");
        } catch (InvalidBidException expected) {
            // Invalid amounts remain InvalidBidException.
        }
    }

    private static void checkInsufficientBid(Buyer buyer, Auction auction, double amount)
            throws InvalidBidException, AuctionClosedException {
        try {
            buyer.placeBid(auction, amount);
            throw new AssertionError("Insufficient bid was accepted.");
        } catch (InsufficientBidException expected) {
            // The minimum increment rule remains in effect.
        }
    }

    private static void checkClosedBid(Buyer buyer, Auction auction, double amount)
            throws InvalidBidException, InsufficientBidException {
        try {
            buyer.placeBid(auction, amount);
            throw new AssertionError("Bid was accepted for a closed or expired auction.");
        } catch (AuctionClosedException expected) {
            // Closed and expired auctions still reject bids.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
