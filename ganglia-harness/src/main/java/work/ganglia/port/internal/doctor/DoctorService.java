package work.ganglia.port.internal.doctor;

import java.util.List;

import io.vertx.core.Future;

/** Runs diagnostic checks and reports results. */
public interface DoctorService {

  /** Runs all checks registered for the STARTUP phase. */
  Future<List<CheckResult>> runStartupChecks();

  /** Runs all checks registered for the RUNTIME phase (health checks). */
  Future<List<CheckResult>> runHealthChecks();
}
