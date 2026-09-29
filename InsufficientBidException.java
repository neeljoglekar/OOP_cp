/** Thrown when a valid bid does not meet an auction's minimum increment. */
public class InsufficientBidException extends Exception {
    public InsufficientBidException(String message) {
        super(message);
    }
}
