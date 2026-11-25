import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** The rules of the system: accounts, tickets, and who is served next. */
public class QueueManager {

    /** EXCEPTION HANDLING: our own error type. Main catches it and shows the message. */
    public static class QueueException extends RuntimeException {
        public QueueException(String message) { super(message); }
    }

    public static final String ORGANIZATION = "University Student Services";

    // ARRAYS: each service is { name, ticket prefix, department }
    public static final String[][] SERVICES = {
            {"Clinic Visit", "C", "CLINIC"},
            {"Student Inquiry", "I", "FACULTY"},
            {"Document Request", "D", "FACULTY"},
            {"Administrative Services", "A", "FACULTY"}
    };

    // DATA HANDLING: lists of objects.
    private final List<User> users = new ArrayList<>();
    private final List<Ticket> tickets = new ArrayList<>();

    // Priority clients (senior / PWD / pregnant) are called first.
    // Inside each group: first come, first served.
    private final Comparator<Ticket> order = Comparator
            .comparing((Ticket t) -> !t.isPriority())
            .thenComparing(Ticket::getArrival);

    public QueueManager() {
        // POLYMORPHISM: different kinds of users are kept in one List<User>.
        users.add(new User.Administrator("admin", "System Administrator", "admin123"));
        users.add(new User.ClinicStaff("clinic", "Clinic Nurse", "clinic123"));
        users.add(new User.FacultyStaff("faculty", "Faculty Member", "faculty123"));
    }

    // ===================== LOGIN =====================
    public User login(String username, String password) {
        for (User u : users)
            if (u.getUsername().equalsIgnoreCase(username) && u.checkPassword(password)) return u;
        return null;
    }

    // ===================== TICKETS =====================
    /** A student gets a ticket. */
    public Ticket issueTicket(int serviceIndex, String idNumber, String name, boolean priority) {
        String[] service = SERVICES[serviceIndex];
        String prefix = service[1];

        // Ticket number = prefix + (how many tickets with this prefix exist) + 1
        int count = 1;
        for (Ticket t : tickets)
            if (t.getNumber().startsWith(prefix + "-")) count++;

        Ticket ticket = new Ticket(prefix + "-" + String.format("%03d", count), service[0],
                User.Department.valueOf(service[2]), idNumber, name, priority);
        tickets.add(ticket);
        return ticket;
    }

    /** Waiting tickets of one department, in the order they will be called. */
    public List<Ticket> getWaiting(User.Department department) {
        return tickets.stream()
                .filter(t -> t.getStatus() == Ticket.Status.WAITING && t.getDepartment() == department)
                .sorted(order)
                .collect(Collectors.toList());
    }

    /** All tickets being served right now. */
    public List<Ticket> getBeingServed() {
        return tickets.stream().filter(t -> t.getStatus() == Ticket.Status.SERVING).collect(Collectors.toList());
    }

    /** The ticket this staff member is serving now (or null). */
    public Ticket getCurrent(User staff) {
        for (Ticket t : getBeingServed())
            if (t.getServedBy().equals(staff.getUsername())) return t;
        return null;
    }

    // ===================== STAFF ACTIONS =====================
    /** Calls the next ticket of the desk. Returns null if nobody is waiting. */
    public Ticket callNext(User staff, User.Department desk) {
        if (getCurrent(staff) != null)
            throw new QueueException("Finish or skip your current client first.");
        List<Ticket> waiting = getWaiting(desk);
        if (waiting.isEmpty()) return null;
        Ticket next = waiting.get(0);
        next.startServing(staff.getUsername());
        return next;
    }

    public Ticket finishCurrent(User staff, Ticket.Status status) {
        Ticket current = getCurrent(staff);
        if (current == null) throw new QueueException("You are not serving anyone right now.");
        current.finish(status);
        return current;
    }
}
