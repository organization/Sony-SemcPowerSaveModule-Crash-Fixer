package be.zvz.sony.spsmcrashfixer;

import android.annotation.SuppressLint;
import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
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

    /**
     * Optimized ProcessMonitor.getPkgName Replacement
     */
    @XposedHooker
    private static class ProcessMonitorHooker implements Hooker {

        private static MethodHandle mhThermalManagerGetter;
        private static MethodHandle mhGetFromFile;
        private static boolean isInitialized = false;

        @BeforeInvocation
        public static void before(@NonNull BeforeHookCallback callback) {
            try {
                String str = (String) callback.getArgs()[0];
                if (str == null) {
                    callback.returnAndSkip(null);
                    return;
                }

                Object thisObject = callback.getThisObject();
                if (thisObject == null) {
                    callback.returnAndSkip(null);
                    return;
                }

                if (!isInitialized) {
                    initializeHandles(thisObject);
                }

                Object thermalManager = mhThermalManagerGetter.invoke(thisObject);
                if (thermalManager == null) {
                    callback.returnAndSkip(null);
                    return;
                }

                String path = "/proc/" + str + "/cmdline";
                String fromFile = (String) mhGetFromFile.invoke(thermalManager, path);

                if (fromFile != null && !fromFile.isEmpty()) {
                    String strTrim = fromFile.trim();

                    if (strTrim.isEmpty()) {
                        callback.returnAndSkip(null);
                        return;
                    }

                    char firstChar = strTrim.charAt(0);

                    if (firstChar == '/') {
                        int iLastIndexOf = strTrim.lastIndexOf('/');
                        int iIndexOf = strTrim.indexOf("--");

                        int iLastIndexOfPlus1 = iLastIndexOf + 1;

                        String result = iIndexOf >= iLastIndexOfPlus1
                                ? strTrim.substring(iLastIndexOfPlus1, iIndexOf)
                                : strTrim.substring(iLastIndexOfPlus1);

                        callback.returnAndSkip(result);
                        return;
                    }

                    if (firstChar != '(' && firstChar != '<' && firstChar != '-') {
                        int iIndexOf2 = strTrim.indexOf(':');

                        if (iIndexOf2 >= 0) {
                            callback.returnAndSkip(strTrim.substring(0, iIndexOf2));
                            return;
                        }

                        int iLastIndexOf2 = strTrim.lastIndexOf('/');
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

        private static void initializeHandles(Object instance) throws Exception {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Class<?> instanceClass = instance.getClass();

            Field managerField = instanceClass.getDeclaredField("mThermalEngineManager");
            managerField.setAccessible(true);
            mhThermalManagerGetter = lookup.unreflectGetter(managerField);

            Class<?> managerClass = managerField.getType();

            Method getFromFileMethod = managerClass.getDeclaredMethod("getFromFile", String.class);
            getFromFileMethod.setAccessible(true);
            mhGetFromFile = lookup.unreflect(getFromFileMethod);

            isInitialized = true;
        }
    }
}