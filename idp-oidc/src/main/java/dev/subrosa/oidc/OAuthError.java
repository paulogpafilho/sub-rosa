package dev.subrosa.oidc;

/** Error messages contain static protocol text only, never submitted credentials. */
public final class OAuthError extends RuntimeException {
  private final String code;

  /** Creates a protocol error using a static error code without credential details. */
  public OAuthError(String code) {
    super(code);
    this.code = code;
  }

  /** Returns the protocol error code safe to include in an error response. */
  public String code() {
    return code;
  }
}
