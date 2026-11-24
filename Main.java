import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;

/** Console menus: Students use the kiosk, Faculty and Clinic Staff use the staff console, Admin manages everything. */
public class Main {
    private static final Scanner in = new Scanner(System.in);
    private static final QueueManager qm = new QueueManager();

    public static void main(String[] args) {
        mainMenu();
    }

    // ===================== INPUT HELPERS =====================
    private static String ask(String prompt) {
        System.out.print(prompt);
        if (!in.hasNextLine()) {
            System.out.println("\nInput closed. Goodbye!");
            System.exit(0);
        }
        return in.nextLine().trim();
    }

    private static String askRequired(String prompt) {
        while (true) {
            String s = ask(prompt);
            if (!s.isEmpty()) return s;
            System.out.println("  This is required.");
        }
    }

    private static int askInt(String prompt, int min, int max) {
        while (true) {
            try {
                int v = Integer.parseInt(ask(prompt));
                if (v >= min && v <= max) return v;
            } catch (NumberFormatException ignored) { }
            System.out.println("  Please enter a number from " + min + " to " + max + ".");
        }
    }

    private static boolean yes(String prompt) { return ask(prompt + " (y/n): ").equalsIgnoreCase("y"); }

    private static int menu(String title, String back, String... options) {
        System.out.println("\n==================================================");
        System.out.println("  " + title);
        System.out.println("==================================================");
        for (int i = 0; i < options.length; i++) System.out.println("  [" + (i + 1) + "] " + options[i]);
        System.out.println("  [0] " + back);
        return askInt("Choose: ", 0, options.length);
    }

    /** Lists services (only those of one department if dept is given). Returns the service index, or -1 for "any". */
    private static int chooseService(User.Role dept, boolean allowAny) {
        List<Integer> idx = qm.serviceIndexes(dept);
        if (idx.isEmpty()) throw new IllegalArgumentException("No services are set up for that department.");
        if (allowAny) System.out.println("  [0] Any service");
        for (int i = 0; i < idx.size(); i++) {
            String[] s = qm.getServices().get(idx.get(i));
            System.out.println("  [" + (i + 1) + "] " + s[0] + " (" + s[1] + ") - " + User.Role.valueOf(s[2]).getDepartmentName());
        }
        int c = askInt("Choose service: ", allowAny ? 0 : 1, idx.size());
        return c == 0 ? -1 : idx.get(c - 1);
    }

    // ===================== MAIN MENU =====================
    private static void mainMenu() {
        while (true) {
            int c = menu(qm.getOrganization() + " - QUEUE SYSTEM", "Exit",
                    "Student / Faculty kiosk (get a ticket)", "Display board", "Staff login (Faculty / Clinic / Admin)");
            if (c == 0) { System.out.println("Goodbye!"); return; }
            if (c == 1) kiosk();
            else if (c == 2) { displayBoard(); ask("\nPress Enter to go back..."); }
            else login();
        }
    }

    // ===================== STUDENTS: TICKET KIOSK =====================
    private static void kiosk() {
        while (true) {
            int c = menu("TICKET KIOSK", "Back", "Get a queue ticket", "Check my queue status");
            if (c == 0) return;
            try {
                if (c == 1) getTicket(); else checkStatus();
            } catch (RuntimeException e) {
                System.out.println("  ! " + e.getMessage());
            }
        }
    }

    private static void getTicket() {
        int type = askInt("I am a: [1] Student  [2] Faculty/Staff : ", 1, 2);
        String clientType = type == 1 ? "Student" : "Faculty";
        String id = askRequired(type == 1 ? "Student ID number: " : "Employee ID number: ");
        String name = askRequired("Your name: ");

        System.out.println("\nWhat do you need?");
        int svc = chooseService(null, false);
        User.Role dept = User.Role.valueOf(qm.getServices().get(svc)[2]);
        if (dept == User.Role.CLINIC)
            System.out.println("  NOTE: If this is a medical EMERGENCY, tell the clinic staff right away.");

        Ticket.Priority p = Ticket.Priority.REGULAR;
        if (qm.isPriorityEnabled() && yes("Priority lane (senior / PWD / pregnant)?")) p = Ticket.Priority.PRIORITY;

        Ticket t = qm.issueTicket(svc, clientType, id, name, p);
        int ahead = qm.getWaiting(dept).indexOf(t);
        System.out.println("\n  +------------------------------------+");
        System.out.printf("  |  YOUR NUMBER:   %-19s|%n", t.getNumber());
        System.out.printf("  |  Service:       %-19s|%n", cut(t.getService(), 19));
        System.out.printf("  |  Go to:         %-19s|%n", dept.getDepartmentName());
        System.out.printf("  |  People ahead:  %-19d|%n", ahead);
        System.out.printf("  |  Estimated wait: about %-3d min      |%n", qm.estimateMinutes(t));
        System.out.println("  +------------------------------------+");
        System.out.println("  Please wait in the waiting area. Watch the display board for your number.");
    }

