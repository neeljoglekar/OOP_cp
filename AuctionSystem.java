import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public synchronized void addProduct(Product product) {
        products.add(product);
    }

    /** Adds a product and auction after their database transaction has committed. */
    public synchronized void addPersistedAuction(Product product, Auction auction) {
        if (product == null || auction == null || auction.getProduct() == null
                || !product.getProductId().equals(auction.getProduct().getProductId())) {
            throw new IllegalArgumentException("Matching product and auction are required.");
        }
        ensureAuctionIdIsAvailable(auction.getAuctionId());
        ensureProductIdIsAvailable(product.getProductId());
        products.add(product);
        auctions.add(auction);
    }

    /** Loads a complete database snapshot before the server starts accepting requests. */
    public synchronized void loadPersistedData(List<Product> loadedProducts,
            List<Auction> loadedAuctions) {
        if (loadedProducts == null || loadedAuctions == null) {
            throw new IllegalArgumentException("Persisted product and auction lists are required.");
        }

        Set<String> productIds = new HashSet<>();
        for (Product product : products) productIds.add(product.getProductId());
        for (Product product : loadedProducts) {
            if (product == null || product.getProductId() == null
                    || !productIds.add(product.getProductId())) {
                throw new IllegalArgumentException("Persisted product IDs must be unique and valid.");
            }
        }

        Set<String> auctionIds = new HashSet<>();
        for (Auction auction : auctions) auctionIds.add(auction.getAuctionId());
        for (Auction auction : loadedAuctions) {
            if (auction == null || auction.getAuctionId() == null
                    || !auctionIds.add(auction.getAuctionId())) {
                throw new IllegalArgumentException("Persisted auction IDs must be unique and valid.");
            }
            if (auction.getProduct() == null
                    || !productIds.contains(auction.getProduct().getProductId())) {
                throw new IllegalArgumentException("Every persisted auction must reference a loaded product.");
            }
        }

        products.addAll(loadedProducts);
        auctions.addAll(loadedAuctions);
    }

    /** Replaces an auction with its committed database snapshot, or adds it if newly visible. */
    public synchronized void replacePersistedAuction(Auction persistedAuction) {
        if (persistedAuction == null || persistedAuction.getAuctionId() == null
                || persistedAuction.getProduct() == null) {
            throw new IllegalArgumentException("A persisted auction with a product is required.");
        }
        String auctionId = persistedAuction.getAuctionId();
        for (int index = 0; index < auctions.size(); index++) {
            if (auctionId.equals(auctions.get(index).getAuctionId())) {
                if (!containsProductId(persistedAuction.getProduct().getProductId())) {
                    products.add(persistedAuction.getProduct());
                }
                auctions.set(index, persistedAuction);
                return;
            }
        }
        if (!containsProductId(persistedAuction.getProduct().getProductId())) {
            products.add(persistedAuction.getProduct());
        }
        auctions.add(persistedAuction);
    }

    public synchronized boolean containsProductId(String productId) {
        for (Product product : products) {
            if (product.getProductId().equals(productId)) return true;
        }
        return false;
    }

    public synchronized boolean containsAuctionId(String auctionId) {
        for (Auction auction : auctions) {
            if (auction.getAuctionId().equals(auctionId)) return true;
        }
        return false;
    }

    public Auction createAuction(String auctionId, Product product, Seller seller) {
        return createAuction(auctionId, product, seller, LocalDateTime.MAX);
    }

    public synchronized Auction createAuction(String auctionId, Product product, Seller seller,
            LocalDateTime endTime) {
        ensureAuctionIdIsAvailable(auctionId);
        Auction auction = new Auction(auctionId, product, seller, endTime);
        auctions.add(auction);
        return auction;
    }

    public synchronized Auction createAuction(String auctionId, Product product, Seller seller,
            LocalDateTime endTime, double minimumBidIncrement) {
        ensureAuctionIdIsAvailable(auctionId);
        Auction auction = new Auction(auctionId, product, seller, endTime, minimumBidIncrement);
        auctions.add(auction);
        return auction;
    }

    private void ensureAuctionIdIsAvailable(String auctionId) {
        if (auctionId == null || auctionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Auction ID is required.");
        }
        for (Auction auction : auctions) {
            if (auctionId.equals(auction.getAuctionId())) {
                throw new IllegalArgumentException("Auction ID already exists.");
            }
        }
    }

    private void ensureProductIdIsAvailable(String productId) {
        if (productId == null || productId.trim().isEmpty()) {
            throw new IllegalArgumentException("Product ID is required.");
        }
        if (containsProductId(productId)) {
            throw new IllegalArgumentException("Product ID already exists.");
        }
    }

    public synchronized Auction getAuction(String auctionId) throws AuctionNotFoundException {
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

    public synchronized List<Auction> getAuctions() {
        return Collections.unmodifiableList(new ArrayList<>(auctions));
    }
}
