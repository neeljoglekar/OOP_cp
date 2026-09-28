public class Seller extends User {
    public Seller(String userId, String name) {
        super(userId, name);
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
}
