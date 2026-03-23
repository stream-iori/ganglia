package work.ganglia.config;

/** Interface Segregation: Provides WebUi specific configuration. */
public interface WebUIConfigProvider {
  boolean isWebUIEnabled();

  int getWebUIPort();

  String getWebRoot();
}
