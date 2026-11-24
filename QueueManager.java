import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The "brain" of the system: accounts, tickets, priority queue, departments (Clinic / Faculty Office),
 * counters, settings, and reports.
 */
public class QueueManager {
    /** true = keep data between runs (saved in ./data).  false = start fresh every time. */
    private static final boolean SAVE_DATA = false;

    private static final Path DIR = Paths.get("data");
    private static final Path USERS_FILE = DIR.resolve("users.csv");
    private static final Path TICKETS_FILE = DIR.resolve("tickets.csv");
    private static final Path SETTINGS_FILE = DIR.resolve("settings.properties");

    // Emergency first, then Priority lane, then Regular; same level = first come, first served.
    private static final Comparator<Ticket> ORDER = Comparator
            .comparing(Ticket::getPriority)
            .thenComparing(Ticket::getArrival)
            .thenComparingInt(Ticket::getSequence);

    private final List<User> users = new ArrayList<>();
    private final List<Ticket> tickets = new ArrayList<>();
    private final List<String[]> services = new ArrayList<>();   // each item = {name, prefix, DEPARTMENT}
    private String organization = "University Student Services";
    private int clinicCounters = 1;
    private int facultyCounters = 2;
    private boolean priorityEnabled = true;

    public QueueManager() {
        if (SAVE_DATA) {
            loadSettings();
            loadUsers();
            loadTickets();
        }
        if (services.isEmpty()) {
            services.add(new String[]{"Clinic Visit", "C", "CLINIC"});
            services.add(new String[]{"Student Inquiry", "I", "FACULTY"});
            services.add(new String[]{"Document Request", "D", "FACULTY"});
            services.add(new String[]{"Administrative Services", "A", "FACULTY"});
        }
        if (users.isEmpty()) {
            users.add(new User("admin", "System Administrator", User.Role.ADMIN, true, User.hash("admin123")));
            saveUsers();
        }
        closeOldTickets();
    }

    // ===================== LOGIN & ACCOUNTS =====================
    public User login(String username, String password) {
        for (User u : users)
            if (u.getUsername().equalsIgnoreCase(username) && u.isActive() && u.checkPassword(password)) return u;
        return null;
    }

    public List<User> getUsers() { return users; }

    public void addUser(String username, String fullName, String password, User.Role role) {
        if (!username.matches("[A-Za-z0-9_]{3,20}"))
            throw new IllegalArgumentException("Username must be 3-20 letters, numbers or _.");
        if (fullName.isBlank()) throw new IllegalArgumentException("Full name is required.");
        if (password.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters.");
        if (findUser(username) != null) throw new IllegalArgumentException("Username already exists.");
        users.add(new User(username, fullName, role, true, User.hash(password)));
        saveUsers();
    }

    public void resetPassword(String username, String newPassword) {
        if (newPassword.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters.");
        requireUser(username).setPassword(newPassword);
        saveUsers();
    }

    public void toggleActive(String username) {
        User u = requireUser(username);
        if (u.isAdmin() && u.isActive() && activeAdmins() <= 1)
            throw new IllegalArgumentException("You need at least one active administrator.");
        u.setActive(!u.isActive());
        saveUsers();
    }

    public void deleteUser(String username, String currentUsername) {
        User u = requireUser(username);
        if (u.getUsername().equalsIgnoreCase(currentUsername))
            throw new IllegalArgumentException("You cannot delete your own account.");
        if (u.isAdmin() && u.isActive() && activeAdmins() <= 1)
            throw new IllegalArgumentException("You need at least one active administrator.");
        users.remove(u);
        saveUsers();
    }

    private int activeAdmins() {
        int n = 0;
        for (User u : users) if (u.isAdmin() && u.isActive()) n++;
        return n;
    }

    private User findUser(String name) {
        for (User u : users) if (u.getUsername().equalsIgnoreCase(name)) return u;
        return null;
    }

    private User requireUser(String name) {
        User u = findUser(name);
        if (u == null) throw new IllegalArgumentException("User not found.");
        return u;
    }

    // ===================== DEPARTMENTS & COUNTERS =====================
    // Counters 1..clinicCounters belong to the Clinic; the rest belong to the Faculty Office.
    public int getTotalCounters() { return clinicCounters + facultyCounters; }

    public User.Role deptOfCounter(int counter) {
        return counter <= clinicCounters ? User.Role.CLINIC : User.Role.FACULTY;
    }

    public String counterLabel(int counter) {
        return counter <= clinicCounters ? "Clinic " + counter : "Office " + (counter - clinicCounters);
    }

    /** First and last counter number this role may use. */
    public int[] counterRange(User.Role role) {
        if (role == User.Role.CLINIC) return new int[]{1, clinicCounters};
        if (role == User.Role.FACULTY) return new int[]{clinicCounters + 1, getTotalCounters()};
        return new int[]{1, getTotalCounters()};
    }

    private int countersOf(User.Role dept) { return dept == User.Role.CLINIC ? clinicCounters : facultyCounters; }

    // ===================== TICKETS =====================
    /** Indexes of services that belong to a department (null = all services). */
    public List<Integer> serviceIndexes(User.Role dept) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < services.size(); i++)
            if (dept == null || dept == User.Role.ADMIN || User.Role.valueOf(services.get(i)[2]) == dept) list.add(i);
        return list;
    }

