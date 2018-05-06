package com.adjust.sdk.adjustsdkplugin;

import android.content.Context;
import android.net.Uri;

import com.adjust.sdk.Adjust;
import com.adjust.sdk.AdjustAttribution;
import com.adjust.sdk.AdjustConfig;
import com.adjust.sdk.AdjustEvent;
import com.adjust.sdk.AdjustEventFailure;
import com.adjust.sdk.AdjustEventSuccess;
import com.adjust.sdk.AdjustSessionFailure;
import com.adjust.sdk.AdjustSessionSuccess;
import com.adjust.sdk.LogLevel;
import com.adjust.sdk.OnAttributionChangedListener;
import com.adjust.sdk.OnDeeplinkResponseListener;
import com.adjust.sdk.OnDeviceIdsRead;
import com.adjust.sdk.OnEventTrackingFailedListener;
import com.adjust.sdk.OnEventTrackingSucceededListener;
import com.adjust.sdk.OnSessionTrackingFailedListener;
import com.adjust.sdk.OnSessionTrackingSucceededListener;

import java.util.HashMap;
import java.util.Map;

import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * com.adjust.sdk.adjustsdkplugin
 * Created by 2beens on 25.04.18.
 */
public class AdjustSdkPlugin implements MethodCallHandler {
  private static MethodChannel channel;
  private static MethodChannel deeplinkChannel;
  private Context applicationContext;

  public AdjustSdkPlugin(Context context) {
    this.applicationContext = context;
  }

  /**
   * Plugin registration.
   */
  public static void registerWith(Registrar registrar) {
    if (channel != null) {
      throw new IllegalStateException("You should not call registerWith more than once.");
    }

    AdjustSdkPlugin adjustSdkPlugin = new AdjustSdkPlugin(registrar.context());
    channel = new MethodChannel(registrar.messenger(), "com.adjust/api");
    channel.setMethodCallHandler(adjustSdkPlugin);

    deeplinkChannel = new MethodChannel(registrar.messenger(), "com.adjust/deeplink");
    deeplinkChannel.setMethodCallHandler(adjustSdkPlugin);
  }

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    log("Trying to call a method: " + call.method);

