package work.ganglia.kernel.doctor;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.port.internal.doctor.CheckResult;
import work.ganglia.port.internal.doctor.DoctorCheck;
import work.ganglia.port.internal.doctor.DoctorService;

/** Default implementation that runs checks in parallel, logging results by severity. */
public class DefaultDoctorService implements DoctorService {

  private static final Logger logger = LoggerFactory.getLogger(DefaultDoctorService.class);

  private final Vertx vertx;
  private final List<DoctorCheck> checks;

  public DefaultDoctorService(Vertx vertx, List<DoctorCheck> checks) {
    this.vertx = vertx;
    this.checks = checks != null ? checks : Collections.emptyList();
  }

  @Override
  public Future<List<CheckResult>> runStartupChecks() {
    return runChecks(DoctorCheck.Phase.STARTUP);
  }

  @Override
  public Future<List<CheckResult>> runHealthChecks() {
    return runChecks(DoctorCheck.Phase.RUNTIME);
  }

  private Future<List<CheckResult>> runChecks(DoctorCheck.Phase phase) {
    List<DoctorCheck> phaseChecks = checks.stream().filter(c -> c.phase() == phase).toList();

    if (phaseChecks.isEmpty()) {
      return Future.succeededFuture(Collections.emptyList());
    }

    List<Future<CheckResult>> futures =
        phaseChecks.stream()
            .map(
                check ->
                    check
                        .execute(vertx)
                        .recover(
                            t ->
                                Future.succeededFuture(
                                    CheckResult.error(
                                        check.getClass().getSimpleName(), t.getMessage()))))
            .toList();

    return Future.all(futures)
        .map(
            cf -> {
              List<CheckResult> results = cf.list();
              for (CheckResult result : results) {
                logResult(result);
              }
              return results;
            });
  }

  private void logResult(CheckResult result) {
    switch (result.status()) {
      case PASSED -> logger.info("[DOCTOR] PASSED — {}: {}", result.name(), result.message());
      case WARNING -> logger.warn("[DOCTOR] WARNING — {}: {}", result.name(), result.message());
      case ERROR -> logger.error("[DOCTOR] ERROR — {}: {}", result.name(), result.message());
    }
  }
}
