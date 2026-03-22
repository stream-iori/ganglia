package work.ganglia.config;

/** Interface Segregation: Provides WebUi specific configuration. */
public interface WebUiConfigProvider {
  boolean isWebUiEnabled();

  int getWebUiPort();

  String getWebRoot();
}
