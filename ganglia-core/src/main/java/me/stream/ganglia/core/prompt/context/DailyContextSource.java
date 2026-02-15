package me.stream.ganglia.core.prompt.context;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.core.model.SessionContext;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * Sources context from the daily journal file.
 */
public class DailyContextSource implements ContextSource {
    private final Vertx vertx;
    private final String basePath;

    public DailyContextSource(Vertx vertx, String basePath) {
        this.vertx = vertx;
        this.basePath = basePath;
    }

    @Override
    public Future<List<ContextFragment>> getFragments(SessionContext context) {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String filePath = basePath + "/daily-" + dateStr + ".md";

        return vertx.fileSystem().exists(filePath)
            .compose(exists -> {
                if (!exists) return Future.succeededFuture(Collections.emptyList());
                return vertx.fileSystem().readFile(filePath)
                    .map(buffer -> {
                        String content = buffer.toString();
                        return List.of(new ContextFragment("Daily Journal", content, 9, false));
                    });
            });
    }
}
