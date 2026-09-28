public class Bid {
    private Buyer bidder;
    private double amount;

    public Bid(Buyer bidder, double amount) {
        this.bidder = bidder;
        this.amount = amount;
    }

    public Buyer getBidder() {
        return bidder;
    }

    public double getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return bidder.getName() + " bid " + String.format("%.2f", amount);
    }
}
