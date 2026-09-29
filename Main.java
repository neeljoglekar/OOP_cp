import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

public class Main {
    private static final UserDAO USER_DAO = new UserDAO();

    public static void main(String[] args) {
        AuctionSystem auctionSystem = new AuctionSystem();
        demonstrateMinimumBidIncrement();
        demonstrateExpiredAuction();

        try (Scanner scanner = new Scanner(System.in)) {
            boolean running = true;
            while (running) {
                System.out.println("\n=== Online Auction Simulator ===");
                System.out.println("1. Register");
                System.out.println("2. Login");
                System.out.println("3. Exit");

                int choice = readMenuChoice(scanner, "Choose an option: ");
                switch (choice) {
                    case 1:
                        register(scanner);
                        break;
                    case 2:
                        login(scanner, auctionSystem);
                        break;
                    case 3:
                        running = false;
                        System.out.println("Goodbye.");
                        break;
                    default:
                        System.out.println("Invalid menu choice. Please enter 1, 2, or 3.");
                }
            }
        }
    }

    private static void register(Scanner scanner) {
        try {
            int userId = readInteger(scanner, "User ID: ");
            System.out.print("Name: ");
            String name = scanner.nextLine();
            System.out.print("Email: ");
            String email = scanner.nextLine();
            System.out.print("Password: ");
            char[] password = scanner.nextLine().toCharArray();
            System.out.print("Role (BUYER, SELLER, ADMIN): ");
            String role = scanner.nextLine();

            try {
                User registeredUser = USER_DAO.registerUser(userId, name, email, password, role);
                System.out.println("Registration successful for " + registeredUser.getRole() + ".");
            } finally {
                java.util.Arrays.fill(password, '\0');
            }
        } catch (SQLException | IllegalArgumentException exception) {
            System.out.println("Registration failed: " + exception.getMessage());
        }
    }

    private static void login(Scanner scanner, AuctionSystem auctionSystem) {
        System.out.print("Email: ");
        String email = scanner.nextLine();
        System.out.print("Password: ");
        char[] password = scanner.nextLine().toCharArray();

        User user;
        try {
            user = USER_DAO.login(email, password);
        } catch (SQLException exception) {
            System.out.println("Login failed: " + exception.getMessage());
            return;
        } finally {
            java.util.Arrays.fill(password, '\0');
        }

        if (user == null) {
            System.out.println("Login failed. Check your email and password.");
            return;
        }

        System.out.println("Welcome, " + user.getName() + "!");
        showRoleMenu(scanner, auctionSystem);
    }

    private static void showRoleMenu(Scanner scanner, AuctionSystem auctionSystem) {
        while (UserSession.isLoggedIn()) {
            User currentUser = UserSession.getCurrentUser();
            if (currentUser instanceof Buyer) {
                showBuyerMenu(scanner, auctionSystem, (Buyer) currentUser);
            } else if (currentUser instanceof Seller) {
                showSellerMenu(scanner, auctionSystem, (Seller) currentUser);
            } else if (currentUser instanceof Admin) {
                showAdminMenu(scanner, auctionSystem, (Admin) currentUser);
            } else {
                System.out.println("The current session has an unsupported user type.");
                UserSession.logout();
            }
        }
    }

    private static void showBuyerMenu(Scanner scanner, AuctionSystem system, Buyer buyer) {
        System.out.println("\n=== Buyer Menu ===");
        System.out.println("1. View Profile");
        System.out.println("2. Update Profile");
        System.out.println("3. View Auctions");
        System.out.println("4. Place Bid");
        System.out.println("5. Logout");

        switch (readMenuChoice(scanner, "Choose an option: ")) {
            case 1:
                viewProfile();
                break;
            case 2:
                updateProfile(scanner);
                break;
            case 3:
                viewAuctions(system);
                break;
            case 4:
                placeBid(scanner, system, buyer);
                break;
            case 5:
                logout();
                break;
            default:
                System.out.println("Invalid menu choice. Please choose an option from 1 to 5.");
        }
    }

    private static void showSellerMenu(Scanner scanner, AuctionSystem system, Seller seller) {
        System.out.println("\n=== Seller Menu ===");
        System.out.println("1. View Profile");
        System.out.println("2. Update Profile");
        System.out.println("3. Create Auction");
        System.out.println("4. View Auctions");
        System.out.println("5. Close Auction");
        System.out.println("6. Logout");

        switch (readMenuChoice(scanner, "Choose an option: ")) {
            case 1:
                viewProfile();
                break;
            case 2:
                updateProfile(scanner);
                break;
            case 3:
                createAuction(scanner, system, seller);
                break;
            case 4:
                viewAuctions(system);
                break;
            case 5:
                closeAuction(scanner, system);
                break;
            case 6:
                logout();
                break;
            default:
                System.out.println("Invalid menu choice. Please choose an option from 1 to 6.");
        }
    }

