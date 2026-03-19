package work.ganglia.ui;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;

/**
 * JLine completer that provides slash-command candidates when input starts with '/'.
 */
public class SlashCommandCompleter implements Completer {

    private static final List<CommandDef> COMMANDS = List.of(
            new CommandDef("/help", "List available commands"),
            new CommandDef("/clear", "Clear the screen"),
            new CommandDef("/expand", "Toggle expand/collapse response"),
            new CommandDef("/exit", "Exit Ganglia")
    );

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String word = line.word();
        if (word == null || !word.startsWith("/")) {
            return;
        }
        for (CommandDef cmd : COMMANDS) {
            if (cmd.name.startsWith(word)) {
                candidates.add(new Candidate(cmd.name, cmd.name, "Commands", cmd.description, null, null, true));
            }
        }
    }

    private record CommandDef(String name, String description) {}
}