    public Ticket issueTicket(int serviceIndex, String clientType, String idNumber, String name,
                              Ticket.Priority priority) {
        String[] svc = services.get(serviceIndex);
        if (!priorityEnabled && priority == Ticket.Priority.PRIORITY) priority = Ticket.Priority.REGULAR;
        LocalDate today = LocalDate.now();
        int seq = 1;
        for (Ticket t : tickets)
            if (t.getNumber().startsWith(svc[1] + "-") && t.getArrival().toLocalDate().equals(today))
                seq = Math.max(seq, t.getSequence() + 1);
        String number = svc[1] + "-" + String.format("%03d", seq);
        Ticket t = new Ticket(seq, number, svc[0], User.Role.valueOf(svc[2]), clientType,
                idNumber.isBlank() ? "-" : idNumber.trim(), name.isBlank() ? "Walk-in" : name.trim(),
                priority, LocalDateTime.now());
        tickets.add(t);
        saveTickets();
        return t;
    }

    /** Only clinic staff (and admins) can create emergency tickets, and only for clinic services. */
    public Ticket issueEmergency(User by, int serviceIndex, String name) {
        if (by.getRole() == User.Role.FACULTY)
            throw new IllegalStateException("Only clinic staff can create emergency tickets.");
        if (User.Role.valueOf(services.get(serviceIndex)[2]) != User.Role.CLINIC)
            throw new IllegalStateException("Emergency tickets are for clinic services only.");
        return issueTicket(serviceIndex, "Walk-in", "-", name, Ticket.Priority.EMERGENCY);
    }

    /** Calls the next ticket of this counter's department. serviceIndex = -1 means any service. */
    public Ticket callNext(int counter, User staff, int serviceIndex) {
        User.Role dept = deptOfCounter(counter);
        if (!staff.isAdmin() && staff.getRole() != dept)
            throw new IllegalStateException("You can only use " + staff.getRole().getDepartmentName() + " counters.");
        Ticket cur = getServingAt(counter);
        if (cur != null) throw new IllegalStateException("Counter is still serving " + cur.getNumber()
                + ". Complete it or mark it no-show first.");
        final String svcName = serviceIndex < 0 ? null : services.get(serviceIndex)[0];
        Ticket next = getWaiting(dept).stream()
                .filter(t -> svcName == null || t.getService().equals(svcName))
                .findFirst().orElse(null);
        if (next == null) return null;
        next.setStatus(Ticket.Status.SERVING);
        next.setCalled(LocalDateTime.now());
        next.setCounter(counter);
        next.setServedBy(staff.getUsername());
        saveTickets();
        return next;
    }

    public Ticket complete(int counter) { return finish(counter, Ticket.Status.COMPLETED); }
    public Ticket noShow(int counter) { return finish(counter, Ticket.Status.NO_SHOW); }

    private Ticket finish(int counter, Ticket.Status status) {
        Ticket t = getServingAt(counter);
        if (t == null) throw new IllegalStateException("This counter is not serving anyone.");
        t.setStatus(status);
        t.setCompleted(LocalDateTime.now());
        saveTickets();
        return t;
    }

