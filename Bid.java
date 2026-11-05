import java.time.LocalDateTime;
import java.util.Objects;

public class Bid {
    private Buyer bidder;
    private double amount;
    private final LocalDateTime timestamp;

    public Bid(Buyer bidder, double amount) {
        this(bidder, amount, LocalDateTime.now());
    }

    /** Reconstructs a persisted bid while preserving its stored timestamp. */
    public Bid(Buyer bidder, double amount, LocalDateTime timestamp) {
        this.bidder = bidder;
        this.amount = amount;
        this.timestamp = Objects.requireNonNull(timestamp, "Bid timestamp is required.");
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