    private static void checkStatus() {
        String num = askRequired("Enter your ticket number (e.g. C-001): ");
        Ticket t = qm.findActive(num);
        if (t == null) {
            System.out.println("  No active ticket with that number today.");
        } else if (t.getStatus() == Ticket.Status.SERVING) {
            System.out.println("  Your number is being served at " + qm.counterLabel(t.getCounter()) + ". Please proceed now!");
        } else {
            System.out.println("  Ticket " + t.getNumber() + ": " + qm.getWaiting(t.getDepartment()).indexOf(t)
                    + " people ahead, about " + qm.estimateMinutes(t) + " min.");
        }
    }

    // ===================== DISPLAY BOARD =====================
    private static void displayBoard() {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE MMM d, HH:mm:ss"));
        System.out.println("\n==================================================");
        System.out.println("  " + qm.getOrganization().toUpperCase());
        System.out.println("  " + now);
        System.out.println("==================================================");
        System.out.println("  NOW SERVING");
        for (int i = 1; i <= qm.getTotalCounters(); i++) {
            Ticket t = qm.getServingAt(i);
            System.out.printf("   %-10s :  %s%n", qm.counterLabel(i), t == null ? "----" : t.getNumber());
        }
        for (User.Role d : new User.Role[]{User.Role.CLINIC, User.Role.FACULTY}) {
            System.out.println("--------------------------------------------------");
            System.out.println("  NEXT IN LINE - " + d.getDepartmentName().toUpperCase());
            List<Ticket> w = qm.getWaiting(d);
            if (w.isEmpty()) System.out.println("   No one waiting.");
            for (int i = 0; i < Math.min(6, w.size()); i++) {
                Ticket t = w.get(i);
                String tag = t.getPriority() == Ticket.Priority.EMERGENCY ? "  [EMERGENCY]"
                        : t.getPriority() == Ticket.Priority.PRIORITY ? "  [Priority]" : "";
                System.out.printf("   %d. %s%s%n", i + 1, t.getNumber(), tag);
            }
            if (w.size() > 6) System.out.println("   ... +" + (w.size() - 6) + " more");
        }
        System.out.println("==================================================");
    }