    private static void showAdminMenu(Scanner scanner, AuctionSystem system, Admin admin) {
        System.out.println("\n=== Admin Menu ===");
        System.out.println("1. View Profile");
        System.out.println("2. Update Profile");
        System.out.println("3. Monitor Auctions");
        System.out.println("4. View Auctions");
        System.out.println("5. Logout");

        switch (readMenuChoice(scanner, "Choose an option: ")) {
            case 1:
                viewProfile();
                break;
            case 2:
                updateProfile(scanner);
                break;
            case 3:
                try {
                    admin.monitorAuctions(system);
                } catch (UnauthorizedActionException exception) {
                    System.out.println("Operation denied: " + exception.getMessage());
                }
                break;
            case 4:
                viewAuctions(system);
                break;
            case 5:
                logout();
                break;
            default:
                System.out.println("Invalid menu choice. Please choose an option from 1 to 5.");
        }
    }

    private static void viewProfile() {
        if (!UserSession.isLoggedIn()) {
            System.out.println("Please log in before viewing a profile.");
            return;
        }

        try {
            User user = USER_DAO.viewCurrentProfile();
            System.out.println("User ID: " + user.getUserId());
            System.out.println("Name: " + user.getName());
            System.out.println("Email: " + user.getEmail());
            System.out.println("Role: " + user.getRole());
        } catch (SQLException | IllegalStateException | UserNotFoundException exception) {
            System.out.println("Could not view profile: " + exception.getMessage());
        }
    }

    private static void updateProfile(Scanner scanner) {
        if (!UserSession.isLoggedIn()) {
            System.out.println("Please log in before updating a profile.");
            return;
        }

        System.out.print("New name: ");
        String name = scanner.nextLine();
        System.out.print("New email: ");
        String email = scanner.nextLine();

        try {
            if (USER_DAO.updateCurrentProfile(name, email)) {
                System.out.println("Name and email updated.");
            } else {
                System.out.println("Profile was not updated. The email may already be in use.");
            }

            System.out.print("Change password too? (y/n): ");
            String answer = scanner.nextLine().trim();
            if (answer.equalsIgnoreCase("y")) {
                System.out.print("New password: ");
                char[] newPassword = scanner.nextLine().toCharArray();
                try {
                    if (USER_DAO.changeCurrentPassword(newPassword)) {
                        System.out.println("Password updated.");
                    } else {
                        System.out.println("Password was not updated.");
                    }
                } finally {
                    java.util.Arrays.fill(newPassword, '\0');
                }
            }
        } catch (SQLException | IllegalStateException | IllegalArgumentException
                | UserNotFoundException exception) {
            System.out.println("Profile update failed: " + exception.getMessage());
        }
    }

    private static void createAuction(Scanner scanner, AuctionSystem system, Seller seller) {
        System.out.print("Product ID: ");
        String productId = scanner.nextLine();
        System.out.print("Product name: ");
        String productName = scanner.nextLine();
        System.out.print("Product description: ");
        String description = scanner.nextLine();
        System.out.print("Product condition (for example, Good or Like New): ");
        String condition = scanner.nextLine();
        System.out.print("Auction ID: ");
        String auctionId = scanner.nextLine();
        LocalDateTime endTime = readEndTime(scanner);
        double minimumBidIncrement = readMinimumBidIncrement(scanner);

        Product product = new Product(productId, productName, description, condition);
        seller.listProduct(system, product);
        Auction auction = seller.createAuction(system, auctionId, product, endTime,
                minimumBidIncrement);
        System.out.println("Created " + auction + ".");
    }

    private static void viewAuctions(AuctionSystem system) {
        List<Auction> auctions = system.getAuctions();
        if (auctions.isEmpty()) {
            System.out.println("There are no auctions to display.");
            return;
        }

        for (Auction auction : auctions) {
            System.out.println(auction);
            Product product = auction.getProduct();
            System.out.println("  Description: " + product.getDescription());
            System.out.println("  Condition: " + product.getCondition());
            if (auction.getBids().isEmpty()) {
                System.out.println("  No bids yet.");
            } else {
                for (Bid bid : auction.getBids()) {
                    System.out.println("  " + bid);
                }
            }
            Bid highestBid = auction.getHighestValidBid();
            if (highestBid != null) {
                System.out.printf("  Highest bid: %.2f%n", highestBid.getAmount());
            }
            if (auction.isClosed()) {
                Buyer winner = auction.getWinner();
                System.out.println("  Winner: " + (winner == null ? "No winner" : winner.getName()));
            }
        }
    }

    private static void placeBid(Scanner scanner, AuctionSystem system, Buyer buyer) {
        System.out.print("Auction ID: ");
        String auctionId = scanner.nextLine();
        try {
            Auction auction = system.getAuction(auctionId);
            double amount = readBidAmount(scanner);
            Bid bid = buyer.placeBid(auction, amount);
            System.out.println("Bid placed: " + bid);
        } catch (AuctionNotFoundException | InvalidBidException | AuctionClosedException
                | InsufficientBidException exception) {
            System.out.println("Bid was not placed: " + exception.getMessage());
        }
    }