    private boolean canManage(User by, Ticket t) { return by.isAdmin() || by.getRole() == t.getDepartment(); }

    public void setPriority(Ticket t, Ticket.Priority p, User by) {
        if (!canManage(by, t)) throw new IllegalStateException("That ticket belongs to another department.");
        if (p == Ticket.Priority.EMERGENCY && (by.getRole() == User.Role.FACULTY || t.getDepartment() != User.Role.CLINIC))
            throw new IllegalStateException("Emergency can only be set by clinic staff for clinic tickets.");
        if (p == Ticket.Priority.PRIORITY && !priorityEnabled)
            throw new IllegalStateException("The priority lane is turned off in the settings.");
        t.setPriority(p);
        saveTickets();
    }

    public void cancel(Ticket t, User by) {
        if (!canManage(by, t)) throw new IllegalStateException("That ticket belongs to another department.");
        t.setStatus(Ticket.Status.CANCELLED);
        t.setCompleted(LocalDateTime.now());
        saveTickets();
    }

    public Ticket getServingAt(int counter) {
        for (Ticket t : tickets)
            if (t.getStatus() == Ticket.Status.SERVING && t.getCounter() == counter) return t;
        return null;
    }

    /** Waiting tickets in calling order. dept = null means every department. */
    public List<Ticket> getWaiting(User.Role dept) {
        return tickets.stream()
                .filter(t -> t.getStatus() == Ticket.Status.WAITING)
                .filter(t -> dept == null || dept == User.Role.ADMIN || t.getDepartment() == dept)
                .sorted(ORDER).collect(Collectors.toList());
    }

    public int servedToday(User.Role dept) {
        return (int) tickets.stream().filter(t -> t.getStatus() == Ticket.Status.COMPLETED
                && t.getArrival().toLocalDate().equals(LocalDate.now())
                && (dept == null || dept == User.Role.ADMIN || t.getDepartment() == dept)).count();
    }

    /** Finds today's ticket that is still waiting or being served. */
    public Ticket findActive(String number) {
        for (Ticket t : tickets)
            if (t.getNumber().equalsIgnoreCase(number) && t.getArrival().toLocalDate().equals(LocalDate.now())
                    && (t.getStatus() == Ticket.Status.WAITING || t.getStatus() == Ticket.Status.SERVING)) return t;
        return null;
    }

    /** Rough waiting time in minutes, based on average service time of that department. */
    public int estimateMinutes(Ticket t) {
        int ahead = getWaiting(t.getDepartment()).indexOf(t);
        double avg = tickets.stream()
                .filter(x -> x.getDepartment() == t.getDepartment() && x.getStatus() == Ticket.Status.COMPLETED)
                .mapToLong(Ticket::getServiceSeconds).average().orElse(300);
        avg = Math.max(avg, 60);
        return (int) Math.ceil(ahead * avg / Math.max(1, countersOf(t.getDepartment())) / 60.0);
    }

    // Tickets from a previous day are closed so each day starts fresh.
    private void closeOldTickets() {
        boolean changed = false;
        for (Ticket t : tickets) {
            boolean open = t.getStatus() == Ticket.Status.WAITING || t.getStatus() == Ticket.Status.SERVING;
            if (open && t.getArrival().toLocalDate().isBefore(LocalDate.now())) {
                t.setStatus(t.getStatus() == Ticket.Status.SERVING ? Ticket.Status.NO_SHOW : Ticket.Status.CANCELLED);
                t.setCompleted(LocalDateTime.now());
                changed = true;
            }
        }
        if (changed) saveTickets();
    }

    // ===================== SETTINGS =====================
    public String getOrganization() { return organization; }
    public int getClinicCounters() { return clinicCounters; }
    public int getFacultyCounters() { return facultyCounters; }
    public boolean isPriorityEnabled() { return priorityEnabled; }
    public List<String[]> getServices() { return services; }

    public void setOrganization(String name) { organization = name; saveSettings(); }
    public void setClinicCounters(int n) { clinicCounters = n; saveSettings(); }
    public void setFacultyCounters(int n) { facultyCounters = n; saveSettings(); }
    public void togglePriority() { priorityEnabled = !priorityEnabled; saveSettings(); }

