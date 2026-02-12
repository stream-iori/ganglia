package me.stream.ganglia.core.prompt.context;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.skills.SkillPromptInjector;
import me.stream.ganglia.skills.SkillSuggester;

import java.util.ArrayList;
import java.util.List;

public class SkillContextSource implements ContextSource {
    private final SkillPromptInjector skillInjector;
    private final SkillSuggester skillSuggester;

    public SkillContextSource(SkillPromptInjector skillInjector, SkillSuggester skillSuggester) {
        this.skillInjector = skillInjector;
        this.skillSuggester = skillSuggester;
    }

    @Override
    public Future<List<ContextFragment>> getFragments(SessionContext sessionContext) {
        Future<String> skillsFuture = skillInjector != null ?
                skillInjector.injectSkills(sessionContext.activeSkillIds()) : Future.succeededFuture("");

        Future<String> suggestionsFuture = skillSuggester != null ?
                skillSuggester.suggestSkills(".", sessionContext.activeSkillIds()) : Future.succeededFuture("");

        return Future.join(skillsFuture, suggestionsFuture).map(composite -> {
            List<ContextFragment> fragments = new ArrayList<>();
            String skills = composite.resultAt(0);
            String suggestions = composite.resultAt(1);

            if (!skills.isEmpty()) {
                fragments.add(new ContextFragment("Active Skills", skills, ContextFragment.PRIORITY_SKILLS, false));
            }
            if (!suggestions.isEmpty()) {
                fragments.add(new ContextFragment("Skill Suggestions", suggestions, ContextFragment.PRIORITY_SKILLS, false));
            }
            return fragments;
        });
    }
}
