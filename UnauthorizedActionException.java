/** Thrown when the current user is not allowed to perform an action. */
public class UnauthorizedActionException extends Exception {
    public UnauthorizedActionException(String message) {
        super(message);
    }
}
