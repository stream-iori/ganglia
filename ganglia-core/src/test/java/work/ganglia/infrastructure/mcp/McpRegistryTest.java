package work.ganglia.infrastructure.mcp;

import org.junit.jupiter.api.Test;
import work.ganglia.port.mcp.McpClient;

import java.util.List;

import static org.mockito.Mockito.*;

public class McpRegistryTest {

    @Test
    void testCloseClosesAllClients() throws Exception {
        McpClient client1 = mock(McpClient.class);
        McpClient client2 = mock(McpClient.class);
        
        McpRegistry registry = new McpRegistry(List.of(), List.of(client1, client2));
        
        registry.close();
        
        verify(client1, times(1)).close();
        verify(client2, times(1)).close();
    }

    @Test
    void testCloseHandlesExceptionsGracefully() throws Exception {
        McpClient client1 = mock(McpClient.class);
        McpClient client2 = mock(McpClient.class);
        
        doThrow(new RuntimeException("Failed")).when(client1).close();
        
        McpRegistry registry = new McpRegistry(List.of(), List.of(client1, client2));
        
        registry.close();
        
        verify(client1, times(1)).close();
        verify(client2, times(1)).close();
    }
}
