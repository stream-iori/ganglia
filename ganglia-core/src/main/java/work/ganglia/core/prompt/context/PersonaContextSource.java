package work.ganglia.core.prompt.context;

import io.vertx.core.Future;
import work.ganglia.core.model.SessionContext;

import java.util.List;

public class PersonaContextSource implements ContextSource {
    @Override
    public Future<List<ContextFragment>> getFragments(SessionContext sessionContext) {
        String persona = """
                You are Ganglia, an advanced AI software engineer.
                You are running in a CLI environment.
                """;
        return Future.succeededFuture(List.of(new ContextFragment("Persona", persona, ContextFragment.PRIORITY_PERSONA, true)));
    }
}
