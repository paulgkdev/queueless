import java.time.Duration;
import java.time.LocalDateTime;

/** One queue ticket for a student or faculty member. */
public class Ticket {
    public enum Priority { EMERGENCY, PRIORITY, REGULAR }          // top of the list is served first
    public enum Status { WAITING, SERVING, COMPLETED, NO_SHOW, CANCELLED }

    private final int sequence;
    private final String number;
    private final String service;
    private final User.Role department;      // CLINIC or FACULTY (who handles this ticket)
    private final String clientType;         // "Student" or "Faculty"
    private final String idNumber;
    private final String customerName;
    private Priority priority;
    private final LocalDateTime arrival;
    private LocalDateTime called;
    private LocalDateTime completed;
    private Status status = Status.WAITING;
    private int counter;
    private String servedBy = "";

    public Ticket(int sequence, String number, String service, User.Role department, String clientType,
                  String idNumber, String customerName, Priority priority, LocalDateTime arrival) {
        this.sequence = sequence;
        this.number = number;
        this.service = service;
        this.department = department;
        this.clientType = clientType;
        this.idNumber = idNumber;
        this.customerName = customerName;
        this.priority = priority;
        this.arrival = arrival;
    }

    /** Seconds spent waiting before being called. */
    public long getWaitSeconds() {
        LocalDateTime end = called != null ? called : (completed != null ? completed : LocalDateTime.now());
        return Math.max(0, Duration.between(arrival, end).getSeconds());
    }

    /** Seconds spent being served (0 if never served). */
    public long getServiceSeconds() {
        if (called == null || status == Status.NO_SHOW || status == Status.CANCELLED) return 0;
        LocalDateTime end = completed != null ? completed : LocalDateTime.now();
        return Math.max(0, Duration.between(called, end).getSeconds());
    }

    private static String clean(String s) { return s.replace(",", " "); }

    public String toLine() {
        return String.join(",", "" + sequence, number, clean(service), department.name(), clean(clientType),
                clean(idNumber), clean(customerName), priority.name(), arrival.toString(),
                called == null ? "" : called.toString(), completed == null ? "" : completed.toString(),
                status.name(), "" + counter, servedBy);
    }

    public static Ticket fromLine(String line) {
        String[] f = line.split(",", -1);
        Ticket t = new Ticket(Integer.parseInt(f[0]), f[1], f[2], User.Role.valueOf(f[3]), f[4], f[5], f[6],
                Priority.valueOf(f[7]), LocalDateTime.parse(f[8]));
        t.called = f[9].isEmpty() ? null : LocalDateTime.parse(f[9]);
        t.completed = f[10].isEmpty() ? null : LocalDateTime.parse(f[10]);
        t.status = Status.valueOf(f[11]);
        t.counter = Integer.parseInt(f[12]);
        t.servedBy = f[13];
        return t;
    }

    public int getSequence() { return sequence; }
    public String getNumber() { return number; }
    public String getService() { return service; }
    public User.Role getDepartment() { return department; }
    public String getClientType() { return clientType; }
    public String getIdNumber() { return idNumber; }
    public String getCustomerName() { return customerName; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority p) { this.priority = p; }
    public LocalDateTime getArrival() { return arrival; }
    public LocalDateTime getCalled() { return called; }
    public void setCalled(LocalDateTime t) { this.called = t; }
    public LocalDateTime getCompleted() { return completed; }
    public void setCompleted(LocalDateTime t) { this.completed = t; }
    public Status getStatus() { return status; }
    public void setStatus(Status s) { this.status = s; }
    public int getCounter() { return counter; }
    public void setCounter(int c) { this.counter = c; }
    public String getServedBy() { return servedBy; }
    public void setServedBy(String s) { this.servedBy = s; }
}
