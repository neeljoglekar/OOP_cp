public class Buyer extends User implements Payable {
    public Buyer(String userId, String name) {
        super(userId, name);
    }

    @Override
    public String getRole() {
        return "Buyer";
    }

    public Bid placeBid(Auction auction, double amount)
            throws InvalidBidException, AuctionClosedException {
        return auction.placeBid(this, amount);
    }

    @Override
    public void payForAuction(Auction auction) {
        if (auction == null || auction.getWinner() != this) {
            System.out.println(getName() + " is not the winner; no payment was made.");
            return;
        }

        System.out.printf("Payment of %.2f recorded for auction %s.%n",
                auction.getHighestValidBid().getAmount(), auction.getAuctionId());
    }
}
