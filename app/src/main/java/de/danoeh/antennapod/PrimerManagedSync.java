package de.danoeh.antennapod;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.RestrictionsManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;

import java.util.Locale;

/**
 * Primer fork: sync credentials arrive as managed configuration, not by typing.
 *
 * The family's tablets are Guardian device-owner devices. Guardian pushes a
 * per-child app restriction bundle to this package (the same Android
 * mechanism that locks Chrome to the learning origins), so the gpodder-style
 * sync is configured the moment the app first starts, with no login screen
 * and no shared account: each child's tablet carries that child's username,
 * password and a device id. The restriction keys are:
 *
 *   gpodder_server    e.g. http://192.168.0.188:5845
 *   gpodder_username  the child's account
 *   gpodder_password
 *   gpodder_device    device id (a-z0-9_), e.g. maria_tablet
 *
 * Applied on every app start and whenever the restrictions change while the
 * app is running. Re-applying identical values is a no-op; different values
 * (a rotated password, a new server) replace the stored ones and trigger a
 * full sync so the server-side subscriptions (Wonder Pod) appear at once.
 */
public final class PrimerManagedSync {
    private static final String TAG = "PrimerManagedSync";

    private PrimerManagedSync() {
    }

    public static void install(Context context) {
        apply(context);
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                apply(c);
            }
        }, new IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED));
    }

    public static void apply(Context context) {
        try {
            RestrictionsManager rm = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);
            if (rm == null) {
                return;
            }
            Bundle b = rm.getApplicationRestrictions();
            if (b == null) {
                return;
            }
            String server = b.getString("gpodder_server");
            String user = b.getString("gpodder_username");
            String password = b.getString("gpodder_password");
            String device = b.getString("gpodder_device");
            if (TextUtils.isEmpty(server) || TextUtils.isEmpty(user) || TextUtils.isEmpty(password)) {
                return;
            }
            if (TextUtils.isEmpty(device)) {
                device = (user + "_device").replaceAll("[^a-zA-Z0-9]", "_").toLowerCase(Locale.US);
            }
            boolean same = SynchronizationProvider.GPODDER_NET.getIdentifier()
                    .equals(SynchronizationSettings.getSelectedSyncProviderKey())
                    && server.equals(SynchronizationCredentials.getHosturl())
                    && user.equals(SynchronizationCredentials.getUsername())
                    && password.equals(SynchronizationCredentials.getPassword())
                    && device.equals(SynchronizationCredentials.getDeviceId());
            if (same) {
                return;
            }
            SynchronizationCredentials.clear();
            SynchronizationCredentials.setHosturl(server);
            SynchronizationCredentials.setUsername(user);
            SynchronizationCredentials.setPassword(password);
            SynchronizationCredentials.setDeviceId(device);
            SynchronizationSettings.setSelectedSyncProvider(SynchronizationProvider.GPODDER_NET.getIdentifier());
            Log.i(TAG, "sync configured from managed config: " + user + "@" + server + " as " + device);
            SynchronizationQueue.getInstance().fullSync();
        } catch (Exception e) {
            Log.w(TAG, "managed sync config failed: " + e.getMessage());
        }
    }
}
