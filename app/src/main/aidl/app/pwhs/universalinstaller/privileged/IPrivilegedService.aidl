// AIDL spoken across the libsu RootService binder. The service runs as UID 0; the app
// process holds the proxy. Both sides see the same Stub generated from this file.
package app.pwhs.universalinstaller.privileged;

import android.content.ComponentName;

interface IPrivilegedService {
    /**
     * Make `component` the preferred handler for APK install intents.
     * lock=true → clear competing preferred activities and install ours (also persistent in root mode).
     * lock=false → clear only our own preferred activities, letting Android's chooser re-engage.
     */
    void setDefaultInstaller(in ComponentName component, boolean lock);

    /**
     * Make `component` the preferred handler for package uninstall intents.
     * lock=true → clear competing preferred activities and set ours as default.
     * lock=false → clear only our own preferred activities, letting Android's chooser re-engage.
     */
    void setDefaultUninstaller(in ComponentName component, boolean lock);
}
