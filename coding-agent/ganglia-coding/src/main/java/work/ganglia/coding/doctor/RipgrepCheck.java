package work.ganglia.coding.doctor;

import java.util.List;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import work.ganglia.port.internal.doctor.CheckResult;
import work.ganglia.port.internal.doctor.DoctorCheck;
import work.ganglia.util.ProcessOptions;
import work.ganglia.util.VertxProcess;

/** Checks whether ripgrep (rg) is available on the system PATH. */
public class RipgrepCheck implements DoctorCheck {

  private static final String NAME = "ripgrep";

  @Override
  public Phase phase() {
    return Phase.STARTUP;
  }

  @Override
  public Future<CheckResult> execute(Vertx vertx) {
    return VertxProcess.execute(
            vertx, List.of("rg", "--version"), new ProcessOptions(null, 5000, 1024), null)
        .map(
            result -> {
              if (result.succeeded()) {
                String firstLine =
                    result.output().lines().findFirst().orElse(result.output().trim());
                return CheckResult.passed(NAME, firstLine);
              }
              return CheckResult.warning(NAME, "rg exited with code " + result.exitCode());
            })
        .recover(
            t ->
                Future.succeededFuture(
                    CheckResult.warning(
                        NAME, "rg not found on PATH; grep_search will use grep fallback")));
  }
}
