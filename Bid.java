import java.time.LocalDateTime;

public class Bid {
    private Buyer bidder;
    private double amount;
    private final LocalDateTime timestamp;

    public Bid(Buyer bidder, double amount) {
        this.bidder = bidder;
        this.amount = amount;
        this.timestamp = LocalDateTime.now();
    }

    public Buyer getBidder() {
        return bidder;
    }

    public double getAmount() {
        return amount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return bidder.getName() + " bid " + String.format("%.2f", amount)
                + " at " + timestamp;
    }
}
