package com.hoxfon.react.RNTwilioVoice.fcm;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Notification;
import android.os.Build;
import android.graphics.Color;
import android.content.Context;
import androidx.core.app.NotificationCompat;

import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.hoxfon.react.RNTwilioVoice.Constants;
import com.hoxfon.react.RNTwilioVoice.IncomingCallNotificationService;
import com.twilio.voice.CallException;
import com.hoxfon.react.RNTwilioVoice.BuildConfig;
import com.hoxfon.react.RNTwilioVoice.CallNotificationManager;
import com.twilio.voice.CallInvite;
import com.twilio.voice.CancelledCallInvite;
import com.twilio.voice.MessageListener;
import com.twilio.voice.Voice;

import com.hoxfon.react.RNTwilioVoice.R;

import java.util.Map;
import java.util.Random;

import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.TAG;
import io.intercom.android.sdk.push.IntercomPushClient;

public class VoiceFirebaseMessagingService extends FirebaseMessagingService {
    private final IntercomPushClient intercomPushClient = new IntercomPushClient();

    private void startMyOwnForeground(){
        String NOTIFICATION_CHANNEL_ID = "com.salesmessage.arcadia.app";
        String channelName = "Salesmassage Call Service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.drawable.ic_call_white_24dp)
                .setContentTitle(getString(R.string.call_incoming_title))
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(2, notification);
    }

    @Override
    public void onCreate() {
        if (isServiceRunning()) {
            startMyOwnForeground();
        }

        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopForeground(true);
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)){
            if ("com.salesmessage.arcadia.app".equals(service.service.getClassName())) {
                return true;
            }
        }
            return false;
    }

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

    private void handleInvite(CallInvite callInvite, int notificationId) {
        Intent intent = new Intent(this, IncomingCallNotificationService.class);
        intent.setAction(Constants.ACTION_INCOMING_CALL);
        intent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        intent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void handleCancelledCallInvite(CancelledCallInvite cancelledCallInvite, CallException callException) {
        Intent intent = new Intent(this, IncomingCallNotificationService.class);
        intent.setAction(Constants.ACTION_CANCEL_CALL);
        intent.putExtra(Constants.CANCELLED_CALL_INVITE, cancelledCallInvite);
        if (callException != null) {
            intent.putExtra(Constants.CANCELLED_CALL_INVITE_EXCEPTION, callException.getMessage());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }
}
