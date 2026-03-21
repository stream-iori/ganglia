package work.ganglia.ui;

import java.util.List;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

/** JLine completer that provides slash-command candidates when input starts with '/'. */
public class SlashCommandCompleter implements Completer {

  private static final List<CommandDef> COMMANDS =
      List.of(
          new CommandDef("/help", "List available commands", "Info"),
          new CommandDef("/expand", "Toggle expand/collapse", "Display"),
          new CommandDef("/clear", "Clear the screen", "Session"),
          new CommandDef("/exit", "Exit Ganglia", "Session"));

  @Override
  public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
    String word = line.word();
    if (word == null || !word.startsWith("/")) {
      return;
    }
    for (CommandDef cmd : COMMANDS) {
      if (cmd.name.startsWith(word)) {
        candidates.add(
            new Candidate(cmd.name, cmd.name, cmd.group, cmd.description, null, null, true));
      }
    }
  }

  private record CommandDef(String name, String description, String group) {}
}
