package app.dinacharya.wallpaper;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Tasks;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * "Sign in with Google" through the phone's own account list (Google Play services), the way
 * WhatsApp asks for your account. Asks only for the app's hidden Drive space, your email and name.
 *
 * Google matches the app by its package name and signing key, which must be registered once in
 * Google Cloud (see the README). Everything here is kept in one class so that, if a phone has no
 * Play services, the code sign-in can be used instead.
 */
final class PickerAuth implements Tokens {
    static final String SCOPE_EMAIL = "https://www.googleapis.com/auth/userinfo.email";
    static final String SCOPE_PROFILE = "https://www.googleapis.com/auth/userinfo.profile";

    interface Callback {
        void token(String accessToken);

        void failed(String message, boolean useCodeInstead);
    }

    private final Context c;
    private String last;

    PickerAuth(Context context) {
        c = context.getApplicationContext();
    }

    static AuthorizationRequest request() {
        List<Scope> scopes = Arrays.asList(new Scope(CloudDrive.SCOPE_APPDATA), new Scope(SCOPE_EMAIL), new Scope(SCOPE_PROFILE));
        return AuthorizationRequest.builder().setRequestedScopes(scopes).build();
    }

    /** The SHA-1 of the signing key in this project (app/signing/dinacharya.jks), as given in the README. */
    static final String EXPECTED_SHA1 = "48:94:D3:1F:BE:2F:4A:60:ED:4A:B2:1F:E9:00:61:FE:3D:8C:E8:B8";

    /** What to say when Google's screen closes without an account. */
    static final String CLOSED = "Google's sign-in screen closed without an account. If you did not close it yourself, "
            + "Google is not accepting this app yet. Tap \u201cCheck setup\u201d to compare this app with your Google Cloud settings.";

    /** Explains a Google error in plain words, with its code so it can be looked up. */
    static String explain(Exception e) {
        if (e instanceof ApiException) {
            int code = ((ApiException) e).getStatusCode();
            String m = e.getMessage();
            String detail = (m == null || m.trim().isEmpty()) ? "" : "\n\nGoogle said: " + m.trim();
            if (m != null && m.contains("UNREGISTERED_ON_API_CONSOLE")) {
                return "Google has no record of this app (code " + code + ", UNREGISTERED_ON_API_CONSOLE). In Google Cloud, "
                        + "the Android client for package app.dinacharya.wallpaper with this app's SHA-1 is missing, or it is in "
                        + "a different project than the Drive API. Tap “Check setup” for the exact values to enter." + detail;
            }
            if (code == 10) {
                return "Google does not recognise this app (developer error 10). The package name or SHA-1 registered in "
                        + "Google Cloud does not match this app, or the Android client is in another project. Tap \u201cCheck setup\u201d." + detail;
            }
            if (code == 7) return "No internet connection. Try again when you are online." + detail;
            if (code == 16 || code == 12501) return CLOSED + "\n\n(Code " + code + ")" + detail;
            if (code == 17) return "Google Play services cannot do this sign-in on this phone (code 17). Use \u201cSign in with a code\u201d." + detail;
            return "Google sign-in failed (code " + code + ")." + detail;
        }
        String m = e.getMessage();
        return "Google sign-in failed" + (m == null ? "." : ": " + m);
    }

    /** The SHA-1 fingerprint of the key this installed app was signed with, like 48:94:D3:..., or "" if unknown. */
    @SuppressWarnings("deprecation")
    static String signingSha1(Context c) {
        try {
            android.content.pm.PackageManager pm = c.getPackageManager();
            android.content.pm.Signature[] sigs;
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                android.content.pm.PackageInfo pi = pm.getPackageInfo(c.getPackageName(), android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
                android.content.pm.SigningInfo si = pi.signingInfo;
                if (si == null) return "";
                sigs = si.hasMultipleSigners() ? si.getApkContentsSigners() : si.getSigningCertificateHistory();
            } else {
                sigs = pm.getPackageInfo(c.getPackageName(), android.content.pm.PackageManager.GET_SIGNATURES).signatures;
            }
            if (sigs == null || sigs.length == 0) return "";
            byte[] d = java.security.MessageDigest.getInstance("SHA-1").digest(sigs[sigs.length - 1].toByteArray());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < d.length; i++) {
                if (i > 0) sb.append(':');
                sb.append(String.format(java.util.Locale.ROOT, "%02X", d[i] & 0xFF));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Starts the sign-in from a screen. Shows the account list when Google needs the user. */
    static void begin(final Activity a, final int requestCode, final Callback cb) {
        try {
            Identity.getAuthorizationClient(a).authorize(request())
                    .addOnSuccessListener(result -> {
                        if (result.hasResolution()) {
                            PendingIntent pi = result.getPendingIntent();
                            if (pi == null) {
                                cb.failed("Google sign-in could not start.", true);
                                return;
                            }
                            try {
                                a.startIntentSenderForResult(pi.getIntentSender(), requestCode, null, 0, 0, 0);
                            } catch (IntentSender.SendIntentException ex) {
                                cb.failed("Google sign-in could not start.", true);
                            }
                        } else {
                            cb.token(result.getAccessToken());
                        }
                    })
                    .addOnFailureListener(ex -> cb.failed(explain(ex), true));
        } catch (RuntimeException ex) {
            // No Google Play services on this phone, or an old version.
            cb.failed("This phone's Google Play services could not start the sign-in. Use “Sign in with a code” instead.", true);
        }
    }

    /** Reads the answer when the account list closes. Returns the access token. */
    static String finish(Activity a, Intent data) throws ApiException {
        AuthorizationResult r = Identity.getAuthorizationClient(a).getAuthorizationResultFromIntent(data);
        return r.getAccessToken();
    }

    /** A token without showing anything; for backups in the background. Must not run on the main thread. */
    @Override
    public String token(boolean fresh) throws IOException {
        if (fresh && last != null) {
            try {
                GoogleAuthUtil.clearToken(c, last);
            } catch (Exception ignored) {
                // a new token is asked for below either way
            }
        }
        try {
            AuthorizationResult r = Tasks.await(Identity.getAuthorizationClient(c).authorize(request()), 60, TimeUnit.SECONDS);
            if (r.hasResolution() || r.getAccessToken() == null) {
                throw new CloudException("Google needs you to sign in again. Open Backup and tap Sign in.", 401, true);
            }
            last = r.getAccessToken();
            return last;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            boolean again = cause instanceof ApiException && ((ApiException) cause).getStatusCode() != 7;
            throw new CloudException(cause instanceof Exception ? explain((Exception) cause) : "Google sign-in failed.", 0, again);
        } catch (TimeoutException e) {
            throw new CloudException("Google sign-in took too long. Will try again later.", 0, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CloudException("Interrupted.", 0, false);
        } catch (RuntimeException e) {
            throw new CloudException("Google Play services are not available on this phone.", 0, true);
        }
    }
}
