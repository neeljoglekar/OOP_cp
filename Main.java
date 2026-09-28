public class Main {
    public static void main(String[] args) {
        AuctionSystem system = new AuctionSystem();

        Seller seller = new Seller("S1", "Sam Seller");
        Buyer buyerOne = new Buyer("B1", "Ari Buyer");
        Buyer buyerTwo = new Buyer("B2", "Bea Buyer");
        Admin admin = new Admin("A1", "Alex Admin");

        system.addUser(seller);
        system.addUser(buyerOne);
        system.addUser(buyerTwo);
        system.addUser(admin);

        User[] participants = {seller, buyerOne, buyerTwo, admin};
        System.out.println("Participants:");
        for (User participant : participants) {
            System.out.println("- " + participant);
        }

        Product product = new Product("P1", "Vintage camera");
        seller.listProduct(system, product);
        Auction auction = seller.createAuction(system, "AU1", product);

        try {
            buyerOne.placeBid(auction, 100.00);
            buyerTwo.placeBid(auction, 150.00);
            buyerOne.placeBid(auction, 200.00);

            System.out.println("Bids:");
            for (Bid bid : auction.getBids()) {
                System.out.println("- " + bid);
            }
            System.out.printf("Highest bid: %.2f%n", auction.getHighestValidBid().getAmount());

            try {
                buyerTwo.placeBid(auction, 175.00);
            } catch (InvalidBidException exception) {
                System.out.println("Invalid bid handled: " + exception.getMessage());
            }

            auction.closeAuction();
            Buyer winner = auction.getWinner();
            System.out.println("Winner: " + (winner == null ? "No winner" : winner.getName()));
            if (winner != null) {
                winner.payForAuction(auction);
            }

            try {
                buyerTwo.placeBid(auction, 250.00);
            } catch (AuctionClosedException exception) {
                System.out.println("Closed auction handled: " + exception.getMessage());
            }

            admin.monitorAuctions(system);
        } catch (InvalidBidException | AuctionClosedException exception) {
            System.out.println("Auction operation failed: " + exception.getMessage());
        }

        try {
            system.getAuction("MISSING-AUCTION");
        } catch (AuctionNotFoundException exception) {
            System.out.println("Missing auction handled: " + exception.getMessage());
        }
    }
}
