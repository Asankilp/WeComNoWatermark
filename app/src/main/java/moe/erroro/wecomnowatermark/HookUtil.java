package moe.erroro.wecomnowatermark;

import android.graphics.Canvas;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 反射/Hook 工具方法集合。
 *
 * <p>设计原则：所有 hook 都按「方法名 + 返回类型」或「方法名 + 参数形状」匹配，
 * 不写死混淆后的参数类型，尽量降低企业微信升级后失效的概率。</p>
 */
final class HookUtil {

    static final String TAG = "WeComNoWatermark";

    /** 累计成功安装的 hook 数量，用于判断模块是否真的挂上了目标方法 */
    private static int sInstalled = 0;

    static int installedCount() {
        return sInstalled;
    }

    private HookUtil() {
    }

    // ---------------------------------------------------------------- log

    static void log(String msg) {
        XposedBridge.log("[" + TAG + "] " + msg);
    }

    static void log(String msg, Throwable t) {
        XposedBridge.log("[" + TAG + "] " + msg + " : " + Log.getStackTraceString(t));
    }

    // ------------------------------------------------------------- lookup

    static Class<?> cls(ClassLoader cl, String name) {
        try {
            return XposedHelpers.findClass(name, cl);
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------- hooks

    /** 让指定类里所有名为 name、返回 boolean 的方法一律返回 false。 */
    static int forceFalse(Class<?> c, String... names) {
        return forceValue(c, Boolean.FALSE, "boolean", names);
    }

    /** 让指定类里所有名为 name、返回 String 的方法一律返回空串。 */
    static int forceEmptyString(Class<?> c, String... names) {
        return forceValue(c, "", "string", names);
    }

    private static int forceValue(Class<?> c, final Object value, String kind, String... names) {
        if (c == null) {
            return 0;
        }
        int count = 0;
        for (Method m : c.getDeclaredMethods()) {
            if (!nameMatches(m.getName(), names)) {
                continue;
            }
            Class<?> rt = m.getReturnType();
            boolean ok = "boolean".equals(kind) ? (rt == boolean.class)
                    : "string".equals(kind) && (rt == String.class);
            if (!ok) {
                continue;
            }
            try {
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(value);
                    }
                });
                count++;
                sInstalled++;
            } catch (Throwable t) {
                Report.fail("hook " + c.getName() + "#" + m.getName(), t);
            }
        }
        return count;
    }

    /** 让指定类里所有名为 name 的 void 方法变成空实现。 */
    static int noop(Class<?> c, String... names) {
        if (c == null) {
            return 0;
        }
        int count = 0;
        for (Method m : c.getDeclaredMethods()) {
            if (!nameMatches(m.getName(), names)) {
                continue;
            }
            if (m.getReturnType() != void.class) {
                continue;
            }
            try {
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(null);
                    }
                });
                count++;
                sInstalled++;
            } catch (Throwable t) {
                Report.fail("hook " + c.getName() + "#" + m.getName(), t);
            }
        }
        return count;
    }

    /**
     * 把某个类里所有「第一个参数是 Canvas 的 void 方法」变成空实现。
     *
     * <p>这是水印渲染器（混淆类 {@code dsb0}）的绘制入口，形如
     * {@code n(Canvas)} / {@code o(Canvas,int,int)} / {@code m(Canvas,int,int)}。
     * 名字随版本变，但「第一个参数是 Canvas」这个形状是稳定的。</p>
     */
    static int noopCanvasDraw(Class<?> c) {
        if (c == null) {
            return 0;
        }
        int count = 0;
        for (Method m : declaredMethodsDeep(c)) {
            Class<?>[] p = m.getParameterTypes();
            if (m.getReturnType() != void.class || p.length < 1 || p[0] != Canvas.class) {
                continue;
            }
            try {
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(null);
                    }
                });
                count++;
                sInstalled++;
            } catch (Throwable t) {
                Report.fail("hook draw " + c.getName() + "#" + m.getName(), t);
            }
        }
        return count;
    }

    /** 类本身（含父类）的方法，用于匹配继承来的绘制入口。 */
    private static Method[] declaredMethodsDeep(Class<?> c) {
        java.util.List<Method> out = new java.util.ArrayList<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                out.add(m);
            }
        }
        return out.toArray(new Method[0]);
    }

    /** 该类是否具备「第一个参数是 Canvas 的 void 方法」，用来识别水印渲染器。 */
    static boolean looksLikeRenderer(Class<?> c) {
        if (c == null || c.isPrimitive() || c.isInterface() || c.isArray()) {
            return false;
        }
        String n = c.getName();
        if (n.startsWith("java.") || n.startsWith("javax.")
                || n.startsWith("android.") || n.startsWith("androidx.")
                || n.startsWith("kotlin.") || n.startsWith("kotlinx.")) {
            return false;
        }
        for (Method m : c.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (m.getReturnType() == void.class && p.length >= 1 && p[0] == Canvas.class) {
                return true;
            }
        }
        return false;
    }

    /** 类中第一个「像水印渲染器」的字段类型。 */
    static Class<?> anyRendererField(Class<?> c) {
        if (c == null) {
            return null;
        }
        for (Field f : c.getDeclaredFields()) {
            if (looksLikeRenderer(f.getType())) {
                return f.getType();
            }
        }
        return null;
    }

    private static boolean nameMatches(String actual, String[] names) {
        for (String n : names) {
            if (n.equals(actual)) {
                return true;
            }
        }
        return false;
    }
}
