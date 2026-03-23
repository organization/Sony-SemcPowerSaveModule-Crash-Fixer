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

@SuppressLint({"PrivateApi", "BlockedPrivateApi"})
public class MainModule extends XposedModule {

    private static final String TAG = "MainModule";
    private static final String TARGET_PACKAGE = "com.sonyericsson.psm.sysmonservice";
    private static final String TARGET_CLASS = "com.sonyericsson.psm.sysmonservice.ProcessMonitor";
    private static XposedModule module;

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        module = this;
        log(Log.INFO, TAG, "Init module");
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        if (!param.getPackageName().equals(TARGET_PACKAGE)) {
            return;
        }

        try {
            Class<?> processMonitorClass = param.getClassLoader().loadClass(TARGET_CLASS);

            Method getPkgNameMethod = processMonitorClass.getDeclaredMethod("getPkgName", String.class);
            getPkgNameMethod.setAccessible(true);

            hook(getPkgNameMethod).intercept(new ProcessMonitorHooker());

            log(Log.INFO, TAG, "Hooked getPkgName successfully.");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to hook getPkgName", t);
        }
    }

    /**
     * Optimized ProcessMonitor.getPkgName Replacement
     */
    private static class ProcessMonitorHooker implements XposedInterface.Hooker {

        private static MethodHandle mhThermalManagerGetter;
        private static MethodHandle mhGetFromFile;
        private static volatile boolean isInitialized = false;

        @Override
        public Object intercept(@NonNull Chain chain) throws Throwable {
            try {
                String str = (String) chain.getArg(0);
                if (str == null) {
                    return null;
                }

                Object thisObject = chain.getThisObject();
                if (thisObject == null) {
                    return null;
                }

                if (!isInitialized) {
                    initializeHandles(thisObject);
                }

                Object thermalManager = mhThermalManagerGetter.invoke(thisObject);
                if (thermalManager == null) {
                    return null;
                }

                String path = "/proc/" + str + "/cmdline";
                String fromFile = (String) mhGetFromFile.invoke(thermalManager, path);

                if (fromFile != null && !fromFile.isEmpty()) {
                    String strTrim = fromFile.trim();

                    if (strTrim.isEmpty()) {
                        return null;
                    }

                    char firstChar = strTrim.charAt(0);

                    if (firstChar == '/') {
                        int iLastIndexOf = strTrim.lastIndexOf('/');
                        int iIndexOf = strTrim.indexOf("--");

                        int iLastIndexOfPlus1 = iLastIndexOf + 1;

                        return iIndexOf >= iLastIndexOfPlus1
                                ? strTrim.substring(iLastIndexOfPlus1, iIndexOf)
                                : strTrim.substring(iLastIndexOfPlus1);
                    }

                    if (firstChar != '(' && firstChar != '<' && firstChar != '-') {
                        int iIndexOf2 = strTrim.indexOf(':');

                        if (iIndexOf2 >= 0) {
                            return strTrim.substring(0, iIndexOf2);
                        }

                        int iLastIndexOf2 = strTrim.lastIndexOf('/');
                        return iLastIndexOf2 >= 0
                                ? strTrim.substring(iLastIndexOf2 + 1)
                                : strTrim;
                    }
                }

                return null;

            } catch (Throwable t) {
                module.log(Log.ERROR, TAG, "Error inside hook logic", t);
                throw t;
            }
        }

        private static synchronized void initializeHandles(Object instance) throws Exception {
            if (isInitialized) {
                return;
            }

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
