package work.ganglia.ui;

import org.jline.reader.Candidate;
import org.jline.reader.ParsedLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SlashCommandCompleterTest {

    private SlashCommandCompleter completer;

    @BeforeEach
    void setUp() {
        completer = new SlashCommandCompleter();
    }

    @Test
    void testSlashPrefixReturnsCandidates() {
        List<Candidate> candidates = new ArrayList<>();
        completer.complete(null, parsedLine("/"), candidates);

        assertFalse(candidates.isEmpty(), "Should return candidates for '/' prefix");
        List<String> names = candidates.stream().map(Candidate::value).toList();
        assertTrue(names.contains("/help"));
        assertTrue(names.contains("/clear"));
        assertTrue(names.contains("/expand"));
        assertTrue(names.contains("/exit"));
    }

    @Test
    void testSlashPartialFiltersCandidates() {
        List<Candidate> candidates = new ArrayList<>();
        completer.complete(null, parsedLine("/he"), candidates);

        assertEquals(1, candidates.size());
        assertEquals("/help", candidates.get(0).value());
    }

    @Test
    void testSlashExactMatchReturnsSingle() {
        List<Candidate> candidates = new ArrayList<>();
        completer.complete(null, parsedLine("/exit"), candidates);

        assertEquals(1, candidates.size());
        assertEquals("/exit", candidates.get(0).value());
    }

    @Test
    void testNonSlashPrefixReturnsNothing() {
        List<Candidate> candidates = new ArrayList<>();
        completer.complete(null, parsedLine("hello"), candidates);

        assertTrue(candidates.isEmpty(), "Should not return candidates for non-/ prefix");
    }

    @Test
    void testEmptyWordReturnsNothing() {
        List<Candidate> candidates = new ArrayList<>();
        completer.complete(null, parsedLine(""), candidates);

        assertTrue(candidates.isEmpty(), "Should not return candidates for empty input");
    }

    @Test
    void testCandidatesHaveGroupAndDescription() {
        List<Candidate> candidates = new ArrayList<>();
        completer.complete(null, parsedLine("/"), candidates);

        for (Candidate c : candidates) {
            assertNotNull(c.group(), "All candidates should have a group");
            assertFalse(c.group().isEmpty(), "Group should not be empty");
            assertNotNull(c.descr(), "All candidates should have a description");
        }
    }

    private ParsedLine parsedLine(String word) {
        return new ParsedLine() {
            @Override public String word() { return word; }
            @Override public int wordCursor() { return word.length(); }
            @Override public int wordIndex() { return 0; }
            @Override public List<String> words() { return List.of(word); }
            @Override public String line() { return word; }
            @Override public int cursor() { return word.length(); }
        };
    }
}
