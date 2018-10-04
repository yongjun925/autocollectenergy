package com.xposed.pyj.autocollectenergy;


import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedHook implements IXposedHookLoadPackage {

    private static boolean first = false;
    private static String TAG = "XposedHookPyj";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        Log.i(TAG, lpparam.packageName);
        if ("com.eg.android.AlipayGphone".equals(lpparam.packageName)) {
            hookSecurity(lpparam);
            hookRpcCall(lpparam);
        }
    }

    private void hookSecurity(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class loadClass = lpparam.classLoader.loadClass("android.util.Base64");
            if (loadClass != null) {
                XposedHelpers.findAndHookMethod(loadClass, "decode", new Object[]{String.class, Integer.TYPE, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                    }
                }});
            }
            loadClass = lpparam.classLoader.loadClass("android.app.Dialog");
            if (loadClass != null) {
                XposedHelpers.findAndHookMethod(loadClass, "show", new Object[]{new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        try {
                            throw new NullPointerException();
                        } catch (Exception e) {
                        }
                    }
                }});
            }
            loadClass = lpparam.classLoader.loadClass("com.alipay.mobile.base.security.CI");
            if (loadClass != null) {
                XposedHelpers.findAndHookMethod(loadClass, "a", new Object[]{loadClass, Activity.class, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        return null;
                    }
                }});
                XposedHelpers.findAndHookMethod(loadClass, "a", new Object[]{String.class, String.class, String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        param.setResult(null);
                    }
                }});
            }
        } catch (Throwable e) {
            Log.i(TAG, "hookSecurity err:" + Log.getStackTraceString(e));
        }
    }

    private void hookRpcCall(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", new Object[]{Context.class, new ApplicationAttachMethodHook()});
        } catch (Exception e2) {
            Log.i(TAG, "hookRpcCall err:" + Log.getStackTraceString(e2));
        }
    }

    private class ApplicationAttachMethodHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            super.afterHookedMethod(param);
            if (first) return;
            final ClassLoader loader = ((Context) param.args[0]).getClassLoader();
            Class clazz = loader.loadClass("com.alipay.mobile.nebulacore.ui.H5FragmentManager");
            if (clazz != null) {
                Class<?> h5FragmentClazz = loader.loadClass("com.alipay.mobile.nebulacore.ui.H5Fragment");
                if (h5FragmentClazz != null) {
                    XposedHelpers.findAndHookMethod(clazz, "pushFragment", h5FragmentClazz,
                            boolean.class, Bundle.class, boolean.class, boolean.class, new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    super.afterHookedMethod(param);
                                    Log.i("fragment", "cur fragment: " + param.args[0]);
                                    AliMobileAutoCollectEnergyUtils.curH5Fragment = param.args[0];
                                }
                            });
                }
            }

            clazz = loader.loadClass("com.alipay.mobile.nebulacore.ui.H5Activity");
            if (clazz != null) {
                XposedHelpers.findAndHookMethod(clazz, "onResume", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        AliMobileAutoCollectEnergyUtils.h5Activity = (Activity) param.thisObject;
                    }
                });
            }

            clazz = loader.loadClass("com.alipay.mobile.nebulabiz.rpc.H5RpcUtil");
            if (clazz != null) {
                first = true;
                Log.i(TAG, "first");
                Class<?> h5PageClazz = loader.loadClass("com.alipay.mobile.h5container.api.H5Page");
                Class<?> jsonClazz = loader.loadClass("com.alibaba.fastjson.JSONObject");
                if (h5PageClazz != null && jsonClazz != null) {
                    XposedHelpers.findAndHookMethod(clazz, "rpcCall", String.class, String.class, String.class,
                            boolean.class, jsonClazz, String.class, boolean.class, h5PageClazz,
                            int.class, String.class, boolean.class, int.class, new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    super.beforeHookedMethod(param);
//                                                    Log.i(TAG, "param" + param.args);
                                }

                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    super.afterHookedMethod(param);
                                    Object resp = param.getResult();
                                    if (resp != null) {
                                        Method method = resp.getClass().getMethod("getResponse", new Class<?>[]{});
                                        String response = (String) method.invoke(resp, new Object[]{});
                                        Log.i(TAG, "response: " + response);

                                        if (AliMobileAutoCollectEnergyUtils.isRankList(response)) {
                                            Log.i(TAG, "autoGetCanCollectUserIdList");
                                            AliMobileAutoCollectEnergyUtils.autoGetCanCollectUserIdList(loader, response);
                                        }

                                        // 第一次是自己的能量，比上面的获取用户信息还要早，所有这里需要记录当前自己的userid值
                                        if (AliMobileAutoCollectEnergyUtils.isUserDetail(response)) {
                                            Log.i(TAG, "autoGetCanCollectBubbleIdList");
                                            AliMobileAutoCollectEnergyUtils.autoGetCanCollectBubbleIdList(loader, response);
                                        }
                                    }
                                }
                            });
                }
            }
        }
    }
}
