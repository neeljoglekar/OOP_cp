import java.util.List;

public class Admin extends User {
    public Admin(String userId, String name) {
        super(userId, name);
    }

    @Override
    public String getRole() {
        return "Admin";
    }

    public void monitorAuctions(AuctionSystem system) {
        List<Auction> auctions = system.getAuctions();
        System.out.println("Auctions in the system: " + auctions.size());
        for (Auction auction : auctions) {
            System.out.println(auction);
        }
    }
}
