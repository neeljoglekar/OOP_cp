import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Auction implements Biddable {
    private static final double DEFAULT_MINIMUM_BID_INCREMENT = 1.00;

    private String auctionId;
    private Product product;
    private Seller seller;
    private LocalDateTime endTime;
    private double minimumBidIncrement;
    private boolean closed;
    private List<Bid> bids;

    /** Keeps older callers working with the documented default increment. */
    public Auction(String auctionId, Product product, Seller seller) {
        this(auctionId, product, seller, LocalDateTime.MAX, DEFAULT_MINIMUM_BID_INCREMENT);
    }

    public Auction(String auctionId, Product product, Seller seller, LocalDateTime endTime) {
        this(auctionId, product, seller, endTime, DEFAULT_MINIMUM_BID_INCREMENT);
    }

    public Auction(String auctionId, Product product, Seller seller, double minimumBidIncrement) {
        this(auctionId, product, seller, LocalDateTime.MAX, minimumBidIncrement);
    }

    public Auction(String auctionId, Product product, Seller seller, LocalDateTime endTime,
            double minimumBidIncrement) {
        this.auctionId = auctionId;
        this.product = product;
        this.seller = seller;
        this.endTime = Objects.requireNonNull(endTime, "Auction end time is required.");
        if (!Double.isFinite(minimumBidIncrement) || minimumBidIncrement <= 0) {
            throw new IllegalArgumentException("Minimum bid increment must be a positive finite number.");
        }
        this.minimumBidIncrement = minimumBidIncrement;
        this.closed = false;
        this.bids = new ArrayList<>();
    }

    public String getAuctionId() {
        return auctionId;
    }

    public Product getProduct() {
        return product;
    }

    public Seller getSeller() {
        return seller;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public double getMinimumBidIncrement() {
        return minimumBidIncrement;
    }

    public boolean isExpired() {
        return !LocalDateTime.now().isBefore(endTime);
    }

    public synchronized boolean isClosed() {
        return closed || isExpired();
    }

    public synchronized String getStatus() {
        if (closed) {
            return "manually closed";
        }
        if (isExpired()) {
            return "expired";
        }
        return "open";
    }

    public synchronized List<Bid> getBids() {
        return Collections.unmodifiableList(new ArrayList<>(bids));
    }

    @Override
    public synchronized Bid placeBid(Buyer buyer, double amount)
            throws InvalidBidException, AuctionClosedException, InsufficientBidException {
        if (buyer == null || !Double.isFinite(amount) || amount <= 0) {
            throw new InvalidBidException("A bid must have a buyer and a positive finite amount.");
        }
        if (closed) {
            throw new AuctionClosedException("Auction " + auctionId + " was manually closed.");
        }
        if (isExpired()) {
            throw new AuctionClosedException("Auction " + auctionId + " has expired.");
        }

        Bid currentHighest = getHighestValidBid();
        if (currentHighest != null) {
            if (amount <= currentHighest.getAmount()) {
                throw new InvalidBidException("A new bid must be higher than the current highest bid.");
            }

            double minimumRequiredBid = currentHighest.getAmount() + minimumBidIncrement;
            if (amount < minimumRequiredBid) {
                throw new InsufficientBidException(String.format(
                        "The minimum next bid for auction %s is %.2f.", auctionId, minimumRequiredBid));
            }
        }

        Bid bid = new Bid(buyer, amount);
        bids.add(bid);
        return bid;
    }

    public synchronized Bid getHighestValidBid() {
        Bid highest = null;
        for (Bid bid : bids) {
            if (highest == null || bid.getAmount() > highest.getAmount()) {
                highest = bid;
            }
        }
        return highest;
    }

    public synchronized void closeAuction() {
        closed = true;
    }

    public synchronized Buyer getWinner() {
        if (!isClosed()) {
            return null;
        }
        Bid highest = getHighestValidBid();
        return highest == null ? null : highest.getBidder();
    }

    @Override
    public String toString() {
        String endTimeDescription = endTime.equals(LocalDateTime.MAX)
                ? "no expiry configured" : "ends " + endTime;
        return "Auction " + auctionId + " for " + product.getName()
                + " (" + getStatus() + ", " + endTimeDescription
                + ", minimum bid increment " + String.format("%.2f", minimumBidIncrement) + ")";
    }
}
