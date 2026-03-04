package work.ganglia.swebench;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SWEBenchDatasetLoader {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static List<SWEBenchTask> loadFromJsonl(Path filePath) throws IOException {
        List<SWEBenchTask> tasks = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                SWEBenchTask task = mapper.readValue(line, SWEBenchTask.class);
                tasks.add(task);
            }
        }
        return tasks;
    }
}
