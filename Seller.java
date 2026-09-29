import java.time.LocalDateTime;

public class Seller extends User {
    public Seller(String userId, String name) {
        super(userId, name);
    }

    public Seller(String userId, String name, String email) {
        super(userId, name, email);
    }

    @Override
    public String getRole() {
        return "Seller";
    }

    public void listProduct(AuctionSystem system, Product product) {
        system.addProduct(product);
    }

    public Auction createAuction(AuctionSystem system, String auctionId, Product product) {
        return system.createAuction(auctionId, product, this);
    }

    public Auction createAuction(AuctionSystem system, String auctionId, Product product,
            LocalDateTime endTime) {
        return system.createAuction(auctionId, product, this, endTime);
    }

    public Auction createAuction(AuctionSystem system, String auctionId, Product product,
            LocalDateTime endTime, double minimumBidIncrement) {
        return system.createAuction(auctionId, product, this, endTime, minimumBidIncrement);
    }
}