    private static void closeAuction(Scanner scanner, AuctionSystem system) {
        System.out.print("Auction ID to close: ");
        String auctionId = scanner.nextLine();
        try {
            Auction auction = system.getAuction(auctionId);
            auction.closeAuction();
            Buyer winner = auction.getWinner();
            System.out.println("Auction closed. Winner: "
                    + (winner == null ? "No winner" : winner.getName()));
        } catch (AuctionNotFoundException exception) {
            System.out.println("Could not close auction: " + exception.getMessage());
        }
    }

    private static void logout() {
        UserSession.logout();
        System.out.println("You have been logged out.");
    }

    private static int readMenuChoice(Scanner scanner, String prompt) {
        if (!scanner.hasNextLine()) {
            return 3;
        }
        System.out.print(prompt);
        String input = scanner.nextLine().trim();
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException exception) {
            return Integer.MIN_VALUE;
        }
    }

    private static int readInteger(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException exception) {
                System.out.println("Please enter a whole number.");
            }
        }
    }

    private static double readBidAmount(Scanner scanner) {
        while (true) {
            System.out.print("Bid amount: ");
            String input = scanner.nextLine().trim();
            try {
                return Double.parseDouble(input);
            } catch (NumberFormatException exception) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    private static LocalDateTime readEndTime(Scanner scanner) {
        while (true) {
            System.out.print("Auction end time (yyyy-MM-ddTHH:mm, local time): ");
            String input = scanner.nextLine().trim();
            try {
                return LocalDateTime.parse(input);
            } catch (DateTimeParseException exception) {
                System.out.println("Enter the end time in the shown format, for example 2026-10-05T18:30.");
            }
        }
    }

    private static double readMinimumBidIncrement(Scanner scanner) {
        while (true) {
            System.out.print("Minimum bid increment: ");
            String input = scanner.nextLine().trim();
            try {
                double increment = Double.parseDouble(input);
                if (!Double.isFinite(increment) || increment <= 0) {
                    System.out.println("The minimum bid increment must be a positive finite number.");
                    continue;
                }
                return increment;
            } catch (NumberFormatException exception) {
                System.out.println("Enter a valid number for the minimum bid increment.");
            }
        }
    }

    private static void demonstrateMinimumBidIncrement() {
        AuctionSystem demoSystem = new AuctionSystem();
        Seller seller = new Seller("DEMO-SELLER", "Demo Seller");
        Buyer firstBuyer = new Buyer("DEMO-BUYER-1", "Ari Buyer");
        Buyer secondBuyer = new Buyer("DEMO-BUYER-2", "Bea Buyer");
        Product product = new Product("DEMO-PRODUCT", "Vintage camera",
                "A working camera with minor signs of use.", "Good");
        seller.listProduct(demoSystem, product);
        Auction auction = seller.createAuction(demoSystem, "DEMO-MINIMUM-BID",
                product, LocalDateTime.now().plusHours(1), 50.00);

        System.out.println("Minimum bid increment demonstration (increment: 50.00):");
        demonstrateBid(firstBuyer, auction, 500.00, "First bid");
        demonstrateBid(secondBuyer, auction, 520.00, "Bid below increment");
        demonstrateBid(secondBuyer, auction, 549.00, "Bid below increment");
        demonstrateBid(secondBuyer, auction, 550.00, "Bid at required minimum");
        demonstrateBid(firstBuyer, auction, 600.00, "Bid above required minimum");
        demonstrateBid(secondBuyer, auction, Double.NaN, "NaN bid");

        auction.closeAuction();
        Buyer winner = auction.getWinner();
        System.out.println("Winner: " + (winner == null ? "No winner" : winner.getName()));
        if (winner != null) {
            winner.payForAuction(auction);
        }
        demonstrateBid(secondBuyer, auction, 650.00, "Bid after manual close");

        try {
            demoSystem.getAuction("MISSING-DEMO-AUCTION");
        } catch (AuctionNotFoundException exception) {
            System.out.println("Missing-auction demonstration: " + exception.getMessage());
        }
    }

    private static void demonstrateBid(Buyer buyer, Auction auction, double amount, String label) {
        try {
            Bid bid = buyer.placeBid(auction, amount);
            System.out.println(label + " accepted: " + bid);
            System.out.println("  Automatic bid timestamp: " + bid.getTimestamp());
        } catch (InvalidBidException | AuctionClosedException | InsufficientBidException exception) {
            System.out.println(label + " rejected: " + exception.getMessage());
        }
    }

    private static void demonstrateExpiredAuction() {
        Seller seller = new Seller("DEMO-SELLER", "Demo Seller");
        Buyer buyer = new Buyer("DEMO-BUYER", "Demo Buyer");
        Product product = new Product("DEMO-PRODUCT", "Demo item",
                "A small example used to demonstrate an expired auction.", "Good");
        Auction auction = new Auction("DEMO-EXPIRED", product, seller,
                LocalDateTime.now().minusMinutes(1));

        try {
            buyer.placeBid(auction, 10.00);
            System.out.println("Expired-auction demonstration failed: bid was accepted.");
        } catch (AuctionClosedException exception) {
            System.out.println("Expired-auction demonstration: bid rejected after end time.");
        } catch (InvalidBidException | InsufficientBidException exception) {
            System.out.println("Expired-auction demonstration failed: " + exception.getMessage());
        }
    }
}
