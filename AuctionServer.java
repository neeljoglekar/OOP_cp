import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lightweight HTTP API that delegates auction rules to the existing OOP classes. */
public class AuctionServer {
    public static final int DEFAULT_PORT = 8080;
    private static final int MAX_REQUEST_BYTES = 4096;
    private static final String JSON_NUMBER =
            "-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?";
    private static final Pattern BUYER_FIRST_BID_REQUEST = Pattern.compile(
            "^\\s*\\{\\s*\"buyerId\"\\s*:\\s*(0|[1-9][0-9]*)\\s*,"
                    + "\\s*\"amount\"\\s*:\\s*(" + JSON_NUMBER + ")\\s*}\\s*$");
    private static final Pattern AMOUNT_FIRST_BID_REQUEST = Pattern.compile(
            "^\\s*\\{\\s*\"amount\"\\s*:\\s*(" + JSON_NUMBER + ")\\s*,"
                    + "\\s*\"buyerId\"\\s*:\\s*(0|[1-9][0-9]*)\\s*}\\s*$");
    private static final Pattern AMOUNT_ONLY_BID_REQUEST = Pattern.compile(
            "^\\s*\\{\\s*\"amount\"\\s*:\\s*(" + JSON_NUMBER + ")\\s*}\\s*$");
    private static final Pattern AUCTION_CREATION_REQUEST = Pattern.compile(
            "^\\s*\\{\\s*\"auctionId\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,"
                    + "\\s*\"productId\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,"
                    + "\\s*\"productName\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,"
                    + "\\s*\"description\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,"
                    + "\\s*\"condition\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,"
                    + "\\s*\"endTime\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,"
                    + "\\s*\"minimumBidIncrement\"\\s*:\\s*(" + JSON_NUMBER + ")\\s*}\\s*$");

    private final AuctionSystem auctionSystem;
    private final UserDAO userDAO;
    private final RestSessionStore sessionStore = new RestSessionStore();
    private final HttpServer server;
    private final ExecutorService requestExecutor;

