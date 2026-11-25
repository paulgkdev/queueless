import java.util.List;
import java.util.Scanner;

/**
 * Students: get a ticket and view the queue (no login).
 * Staff (Faculty, Clinic, Administrator): log in and go straight to the staff console.
 */
public class Main {
    private static final Scanner in = new Scanner(System.in);
    private static final QueueManager qm = new QueueManager();

    public static void main(String[] args) {
        while (true) {
            System.out.println("\n=== " + QueueManager.ORGANIZATION + " ===");
            System.out.println("  [1] Get a ticket");
            System.out.println("  [2] View queue");
            System.out.println("  [3] Staff login");
            System.out.println("  [0] Exit");
            int choice = askNumber("Choose: ", 0, 3);

            if (choice == 0) {
                System.out.println("\nThank you for using QueueLess!");
                System.out.println("           COMPLETED");
                return;
            }
            if (choice == 1) getTicket();
            else if (choice == 2) viewQueue();
            else login();
        }
    }

    // ===================== INPUT HELPERS =====================
    private static String ask(String text) {
        System.out.print(text);
        if (!in.hasNextLine()) System.exit(0);      // input closed
        return in.nextLine().trim();
    }

    private static String askRequired(String text) {
        String s = ask(text);
        while (s.isEmpty()) s = ask("  This is required. " + text);
        return s;
    }

    /** Asks a yes/no question. Keeps asking until the answer is y or n. */
    private static boolean askYesNo(String text) {
        while (true) {
            String answer = ask(text);
            if (answer.equalsIgnoreCase("y")) return true;
            if (answer.equalsIgnoreCase("n")) return false;
            System.out.println("  Please type y or n.");
        }
    }

    private static int askNumber(String text, int min, int max) {
        while (true) {
            try {
                int n = Integer.parseInt(ask(text));
                if (n >= min && n <= max) return n;
            } catch (NumberFormatException e) {
                // not a number, ask again
            }
            System.out.println("  Please enter a number from " + min + " to " + max + ".");
        }
    }

    private static String time(long seconds) { return (seconds / 60) + "m " + (seconds % 60) + "s"; }

    // ===================== STUDENT: GET A TICKET =====================
    private static void getTicket() {
        System.out.println("\nWhat do you need?");
        for (int i = 0; i < QueueManager.SERVICES.length; i++)
            System.out.println("  [" + (i + 1) + "] " + QueueManager.SERVICES[i][0]);
        int service = askNumber("Choose: ", 1, QueueManager.SERVICES.length) - 1;
        String id = askRequired("ID number: ");
        String name = askRequired("Name: ");
        boolean priority = askYesNo("Are you a senior citizen, PWD, or pregnant? (y/n): ");

        Ticket ticket = qm.issueTicket(service, id, name, priority);
        System.out.println("\n  YOUR NUMBER: " + ticket.getNumber());
        if (priority) System.out.println("  PRIORITY LANE: you will be called before regular clients.");
        System.out.println("  Go to: " + ticket.getDepartment().getLabel());
        System.out.println("  People ahead of you: " + qm.getWaiting(ticket.getDepartment()).indexOf(ticket));
        System.out.println("  Please wait, then choose 'View queue' to see if your number is called.");
    }

    // ===================== STUDENT: VIEW QUEUE =====================
    private static void viewQueue() {
        System.out.println("\n--- NOW SERVING ---");
        List<Ticket> serving = qm.getBeingServed();
        if (serving.isEmpty()) System.out.println("  Nobody yet.");
        for (Ticket t : serving)
            System.out.println("  " + t.getNumber() + "  (" + t.getDepartment().getLabel() + ")");

        showWaiting(User.Department.CLINIC);
        showWaiting(User.Department.FACULTY);
    }

    private static void showWaiting(User.Department department) {
        System.out.println("\n--- WAITING: " + department.getLabel().toUpperCase() + " ---");
        List<Ticket> waiting = qm.getWaiting(department);
        if (waiting.isEmpty()) System.out.println("  No one waiting.");
        for (int i = 0; i < waiting.size(); i++) {
            Ticket t = waiting.get(i);
            System.out.println("  " + (i + 1) + ". " + t.getNumber() + (t.isPriority() ? "  [PRIORITY]" : ""));
        }
    }

    // ===================== STAFF LOGIN =====================
    private static void login() {
        for (int triesLeft = 3; triesLeft > 0; triesLeft--) {
            String username = ask("\nUsername (empty to cancel): ");
            if (username.isEmpty()) return;
            User user = qm.login(username, ask("Password: "));
            if (user != null) {
                // POLYMORPHISM: getTitle() gives a different answer for each kind of user.
                System.out.println("Welcome, " + user.getFullName() + " (" + user.getTitle() + ")");
                staffConsole(user);
                return;
            }
            System.out.println("  Wrong username or password. Tries left: " + (triesLeft - 1));
        }
    }

    // ===================== STAFF CONSOLE =====================
    private static void staffConsole(User staff) {
        // POLYMORPHISM + ARRAYS: each kind of user returns its own list of desks.
        User.Department[] desks = staff.getDesks();
        User.Department desk = desks[0];
        if (desks.length > 1) {                      // only the administrator has a choice
            System.out.println("\nWhich desk will you serve?");
            for (int i = 0; i < desks.length; i++)
                System.out.println("  [" + (i + 1) + "] " + desks[i].getLabel());
            desk = desks[askNumber("Choose: ", 1, desks.length) - 1];
        }

        while (true) {
            // The screen shows the current client and the waiting list every time.
            Ticket current = qm.getCurrent(staff);
            System.out.println("\n=== " + desk.getLabel().toUpperCase() + " ===");
            if (current == null) System.out.println("You are serving: nobody");
            else System.out.println("You are serving: " + current.getNumber() + " - " + current.getName()
                    + " (" + current.getService() + ")");
            showWaiting(desk);

            System.out.println("\n  [1] Call next");
            System.out.println("  [2] Finish current (done)");
            System.out.println("  [3] Skip current (did not show up)");
            System.out.println("  [0] Logout");
            int choice = askNumber("Choose: ", 0, 3);
            if (choice == 0) return;

            // EXCEPTION HANDLING: rule violations are thrown as QueueException and shown here.
            try {
                if (choice == 1) {
                    Ticket t = qm.callNext(staff, desk);
                    if (t == null) System.out.println("  No one is waiting.");
                    else System.out.println("  >>> NOW CALLING " + t.getNumber() + " - " + t.getName()
                            + " (waited " + time(t.getWaitSeconds()) + ")");
                } else if (choice == 2) {
                    Ticket t = qm.finishCurrent(staff, Ticket.Status.COMPLETED);
                    System.out.println("  Done with " + t.getNumber() + " (service time " + time(t.getServiceSeconds()) + ")");
                } else {
                    System.out.println("  Skipped " + qm.finishCurrent(staff, Ticket.Status.NO_SHOW).getNumber());
                }
            } catch (QueueManager.QueueException e) {
                System.out.println("  ! " + e.getMessage());
            }
        }
    }
}
