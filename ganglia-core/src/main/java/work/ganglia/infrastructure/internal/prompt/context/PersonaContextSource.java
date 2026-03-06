package work.ganglia.infrastructure.internal.prompt.context;

import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.prompt.ContextSource;

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
