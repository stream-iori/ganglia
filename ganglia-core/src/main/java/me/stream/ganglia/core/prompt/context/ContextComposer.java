package me.stream.ganglia.core.prompt.context;

import me.stream.ganglia.memory.TokenCounter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Composes fragments into a final prompt string, applying pruning based on priority.
 */
public class ContextComposer {
    private final TokenCounter tokenCounter;

    public ContextComposer(TokenCounter tokenCounter) {
        this.tokenCounter = tokenCounter;
    }

    public String compose(List<ContextFragment> fragments, int maxTokens) {
        // Sort by priority (ascending)
        List<ContextFragment> sorted = fragments.stream()
                .sorted(Comparator.comparingInt(ContextFragment::priority))
                .toList();

        // Try to fit as many as possible
        List<ContextFragment> toInclude = new ArrayList<>(sorted);
        
        while (calculateTokens(toInclude) > maxTokens && hasPrunable(toInclude)) {
            // Prune from the bottom (highest priority number)
            pruneOne(toInclude);
        }

        return toInclude.stream()
                .map(f -> "## " + f.name() + "\n" + f.content())
                .collect(Collectors.joining("\n\n"));
    }

    private int calculateTokens(List<ContextFragment> fragments) {
        String combined = fragments.stream()
                .map(f -> f.name() + "\n" + f.content())
                .collect(Collectors.joining("\n\n"));
        return tokenCounter.count(combined);
    }

    private boolean hasPrunable(List<ContextFragment> fragments) {
        return fragments.stream().anyMatch(f -> !f.isMandatory());
    }

    private void pruneOne(List<ContextFragment> fragments) {
        // Find the non-mandatory fragment with the largest priority number
        ContextFragment victim = fragments.stream()
                .filter(f -> !f.isMandatory())
                .max(Comparator.comparingInt(ContextFragment::priority))
                .orElse(null);

        if (victim != null) {
            fragments.remove(victim);
        }
    }
}