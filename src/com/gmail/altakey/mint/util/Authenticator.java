package com.gmail.altakey.mint.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.os.SystemClock;
import android.util.Log;

import com.apache.commons.codec.binary.Hex;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpResponse;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.InputStreamReader;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.HashMap;

import com.gmail.altakey.mint.konst.ConfigKey;

public class Authenticator {
    private static final String PREFERENCE_KEY = "auth_token";
    private static final String PREFERENCE_USER_ID = "auth_user_id";
    private static final String PREFERENCE_NOT_AFTER = "auth_not_after";
    private static final long TTL = 3 * 3600 * 1000;

    public static final String APP_NAME = "mint";
    public static final String APP_ID = "api4f508532c789a";

    private static String sToken;
    private static long sNotAfter;
    private static String sUserId;
    private Context mContext;
    private String mEmail;
    private String mPassword;

    public Authenticator(Context c, String email, String password) {
        mContext = c;
        mEmail = email;
        mPassword = password;
    }

    public static Authenticator create(Context context) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        String userId = pref.getString(ConfigKey.USER_ID, null);
        String userPassword = pref.getString(ConfigKey.USER_PASSWORD, null);
        return new Authenticator(context, userId, userPassword);
    }

    public static void purge(Activity activity) {
        Authenticator auth = new Authenticator(activity, null, null);
        auth.revoke();
        auth.unlink();
    }

    public String authenticate() throws IOException, BogusException, FailureException, ErrorException {
        openSession();
        if (bogus())
            throw new BogusException();
        return getKey();
    }

    public String openSession() throws IOException, BogusException, FailureException, ErrorException {
        Gson gson = new Gson();
        HttpClient client = new DefaultHttpClient();
        HttpGet req;
        HttpResponse response;
        HttpEntity entity;
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mContext);

        long now = System.currentTimeMillis();

        if (!invalid(now))
            return sToken;

        sToken = pref.getString(PREFERENCE_KEY, null);
        sNotAfter = pref.getLong(PREFERENCE_NOT_AFTER, 0);

        if (invalid(now)) {
            lookup();
            req = new HttpGet(
                String.format(
                    "https://api.toodledo.com/2/account/token.php?"
                    + "appid=%s&"
                    + "userid=%s&"
                    + "sig=%s",
                    APP_NAME,
                    lookup(),
                    getSignature()
                    )
                );
            response = client.execute(req);
            entity = response.getEntity();
            Map<String, ?> tokenResponse = gson.fromJson(new InputStreamReader(entity.getContent()), new TypeToken<HashMap<String, Object>>() {}.getType());
            entity.consumeContent();
            Log.d("A.oS", String.format("got: %s", tokenResponse.toString()));
            if (tokenResponse.containsKey("token")) {
                sToken = (String)tokenResponse.get("token");
                sNotAfter = now + TTL;
                pref.edit()
                    .putString(PREFERENCE_KEY, sToken)
                    .putLong(PREFERENCE_NOT_AFTER, sNotAfter)
                    .commit();
            } else {
                Double code = (Double)tokenResponse.get("errorCode");
                if (code != null && code.intValue() == 2) {
                    throw new FailureException();
                } else {
                    String descriptor = (String)tokenResponse.get("errorDesc");
                    throw new ErrorException(descriptor);
                }
            }
        }
        return sToken;
    }

    private String lookup() throws IOException, BogusException, FailureException, ErrorException {
        Gson gson = new Gson();
        HttpClient client = new DefaultHttpClient();
        HttpGet req;
        HttpResponse response;
        HttpEntity entity;
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mContext);

        if (sUserId != null)
            return sUserId;

        sUserId = pref.getString(PREFERENCE_USER_ID, null);

        if (sUserId == null) {
            if (bogus()) {
                throw new BogusException();
            }

            req = new HttpGet(
                String.format(
                    "https://api.toodledo.com/2/account/lookup.php?"
                    + "appid=%s&"
                    + "email=%s&"
                    + "pass=%s&"
                    + "sig=%s",
                    APP_NAME,
                    mEmail,
                    mPassword,
                    getLookupSignature()
                    )
                );
            response = client.execute(req);
            entity = response.getEntity();
            Map<String, ?> tokenResponse = gson.fromJson(new InputStreamReader(entity.getContent()), new TypeToken<HashMap<String, Object>>() {}.getType());
            entity.consumeContent();
            Log.d("A.l", String.format("got: %s", tokenResponse.toString()));
            if (tokenResponse.containsKey("userid")) {
                sUserId = (String)tokenResponse.get("userid");
                pref.edit()
                    .putString(PREFERENCE_USER_ID, sUserId)
                    .commit();
            } else {
                Double code = (Double)tokenResponse.get("errorCode");
                if (code != null && code.intValue() == 12) {
                    throw new FailureException();
                } else {
                    String descriptor = (String)tokenResponse.get("errorDesc");
                    throw new ErrorException(descriptor);
                }
            }
        }
        return sUserId;
    }

    public void revoke() {
        sToken = null;
        sNotAfter = 0;
        PreferenceManager.getDefaultSharedPreferences(mContext)
            .edit()
            .putString(PREFERENCE_KEY, null)
            .putLong(PREFERENCE_NOT_AFTER, 0)
            .commit();
    }

    public void unlink() {
        revoke();
        sUserId = null;
        PreferenceManager.getDefaultSharedPreferences(mContext)
            .edit()
            .putString(PREFERENCE_USER_ID, null)
            .commit();
    }

    private final String getSignature() throws BogusException {
        if (sUserId == null) {
            throw new BogusException();
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(sUserId.getBytes());
            md.update(APP_ID.getBytes());
            return Hex.encodeHexString(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private final String getLookupSignature() throws BogusException {
        if (mEmail == null) {
            throw new BogusException();
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(mEmail.getBytes());
            md.update(APP_ID.getBytes());
            return Hex.encodeHexString(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private final String getKey() throws BogusException {
        if (mPassword == null || sToken == null) {
            throw new BogusException();
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            MessageDigest md2 = MessageDigest.getInstance("MD5");
            md2.update(mPassword.getBytes());
            md.update(Hex.encodeHexString(md2.digest()).getBytes());
            md.update(APP_ID.getBytes());
            md.update(sToken.getBytes());
            return Hex.encodeHexString(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean invalid(long at) {
        return (sToken == null || sNotAfter < at);
    }

    public boolean bogus() {
        return (mEmail == null || mPassword == null);
    }

    public static class Exception extends java.lang.Exception {
        public Exception() {
            super();
        }

        public Exception(String desc) {
            super(desc);
        }
    }

    public static class BogusException extends Exception {
    }

    public static class FailureException extends Exception {
    }

    public static class ErrorException extends Exception {
        public ErrorException(String desc) {
            super(desc);
        }
    }
}
