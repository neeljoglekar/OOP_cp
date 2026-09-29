import java.time.LocalDateTime;
import java.util.List;

/** In-memory concurrent-bidding check; it does not require MySQL. */
public class SynchronizationTest {
    public static void main(String[] args) throws InterruptedException {
        Seller seller = new Seller("SYNC-SELLER", "Synchronization Seller");
        Product product = new Product("SYNC-PRODUCT", "Test auction item",
                "An item used to test simultaneous bidding.", "Good");
        Auction auction = new Auction("SYNC-AUCTION", product, seller,
                LocalDateTime.now().plusHours(1), 10.00);

        Buyer[] buyers = {
            new Buyer("SYNC-BUYER-1", "Buyer One"),
            new Buyer("SYNC-BUYER-2", "Buyer Two"),
            new Buyer("SYNC-BUYER-3", "Buyer Three"),
            new Buyer("SYNC-BUYER-4", "Buyer Four"),
            new Buyer("SYNC-BUYER-5", "Buyer Five")
        };
        // Deliberately mix the amounts so the outcome depends on thread scheduling.
        double[] amounts = {100.00, 140.00, 110.00, 130.00, 120.00};
        String[] results = new String[amounts.length];
        Thread[] threads = new Thread[amounts.length];
        Object startGate = new Object();
        boolean[] startBidding = {false};

        for (int index = 0; index < amounts.length; index++) {
            final int bidIndex = index;
            threads[index] = new Thread(() -> {
                try {
                    synchronized (startGate) {
                        while (!startBidding[0]) {
                            startGate.wait();
                        }
                    }
                    Bid accepted = buyers[bidIndex].placeBid(auction, amounts[bidIndex]);
                    results[bidIndex] = String.format("Accepted %.2f from %s at %s",
                            accepted.getAmount(), accepted.getBidder().getName(),
                            accepted.getTimestamp());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    results[bidIndex] = "Bid thread was interrupted.";
                } catch (InvalidBidException | AuctionClosedException
                        | InsufficientBidException exception) {
                    results[bidIndex] = String.format("Rejected %.2f: %s",
                            amounts[bidIndex], exception.getMessage());
                }
            }, "bid-attempt-" + (index + 1));
        }

        // Start every bid attempt before waiting for any one of them to finish.
        for (Thread thread : threads) {
            thread.start();
        }
        synchronized (startGate) {
            startBidding[0] = true;
            startGate.notifyAll();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        int acceptedCount = 0;
        int rejectedCount = 0;
        for (int index = 0; index < results.length; index++) {
            String result = results[index];
            if (result == null) {
                throw new AssertionError("A bid thread did not report a result.");
            }
            System.out.println("Attempt " + (index + 1) + " outcome: " + result);
            if (result.startsWith("Accepted ")) {
                acceptedCount++;
            } else if (result.startsWith("Rejected ")) {
                rejectedCount++;
            } else {
                throw new AssertionError(result);
            }
        }

        List<Bid> acceptedBids = auction.getBids();
        if (acceptedBids.isEmpty() || acceptedBids.size() != acceptedCount
                || acceptedCount + rejectedCount != amounts.length) {
            throw new AssertionError("Reported bid outcomes do not match auction history.");
        }

        double maximumAmount = 0.0;
        double previousAmount = 0.0;
        boolean firstBid = true;
        for (Bid bid : acceptedBids) {
            if (bid.getBidder() == null || !Double.isFinite(bid.getAmount())
                    || bid.getAmount() <= 0) {
                throw new AssertionError("Auction contains an invalid bid.");
            }
            System.out.printf("Stored accepted order: %s bid %.2f%n",
                    bid.getBidder().getName(), bid.getAmount());
            if (!firstBid && bid.getAmount() < previousAmount + auction.getMinimumBidIncrement()) {
                throw new AssertionError("Accepted bid history violates the minimum increment.");
            }
            previousAmount = bid.getAmount();
            maximumAmount = Math.max(maximumAmount, bid.getAmount());
            firstBid = false;
        }

        Bid highest = auction.getHighestValidBid();
        if (highest == null || highest.getAmount() != maximumAmount
                || highest.getAmount() != previousAmount) {
            throw new AssertionError("Final highest bid does not match accepted bid history.");
        }

        auction.closeAuction();
        Buyer winner = auction.getWinner();
        if (winner != highest.getBidder()) {
            throw new AssertionError("Winner does not match the final highest bid.");
        }

        System.out.println("Accepted bid count: " + acceptedBids.size());
        System.out.println("Rejected bid count: " + rejectedCount);
        System.out.printf("Final highest bid: %.2f%n", highest.getAmount());
        System.out.println("Winner: " + winner.getName());
        System.out.println("SynchronizationTest passed.");
    }
}
