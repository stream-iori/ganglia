package work.ganglia.trading.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class BM25IndexTest {

  @Test
  void emptyIndexReturnsEmptyScores() {
    BM25Index index = new BM25Index();
    assertEquals(0, index.score("query").length);
  }

  @Test
  void singleDocumentScoring() {
    BM25Index index = new BM25Index();
    index.add("apple stock price earnings report");
    double[] scores = index.score("apple earnings");
    assertEquals(1, scores.length);
    assertTrue(scores[0] > 0, "Matching document should have positive score");
  }

  @Test
  void noMatchReturnsZero() {
    BM25Index index = new BM25Index();
    index.add("apple stock price");
    double[] scores = index.score("bitcoin crypto");
    assertEquals(0.0, scores[0]);
  }

  @Test
  void relevantDocScoresHigher() {
    BM25Index index = new BM25Index();
    index.add("apple stock price earnings report quarterly revenue"); // relevant
    index.add("weather forecast rain temperature humidity wind"); // irrelevant
    index.add("apple earnings growth revenue profit margin"); // very relevant

    double[] scores = index.score("apple earnings revenue");
    assertTrue(scores[0] > scores[1], "Apple doc should score higher than weather doc");
    assertTrue(scores[2] > scores[1], "Apple earnings doc should score higher than weather doc");
  }

  @Test
  void normalizeScalesToZeroOne() {
    double[] raw = {0.0, 3.0, 1.5, 6.0};
    double[] norm = BM25Index.normalize(raw);
    assertEquals(0.0, norm[0]);
    assertEquals(0.5, norm[1]);
    assertEquals(0.25, norm[2]);
    assertEquals(1.0, norm[3]);
  }

  @Test
  void normalizeAllZeros() {
    double[] raw = {0.0, 0.0, 0.0};
    double[] norm = BM25Index.normalize(raw);
    assertEquals(0.0, norm[0]);
    assertEquals(0.0, norm[1]);
  }

  @Test
  void tokenizeBasic() {
    List<String> tokens = BM25Index.tokenize("Hello World! Test-123 foo_bar");
    assertTrue(tokens.contains("hello"));
    assertTrue(tokens.contains("world"));
    assertTrue(tokens.contains("test"));
    assertTrue(tokens.contains("123"));
    assertTrue(tokens.contains("foo_bar"));
  }

  @Test
  void sizeTracksDocuments() {
    BM25Index index = new BM25Index();
    assertEquals(0, index.size());
    index.add("doc one");
    assertEquals(1, index.size());
    index.add("doc two");
    assertEquals(2, index.size());
  }
}