    switch (call.method) {
      case "getPlatformVersion": getPlatformVersion(result); break;
      case "onCreate": onCreate(call, result); break;
      case "onPause": onPause(result); break;
      case "onResume": onResume(result); break;
      case "trackEvent": trackEvent(call, result); break;
      case "isEnabled": isEnabled(result); break;
      case "setIsEnabled": setIsEnabled(call, result); break;

      case "setOfflineMode": setOfflineMode(call, result); break;
      case "setPushToken": setPushToken(call, result); break;
      case "appWillOpenUrl": appWillOpenUrl(call, result); break;
      case "sendFirstPackages": sendFirstPackages(result); break;
      case "getAdid": getAdid(result); break;
      case "getGoogleAdId": getGoogleAdId(result); break;
      case "getAttribution": getAttribution(result); break;

      case "addSessionCallbackParameter": addSessionCallbackParameter(call, result); break;
      case "addSessionPartnerParameter": addSessionPartnerParameter(call, result); break;

      default:
        error("Not implemented method: " + call.method);
        result.notImplemented();
        break;
    }
  }

  private void getPlatformVersion(Result result) {
    result.success("Android " + android.os.Build.VERSION.RELEASE);
  }

  private void onCreate(MethodCall call, Result result) {
    Map adjustConfigMap = (Map)call.arguments;

    String appToken = (String) adjustConfigMap.get("appToken");
    String logLevel = (String) adjustConfigMap.get("logLevel");
    String environment = (String) adjustConfigMap.get("environment");
    String defaultTracker = (String) adjustConfigMap.get("defaultTracker");
    String isDeviceKnownString = (String) adjustConfigMap.get("isDeviceKnown");
    boolean isDeviceKnown = Boolean.valueOf(isDeviceKnownString);

    AdjustConfig config = new AdjustConfig(applicationContext, appToken, environment);
    String userAgent = (String) adjustConfigMap.get("userAgent");
    config.setUserAgent(userAgent);
    config.setLogLevel(LogLevel.valueOf(logLevel));
    config.setDefaultTracker(defaultTracker);
    config.setDeviceKnown(isDeviceKnown);

    AdjustSdkPlugin.log("Calling onCreate with values:");
    AdjustSdkPlugin.log("\tappToken: " + appToken);
    AdjustSdkPlugin.log("\tenvironment: " + environment);
    AdjustSdkPlugin.log("\tuserAgent: " + userAgent);
    AdjustSdkPlugin.log("\tlogLevel: " + logLevel);

    config.setOnDeeplinkResponseListener(new OnDeeplinkResponseListener() {
      @Override
      public boolean launchReceivedDeeplink(Uri uri) {
        HashMap uriParamsMap = new HashMap();
        uriParamsMap.put("uri", uri);

        final boolean[] shouldLaunchUri = new boolean[1];
        deeplinkChannel.invokeMethod("should-launch-uri", uriParamsMap, new Result() {
          @Override
          public void success(Object o) {
            if(o instanceof Boolean) {
              shouldLaunchUri[0] = (Boolean)o;
            } else {
              shouldLaunchUri[0] = false;
            }
          }

          @Override
          public void error(String s, String s1, Object o) {
            shouldLaunchUri[0] = false;
          }

          @Override
          public void notImplemented() {
            shouldLaunchUri[0] = false;
          }
        });

        // TODO: this won't work :)
        return shouldLaunchUri[0];
      }
    });

    config.setOnSessionTrackingSucceededListener(new OnSessionTrackingSucceededListener() {
      @Override
      public void onFinishedSessionTrackingSucceeded(AdjustSessionSuccess adjustSessionSuccess) {
        HashMap adjustSessionSuccessMap = new HashMap();
        adjustSessionSuccessMap.put("message", adjustSessionSuccess.message);
        adjustSessionSuccessMap.put("timestamp", adjustSessionSuccess.timestamp);
        adjustSessionSuccessMap.put("adid", adjustSessionSuccess.adid);
        adjustSessionSuccessMap.put("jsonResponse", adjustSessionSuccess.jsonResponse.toString());

        channel.invokeMethod("session-success", adjustSessionSuccessMap);
      }
    });

    config.setOnSessionTrackingFailedListener(new OnSessionTrackingFailedListener() {
      @Override
      public void onFinishedSessionTrackingFailed(AdjustSessionFailure adjustSessionFailure) {
        HashMap adjustSessionFailureMap = new HashMap();
        adjustSessionFailureMap.put("message", adjustSessionFailure.message);
        adjustSessionFailureMap.put("timestamp", adjustSessionFailure.timestamp);
        adjustSessionFailureMap.put("adid", adjustSessionFailure.adid);
        adjustSessionFailureMap.put("willRetry", adjustSessionFailure.willRetry);
        adjustSessionFailureMap.put("jsonResponse", adjustSessionFailure.jsonResponse.toString());

        channel.invokeMethod("session-fail", adjustSessionFailureMap);
      }
    });

    config.setOnEventTrackingSucceededListener(new OnEventTrackingSucceededListener() {
      @Override
      public void onFinishedEventTrackingSucceeded(AdjustEventSuccess adjustEventSuccess) {
        HashMap adjustEventSuccessMap = new HashMap();
        adjustEventSuccessMap.put("message", adjustEventSuccess.message);
        adjustEventSuccessMap.put("timestamp", adjustEventSuccess.timestamp);
        adjustEventSuccessMap.put("adid", adjustEventSuccess.adid);
        adjustEventSuccessMap.put("eventToken", adjustEventSuccess.eventToken);
        adjustEventSuccessMap.put("jsonResponse", adjustEventSuccess.jsonResponse.toString());

        channel.invokeMethod("event-success", adjustEventSuccess);
      }
    });

    config.setOnEventTrackingFailedListener(new OnEventTrackingFailedListener() {
      @Override
      public void onFinishedEventTrackingFailed(AdjustEventFailure adjustEventFailure) {
        HashMap<String, String> adjustEventFailureMap = new HashMap();
        adjustEventFailureMap.put("message", adjustEventFailure.message);
        adjustEventFailureMap.put("timestamp", adjustEventFailure.timestamp);
        adjustEventFailureMap.put("adid", adjustEventFailure.adid);
        adjustEventFailureMap.put("eventToken", adjustEventFailure.eventToken);
        adjustEventFailureMap.put("willRetry", Boolean.toString(adjustEventFailure.willRetry));
        adjustEventFailureMap.put("jsonResponse", adjustEventFailure.jsonResponse.toString());

        channel.invokeMethod("event-fail", adjustEventFailureMap);
      }
    });

    config.setOnAttributionChangedListener(new OnAttributionChangedListener() {
      @Override
      public void onAttributionChanged(AdjustAttribution adjustAttribution) {
        HashMap<String, String> adjustAttributionMap = new HashMap();
        adjustAttributionMap.put("trackerToken", adjustAttribution.trackerToken);
        adjustAttributionMap.put("trackerName", adjustAttribution.trackerName);
        adjustAttributionMap.put("network", adjustAttribution.network);
        adjustAttributionMap.put("campaign", adjustAttribution.campaign);
        adjustAttributionMap.put("adgroup", adjustAttribution.adgroup);
        adjustAttributionMap.put("creative", adjustAttribution.creative);
        adjustAttributionMap.put("clickLabel", adjustAttribution.clickLabel);
        adjustAttributionMap.put("adid", adjustAttribution.adid);

        channel.invokeMethod("attribution-change", adjustAttributionMap);
      }
    });

    AdjustBridge.onCreate(config);

    result.success(null);
  }

  private void onResume(Result result) {
    AdjustBridge.onResume();
    result.success(null);
  }

  private void onPause(Result result) {
    AdjustBridge.onPause();
    result.success(null);
  }

  private void trackEvent(MethodCall call, Result result) {
    Map eventParamsMap = (Map)call.arguments;
    String revenue = (String) eventParamsMap.get("revenue");
    String currency = (String) eventParamsMap.get("currency");
    String orderId = (String) eventParamsMap.get("orderId");
    String eventToken = (String) eventParamsMap.get("eventToken");

    AdjustEvent event = new AdjustEvent(eventToken);
    event.setRevenue(Double.valueOf(revenue), currency);
    event.setOrderId(orderId);

    AdjustBridge.trackEvent(event);
    result.success(null);
  }

  private void isEnabled(Result result) {
    result.success(AdjustBridge.isEnabled());
  }

  private void setOfflineMode(MethodCall call, Result result) {
    Map isOfflineParamsMap = (Map)call.arguments;
    boolean isOffline = (boolean) isOfflineParamsMap.get("isOffline");
    AdjustBridge.setOfflineMode(isOffline);
    result.success(null);
  }

  private void setPushToken(MethodCall call, Result result) {
    Map tokenParamsMap = (Map)call.arguments;
    if(!tokenParamsMap.containsKey("token")) {
      result.error("0", "Arguments null or wrong (missing argument >token<", null);
      return;
    }

    String pushToken = tokenParamsMap.get("token").toString();
    AdjustBridge.setPushToken(pushToken);
    result.success(null);
  }

  private void setIsEnabled(MethodCall call, Result result) {
    Map isEnabledParamsMap = (Map)call.arguments;
    if(!isEnabledParamsMap.containsKey("isEnabled")) {
      result.error("0", "Arguments null or wrong (missing argument >isEnabled<", null);
      return;
    }

    boolean isEnabled = (boolean) isEnabledParamsMap.get("isEnabled");
    AdjustBridge.setIsEnabled(isEnabled);
    result.success(null);
  }

  private void appWillOpenUrl(MethodCall call, Result result) {
    Map urlParamsMap = (Map)call.arguments;
    if(!urlParamsMap.containsKey("token")) {
      result.error("0", "Arguments null or wrong (missing argument >url<", null);
      return;
    }

    String url = urlParamsMap.get("url").toString();
    AdjustBridge.appWillOpenUrl(url);
    result.success(null);
  }

  private void sendFirstPackages(Result result) {
    AdjustBridge.sendFirstPackages();
    result.success(null);
  }

  private void getAdid(Result result) {
    result.success(AdjustBridge.getAdid());
  }

  private void getGoogleAdId(final Result result) {
    AdjustBridge.getGoogleAdId(applicationContext, new OnDeviceIdsRead() {
      @Override
      public void onGoogleAdIdRead(String googleAdId) {
        result.success(googleAdId);
      }
    });
  }

  private void getAttribution(Result result) {
    result.success(AdjustBridge.getAttribution());
  }

  private void addSessionCallbackParameter(MethodCall call, Result result) {
    if(!call.hasArgument("key") && !call.hasArgument("value")) {
      result.error("0", "Arguments null or wrong", null);
      return;
    }

    String key = (String) call.argument("key");
    String value = (String) call.argument("value");
    AdjustBridge.addSessionCallbackParameter(key, value);

    result.success(null);
  }

  private void addSessionPartnerParameter(MethodCall call, Result result) {
    if(!call.hasArgument("key") && !call.hasArgument("value")) {
      result.error("0", "Arguments null or wrong", null);
      return;
    }

    String key = (String) call.argument("key");
    String value = (String) call.argument("value");
    AdjustBridge.addSessionPartnerParameter(key, value);

    result.success(null);
  }

  public static void log(String message) {
    System.out.println(">>> ADJUST PLUGIN LOG: " + message);
  }

  public static void error(String message) { System.out.println("!!>>> ADJUST PLUGIN ERROR LOG: " + message); }
}
