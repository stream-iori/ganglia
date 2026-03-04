package work.ganglia.skills;

import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultSkillService implements SkillService {
    private static final Logger log = LoggerFactory.getLogger(DefaultSkillService.class);

    private final List<SkillLoader> loaders;
    private final Map<String, SkillManifest> skills = new ConcurrentHashMap<>();

    public DefaultSkillService(List<SkillLoader> loaders) {
        this.loaders = loaders;
    }

    public DefaultSkillService(SkillLoader... loaders) {
        this.loaders = Arrays.asList(loaders);
    }

    @Override
    public Future<Void> init() {
        return reload();
    }

    @Override
    public List<SkillManifest> getAvailableSkills() {
        return new ArrayList<>(skills.values());
    }

    @Override
    public Optional<SkillManifest> getSkill(String id) {
        return Optional.ofNullable(skills.get(id));
    }

    @Override
    public Future<Void> reload() {
        List<Future<List<SkillManifest>>> futures = loaders.stream()
            .map(SkillLoader::load)
            .toList();

        return Future.join(futures).map(composite -> {
            skills.clear();
            for (int i = 0; i < futures.size(); i++) {
                List<SkillManifest> loaded = composite.resultAt(i);
                for (SkillManifest m : loaded) {
                    skills.put(m.id(), m);
                    log.info("Registered skill: {} ({})", m.name(), m.id());
                }
            }
            return null;
        });
    }
}
