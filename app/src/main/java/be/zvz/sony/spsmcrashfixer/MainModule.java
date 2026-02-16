package be.zvz.sony.spsmcrashfixer;

import android.annotation.SuppressLint;
import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;

@SuppressLint({"PrivateApi", "BlockedPrivateApi"})
public class MainModule extends XposedModule {

    private static final String TARGET_PACKAGE = "com.sonyericsson.psm.sysmonservice";
    private static final String TARGET_CLASS = "com.sonyericsson.psm.sysmonservice.ProcessMonitor";
    private static final String TAG = "SonySpsmCrashFixer";

    public MainModule(XposedInterface base, ModuleLoadedParam param) {
        super(base, param);
    }

    @Override
    public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        super.onPackageLoaded(param);

        if (!param.getPackageName().equals(TARGET_PACKAGE)) {
            return;
        }

        try {
            Class<?> processMonitorClass = param.getClassLoader().loadClass(TARGET_CLASS);
            Method getPkgNameMethod = processMonitorClass.getDeclaredMethod("getPkgName", String.class);
            getPkgNameMethod.setAccessible(true);
            hook(getPkgNameMethod, ProcessMonitorHooker.class);
            Log.d(TAG, "Hooked getPkgName successfully.");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook getPkgName", t);
        }
    }

    @XposedHooker
    private static class ProcessMonitorHooker implements Hooker {
        private static Field managerField;
        private static Method getFromFileMethod;

        @BeforeInvocation
        public static void before(@NonNull BeforeHookCallback callback) {
            try {
                String str = (String) callback.getArgs()[0];
                Object thisObject = callback.getThisObject();

                if (thisObject == null) {
                    callback.returnAndSkip(null);
                    return;
                }

                if (managerField == null) {
                    managerField = thisObject.getClass().getDeclaredField("mThermalEngineManager");
                    managerField.setAccessible(true);
                }

                Object currentThermalManager = managerField.get(thisObject);

                if (currentThermalManager == null) {
                    callback.returnAndSkip(null);
                    return;
                }

                if (getFromFileMethod == null) {
                    getFromFileMethod = currentThermalManager.getClass().getDeclaredMethod("getFromFile", String.class);
                    getFromFileMethod.setAccessible(true);
                }

                String fromFile = (String) getFromFileMethod.invoke(currentThermalManager, "/proc/" + str + "/cmdline");

                if (fromFile != null && !fromFile.isEmpty()) {
                    String strTrim = fromFile.trim();

                    if (strTrim.isEmpty()) {
                        callback.returnAndSkip(null);
                        return;
                    }

                    if (strTrim.charAt(0) == '/') {
                        int iLastIndexOf = strTrim.lastIndexOf('/') + 1;
                        int iIndexOf = strTrim.indexOf("--");

                        String result = iIndexOf >= iLastIndexOf
                                ? strTrim.substring(iLastIndexOf, iIndexOf)
                                : strTrim.substring(iLastIndexOf);

                        callback.returnAndSkip(result);
                        return;
                    }

                    if (strTrim.charAt(0) != '(' && strTrim.charAt(0) != '<' && strTrim.charAt(0) != '-') {
                        int iIndexOf2 = strTrim.indexOf(':');
                        int iLastIndexOf2 = strTrim.lastIndexOf('/');

                        if (iIndexOf2 >= 0) {
                            callback.returnAndSkip(strTrim.substring(0, iIndexOf2));
                            return;
                        }

                        String result = iLastIndexOf2 >= 0
                                ? strTrim.substring(iLastIndexOf2 + 1)
                                : strTrim;

                        callback.returnAndSkip(result);
                        return;
                    }
                }

                callback.returnAndSkip(null);

            } catch (Throwable t) {
                Log.e(TAG, "Error inside hook logic", t);
                callback.throwAndSkip(t);
            }
        }
    }
}
