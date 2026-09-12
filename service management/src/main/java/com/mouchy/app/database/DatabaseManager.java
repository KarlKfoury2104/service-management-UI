package com.mouchy.app.database;

import com.mouchy.app.models.AdminUser;
import com.mouchy.app.models.ClientUser;
import com.mouchy.app.models.Service;
import com.mouchy.app.models.ServiceRequest;
import com.mouchy.app.models.User;
import com.mouchy.app.security.PasswordUtil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Handles all database operations using SQLite and JDBC.
 */
public class DatabaseManager {
    private static final String DB_URL = buildDatabaseUrl();
    private static final DateTimeFormatter DB_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> VALID_REQUEST_STATUSES =
            Set.of("PENDING", "IN_PROGRESS", "COMPLETED", "REJECTED");
    private static final Set<String> VALID_ROLES = Set.of("ADMIN", "CLIENT");



    /**
     * Stores the writable database in the user's application-data directory
     * instead of beside the program files. If an older mouchy.db exists in the
     * launch directory, it is copied once so existing user data is preserved.
     */
    private static String buildDatabaseUrl() {
        try {
            String localAppData = System.getenv("LOCALAPPDATA");
            Path appDirectory;

            if (localAppData != null && !localAppData.isBlank()) {
                appDirectory = Path.of(localAppData, "MouchyServicePortal");
            } else {
                appDirectory = Path.of(System.getProperty("user.home"), ".mouchy-service-portal");
            }

            Files.createDirectories(appDirectory);
            Path target = appDirectory.resolve("mouchy.db").toAbsolutePath();
            Path legacy = Path.of("mouchy.db").toAbsolutePath();

            if (!Files.exists(target) && Files.exists(legacy) && !legacy.equals(target)) {
                Files.copy(legacy, target);
                System.out.println("Migrated legacy database to: " + target);
            }

            return "jdbc:sqlite:" + target;
        } catch (IOException | RuntimeException e) {
            System.err.println("Could not initialize application-data database path. Falling back to local mouchy.db: " + e.getMessage());
            return "jdbc:sqlite:mouchy.db";
        }
    }
    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError("Failed to load SQLite JDBC driver: " + e.getMessage());
        }
    }

    /**
     * Opens a SQLite connection and enables foreign-key enforcement for it.
     * SQLite requires PRAGMA foreign_keys to be enabled on every connection.
     */
    private static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
            stmt.execute("PRAGMA busy_timeout = 5000");
        } catch (SQLException ex) {
            conn.close();
            throw ex;
        }
        return conn;
    }

    /**
     * Initializes database tables, applies lightweight migrations, and inserts
     * seed data when the database is empty.
     */
    public static void initializeDatabase() {
        String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "username TEXT UNIQUE NOT NULL," +
                "password TEXT NOT NULL," +
                "role TEXT NOT NULL," +
                "full_name TEXT NOT NULL," +
                "email TEXT NOT NULL," +
                "phone TEXT NOT NULL" +
                ");";

        String createServicesTable = "CREATE TABLE IF NOT EXISTS services (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "description TEXT NOT NULL," +
                "price REAL NOT NULL," +
                "category TEXT NOT NULL," +
                "active INTEGER NOT NULL DEFAULT 1" +
                ");";

        String createRequestsTable = "CREATE TABLE IF NOT EXISTS requests (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "client_id INTEGER NOT NULL," +
                "service_id INTEGER NOT NULL," +
                "title TEXT NOT NULL," +
                "description TEXT NOT NULL," +
                "status TEXT NOT NULL," +
                "price REAL NOT NULL," +
                "preferred_date TEXT NOT NULL," +
                "created_at TEXT NOT NULL," +
                "comments TEXT," +
                "FOREIGN KEY (client_id) REFERENCES users(id) ON DELETE CASCADE," +
                "FOREIGN KEY (service_id) REFERENCES services(id)" +
                ");";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(createUsersTable);
            stmt.execute(createServicesTable);
            ensureServiceActiveColumn(conn);
            stmt.execute(createRequestsTable);

            seedUsers(conn);
            removeLegacyDefaultClient(conn);
            normalizeLegacyDefaultAdminPhone(conn);
            migrateLegacyPlaintextPasswords(conn);
            seedServices(conn);

        } catch (SQLException | RuntimeException e) {
            System.err.println("Error initializing database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Existing project databases were created before the active flag existed.
     * Add it in place so deleting a service can safely archive it while keeping
     * historical requests intact.
     */
    private static void ensureServiceActiveColumn(Connection conn) throws SQLException {
        boolean found = false;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(services)")) {
            while (rs.next()) {
                if ("active".equalsIgnoreCase(rs.getString("name"))) {
                    found = true;
                    break;
                }
            }
        }

        if (!found) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE services ADD COLUMN active INTEGER NOT NULL DEFAULT 1");
            }
        }
    }

    private static void seedUsers(Connection conn) throws SQLException {
        String checkUsers = "SELECT COUNT(*) FROM users";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(checkUsers)) {
            if (rs.next() && rs.getInt(1) == 0) {
                String insertUser = "INSERT INTO users (username, password, role, full_name, email, phone) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertUser)) {
                    pstmt.setString(1, "admin");
                    pstmt.setString(2, PasswordUtil.hashPassword("admin123"));
                    pstmt.setString(3, "ADMIN");
                    pstmt.setString(4, "System Administrator");
                    pstmt.setString(5, "admin@mouchy.com");
                    pstmt.setString(6, "15550199");
                    pstmt.executeUpdate();

                    System.out.println("Default admin seeded successfully.");
                }
            }
        }
    }

    /**
     * Removes the old demo client that previous project versions generated
     * automatically. The extra identity checks avoid deleting an unrelated
     * user who merely chose the username "client".
     */
    private static void removeLegacyDefaultClient(Connection conn) throws SQLException {
        String sql = "DELETE FROM users WHERE username = 'client' AND role = 'CLIENT' " +
                "AND full_name = 'John Doe' AND email = 'john.doe@gmail.com'";
        try (Statement stmt = conn.createStatement()) {
            int deleted = stmt.executeUpdate(sql);
            if (deleted > 0) {
                System.out.println("Removed legacy auto-generated client account.");
            }
        }
    }

    /**
     * Previous seed data used punctuation in the admin phone number. Keep the
     * built-in account consistent with the new digits-only phone rule.
     */
    private static void normalizeLegacyDefaultAdminPhone(Connection conn) throws SQLException {
        String sql = "UPDATE users SET phone = '15550199' WHERE username = 'admin' AND role = 'ADMIN' " +
                "AND email = 'admin@mouchy.com' AND phone = '+1 (555) 0199'";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    /**
     * Upgrades old databases that stored passwords as plain text. Because the
     * old value is the user's actual password, it can be hashed in-place while
     * preserving the same login credentials.
     */
    private static void migrateLegacyPlaintextPasswords(Connection conn) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        List<String> passwords = new ArrayList<>();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, password FROM users")) {
            while (rs.next()) {
                String stored = rs.getString("password");
                if (!PasswordUtil.isHashed(stored)) {
                    ids.add(rs.getInt("id"));
                    passwords.add(stored);
                }
            }
        }

        if (ids.isEmpty()) {
            return;
        }

        String update = "UPDATE users SET password = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(update)) {
            for (int i = 0; i < ids.size(); i++) {
                pstmt.setString(1, PasswordUtil.hashPassword(passwords.get(i)));
                pstmt.setInt(2, ids.get(i));
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    private static void seedServices(Connection conn) throws SQLException {
        String checkServices = "SELECT COUNT(*) FROM services";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(checkServices)) {
            if (rs.next() && rs.getInt(1) == 0) {
                String insertService = "INSERT INTO services (name, description, price, category) VALUES (?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertService)) {
                    pstmt.setString(1, "Modern Website Development");
                    pstmt.setString(2, "Complete custom Next.js/React website with responsive design, CMS integration, and SEO optimization.");
                    pstmt.setDouble(3, 2500.00);
                    pstmt.setString(4, "Development");
                    pstmt.executeUpdate();

                    pstmt.setString(1, "Premium Branding Package");
                    pstmt.setString(2, "Full brand identity design, including logos, visual brand guidelines, typography, and stationery designs.");
                    pstmt.setDouble(3, 1200.00);
                    pstmt.setString(4, "Design");
                    pstmt.executeUpdate();

                    pstmt.setString(1, "Social Media Marketing");
                    pstmt.setString(2, "Monthly management of 3 channels (Instagram, LinkedIn, Twitter) with 15 custom posts, captions, and ad campaigns.");
                    pstmt.setDouble(3, 850.00);
                    pstmt.setString(4, "Marketing");
                    pstmt.executeUpdate();

                    pstmt.setString(1, "SEO & Content Audit");
                    pstmt.setString(2, "Deep technical review of search engine ranking factors, site speed audit, and a comprehensive keyword plan.");
                    pstmt.setDouble(3, 600.00);
                    pstmt.setString(4, "Consulting");
                    pstmt.executeUpdate();

                    System.out.println("Default services seeded successfully.");
                }
            }
        }
    }

    // --- Authentication & User Operations ---

    public static User authenticate(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                String storedPassword = rs.getString("password");
                if (!PasswordUtil.verifyPassword(password, storedPassword)) {
                    return null;
                }

                int id = rs.getInt("id");
                String role = rs.getString("role");
                String fullName = rs.getString("full_name");
                String email = rs.getString("email");
                String phone = rs.getString("phone");

                // Do not keep the password/hash in the in-memory UI model.
                if ("ADMIN".equalsIgnoreCase(role)) {
                    return new AdminUser(id, username, "", fullName, email, phone);
                }
                if ("CLIENT".equalsIgnoreCase(role)) {
                    return new ClientUser(id, username, "", fullName, email, phone);
                }

                System.err.println("Unknown role for user '" + username + "': " + role);
                return null;
            }
        } catch (SQLException e) {
            System.err.println("Auth error: " + e.getMessage());
            return null;
        }
    }

    public static boolean usernameExists(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ? LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Username lookup error: " + e.getMessage());
            return false;
        }
    }

    public static boolean registerClient(String username, String password, String fullName, String email, String phone) {
        String sql = "INSERT INTO users (username, password, role, full_name, email, phone) VALUES (?, ?, 'CLIENT', ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, PasswordUtil.hashPassword(password));
            pstmt.setString(3, fullName);
            pstmt.setString(4, email);
            pstmt.setString(5, phone);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException | RuntimeException e) {
            System.err.println("Registration error: " + e.getMessage());
            return false;
        }
    }

    public static List<User> getAllUsers() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY role ASC, full_name ASC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String username = rs.getString("username");
                String role = rs.getString("role");
                String fullName = rs.getString("full_name");
                String email = rs.getString("email");
                String phone = rs.getString("phone");

                if ("ADMIN".equalsIgnoreCase(role)) {
                    list.add(new AdminUser(id, username, "", fullName, email, phone));
                } else if ("CLIENT".equalsIgnoreCase(role)) {
                    list.add(new ClientUser(id, username, "", fullName, email, phone));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching users: " + e.getMessage());
        }
        return list;
    }

    public static boolean updateUserProfile(int id, String fullName, String email, String phone) {
        String sql = "UPDATE users SET full_name = ?, email = ?, phone = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, fullName);
            pstmt.setString(2, email);
            pstmt.setString(3, phone);
            pstmt.setInt(4, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating profile: " + e.getMessage());
            return false;
        }
    }

    public static boolean createUser(String username, String password, String role, String fullName, String email, String phone) {
        String normalizedRole = role == null ? "" : role.toUpperCase();
        if (!VALID_ROLES.contains(normalizedRole)) {
            return false;
        }

        String sql = "INSERT INTO users (username, password, role, full_name, email, phone) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, PasswordUtil.hashPassword(password));
            pstmt.setString(3, normalizedRole);
            pstmt.setString(4, fullName);
            pstmt.setString(5, email);
            pstmt.setString(6, phone);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException | RuntimeException e) {
            System.err.println("Error creating user: " + e.getMessage());
            return false;
        }
    }

    public static boolean deleteUser(int id) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting user: " + e.getMessage());
            return false;
        }
    }

    // --- Services Operations (CRUD) ---

    /**
     * Returns only services currently offered to clients.
     */
    public static List<Service> getAllServices() {
        List<Service> list = new ArrayList<>();
        String sql = "SELECT * FROM services WHERE active = 1 ORDER BY category ASC, name ASC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Service(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getDouble("price"),
                        rs.getString("category")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching services: " + e.getMessage());
        }
        return list;
    }

    public static boolean isServiceActive(int id) {
        String sql = "SELECT 1 FROM services WHERE id = ? AND active = 1";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Error checking service availability: " + e.getMessage());
            return false;
        }
    }

    public static boolean addService(String name, String description, double price, String category) {
        String sql = "INSERT INTO services (name, description, price, category, active) VALUES (?, ?, ?, ?, 1)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, description);
            pstmt.setDouble(3, price);
            pstmt.setString(4, category);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error adding service: " + e.getMessage());
            return false;
        }
    }

    public static boolean updateService(int id, String name, String description, double price, String category) {
        String sql = "UPDATE services SET name = ?, description = ?, price = ?, category = ? WHERE id = ? AND active = 1";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, description);
            pstmt.setDouble(3, price);
            pstmt.setString(4, category);
            pstmt.setInt(5, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating service: " + e.getMessage());
            return false;
        }
    }

    /**
     * Removes a service from the active catalog without deleting its database
     * row, so historical client requests keep their service information.
     */
    public static boolean archiveService(int id) {
        String sql = "UPDATE services SET active = 0 WHERE id = ? AND active = 1";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error archiving service: " + e.getMessage());
            return false;
        }
    }

    /**
     * Backward-compatible alias for older controller code.
     */
    public static boolean deleteService(int id) {
        return archiveService(id);
    }

    // --- Service Requests / Bookings Operations ---

    public static List<ServiceRequest> getAllRequests() {
        List<ServiceRequest> list = new ArrayList<>();
        String sql = "SELECT r.*, u.full_name AS client_name, s.name AS service_name " +
                "FROM requests r " +
                "JOIN users u ON r.client_id = u.id " +
                "JOIN services s ON r.service_id = s.id " +
                "ORDER BY r.created_at DESC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRequest(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching all requests: " + e.getMessage());
        }
        return list;
    }

    public static List<ServiceRequest> getRequestsByClient(int clientId) {
        List<ServiceRequest> list = new ArrayList<>();
        String sql = "SELECT r.*, u.full_name AS client_name, s.name AS service_name " +
                "FROM requests r " +
                "JOIN users u ON r.client_id = u.id " +
                "JOIN services s ON r.service_id = s.id " +
                "WHERE r.client_id = ? " +
                "ORDER BY r.created_at DESC";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, clientId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRequest(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching client requests: " + e.getMessage());
        }
        return list;
    }

    private static ServiceRequest mapRequest(ResultSet rs) throws SQLException {
        return new ServiceRequest(
                rs.getInt("id"),
                rs.getInt("client_id"),
                rs.getString("client_name"),
                rs.getInt("service_id"),
                rs.getString("service_name"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getDouble("price"),
                rs.getString("preferred_date"),
                rs.getString("created_at"),
                rs.getString("comments")
        );
    }

    /**
     * Creates a request only for an active service and copies the current price
     * directly from the database. This prevents stale UI data from creating a
     * request for an archived service or with an outdated quote.
     */
    public static boolean createRequest(int clientId, int serviceId, String title, String description, String preferredDate) {
        String sql = "INSERT INTO requests (client_id, service_id, title, description, status, price, preferred_date, created_at, comments) " +
                "SELECT ?, s.id, ?, ?, 'PENDING', s.price, ?, ?, '' " +
                "FROM services s WHERE s.id = ? AND s.active = 1";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, clientId);
            pstmt.setString(2, title);
            pstmt.setString(3, description);
            pstmt.setString(4, preferredDate);
            pstmt.setString(5, LocalDateTime.now().format(DB_TIMESTAMP_FORMATTER));
            pstmt.setInt(6, serviceId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error creating booking request: " + e.getMessage());
            return false;
        }
    }

    public static boolean updateRequestStatus(int requestId, String status, String comments) {
        String normalizedStatus = status == null ? "" : status.toUpperCase();
        if (!VALID_REQUEST_STATUSES.contains(normalizedStatus)) {
            return false;
        }

        String sql = "UPDATE requests SET status = ?, comments = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, normalizedStatus);
            pstmt.setString(2, comments == null ? "" : comments);
            pstmt.setInt(3, requestId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating request status: " + e.getMessage());
            return false;
        }
    }
}
