import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AuctionSystem {
    private List<User> users;
    private List<Product> products;
    private List<Auction> auctions;

    public AuctionSystem() {
        users = new ArrayList<>();
        products = new ArrayList<>();
        auctions = new ArrayList<>();
    }

    public void addUser(User user) {
        users.add(user);
    }

    public void addProduct(Product product) {
        products.add(product);
    }

    public Auction createAuction(String auctionId, Product product, Seller seller) {
        return createAuction(auctionId, product, seller, LocalDateTime.MAX);
    }

    public Auction createAuction(String auctionId, Product product, Seller seller,
            LocalDateTime endTime) {
        Auction auction = new Auction(auctionId, product, seller, endTime);
        auctions.add(auction);
        return auction;
    }

    public Auction createAuction(String auctionId, Product product, Seller seller,
            LocalDateTime endTime, double minimumBidIncrement) {
        Auction auction = new Auction(auctionId, product, seller, endTime, minimumBidIncrement);
        auctions.add(auction);
        return auction;
    }

    public Auction getAuction(String auctionId) throws AuctionNotFoundException {
        for (Auction auction : auctions) {
            if (auction.getAuctionId().equals(auctionId)) {
                return auction;
            }
        }
        throw new AuctionNotFoundException("Auction " + auctionId + " was not found.");
    }

    public List<User> getUsers() {
        return Collections.unmodifiableList(users);
    }

    public List<Product> getProducts() {
        return Collections.unmodifiableList(products);
    }

    public List<Auction> getAuctions() {
        return Collections.unmodifiableList(auctions);
    }
}
