import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/** A staff account (Faculty Member, Clinic Staff, or Administrator). Students do not need an account. */
public class User {
    public enum Role {
        ADMIN("Administrator", "All departments"),
        FACULTY("Faculty Member", "Faculty Office"),
        CLINIC("Clinic Staff", "Clinic");

        private final String label;
        private final String departmentName;

        Role(String label, String departmentName) {
            this.label = label;
            this.departmentName = departmentName;
        }

        public String getDepartmentName() { return departmentName; }

        @Override
        public String toString() { return label; }
    }

    private final String username;
    private String fullName;
    private Role role;
    private boolean active;
    private String passwordHash;

    public User(String username, String fullName, Role role, boolean active, String passwordHash) {
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.active = active;
        this.passwordHash = passwordHash;
    }

    /** Passwords are never stored as plain text. */
    public static String hash(String password) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest(("queue-salt:" + password).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean checkPassword(String password) { return passwordHash.equals(hash(password)); }
    public void setPassword(String password) { this.passwordHash = hash(password); }

    public String toLine() {
        return String.join(",", username, passwordHash, fullName.replace(",", " "), role.name(), "" + active);
    }

    public static User fromLine(String line) {
        String[] f = line.split(",", -1);
        return new User(f[0], f[2], Role.valueOf(f[3]), Boolean.parseBoolean(f[4]), f[1]);
    }

    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public void setFullName(String n) { this.fullName = n; }
    public Role getRole() { return role; }
    public void setRole(Role r) { this.role = r; }
    public boolean isActive() { return active; }
    public void setActive(boolean a) { this.active = a; }
    public boolean isAdmin() { return role == Role.ADMIN; }
}
