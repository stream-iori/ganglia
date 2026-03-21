package work.ganglia.ui;

import io.vertx.core.json.JsonObject;
import java.io.PrintWriter;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import work.ganglia.kernel.todo.TaskStatus;
import work.ganglia.kernel.todo.ToDoItem;
import work.ganglia.kernel.todo.ToDoList;

/**
 * Renders the task panel in the fixed bottom area of the terminal. Shows active tasks with elapsed
 * time and completed/pending task tree.
 */
public class TaskPanelRenderer {

  private volatile ToDoList currentPlan;
  private volatile long turnStartTime;
  private Timer elapsedTimer;

  public TaskPanelRenderer() {}

  /**
   * Returns the number of rows needed to render the task panel. Returns 0 if there are no tasks.
   */
  public int getHeight(int termHeight) {
    ToDoList plan = currentPlan; // single volatile read
    if (plan == null || plan.isEmpty()) {
      return 0;
    }
    List<ToDoItem> items = plan.items();
    // header line + one line per item, capped at 1/3 of terminal height
    int needed = 1 + items.size();
    return Math.min(needed, termHeight / 3);
  }

  /** Updates the current plan state. */
  public void updatePlan(ToDoList plan) {
    this.currentPlan = plan;
  }

  /**
   * Deserializes a ToDoList from EventBus data map. EventBus serializes records as LinkedHashMap;
   * this reconstructs the proper type.
   */
  @SuppressWarnings("unchecked")
  public void updatePlanFromData(Object planData) {
    if (planData instanceof ToDoList plan) {
      this.currentPlan = plan;
    } else if (planData != null) {
      this.currentPlan = JsonObject.mapFrom(planData).mapTo(ToDoList.class);
    }
  }

  /** Records the start time for elapsed display. */
  public void onTurnStarted() {
    this.turnStartTime = System.currentTimeMillis();
  }

  /** Starts a 1-second timer to refresh the elapsed display. */
  public void startElapsedTimer(Runnable refreshCallback) {
    stopElapsedTimer();
    elapsedTimer = new Timer("task-panel-elapsed", true);
    elapsedTimer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            ToDoList plan = currentPlan; // single volatile read
            if (plan != null && !plan.isEmpty() && turnStartTime > 0) {
              refreshCallback.run();
            }
          }
        },
        1000,
        1000);
  }

  /** Stops the elapsed timer. */
  public void stopElapsedTimer() {
    if (elapsedTimer != null) {
      elapsedTimer.cancel();
      elapsedTimer = null;
    }
  }

  /**
   * Renders the task panel at the given starting row using absolute cursor positioning.
   *
   * @param writer terminal writer
   * @param startRow first row of the task panel (1-based)
   * @param cols terminal width
   * @param maxRows maximum rows available for the panel
   */
  public void renderAt(PrintWriter writer, int startRow, int cols, int maxRows) {
    ToDoList plan = currentPlan; // single volatile read
    if (plan == null || plan.isEmpty() || maxRows <= 0) {
      return;
    }

    List<ToDoItem> items = plan.items();

    // Find active task for header
    ToDoItem activeTask =
        items.stream().filter(i -> i.status() == TaskStatus.IN_PROGRESS).findFirst().orElse(null);

    // Render header
    writer.print(String.format("\033[%d;1H\033[2K", startRow));
    if (activeTask != null) {
      String elapsed = formatElapsed();
      String header =
          "\033[1m* "
              + truncate(activeTask.description(), cols - elapsed.length() - 5)
              + " "
              + elapsed
              + "\033[0m";
      writer.print(header);
    } else {
      writer.print("\033[1m* Tasks\033[0m");
    }

    // Render items (up to maxRows - 1 for header)
    int availableRows = maxRows - 1;
    int itemCount = Math.min(items.size(), availableRows);

    for (int i = 0; i < itemCount; i++) {
      ToDoItem item = items.get(i);
      int row = startRow + 1 + i;
      writer.print(String.format("\033[%d;1H\033[2K", row));

      boolean isLast = (i == itemCount - 1);
      String connector = isLast ? "  \u2514\u2500 " : "  \u251c\u2500 ";
      String line = connector + formatItem(item, cols - 6);
      writer.print(line);
    }

    // If there are more items than we can display
    if (items.size() > itemCount) {
      int remaining = items.size() - itemCount;
      int row = startRow + itemCount;
      writer.print(String.format("\033[%d;1H\033[2K", row));
      writer.print("  \033[2m... " + remaining + " more\033[0m");
    }
  }

  private String formatItem(ToDoItem item, int maxWidth) {
    return switch (item.status()) {
      case DONE -> "\033[32m\033[9m\u2714 " + truncate(item.description(), maxWidth) + "\033[0m";
      case IN_PROGRESS -> "\033[33m\u25a0 " + truncate(item.description(), maxWidth) + "\033[0m";
      case FAILED -> "\033[31m\u2717 " + truncate(item.description(), maxWidth) + "\033[0m";
      case SKIPPED -> "\033[2m\u2013 " + truncate(item.description(), maxWidth) + "\033[0m";
      default -> "\033[2m\u25a1 " + truncate(item.description(), maxWidth) + "\033[0m";
    };
  }

  private String formatElapsed() {
    if (turnStartTime <= 0) return "";
    long elapsed = System.currentTimeMillis() - turnStartTime;
    long seconds = elapsed / 1000;
    if (seconds < 60) {
      return "(" + seconds + "s)";
    }
    long minutes = seconds / 60;
    seconds = seconds % 60;
    return "(" + minutes + "m " + seconds + "s)";
  }

  private String truncate(String text, int maxWidth) {
    if (text == null) return "";
    if (maxWidth <= 3) return "...";
    if (text.length() <= maxWidth) return text;
    return text.substring(0, maxWidth - 3) + "...";
  }

  public ToDoList getCurrentPlan() {
    return currentPlan;
  }
}