    public void addService(String name, String prefix, User.Role dept) {
        prefix = prefix.toUpperCase();
        if (name.isBlank()) throw new IllegalArgumentException("Service name is required.");
        if (!prefix.matches("[A-Z0-9]{1,3}")) throw new IllegalArgumentException("Prefix must be 1-3 letters/numbers.");
        for (String[] s : services)
            if (s[1].equals(prefix)) throw new IllegalArgumentException("That prefix is already used.");
        services.add(new String[]{name.replace(":", " ").replace(";", " ").trim(), prefix, dept.name()});
        saveSettings();
    }

    public void removeService(int index) {
        if (services.size() <= 1) throw new IllegalArgumentException("Keep at least one service.");
        services.remove(index);
        saveSettings();
    }

    // ===================== REPORT =====================
    public String buildReport(int days, String label) {
        LocalDate from = LocalDate.now().minusDays(days - 1L);
        List<Ticket> list = tickets.stream().filter(t -> !t.getArrival().toLocalDate().isBefore(from))
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("\n===== QUEUE REPORT: ").append(label).append(" =====\n");
        sb.append("Tickets issued    : ").append(list.size()).append('\n');
        sb.append("Completed         : ").append(count(list, Ticket.Status.COMPLETED)).append('\n');
        sb.append("No-show           : ").append(count(list, Ticket.Status.NO_SHOW)).append('\n');
        sb.append("Emergency cases   : ").append(list.stream().filter(t -> t.getPriority() == Ticket.Priority.EMERGENCY).count()).append('\n');
        sb.append("Students served   : ").append(list.stream().filter(t -> t.getClientType().equals("Student") && t.getStatus() == Ticket.Status.COMPLETED).count()).append('\n');
        sb.append("Faculty served    : ").append(list.stream().filter(t -> t.getClientType().equals("Faculty") && t.getStatus() == Ticket.Status.COMPLETED).count()).append('\n');
        sb.append("Average wait      : ").append(fmt(avgWait(list))).append('\n');
        sb.append("Longest wait      : ").append(fmt(list.stream().filter(t -> t.getCalled() != null).mapToLong(Ticket::getWaitSeconds).max().orElse(0))).append('\n');
        sb.append("Average service   : ").append(fmt(avgService(list))).append("\n\n");

        sb.append("BY DEPARTMENT\n");
        sb.append(String.format("  %-16s %8s %12s %12s%n", "Department", "Tickets", "Avg wait", "Avg service"));
        for (User.Role d : new User.Role[]{User.Role.CLINIC, User.Role.FACULTY}) {
            List<Ticket> dl = list.stream().filter(t -> t.getDepartment() == d).collect(Collectors.toList());
            sb.append(String.format("  %-16s %8d %12s %12s%n", d.getDepartmentName(), dl.size(),
                    fmt(avgWait(dl)), fmt(avgService(dl))));
        }

        sb.append("\nBY SERVICE\n");
        sb.append(String.format("  %-26s %8s %12s %12s%n", "Service", "Tickets", "Avg wait", "Avg service"));
        Map<String, List<Ticket>> bySvc = new TreeMap<>();
        for (Ticket t : list) bySvc.computeIfAbsent(t.getService(), k -> new ArrayList<>()).add(t);
        for (Map.Entry<String, List<Ticket>> e : bySvc.entrySet())
            sb.append(String.format("  %-26s %8d %12s %12s%n", e.getKey(), e.getValue().size(),
                    fmt(avgWait(e.getValue())), fmt(avgService(e.getValue()))));

        sb.append("\nPEAK HOURS (arrivals per hour)\n");
        int[] hours = new int[24];
        for (Ticket t : list) hours[t.getArrival().getHour()]++;
        int peak = 0;
        for (int h = 1; h < 24; h++) if (hours[h] > hours[peak]) peak = h;
        if (list.isEmpty()) sb.append("  No data.\n");
        else {
            for (int h = 0; h < 24; h++)
                if (hours[h] > 0) sb.append(String.format("  %02d:00  %-20s %d%n", h, "#".repeat(Math.min(hours[h], 20)), hours[h]));
            sb.append(String.format("  Busiest hour: %02d:00 (%d arrivals)%n", peak, hours[peak]));
        }

        sb.append("\nSTAFF PERFORMANCE\n");
        Map<String, List<Ticket>> byStaff = new TreeMap<>();
        for (Ticket t : list)
            if (t.getStatus() == Ticket.Status.COMPLETED)
                byStaff.computeIfAbsent(t.getServedBy(), k -> new ArrayList<>()).add(t);
        if (byStaff.isEmpty()) sb.append("  No completed transactions.\n");
        else {
            sb.append(String.format("  %-14s %8s %14s%n", "Staff", "Served", "Avg service"));
            for (Map.Entry<String, List<Ticket>> e : byStaff.entrySet())
                sb.append(String.format("  %-14s %8d %14s%n", e.getKey(), e.getValue().size(), fmt(avgService(e.getValue()))));
        }
        return sb.toString();
    }

