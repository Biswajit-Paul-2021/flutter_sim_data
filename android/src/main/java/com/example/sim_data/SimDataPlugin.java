package com.example.sim_data;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Objects;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;

/** SimDataPlugin */
public class SimDataPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.RequestPermissionsResultListener {

  private static final int MY_PERMISSIONS_REQUEST_READ_PHONE_STATE = 123;
  private static final int MY_PERMISSIONS_REQUEST_SEND_SMS_STATE = 101;
  private MethodChannel channel;
  private Activity activity;
  private Result result;
  private  Context context;
  private MethodCall call;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "sim_data");
    channel.setMethodCallHandler(this);
    context = flutterPluginBinding.getApplicationContext();
  }

  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding activityPluginBinding) {
    activity = activityPluginBinding.getActivity();
    activityPluginBinding.addRequestPermissionsResultListener(this);
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {

  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    activity = binding.getActivity();
    binding.addRequestPermissionsResultListener(this);
  }

  @Override
  public void onDetachedFromActivity() {
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    this.result = result;
    this.call = call;
    if (call.method.equals("getPlatformVersion")) {
      result.success("Android " + Build.VERSION.RELEASE);
    } else if(call.method.equals("get_sim_data")){
      if(hasPermissions()){
        getSimData();
      }else{
        requestPermission();
      }
    }else if(call.method.equals("send_sms")){
      if(hasSMSPermissions()){
        sendSMS();
      }else{
        requestSMSPermission();
      }
    }else{
      result.notImplemented();
    }
  }

  void getSimData()  {
    SubscriptionManager subscriptionManager = null;
    JSONArray array = new JSONArray();
      subscriptionManager = (SubscriptionManager) activity.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
      if (ActivityCompat.checkSelfPermission(activity.getApplicationContext(), Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
        result.error("Error","Permission Denied","Permission Denied");
        return;
      }
      List<SubscriptionInfo> infoList = subscriptionManager != null ? subscriptionManager.getActiveSubscriptionInfoList() : null;
      for(int i = 0; i < Objects.requireNonNull(infoList).size(); i++){
        JSONObject map = new JSONObject();
        try{
          map.put("COUNTRY_CODE", infoList.get(i).getCountryIso());

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            map.put("PHONE_NUMBER", subscriptionManager.getPhoneNumber(infoList.get(i).getSubscriptionId()));
          }else{

            map.put("PHONE_NUMBER", infoList.get(i).getNumber());
          }

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            map.put("CARD_ID", infoList.get(i).getCardId());
          }else{
            map.put("CARD_ID", null);
          }

          map.put("CARRIER_NAME", infoList.get(i).getCarrierName());

          map.put("DISPLAY_NAME", infoList.get(i).getDisplayName());

          map.put("SIM_SLOT_INDEX", infoList.get(i).getSimSlotIndex());

          map.put("SUBSCRIPTION_ID", infoList.get(i).getSubscriptionId());

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

            map.put("IS_EMBEDDED", infoList.get(i).isEmbedded());
          }else{
            map.put("IS_EMBEDDED", false);
          }
        }catch(JSONException e){
          result.error("Error","Something went wrong!", "");
        }
        array.put(map);
      }
      result.success(array.toString());
  }

  void sendSMS(){
    String number = call.argument("phone");
    String message = call.argument("msg");
    Integer subId = call.argument("subId");

    String sent = "SMS_SENT";
    String delivered = "SMS_DELIVERED";
    PendingIntent sendPendingIntent;
    PendingIntent deliveryPendingIntent;

      SmsManager smsManager = SmsManager.getSmsManagerForSubscriptionId(subId);

      sendPendingIntent = PendingIntent.getBroadcast(
        context,
        1,
        new Intent(sent),
        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
      );
      deliveryPendingIntent = PendingIntent.getBroadcast(
        context,
        2,
        new Intent(delivered),
        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
      );


      context.registerReceiver(
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            int res = getResultCode();
            if(res == Activity.RESULT_OK){
             result.success(true);
            }else{
              result.success(false);
            }
            context.unregisterReceiver(this);
          }
        },
        new IntentFilter(sent),
        Context.RECEIVER_EXPORTED
      );
//Deliverd Listner not required
//      context.registerReceiver(
//        new BroadcastReceiver() {
//          @Override
//          public void onReceive(Context context, Intent intent) {
//            int res = getResultCode();
//            if(res == Activity.RESULT_OK){
//              result.success(true);
//            }else{
//              result.success(false);
//            }
//            context.unregisterReceiver(this);
//          }
//
//        }, new IntentFilter(delivered),
//        Context.RECEIVER_EXPORTED
//      );

    try {
      smsManager.sendTextMessage(number, null, message, sendPendingIntent, deliveryPendingIntent);
    } catch (Exception e) {
      result.success(false);
    }
  }

  private void requestPermission(){
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
      activity.requestPermissions(
        new String[]{
          Manifest.permission.READ_PHONE_NUMBERS,
          Manifest.permission.READ_PHONE_STATE,
        },
        MY_PERMISSIONS_REQUEST_READ_PHONE_STATE
      );
    } else {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        activity.requestPermissions(
          new String[]{
            Manifest.permission.READ_PHONE_STATE,
          },
          MY_PERMISSIONS_REQUEST_READ_PHONE_STATE
        );
      }else{
        ActivityCompat.requestPermissions(
          activity,
          new String[]{
            Manifest.permission.READ_PHONE_STATE,
          },
          MY_PERMISSIONS_REQUEST_READ_PHONE_STATE
        );
      }
    }
  }

  private boolean hasPermissions() {
    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      return ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    } else {
      return ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }
  }

  private boolean hasSMSPermissions() {
    return ActivityCompat.checkSelfPermission(activity, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
  }

  void requestSMSPermission(){
    ActivityCompat.requestPermissions(
      activity,
      new String[]{
        Manifest.permission.SEND_SMS
      },
      MY_PERMISSIONS_REQUEST_SEND_SMS_STATE
    );
  }


  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
  }

  @Override
  public boolean onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (requestCode == MY_PERMISSIONS_REQUEST_READ_PHONE_STATE) {
      if (grantResults.length > 0 && isGranted(grantResults)) {
        getSimData();
        return true;
      }
    }
    if (requestCode == MY_PERMISSIONS_REQUEST_SEND_SMS_STATE){
      if (grantResults.length > 0 && isGranted(grantResults)) {
        sendSMS();
        return true;
      }
    }
//    result.error("PERMISSION", "onRequestPermissionsResult is not granted", null);
    return false;
  }

  boolean isGranted(int[] arr) {
    for (int num : arr) {
      if (num != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }
}
