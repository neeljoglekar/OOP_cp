public interface Biddable {
    Bid placeBid(Buyer buyer, double amount)
            throws InvalidBidException, AuctionClosedException, InsufficientBidException;
}
