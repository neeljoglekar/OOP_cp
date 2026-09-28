import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Auction implements Biddable {
    private String auctionId;
    private Product product;
    private Seller seller;
    private boolean closed;
    private List<Bid> bids;

    public Auction(String auctionId, Product product, Seller seller) {
        this.auctionId = auctionId;
        this.product = product;
        this.seller = seller;
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

    public boolean isClosed() {
        return closed;
    }

    public List<Bid> getBids() {
        return Collections.unmodifiableList(bids);
    }

    @Override
    public Bid placeBid(Buyer buyer, double amount)
            throws InvalidBidException, AuctionClosedException {
        if (closed) {
            throw new AuctionClosedException("Auction " + auctionId + " is closed.");
        }
        if (buyer == null || amount <= 0) {
            throw new InvalidBidException("A bid must have a buyer and a positive amount.");
        }

        Bid currentHighest = getHighestValidBid();
        if (currentHighest != null && amount <= currentHighest.getAmount()) {
            throw new InvalidBidException("A new bid must be higher than the current highest bid.");
        }

        Bid bid = new Bid(buyer, amount);
        bids.add(bid);
        return bid;
    }

    public Bid getHighestValidBid() {
        Bid highest = null;
        for (Bid bid : bids) {
            if (highest == null || bid.getAmount() > highest.getAmount()) {
                highest = bid;
            }
        }
        return highest;
    }

    public void closeAuction() {
        closed = true;
    }

    public Buyer getWinner() {
        if (!closed) {
            return null;
        }
        Bid highest = getHighestValidBid();
        return highest == null ? null : highest.getBidder();
    }

    @Override
    public String toString() {
        return "Auction " + auctionId + " for " + product.getName()
                + (closed ? " (closed)" : " (open)");
    }
}
