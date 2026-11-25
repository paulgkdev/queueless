import java.time.Duration;
import java.time.LocalDateTime;

/** One queue ticket. Every ticket created is an object of this class. */
public class Ticket {

    public enum Status { WAITING, SERVING, COMPLETED, NO_SHOW }

    // ENCAPSULATION: private fields, read through getters.
    private final String number;                 // example: C-001
    private final String service;
    private final User.Department department;    // CLINIC or FACULTY
    private final String idNumber;
    private final String name;
    private final boolean priority;              // true = senior citizen / PWD / pregnant
    private Status status = Status.WAITING;
    private String servedBy = "";

    // Times, used to track waiting time and service time
    private final LocalDateTime arrival = LocalDateTime.now();
    private LocalDateTime called;
    private LocalDateTime finished;

    public Ticket(String number, String service, User.Department department, String idNumber, String name,
                  boolean priority) {
        this.number = number;
        this.service = service;
        this.department = department;
        this.idNumber = idNumber;
        this.name = name;
        this.priority = priority;
    }

    /** A staff member calls this ticket. */
    public void startServing(String staffUsername) {
        status = Status.SERVING;
        servedBy = staffUsername;
        called = LocalDateTime.now();
    }

    /** The staff member is done (COMPLETED or NO_SHOW). */
    public void finish(Status newStatus) {
        status = newStatus;
        finished = LocalDateTime.now();
    }

    /** Seconds the client waited before being called. */
    public long getWaitSeconds() {
        LocalDateTime end = (called != null) ? called : LocalDateTime.now();
        return Duration.between(arrival, end).getSeconds();
    }

    /** Seconds the client was served. */
    public long getServiceSeconds() {
        if (called == null) return 0;
        LocalDateTime end = (finished != null) ? finished : LocalDateTime.now();
        return Duration.between(called, end).getSeconds();
    }

    public String getNumber() { return number; }
    public String getService() { return service; }
    public User.Department getDepartment() { return department; }
    public String getIdNumber() { return idNumber; }
    public String getName() { return name; }
    public boolean isPriority() { return priority; }
    public Status getStatus() { return status; }
    public String getServedBy() { return servedBy; }
    public LocalDateTime getArrival() { return arrival; }
}
