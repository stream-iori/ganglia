package work.ganglia.coding.tool;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;

/** Validates URLs to prevent SSRF (Server-Side Request Forgery) attacks. */
public final class UrlValidator {

  private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
  private static final Set<String> BLOCKED_ADDRESSES = Set.of("169.254.169.254", "0.0.0.0");

  private UrlValidator() {}

  /**
   * Validates that the URL is safe to fetch: only http/https scheme, no private/loopback IPs.
   *
   * @return null if valid, or an error message string if invalid
   */
  public static String validate(String url) {
    if (url == null || url.isBlank()) {
      return "URL is empty";
    }

    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      return "Invalid URL: " + e.getMessage();
    }

    String scheme = uri.getScheme();
    if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
      return "Blocked scheme: only http and https are allowed";
    }

    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      return "URL has no host";
    }

    InetAddress address;
    try {
      address = InetAddress.getByName(host);
    } catch (UnknownHostException e) {
      return "Cannot resolve host: " + host;
    }

    String hostAddress = address.getHostAddress();
    if (BLOCKED_ADDRESSES.contains(hostAddress)) {
      return "Blocked address: " + hostAddress;
    }

    if (address.isLoopbackAddress()) {
      return "Blocked address: loopback addresses are not allowed";
    }

    if (address.isLinkLocalAddress()) {
      return "Blocked address: link-local addresses are not allowed";
    }

    if (address.isSiteLocalAddress()) {
      return "Blocked address: private network addresses are not allowed";
    }

    return null;
  }
}
