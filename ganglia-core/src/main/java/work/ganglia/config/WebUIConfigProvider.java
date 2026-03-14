package work.ganglia.config;

/**
 * Interface Segregation: Provides WebUI specific configuration.
 */
public interface WebUIConfigProvider {
    boolean isWebUIEnabled();
    int getWebUIPort();
    String getWebRoot();
}
