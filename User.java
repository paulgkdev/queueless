import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * ABSTRACTION: User is an abstract class. You cannot create a plain "User",
 * only one of its kinds (Administrator, ClinicStaff, FacultyStaff).
 * Students do not need an account.
 */
public abstract class User {

    /** The two service desks. Each ticket belongs to one desk. */
    public enum Department {
        CLINIC("Clinic"),
        FACULTY("Faculty Office");

        private final String label;

        Department(String label) { this.label = label; }

        public String getLabel() { return label; }

        @Override
        public String toString() { return label; }
    }

    // ENCAPSULATION: all data is private, it is only reachable through methods.
    private final String username;
    private final String fullName;
    private final String passwordHash;   // the real password is never stored

    protected User(String username, String fullName, String password) {
        this.username = username;
        this.fullName = fullName;
        this.passwordHash = hash(password);
    }

    // Scrambles the password (SHA-256) so it is not stored as plain text.
    private static String hash(String password) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean checkPassword(String password) { return passwordHash.equals(hash(password)); }

    public String getUsername() { return username; }
    public String getFullName() { return fullName; }

    // ABSTRACT METHODS: every kind of user must answer these in its own way.
    public abstract String getTitle();
    public abstract Department[] getDesks();      // the desks this user may serve

    // ===================== INHERITANCE + POLYMORPHISM =====================
    // Each subclass extends User and overrides the abstract methods.

    /** The administrator can serve either desk. */
    public static class Administrator extends User {
        public Administrator(String username, String fullName, String password) {
            super(username, fullName, password);
        }
        @Override public String getTitle() { return "Administrator"; }
        @Override public Department[] getDesks() { return new Department[] { Department.CLINIC, Department.FACULTY }; }
    }

    /** Clinic staff serve the clinic only. */
    public static class ClinicStaff extends User {
        public ClinicStaff(String username, String fullName, String password) {
            super(username, fullName, password);
        }
        @Override public String getTitle() { return "Clinic Staff"; }
        @Override public Department[] getDesks() { return new Department[] { Department.CLINIC }; }
    }

    /** Faculty members serve the faculty office only. */
    public static class FacultyStaff extends User {
        public FacultyStaff(String username, String fullName, String password) {
            super(username, fullName, password);
        }
        @Override public String getTitle() { return "Faculty Member"; }
        @Override public Department[] getDesks() { return new Department[] { Department.FACULTY }; }
    }
}
