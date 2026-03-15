package work.ganglia.infrastructure.internal.prompt.context;

import work.ganglia.port.internal.prompt.ContextFragment;
import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.prompt.ContextSource;

import java.util.List;

public class PersonaContextSource implements ContextSource {
    private final String persona;

    public PersonaContextSource() {
        this("You are a helpful AI assistant.");
    }

    public PersonaContextSource(String persona) {
        this.persona = persona;
    }

    @Override
    public Future<List<ContextFragment>> getFragments(SessionContext sessionContext) {
        return Future.succeededFuture(List.of(
            ContextFragment.mandatory("Persona", persona, ContextFragment.PRIORITY_PERSONA)
        ));
    }
}