    private static long count(List<Ticket> l, Ticket.Status s) { return l.stream().filter(t -> t.getStatus() == s).count(); }

    private static double avgWait(List<Ticket> l) {
        return l.stream().filter(t -> t.getCalled() != null).mapToLong(Ticket::getWaitSeconds).average().orElse(0);
    }

    private static double avgService(List<Ticket> l) {
        return l.stream().filter(t -> t.getStatus() == Ticket.Status.COMPLETED)
                .mapToLong(Ticket::getServiceSeconds).average().orElse(0);
    }

    public static String fmt(double seconds) {
        long s = Math.round(seconds);
        return (s / 60) + "m " + (s % 60) + "s";
    }

    // ===================== SAVE / LOAD (only when SAVE_DATA = true) =====================
    private void saveUsers() { writeLines(USERS_FILE, users.stream().map(User::toLine).collect(Collectors.toList())); }
    private void saveTickets() { writeLines(TICKETS_FILE, tickets.stream().map(Ticket::toLine).collect(Collectors.toList())); }

    private void writeLines(Path file, List<String> lines) {
        if (!SAVE_DATA) return;
        try {
            Files.createDirectories(DIR);
            Files.write(file, lines);
        } catch (IOException e) {
            System.out.println("Could not save " + file + ": " + e.getMessage());
        }
    }

    private void loadUsers() {
        for (String line : readLines(USERS_FILE)) {
            try { users.add(User.fromLine(line)); } catch (RuntimeException ignored) { }
        }
    }

    private void loadTickets() {
        for (String line : readLines(TICKETS_FILE)) {
            try { tickets.add(Ticket.fromLine(line)); } catch (RuntimeException ignored) { }
        }
    }

    private List<String> readLines(Path file) {
        try {
            if (Files.exists(file)) return Files.readAllLines(file);
        } catch (IOException ignored) { }
        return new ArrayList<>();
    }

    private void saveSettings() {
        if (!SAVE_DATA) return;
        Properties p = new Properties();
        p.setProperty("organization", organization);
        p.setProperty("clinicCounters", "" + clinicCounters);
        p.setProperty("facultyCounters", "" + facultyCounters);
        p.setProperty("priority", "" + priorityEnabled);
        p.setProperty("services", services.stream().map(s -> s[0] + ":" + s[1] + ":" + s[2]).collect(Collectors.joining(";")));
        try {
            Files.createDirectories(DIR);
            try (OutputStream out = Files.newOutputStream(SETTINGS_FILE)) { p.store(out, "Queue settings"); }
        } catch (IOException e) {
            System.out.println("Could not save settings: " + e.getMessage());
        }
    }

    private void loadSettings() {
        if (!Files.exists(SETTINGS_FILE)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(SETTINGS_FILE)) {
            p.load(in);
            organization = p.getProperty("organization", organization);
            clinicCounters = Integer.parseInt(p.getProperty("clinicCounters", "1"));
            facultyCounters = Integer.parseInt(p.getProperty("facultyCounters", "2"));
            priorityEnabled = Boolean.parseBoolean(p.getProperty("priority", "true"));
            for (String entry : p.getProperty("services", "").split(";")) {
                String[] parts = entry.split(":");
                if (parts.length == 3) services.add(new String[]{parts[0], parts[1], parts[2]});
            }
        } catch (IOException | RuntimeException ignored) { }
    }
}
