package work.ganglia.port.internal.state;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class AgentSignalTest {

  @Test
  void initialState_notAborted() {
    AgentSignal signal = new AgentSignal();
    assertFalse(signal.isAborted());
  }

  @Test
  void abort_setsAbortedTrue() {
    AgentSignal signal = new AgentSignal();
    signal.abort();
    assertTrue(signal.isAborted());
  }

  @Test
  void abort_idempotent_callbacksRunOnce() {
    AgentSignal signal = new AgentSignal();
    AtomicInteger counter = new AtomicInteger(0);
    signal.onAbort(counter::incrementAndGet);

    signal.abort();
    signal.abort(); // second call should be a no-op

    assertEquals(1, counter.get(), "Callback should only run once regardless of abort() calls");
  }

  @Test
  void onAbort_callbackInvokedOnAbort() {
    AgentSignal signal = new AgentSignal();
    AtomicInteger counter = new AtomicInteger(0);
    signal.onAbort(counter::incrementAndGet);

    assertEquals(0, counter.get());
    signal.abort();
    assertEquals(1, counter.get());
  }

  @Test
  void onAbort_afterAlreadyAborted_executesImmediately() {
    AgentSignal signal = new AgentSignal();
    signal.abort();

    AtomicInteger counter = new AtomicInteger(0);
    signal.onAbort(counter::incrementAndGet); // already aborted — should run now

    assertEquals(1, counter.get(), "onAbort after abort must execute callback immediately");
  }

  @Test
  void abort_callbackExceptionIsolated_otherCallbacksStillRun() {
    AgentSignal signal = new AgentSignal();
    AtomicInteger counter = new AtomicInteger(0);

    signal.onAbort(
        () -> {
          throw new RuntimeException("boom");
        });
    signal.onAbort(counter::incrementAndGet);

    assertDoesNotThrow(signal::abort, "abort() must not propagate callback exceptions");
    assertEquals(1, counter.get(), "Second callback must still run even if first threw");
  }

  @Test
  void abort_multipleCallbacks_allInvoked() {
    AgentSignal signal = new AgentSignal();
    AtomicInteger counter = new AtomicInteger(0);

    signal.onAbort(counter::incrementAndGet);
    signal.onAbort(counter::incrementAndGet);
    signal.onAbort(counter::incrementAndGet);

    signal.abort();

    assertEquals(3, counter.get());
  }
}
