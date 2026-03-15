package work.ganglia.infrastructure.internal.prompt.context;

import work.ganglia.port.internal.prompt.ContextFragment;
import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.skill.SkillRuntime;
import work.ganglia.port.internal.prompt.ContextSource;

import java.util.ArrayList;
import java.util.List;

public class SkillContextSource implements ContextSource {
    private final SkillRuntime skillRuntime;

    public SkillContextSource(SkillRuntime skillRuntime) {
        this.skillRuntime = skillRuntime;
    }

    @Override
    public Future<List<ContextFragment>> getFragments(SessionContext sessionContext) {
        if (skillRuntime == null) {
            return Future.succeededFuture(new ArrayList<>());
        }

        Future<String> skillsFuture = skillRuntime.getActiveSkillsPrompt(sessionContext);
        Future<String> suggestionsFuture = skillRuntime.suggestSkills(sessionContext);

        return Future.join(skillsFuture, suggestionsFuture).map(composite -> {
            List<ContextFragment> fragments = new ArrayList<>();
            String skills = composite.resultAt(0);
            String suggestions = composite.resultAt(1);

            if (!skills.isEmpty()) {
                fragments.add(ContextFragment.prunable("Active Skills", skills, ContextFragment.PRIORITY_SKILLS));
            }
            if (!suggestions.isEmpty()) {
                fragments.add(ContextFragment.prunable("Skill Suggestions", suggestions, ContextFragment.PRIORITY_SKILLS));
            }
            return fragments;
        });
    }
}
