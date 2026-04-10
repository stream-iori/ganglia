package work.ganglia.port.internal.doctor;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

/** A single diagnostic check that verifies an external dependency or system requirement. */
public interface DoctorCheck {

  enum Phase {
    /** Run once during bootstrap. */
    STARTUP,
    /** Run periodically at runtime (reserved for future use). */
    RUNTIME
  }

  /** The phase in which this check should run. */
  Phase phase();

  /** Executes the check and returns a result. Must never fail the returned Future. */
  Future<CheckResult> execute(Vertx vertx);
}