    public AuctionServer(AuctionSystem auctionSystem, UserDAO userDAO, int port)
            throws IOException {
        if (auctionSystem == null || userDAO == null) {
            throw new IllegalArgumentException("Auction system and user DAO are required.");
        }
        this.auctionSystem = auctionSystem;
        this.userDAO = userDAO;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        this.requestExecutor = Executors.newFixedThreadPool(4);
        this.server.setExecutor(requestExecutor);
        this.server.createContext("/api/", this::handleRequest);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
        requestExecutor.shutdown();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        try {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if ("/api/health".equals(path)) {
                if (!"GET".equalsIgnoreCase(method)) {
                    sendError(exchange, 405, "Use GET for this endpoint.");
                    return;
                }
                sendJson(exchange, 200,
                        "{\"success\":true,\"message\":\"Auction API is running.\"}");
                return;
            }

            if ("/api/login".equals(path)) {
                if (!"POST".equalsIgnoreCase(method)) {
                    sendError(exchange, 405, "Use POST for this endpoint.");
                    return;
                }
                login(exchange);
                return;
            }

            if ("/api/register".equals(path)) {
                if (!"POST".equalsIgnoreCase(method)) {
                    sendError(exchange, 405, "Use POST for this endpoint.");
                    return;
                }
                register(exchange);
                return;
            }

            if ("/api/profile".equals(path)) {
                if ("GET".equalsIgnoreCase(method)) {
                    viewProfile(exchange);
                    return;
                }
                if ("PUT".equalsIgnoreCase(method)) {
                    updateProfile(exchange);
                    return;
                }
                sendError(exchange, 405, "Use GET or PUT for this endpoint.");
                return;
            }

            if ("/api/logout".equals(path)) {
                if (!"POST".equalsIgnoreCase(method)) {
                    sendError(exchange, 405, "Use POST for this endpoint.");
                    return;
                }
                String token = readSessionToken(exchange);
                if (token == null || !sessionStore.invalidate(token)) {
                    sendError(exchange, 401, "A valid login session is required to log out.");
                    return;
                }
                sendJson(exchange, 200,
                        "{\"success\":true,\"message\":\"Logged out successfully.\"}");
                return;
            }

            if ("/api/auctions".equals(path)) {
                if ("GET".equalsIgnoreCase(method)) {
                    sendAuctionList(exchange);
                    return;
                }
                if ("POST".equalsIgnoreCase(method)) {
                    createAuction(exchange);
                    return;
                }
                sendError(exchange, 405, "Use GET or POST for this endpoint.");
                return;
            }

            String auctionPrefix = "/api/auctions/";
            if (path.startsWith(auctionPrefix)) {
                String route = path.substring(auctionPrefix.length());
                String bidSuffix = "/bids";
                if (route.endsWith(bidSuffix) && route.length() > bidSuffix.length()) {
                    if (!"POST".equalsIgnoreCase(method)) {
                        sendError(exchange, 405, "Use POST to place a bid.");
                        return;
                    }
                    String auctionId = route.substring(0, route.length() - bidSuffix.length());
                    if (auctionId.contains("/")) {
                        sendError(exchange, 404, "Endpoint was not found.");
                        return;
                    }
                    placeBid(exchange, auctionId);
                    return;
                }

                if (!route.isEmpty() && !route.contains("/")) {
                    if (!"GET".equalsIgnoreCase(method)) {
                        sendError(exchange, 405, "Use GET for this endpoint.");
                        return;
                    }
                    sendAuction(exchange, route);
                    return;
                }
            }

            sendError(exchange, 404, "Endpoint was not found.");
        } catch (AuctionNotFoundException | UserNotFoundException exception) {
            sendError(exchange, 404, exception.getMessage());
        } catch (InvalidBidException | InsufficientBidException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (AuctionClosedException exception) {
            sendError(exchange, 409, exception.getMessage());
        } catch (UnauthorizedActionException exception) {
            sendError(exchange, 403, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (SQLException exception) {
            sendError(exchange, 500, "The user lookup could not be completed.");
        } catch (RuntimeException exception) {
            sendError(exchange, 500, "The auction request could not be completed.");
        } finally {
            exchange.close();
        }
    }

    private void login(HttpExchange exchange) throws IOException, SQLException {
        LoginRequest request = readLoginRequest(exchange);
        char[] password = request.password.toCharArray();
        try {
            User user = userDAO.authenticate(request.email, password);
            if (user == null) {
                sendError(exchange, 401, "Email or password was incorrect.");
                return;
            }
            String sessionToken = sessionStore.createSession(user);
            sendJson(exchange, 200, "{\"success\":true,\"sessionToken\":"
                    + jsonString(sessionToken) + ",\"user\":"
                    + accountUserToJson(user) + "}");
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void register(HttpExchange exchange) throws IOException, SQLException {
        RegistrationRequest request = readRegistrationRequest(exchange);
        String role = request.role.trim().toUpperCase(Locale.ROOT);
        if (!"BUYER".equals(role) && !"SELLER".equals(role)) {
            throw new IllegalArgumentException("Public registration allows BUYER or SELLER only.");
        }

        char[] password = request.password.toCharArray();
        try {
            User user;
            try {
                user = userDAO.registerUser(request.userId, request.name, request.email,
                        password, role);
            } catch (SQLException exception) {
                if (isConstraintViolation(exception)) {
                    sendError(exchange, 409, "This user ID or email is already registered.");
                    return;
                }
                throw exception;
            }
            sendJson(exchange, 201, "{\"success\":true,\"message\":"
                    + jsonString("Registration successful. You can now log in.")
                    + ",\"user\":" + accountUserToJson(user) + "}");
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void viewProfile(HttpExchange exchange)
            throws IOException, SQLException, UserNotFoundException {
        User authenticatedUser = requireAuthenticatedUser(exchange);
        if (authenticatedUser == null) return;
        User profile = userDAO.viewProfile(authenticatedUser);
        sendJson(exchange, 200, "{\"success\":true,\"profile\":"
                + accountUserToJson(profile) + "}");
    }

    private void updateProfile(HttpExchange exchange)
            throws IOException, SQLException, UserNotFoundException {
        User authenticatedUser = requireAuthenticatedUser(exchange);
        if (authenticatedUser == null) return;
        ProfileRequest request = readProfileRequest(exchange);
        if (!userDAO.updateProfile(authenticatedUser, request.name, request.email)) {
            sendError(exchange, 409, "That email address is already used by another account.");
            return;
        }

        char[] password = request.password.toCharArray();
        try {
            if (password.length > 0) {
                userDAO.changePassword(authenticatedUser, password);
            }
        } finally {
            Arrays.fill(password, '\0');
        }

        User profile = userDAO.viewProfile(authenticatedUser);
        sendJson(exchange, 200, "{\"success\":true,\"message\":"
                + jsonString("Profile updated successfully.")
                + ",\"profile\":" + accountUserToJson(profile) + "}");
    }

    private User requireAuthenticatedUser(HttpExchange exchange) throws IOException {
        String token = readSessionToken(exchange);
        User user = token == null ? null : sessionStore.getUser(token);
        if (user == null) {
            sendError(exchange, 401, "A valid login session is required for this request.");
            return null;
        }
        return user;
    }

    private String readSessionToken(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || authorization.length() <= 7
                || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty() || token.contains(" ") || token.contains("\t")) {
            return null;
        }
        return token;
    }

    private boolean isConstraintViolation(SQLException exception) {
        String sqlState = exception.getSQLState();
        return sqlState != null && sqlState.startsWith("23");
    }

    private RegistrationRequest readRegistrationRequest(HttpExchange exchange)
            throws IOException {
        String body = readRequestBody(exchange);
        Pattern pattern = Pattern.compile(
                "^\\s*\\{\\s*\"userId\"\\s*:\\s*(0|[1-9][0-9]*)\\s*,"
                        + "\\s*\"name\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,"
                        + "\\s*\"email\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,"
                        + "\\s*\"password\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,"
                        + "\\s*\"role\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*}\\s*$");
        Matcher matcher = pattern.matcher(body);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Send userId, name, email, password, and role as JSON fields.");
        }
        try {
            int userId = Integer.parseInt(matcher.group(1));
            return new RegistrationRequest(userId,
                    decodeJsonString(matcher.group(2)),
                    decodeJsonString(matcher.group(3)),
                    decodeJsonString(matcher.group(4)),
                    decodeJsonString(matcher.group(5)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("User ID must be a valid integer.");
        }
    }

    private ProfileRequest readProfileRequest(HttpExchange exchange) throws IOException {
        String body = readRequestBody(exchange);
        Pattern pattern = Pattern.compile(
                "^\\s*\\{\\s*\"name\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,"
                        + "\\s*\"email\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
                        + "(?:\\s*,\\s*\"password\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\")?\\s*}\\s*$");
        Matcher matcher = pattern.matcher(body);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Send name and email, with an optional password, as JSON fields.");
        }
        String password = matcher.group(3) == null ? "" : decodeJsonString(matcher.group(3));
        return new ProfileRequest(decodeJsonString(matcher.group(1)),
                decodeJsonString(matcher.group(2)), password);
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        byte[] bodyBytes;
        try (InputStream requestBody = exchange.getRequestBody()) {
            bodyBytes = requestBody.readNBytes(MAX_REQUEST_BYTES + 1);
        }
        if (bodyBytes.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Request body is too large.");
        }
        return new String(bodyBytes, StandardCharsets.UTF_8);
    }

    private LoginRequest readLoginRequest(HttpExchange exchange) throws IOException {
        byte[] bodyBytes;
        try (InputStream requestBody = exchange.getRequestBody()) {
            bodyBytes = requestBody.readNBytes(MAX_REQUEST_BYTES + 1);
        }
        if (bodyBytes.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Request body is too large.");
        }
        String body = new String(bodyBytes, StandardCharsets.UTF_8);
        Pattern emailFirst = Pattern.compile(
                "^\\s*\\{\\s*\"email\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,"
                        + "\\s*\"password\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*}\\s*$");
        Pattern passwordFirst = Pattern.compile(
                "^\\s*\\{\\s*\"password\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,"
                        + "\\s*\"email\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*}\\s*$");
        Matcher matcher = emailFirst.matcher(body);
        boolean emailIsFirst = matcher.matches();
        if (!emailIsFirst) {
            matcher = passwordFirst.matcher(body);
            if (!matcher.matches()) {
                throw new IllegalArgumentException(
                        "Send JSON with string email and password fields.");
            }
        }
        String email = decodeJsonString(matcher.group(emailIsFirst ? 1 : 2));
        String password = decodeJsonString(matcher.group(emailIsFirst ? 2 : 1));
        if (email.trim().isEmpty() || password.isEmpty()) {
            throw new IllegalArgumentException("Email and password are required.");
        }
        return new LoginRequest(email, password);
    }

    private String decodeJsonString(String value) {
        StringBuilder decoded = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character != '\\') {
                decoded.append(character);
                continue;
            }
            if (++index >= value.length()) {
                throw new IllegalArgumentException("Login request contains an invalid escape.");
            }
            char escaped = value.charAt(index);
            switch (escaped) {
                case '"': decoded.append('"'); break;
                case '\\': decoded.append('\\'); break;
                case '/': decoded.append('/'); break;
                case 'b': decoded.append('\b'); break;
                case 'f': decoded.append('\f'); break;
                case 'n': decoded.append('\n'); break;
                case 'r': decoded.append('\r'); break;
                case 't': decoded.append('\t'); break;
                case 'u':
                    if (index + 4 >= value.length()) {
                        throw new IllegalArgumentException("Login request contains an invalid escape.");
                    }
                    try {
                        decoded.append((char) Integer.parseInt(value.substring(index + 1, index + 5), 16));
                    } catch (NumberFormatException exception) {
                        throw new IllegalArgumentException("Login request contains an invalid escape.");
                    }
                    index += 4;
                    break;
                default:
                    throw new IllegalArgumentException("Login request contains an invalid escape.");
            }
        }
        return decoded.toString();
    }

    private void sendAuctionList(HttpExchange exchange) throws IOException {
        List<Auction> auctions = auctionSystem.getAuctions();
        StringBuilder json = new StringBuilder("{\"success\":true,\"count\":")
                .append(auctions.size()).append(",\"auctions\":[");
        for (int index = 0; index < auctions.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append(auctionToJson(auctions.get(index)));
        }
        json.append("]}");
        sendJson(exchange, 200, json.toString());
    }

    private void createAuction(HttpExchange exchange) throws IOException {
        User currentUser = requireAuthenticatedUser(exchange);
        if (currentUser == null) return;
        if (!(currentUser instanceof Seller)) {
            sendError(exchange, 403, "Only a logged-in seller can create an auction.");
            return;
        }

        AuctionCreationRequest request = readAuctionCreationRequest(exchange);
        LocalDateTime endTime;
        double minimumBidIncrement;
        try {
            endTime = LocalDateTime.parse(request.endTime);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Auction end time must be a valid local date and time.");
        }
        if (!endTime.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Auction end time must be in the future.");
        }
        try {
            minimumBidIncrement = Double.parseDouble(request.minimumBidIncrement);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Minimum bid increment must be a positive finite number.");
        }
        if (!Double.isFinite(minimumBidIncrement) || minimumBidIncrement <= 0) {
            throw new IllegalArgumentException("Minimum bid increment must be a positive finite number.");
        }

        Product product = new Product(request.productId, request.productName,
                request.description, request.condition);
        Seller seller = (Seller) currentUser;
        Auction auction;
        synchronized (auctionSystem) {
            try {
                auctionSystem.getAuction(request.auctionId);
                sendError(exchange, 409, "Auction ID already exists.");
                return;
            } catch (AuctionNotFoundException expected) {
                // The ID is available. The system also checks uniqueness when creating it.
            }
            try {
                auction = seller.createAuction(auctionSystem, request.auctionId, product,
                        endTime, minimumBidIncrement);
            } catch (IllegalArgumentException exception) {
                if ("Auction ID already exists.".equals(exception.getMessage())) {
                    sendError(exchange, 409, exception.getMessage());
                    return;
                }
                throw exception;
            }
            seller.listProduct(auctionSystem, product);
        }
        sendJson(exchange, 201, "{\"success\":true,\"message\":"
                + jsonString("Auction created successfully.") + ",\"auction\":"
                + auctionToJson(auction) + "}");
    }

    private AuctionCreationRequest readAuctionCreationRequest(HttpExchange exchange)
            throws IOException {
        String body = readRequestBody(exchange);
        Matcher matcher = AUCTION_CREATION_REQUEST.matcher(body);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Send auctionId, productId, productName, description, condition, endTime, and minimumBidIncrement as JSON fields.");
        }
        String[] fields = new String[6];
        for (int index = 0; index < fields.length; index++) {
            fields[index] = decodeJsonString(matcher.group(index + 1)).trim();
            if (fields[index].isEmpty()) {
                throw new IllegalArgumentException("All auction and product text fields are required.");
            }
        }
        return new AuctionCreationRequest(fields[0], fields[1], fields[2], fields[3],
                fields[4], fields[5], matcher.group(7));
    }

    private void sendAuction(HttpExchange exchange, String auctionId)
            throws IOException, AuctionNotFoundException {
        Auction auction = auctionSystem.getAuction(auctionId);
        sendJson(exchange, 200, "{\"success\":true,\"auction\":"
                + auctionToJson(auction) + "}");
    }

    private void placeBid(HttpExchange exchange, String auctionId)
            throws IOException, AuctionNotFoundException,
            UnauthorizedActionException, InvalidBidException, AuctionClosedException,
            InsufficientBidException {
        User authenticatedUser = requireAuthenticatedUser(exchange);
        if (authenticatedUser == null) return;
        Buyer buyer = requireBuyer(authenticatedUser);
        BidRequest request = readBidRequest(exchange);
        Auction auction = auctionSystem.getAuction(auctionId);

        // Auction owns validation, expiry, increment rules, synchronization, and recording.
        Bid bid = auction.placeBid(buyer, request.amount);
        String json = "{\"success\":true,\"message\":\"Bid placed.\",\"bid\":"
                + bidToJson(bid) + ",\"auction\":" + auctionToJson(auction) + "}";
        sendJson(exchange, 201, json);
    }

    private BidRequest readBidRequest(HttpExchange exchange) throws IOException {
        byte[] bodyBytes;
        try (InputStream requestBody = exchange.getRequestBody()) {
            bodyBytes = requestBody.readNBytes(MAX_REQUEST_BYTES + 1);
        }
        if (bodyBytes.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Request body is too large.");
        }

        String body = new String(bodyBytes, StandardCharsets.UTF_8);
        Matcher matcher = BUYER_FIRST_BID_REQUEST.matcher(body);
        boolean buyerFirst = matcher.matches();
        boolean amountOnly = false;
        if (!buyerFirst) {
            matcher = AMOUNT_FIRST_BID_REQUEST.matcher(body);
            if (!matcher.matches()) {
                matcher = AMOUNT_ONLY_BID_REQUEST.matcher(body);
                amountOnly = matcher.matches();
            }
            if (!matcher.matches()) {
                throw new IllegalArgumentException(
                        "Send JSON with a numeric amount.");
            }
        }

        String buyerIdText = amountOnly ? null : (buyerFirst ? matcher.group(1) : matcher.group(2));
        String amountText = amountOnly ? matcher.group(1) : (buyerFirst ? matcher.group(2) : matcher.group(1));
        try {
            // Older clients may still send buyerId; it is syntax-checked but never trusted.
            if (buyerIdText != null && Integer.parseInt(buyerIdText) <= 0) {
                throw new IllegalArgumentException("buyerId must be a positive integer.");
            }
            double amount = Double.parseDouble(amountText);
            return new BidRequest(amount);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Bid amount is outside the supported range.");
        }
    }

    private Buyer requireBuyer(User user) throws UnauthorizedActionException {
        if (!(user instanceof Buyer)) {
            throw new UnauthorizedActionException("Only a buyer can place an auction bid.");
        }
        return (Buyer) user;
    }

    private String auctionToJson(Auction auction) {
        Product product = auction.getProduct();
        Bid highestBid = auction.getHighestValidBid();
        Buyer winner = auction.getWinner();
        StringBuilder json = new StringBuilder("{")
                .append("\"auctionId\":").append(jsonString(auction.getAuctionId()))
                .append(",\"product\":{")
                .append("\"productId\":").append(jsonString(product.getProductId()))
                .append(",\"name\":").append(jsonString(product.getName()))
                .append(",\"description\":").append(jsonString(product.getDescription()))
                .append(",\"condition\":").append(jsonString(product.getCondition()))
                .append("},\"currentHighestBid\":")
                .append(highestBid == null ? "null" : bidToJson(highestBid))
                .append(",\"minimumBidIncrement\":")
                .append(Double.toString(auction.getMinimumBidIncrement()))
                .append(",\"endTime\":").append(jsonString(auction.getEndTime().toString()))
                .append(",\"status\":").append(jsonString(auction.getStatus()))
                .append(",\"winner\":").append(winner == null ? "null" : userToJson(winner))
                .append(",\"bids\":[");

        List<Bid> bids = auction.getBids();
        for (int index = 0; index < bids.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append(bidToJson(bids.get(index)));
        }
        return json.append("]}").toString();
    }

    private String bidToJson(Bid bid) {
        return "{\"buyerId\":" + jsonString(bid.getBidder().getUserId())
                + ",\"buyerName\":" + jsonString(bid.getBidder().getName())
                + ",\"amount\":" + Double.toString(bid.getAmount())
                + ",\"timestamp\":" + jsonString(bid.getTimestamp().toString()) + "}";
    }

    private String userToJson(User user) {
        return "{\"userId\":" + jsonString(user.getUserId())
                + ",\"name\":" + jsonString(user.getName()) + "}";
    }

    private String accountUserToJson(User user) {
        return "{\"userId\":" + jsonString(user.getUserId())
                + ",\"name\":" + jsonString(user.getName())
                + ",\"email\":" + jsonString(user.getEmail())
                + ",\"role\":" + jsonString(user.getRole()) + "}";
    }

    private String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
            }
        }
        return escaped.append('"').toString();
    }

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods",
                "GET, POST, PUT, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers",
                "Content-Type, Authorization");
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, "{\"success\":false,\"message\":"
                + jsonString(message) + "}");
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(response);
        }
    }

    private static AuctionSystem createDemoSystem() {
        AuctionSystem system = new AuctionSystem();
        Seller seller = new Seller("API-SELLER", "API Demo Seller");
        Product product = new Product("API-PRODUCT-1", "Demo camera",
                "Example product held in memory by the API server.", "Good");
        seller.listProduct(system, product);
        seller.createAuction(system, "API-AUCTION-1", product,
                LocalDateTime.now().plusDays(1), 10.00);
        return system;
    }

    public static void main(String[] args) throws IOException {
        AuctionServer server = new AuctionServer(createDemoSystem(), new UserDAO(), DEFAULT_PORT);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "auction-server-shutdown"));
        server.start();
        System.out.println("Auction API listening at http://localhost:" + server.getPort());
        System.out.println("Example in-memory auction: API-AUCTION-1");
    }

    private static final class BidRequest {
        private final double amount;

        private BidRequest(double amount) {
            this.amount = amount;
        }
    }

    private static final class RestSessionStore {
        private static final SecureRandom RANDOM = new SecureRandom();
        private final Map<String, User> sessions = new ConcurrentHashMap<>();

        private String createSession(User user) {
            byte[] tokenBytes = new byte[32];
            String token;
            do {
                RANDOM.nextBytes(tokenBytes);
                token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
            } while (sessions.putIfAbsent(token, user) != null);
            Arrays.fill(tokenBytes, (byte) 0);
            return token;
        }

        private User getUser(String token) {
            return sessions.get(token);
        }

        private boolean invalidate(String token) {
            return sessions.remove(token) != null;
        }
    }

    private static final class AuctionCreationRequest {
        private final String auctionId;
        private final String productId;
        private final String productName;
        private final String description;
        private final String condition;
        private final String endTime;
        private final String minimumBidIncrement;

        private AuctionCreationRequest(String auctionId, String productId, String productName,
                String description, String condition, String endTime, String minimumBidIncrement) {
            this.auctionId = auctionId;
            this.productId = productId;
            this.productName = productName;
            this.description = description;
            this.condition = condition;
            this.endTime = endTime;
            this.minimumBidIncrement = minimumBidIncrement;
        }
    }

    private static final class LoginRequest {
        private final String email;
        private final String password;

        private LoginRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }

    private static final class RegistrationRequest {
        private final int userId;
        private final String name;
        private final String email;
        private final String password;
        private final String role;

        private RegistrationRequest(int userId, String name, String email,
                String password, String role) {
            this.userId = userId;
            this.name = name;
            this.email = email;
            this.password = password;
            this.role = role;
        }
    }

    private static final class ProfileRequest {
        private final String name;
        private final String email;
        private final String password;

        private ProfileRequest(String name, String email, String password) {
            this.name = name;
            this.email = email;
            this.password = password;
        }
    }
}
