import java.util.List;

public class Admin extends User {
    public Admin(String userId, String name) {
        super(userId, name);
    }

    public Admin(String userId, String name, String email) {
        super(userId, name, email);
    }

    @Override
    public String getRole() {
        return "Admin";
    }

    public void monitorAuctions(AuctionSystem system) {
        if (UserSession.getCurrentUser() != this) {
            throw new SecurityException("Only the logged-in administrator can monitor auctions.");
        }

        List<Auction> auctions = system.getAuctions();
        System.out.println("Auctions in the system: " + auctions.size());
        for (Auction auction : auctions) {
            System.out.println(auction);
        }
    }
}