    // ===================== STAFF LOGIN =====================
    private static void login() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.println("\n--- STAFF LOGIN (leave username empty to cancel) ---");
            String name = ask("Username: ");
            if (name.isEmpty()) return;
            User u = qm.login(name, ask("Password: "));
            if (u != null) {
                System.out.println("Welcome, " + u.getFullName() + " (" + u.getRole() + ")");
                if (u.isAdmin()) adminMenu(u); else staffMenu(u);
                return;
            }
            System.out.println("  Wrong username or password. Attempts left: " + (3 - attempt));
        }
        System.out.println("  Too many failed attempts. Locked for 10 seconds...");
        try { Thread.sleep(10000); } catch (InterruptedException ignored) { }
    }

    private static void staffMenu(User u) {
        while (true) {
            int c = menu(u.getRole().toString().toUpperCase() + " MENU - " + u.getFullName(), "Logout",
                    "Staff console (" + u.getRole().getDepartmentName() + " queue)", "Change my password");
            if (c == 0) return;
            if (c == 1) staffConsole(u); else changeOwnPassword(u);
        }
    }

    private static void changeOwnPassword(User u) {
        if (!u.checkPassword(ask("Current password: "))) { System.out.println("  ! Wrong password."); return; }
        try {
            qm.resetPassword(u.getUsername(), ask("New password (min 6 chars): "));
            System.out.println("  Password changed.");
        } catch (RuntimeException e) {
            System.out.println("  ! " + e.getMessage());
        }
    }

    // ===================== STAFF CONSOLE (calling + priority) =====================
    private static void staffConsole(User u) {
        int[] range = qm.counterRange(u.getRole());
        int counter;
        if (range[0] == range[1]) {
            counter = range[0];
        } else {
            System.out.println("Counters you can use:");
            for (int i = range[0]; i <= range[1]; i++) System.out.println("  [" + i + "] " + qm.counterLabel(i));
            counter = askInt("Which counter are you at? ", range[0], range[1]);
        }
        User.Role dept = qm.deptOfCounter(counter);

        while (true) {
            Ticket cur = qm.getServingAt(counter);
            System.out.println();
            if (cur == null) System.out.println(qm.counterLabel(counter) + " counter: free | Waiting: "
                    + qm.getWaiting(dept).size() + " | Served today: " + qm.servedToday(dept));
            else System.out.println(qm.counterLabel(counter) + " counter: SERVING " + cur.getNumber() + " ("
                    + cur.getClientType() + ": " + cur.getCustomerName() + ", ID " + cur.getIdNumber() + ", "
                    + cur.getService() + ") for " + QueueManager.fmt(cur.getServiceSeconds()));

            int c = menu("STAFF CONSOLE - " + dept.getDepartmentName() + " (" + qm.counterLabel(counter) + ")", "Back",
                    "Call next", "Recall current", "Complete current", "Mark current as no-show",
                    "View waiting queue", "Change a ticket's priority", "Cancel a waiting ticket",
                    "Add emergency walk-in (clinic only)");
            if (c == 0) return;
            try {
                switch (c) {
                    case 1: {
                        System.out.println("Call from which service?");
                        Ticket t = qm.callNext(counter, u, chooseService(dept, true));
                        if (t == null) System.out.println("  No one is waiting.");
                        else System.out.println("\007  >>> NOW CALLING " + t.getNumber() + " to " + qm.counterLabel(counter)
                                + " (" + t.getCustomerName() + ")");
                        break;
                    }
                    case 2:
                        if (cur == null) throw new IllegalStateException("This counter is not serving anyone.");
                        System.out.println("\007  >>> RECALLING " + cur.getNumber() + " to " + qm.counterLabel(counter));
                        break;
                    case 3: System.out.println("  Completed " + qm.complete(counter).getNumber()); break;
                    case 4: System.out.println("  Marked no-show: " + qm.noShow(counter).getNumber()); break;
                    case 5: showWaiting(dept); break;
                    case 6: {
                        Ticket t = pickWaiting(dept);
                        if (t == null) break;
                        Ticket.Priority[] all = Ticket.Priority.values();
                        for (int i = 0; i < all.length; i++) System.out.println("  [" + (i + 1) + "] " + all[i]);
                        Ticket.Priority p = all[askInt("New priority: ", 1, all.length) - 1];
                        qm.setPriority(t, p, u);
                        System.out.println("  " + t.getNumber() + " is now " + p);
                        break;
                    }
                    case 7: {
                        Ticket t = pickWaiting(dept);
                        if (t != null && yes("Cancel " + t.getNumber() + "?")) {
                            qm.cancel(t, u);
                            System.out.println("  Cancelled.");
                        }
                        break;
                    }
                    default: {
                        if (dept != User.Role.CLINIC) throw new IllegalStateException("Emergency tickets are for the Clinic only.");
                        int svc = chooseService(User.Role.CLINIC, false);
                        Ticket t = qm.issueEmergency(u, svc, askRequired("Patient name: "));
                        System.out.println("  Emergency ticket issued: " + t.getNumber() + " (goes to the front of the line)");
                    }
                }
            } catch (RuntimeException e) {
                System.out.println("  ! " + e.getMessage());
            }
        }
    }

    private static void showWaiting(User.Role dept) {
        List<Ticket> w = qm.getWaiting(dept);
        if (w.isEmpty()) { System.out.println("  Queue is empty."); return; }
        System.out.printf("  %-7s %-9s %-16s %-14s %-10s %s%n", "Ticket", "Type", "Name", "Service", "Priority", "Waiting");
        for (Ticket t : w)
            System.out.printf("  %-7s %-9s %-16s %-14s %-10s %s%n", t.getNumber(), t.getClientType(),
                    cut(t.getCustomerName(), 16), cut(t.getService(), 14), t.getPriority(), QueueManager.fmt(t.getWaitSeconds()));
    }

    private static Ticket pickWaiting(User.Role dept) {
        List<Ticket> w = qm.getWaiting(dept);
        if (w.isEmpty()) { System.out.println("  Queue is empty."); return null; }
        showWaiting(dept);
        String num = ask("Ticket number (e.g. C-001): ").toUpperCase();
        for (Ticket t : w) if (t.getNumber().equals(num)) return t;
        System.out.println("  No waiting ticket with that number.");
        return null;
    }

    private static String cut(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "."; }

    // ===================== ADMIN =====================
    private static void adminMenu(User admin) {
        while (true) {
            int c = menu("ADMIN MENU - " + admin.getFullName(), "Logout", "Staff console (call numbers)",
                    "Manage users", "Queue settings", "Reports", "Change my password");
            if (c == 0) return;
            if (c == 1) staffConsole(admin);
            else if (c == 2) manageUsers(admin);
            else if (c == 3) manageSettings();
            else if (c == 4) reports();
            else changeOwnPassword(admin);
        }
    }

    private static void manageUsers(User admin) {
        while (true) {
            System.out.printf("%n  %-14s %-22s %-16s %s%n", "Username", "Full name", "Role", "Status");
            for (User u : qm.getUsers())
                System.out.printf("  %-14s %-22s %-16s %s%n", u.getUsername(), cut(u.getFullName(), 22), u.getRole(),
                        u.isActive() ? "Active" : "Disabled");
            int c = menu("USER MANAGEMENT", "Back", "Add user", "Reset password", "Enable / disable account", "Delete user");
            if (c == 0) return;
            try {
                switch (c) {
                    case 1: {
                        String user = ask("New username: ");
                        String full = ask("Full name: ");
                        String pw = ask("Password (min 6 chars): ");
                        int r = askInt("Role: [1] Faculty Member  [2] Clinic Staff  [3] Administrator : ", 1, 3);
                        User.Role role = r == 1 ? User.Role.FACULTY : r == 2 ? User.Role.CLINIC : User.Role.ADMIN;
                        qm.addUser(user, full, pw, role);
                        System.out.println("  User created.");
                        break;
                    }
                    case 2: qm.resetPassword(ask("Username: "), ask("New password: ")); System.out.println("  Password reset."); break;
                    case 3: qm.toggleActive(ask("Username: ")); System.out.println("  Account updated."); break;
                    default:
                        String name = ask("Username to delete: ");
                        if (yes("Really delete " + name + "?")) { qm.deleteUser(name, admin.getUsername()); System.out.println("  Deleted."); }
                }
            } catch (RuntimeException e) {
                System.out.println("  ! " + e.getMessage());
            }
        }
    }

    private static void manageSettings() {
        while (true) {
            System.out.println("\n  Organization    : " + qm.getOrganization());
            System.out.println("  Clinic counters : " + qm.getClinicCounters());
            System.out.println("  Office counters : " + qm.getFacultyCounters());
            System.out.println("  Priority lane   : " + (qm.isPriorityEnabled() ? "ON" : "OFF"));
            for (String[] s : qm.getServices())
                System.out.println("  Service         : " + s[1] + " - " + s[0] + " (" + User.Role.valueOf(s[2]).getDepartmentName() + ")");
            int c = menu("QUEUE SETTINGS", "Back", "Change organization name", "Change number of clinic counters",
                    "Change number of faculty office counters", "Turn priority lane on/off", "Add service", "Remove service");
            if (c == 0) return;
            try {
                switch (c) {
                    case 1: qm.setOrganization(askRequired("New name: ")); break;
                    case 2: qm.setClinicCounters(askInt("Clinic counters (1-5): ", 1, 5)); break;
                    case 3: qm.setFacultyCounters(askInt("Faculty office counters (1-10): ", 1, 10)); break;
                    case 4: qm.togglePriority(); break;
                    case 5: {
                        String name = ask("Service name: ");
                        String prefix = ask("Ticket prefix (1-3 letters): ");
                        User.Role d = askInt("Department: [1] Clinic  [2] Faculty Office : ", 1, 2) == 1 ? User.Role.CLINIC : User.Role.FACULTY;
                        qm.addService(name, prefix, d);
                        break;
                    }
                    default: qm.removeService(chooseService(null, false));
                }
                System.out.println("  Saved.");
            } catch (RuntimeException e) {
                System.out.println("  ! " + e.getMessage());
            }
        }
    }

    private static void reports() {
        while (true) {
            int c = menu("REPORTS", "Back", "Today", "Last 7 days", "Last 30 days", "All time");
            if (c == 0) return;
            int[] days = {1, 7, 30, 36500};
            String[] labels = {"Today", "Last 7 days", "Last 30 days", "All time"};
            System.out.println(qm.buildReport(days[c - 1], labels[c - 1]));
        }
    }
}
