package work.ganglia.trading.memory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure Java implementation of the BM25 Okapi ranking algorithm. No external dependencies. Provides
 * lexical similarity scoring for document retrieval.
 */
public final class BM25Index {

  private static final Pattern TOKEN_PATTERN = Pattern.compile("\\b\\w+\\b");
  private static final double K1 = 1.5;
  private static final double B = 0.75;

  private final List<List<String>> corpus = new ArrayList<>();
  private final Map<String, Integer> docFreq = new HashMap<>();
  private double avgDocLength;

  /** Add a document to the index. */
  public void add(String document) {
    List<String> tokens = tokenize(document);
    corpus.add(tokens);

    // Update document frequency (count each term once per doc)
    tokens.stream().distinct().forEach(t -> docFreq.merge(t, 1, Integer::sum));

    // Recompute average document length
    avgDocLength = corpus.stream().mapToInt(List::size).average().orElse(0);
  }

  /** Score all indexed documents against the given query. Returns array parallel to add order. */
  public double[] score(String query) {
    if (corpus.isEmpty()) return new double[0];

    List<String> queryTokens = tokenize(query);
    int n = corpus.size();
    double[] scores = new double[n];

    for (String qt : queryTokens) {
      int df = docFreq.getOrDefault(qt, 0);
      if (df == 0) continue;

      // IDF: log((N - df + 0.5) / (df + 0.5) + 1)
      double idf = Math.log((n - df + 0.5) / (df + 0.5) + 1.0);

      for (int i = 0; i < n; i++) {
        List<String> doc = corpus.get(i);
        long tf = doc.stream().filter(qt::equals).count();
        if (tf == 0) continue;

        double docLen = doc.size();
        // BM25 term score
        double tfNorm = (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * docLen / avgDocLength));
        scores[i] += idf * tfNorm;
      }
    }
    return scores;
  }

  /** Return the number of indexed documents. */
  public int size() {
    return corpus.size();
  }

  /** Normalize raw BM25 scores to [0, 1] range. */
  public static double[] normalize(double[] scores) {
    if (scores.length == 0) return scores;
    double max = 0;
    for (double s : scores) {
      if (s > max) max = s;
    }
    if (max == 0) return scores;
    double[] normalized = new double[scores.length];
    for (int i = 0; i < scores.length; i++) {
      normalized[i] = scores[i] / max;
    }
    return normalized;
  }

  static List<String> tokenize(String text) {
    List<String> tokens = new ArrayList<>();
    Matcher m = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
    while (m.find()) {
      tokens.add(m.group());
    }
    return tokens;
  }
}
