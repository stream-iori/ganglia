package work.ganglia.trading.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * BM25-indexed memory of (situation, advice) pairs with optional time-weighted retrieval (TWR).
 * Each role (Bull/Bear/Trader/Judge/PM) maintains an isolated instance.
 */
public class FinancialSituationMemory {

  /** A single stored memory entry. */
  record Entry(String situation, String advice, Instant createdAt) {}

  /** A retrieval result with similarity score. */
  public record MemoryMatch(String situation, String advice, double score) {}

  private final String roleName;
  private final boolean enableTwr;
  private final int halfLifeDays;
  private final List<Entry> entries = new ArrayList<>();
  private final BM25Index index = new BM25Index();

  public FinancialSituationMemory(String roleName, boolean enableTwr, int halfLifeDays) {
    this.roleName = roleName;
    this.enableTwr = enableTwr;
    this.halfLifeDays = halfLifeDays;
  }

  /** Add a situation-advice pair to memory. */
  public synchronized void addSituation(String situation, String advice) {
    entries.add(new Entry(situation, advice, Instant.now()));
    index.add(situation);
  }

  /** Retrieve the top-K most similar past situations. */
  public synchronized List<MemoryMatch> retrieve(String currentSituation, int topK) {
    if (entries.isEmpty()) return List.of();

    double[] rawScores = index.score(currentSituation);
    double[] scores = BM25Index.normalize(rawScores);

    // Apply time-weighted decay if enabled
    if (enableTwr && halfLifeDays > 0) {
      Instant now = Instant.now();
      for (int i = 0; i < scores.length; i++) {
        long ageDays = java.time.Duration.between(entries.get(i).createdAt(), now).toDays();
        double decay = Math.pow(0.5, (double) ageDays / halfLifeDays);
        scores[i] *= decay;
      }
    }

    // Find top-K indices by score descending
    List<Integer> indices = new ArrayList<>();
    for (int i = 0; i < scores.length; i++) {
      if (scores[i] > 0) indices.add(i);
    }
    int finalK = Math.min(topK, indices.size());
    final double[] finalScores = scores;
    indices.sort(Comparator.comparingDouble((Integer i) -> finalScores[i]).reversed());

    List<MemoryMatch> results = new ArrayList<>();
    for (int i = 0; i < finalK; i++) {
      int idx = indices.get(i);
      Entry e = entries.get(idx);
      results.add(new MemoryMatch(e.situation(), e.advice(), finalScores[idx]));
    }
    return results;
  }

  /** Number of stored memories. */
  public synchronized int size() {
    return entries.size();
  }

  public String roleName() {
    return roleName;
  }
}
