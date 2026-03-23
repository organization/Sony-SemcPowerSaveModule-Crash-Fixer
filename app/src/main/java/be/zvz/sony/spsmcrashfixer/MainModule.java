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
    private static final String TARGET_CLASS = TARGET_PACKAGE + ".ProcessMonitor";

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        log(Log.INFO, TAG, "Init module");
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }

        try {
            Class<?> processMonitorClass = param.getClassLoader().loadClass(TARGET_CLASS);
            Method getPkgNameMethod = processMonitorClass.getDeclaredMethod("getPkgName", String.class);

            hook(getPkgNameMethod)
                    .setExceptionMode(ExceptionMode.PASSTHROUGH)
                    .intercept(new ProcessMonitorHooker());

            log(Log.INFO, TAG, "Hooked getPkgName successfully.");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to hook getPkgName", t);
        }
    }

    private static class ProcessMonitorHooker implements XposedInterface.Hooker {

        private static MethodHandle mhThermalManagerGetter;
        private static MethodHandle mhGetFromFile;
        private static volatile boolean isInitialized;

        @Override
        public Object intercept(@NonNull Chain chain) throws Throwable {
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

            String fromFile = (String) mhGetFromFile.invoke(thermalManager, "/proc/" + str + "/cmdline");
            if (fromFile == null) {
                return null;
            }

            String strTrim = fromFile.trim();
            if (strTrim.isEmpty()) {
                return null;
            }

            char firstChar = strTrim.charAt(0);

            if (firstChar == '/') {
                int iLastSlash = strTrim.lastIndexOf('/') + 1;
                int iDash = strTrim.indexOf("--");
                return iDash >= iLastSlash
                        ? strTrim.substring(iLastSlash, iDash)
                        : strTrim.substring(iLastSlash);
            }

            if (firstChar != '(' && firstChar != '<' && firstChar != '-') {
                int iColon = strTrim.indexOf(':');
                if (iColon >= 0) {
                    return strTrim.substring(0, iColon);
                }

                int iLastSlash = strTrim.lastIndexOf('/');
                return iLastSlash >= 0
                        ? strTrim.substring(iLastSlash + 1)
                        : strTrim;
            }

            return null;
        }

        private static synchronized void initializeHandles(Object instance) throws ReflectiveOperationException {
            if (isInitialized) {
                return;
            }

            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Class<?> instanceClass = instance.getClass();

            Field managerField = instanceClass.getDeclaredField("mThermalEngineManager");
            managerField.setAccessible(true);
            mhThermalManagerGetter = lookup.unreflectGetter(managerField);

            Method getFromFileMethod = managerField.getType().getDeclaredMethod("getFromFile", String.class);
            getFromFileMethod.setAccessible(true);
            mhGetFromFile = lookup.unreflect(getFromFileMethod);

            isInitialized = true;
        }
    }
}
