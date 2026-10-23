package app.dinacharya.wallpaper;

import java.io.IOException;

/** Hands out Google access tokens. Two kinds exist: the account picker and the code sign-in. */
public interface Tokens {
    /**
     * A valid access token.
     *
     * @param fresh true after Google refused the last one, so a new one must be fetched
     * @throws CloudException with {@code signInExpired} when the user has to sign in again
     */
    String token(boolean fresh) throws IOException;

    /** Something went wrong talking to Google. */
    final class CloudException extends IOException {
        public final int code;
        /** The sign-in no longer works; the user has to sign in again. */
        public final boolean signInExpired;

        public CloudException(String msg, int code, boolean expired) {
            super(msg);
            this.code = code;
            this.signInExpired = expired;
        }
    }
}
