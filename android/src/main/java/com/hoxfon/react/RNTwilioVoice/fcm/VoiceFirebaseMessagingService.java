package com.hoxfon.react.RNTwilioVoice.fcm;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import android.util.Log;

import android.os.Build;

import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.gson.Gson;
import com.hoxfon.react.RNTwilioVoice.Constants;
import com.hoxfon.react.RNTwilioVoice.IncomingCallNotificationService;
import com.hoxfon.react.RNTwilioVoice.IncomingCallNotificationWorker;
import com.twilio.voice.CallException;
import com.hoxfon.react.RNTwilioVoice.BuildConfig;
import com.hoxfon.react.RNTwilioVoice.CallNotificationManager;
import com.twilio.voice.CallInvite;
import com.twilio.voice.CancelledCallInvite;
import com.twilio.voice.MessageListener;
import com.twilio.voice.Voice;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.TAG;
import io.intercom.android.sdk.push.IntercomPushClient;

import static com.google.firebase.messaging.RemoteMessage.PRIORITY_HIGH;

public class VoiceFirebaseMessagingService extends FirebaseMessagingService {
    private final IntercomPushClient intercomPushClient = new IntercomPushClient();

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);

        intercomPushClient.sendTokenToIntercom(getApplication(), token);
        //DO HOST LOGIC HERE

        Intent intent = new Intent(Constants.ACTION_FCM_TOKEN);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Map message = remoteMessage.getData();
        if (intercomPushClient.isIntercomPush(message)) {
            intercomPushClient.handlePush(getApplication(), message);
        } else {
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "Bundle data: " + remoteMessage.getData());
            }

            // Check if message contains a data payload.
            if (remoteMessage.getData().size() > 0) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (remoteMessage.getOriginalPriority() == PRIORITY_HIGH &&
                            remoteMessage.getPriority() != remoteMessage.getOriginalPriority()
                    ) {
                        // we can not start a service from background if priority != high
                        return;
                    }
                }

                Map<String, String> data = remoteMessage.getData();

                // If notification ID is not provided by the user for push notification, generate one at random
                Random randomNumberGenerator = new Random(System.currentTimeMillis());
                final int notificationId = randomNumberGenerator.nextInt();

                boolean valid = Voice.handleMessage(this, data, new MessageListener() {
                    @Override
                    public void onCallInvite(final CallInvite callInvite) {
                        // We need to run this on the main thread, as the React code assumes that is true.
                        // Namely, DevServerHelper constructs a Handler() without a Looper, which triggers:
                        // "Can't create handler inside thread that has not called Looper.prepare()"
                        Handler handler = new Handler(Looper.getMainLooper());
                        handler.post(new Runnable() {
                            public void run() {
                                CallNotificationManager callNotificationManager = new CallNotificationManager();
                                // Construct and load our normal React JS code bundle
                                ReactInstanceManager mReactInstanceManager = ((ReactApplication) getApplication()).getReactNativeHost().getReactInstanceManager();
                                ReactContext context = mReactInstanceManager.getCurrentReactContext();

                                // initialise appImportance to the highest possible importance in case context is null
                                int appImportance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE;

                                if (context != null) {
                                    appImportance = callNotificationManager.getApplicationImportance((ReactApplicationContext)context);
                                }
                                if (BuildConfig.DEBUG) {
                                    Log.i(TAG, "context: " + context + ". appImportance = " + appImportance);
                                }

                                // when the app is not started or in the background
                                if (appImportance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                                    if (BuildConfig.DEBUG) {
                                        Log.i(TAG, "Background");
                                    }
                                    handleInvite(callInvite, notificationId);
                                    return;
                                }

                                Intent intent = new Intent(Constants.ACTION_INCOMING_CALL);
                                intent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
                                intent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);
                                LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                            }
                        });
                    }

                    @Override
                    public void onCancelledCallInvite(@NonNull CancelledCallInvite cancelledCallInvite, @Nullable CallException callException) {
                        // The call is prematurely disconnected by the caller.
                        // The callee does not accept or reject the call within 30 seconds.
                        // The Voice SDK is unable to establish a connection to Twilio.
                        handleCancelledCallInvite(cancelledCallInvite, callException);
                    }
                });

                if (!valid) {
                    Log.e(TAG, "The message was not a valid Twilio Voice SDK payload: " + remoteMessage.getData());
                }
            }

            // Check if message contains a notification payload.
            if (remoteMessage.getNotification() != null) {
                Log.e(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
            }
        }
    }

    public static String serializeToJson(CallInvite callInvite) {
        Gson gson = new Gson();
        return gson.toJson(callInvite);
    }

    public static String serializeToJsonCancelledCallInvite(CancelledCallInvite cancelledCallInvite) {
        Gson gson = new Gson();
        return gson.toJson(cancelledCallInvite);
    }

    private void handleInvite(CallInvite callInvite, int notificationId) {
        Intent intent = new Intent(this, IncomingCallNotificationService.class);

        intent.setAction(Constants.ACTION_INCOMING_CALL);
        intent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        intent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);

        // Passing params
        // Data.Builder builder = new Data.Builder();
        // Map params = new HashMap<String, Object>();

        // params.put(Constants.INCOMING_CALL_INVITE, serializeToJson(callInvite));
        // params.put(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        // params.put("CALL_ACTION", Constants.ACTION_INCOMING_CALL);

        // builder.putAll(params);
        // Data data = builder.build();

        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        //     OneTimeWorkRequest request = new OneTimeWorkRequest
        //       .Builder(IncomingCallNotificationWorker.class).addTag("IncomingCallNotificationWorker")
        //       .setInputData(data)
        //       .build();
        //     WorkManager.getInstance(this).enqueue(request);
        // } else
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void handleCancelledCallInvite(CancelledCallInvite cancelledCallInvite, CallException callException) {
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        //   // Passing params
        //   Data.Builder builder = new Data.Builder();
        //   Map params = new HashMap<String, Object>();

        //   params.put(Constants.CANCELLED_CALL_INVITE, serializeToJsonCancelledCallInvite(cancelledCallInvite));
        //   params.put("CALL_ACTION", Constants.ACTION_CANCEL_CALL);

        //   builder.putAll(params);
        //   Data data = builder.build();
        //   OneTimeWorkRequest request = new OneTimeWorkRequest
        //     .Builder(IncomingCallNotificationWorker.class).addTag("IncomingCallNotificationWorker")
        //     .setInputData(data)
        //     .build();
        //   WorkManager.getInstance(this).enqueue(request);
        // } else {
          Intent intent = new Intent(this, IncomingCallNotificationService.class);
          intent.setAction(Constants.ACTION_CANCEL_CALL);
          intent.putExtra(Constants.CANCELLED_CALL_INVITE, cancelledCallInvite);

          if (callException != null) {
              intent.putExtra(Constants.CANCELLED_CALL_INVITE_EXCEPTION, callException.getMessage());
          }

          startService(intent);
        // }
    }
}
