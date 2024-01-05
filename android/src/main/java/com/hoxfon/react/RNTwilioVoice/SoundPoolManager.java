package com.hoxfon.react.RNTwilioVoice;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.util.Log;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.TAG;
import android.content.pm.PackageManager;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

public class SoundPoolManager {

    private boolean playing = false;
    private static SoundPoolManager instance;
    private Ringtone ringtone = null;
    private AudioManager audioManager = null;
    private Vibrator vibe = null;

    private static Context _context = null;

    private SoundPoolManager(Context context) {
        vibe = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        Uri ringtoneSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        ringtone = RingtoneManager.getRingtone(context, ringtoneSound);
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes alarmAttribute = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        if (ringtone != null) {
            ringtone.setAudioAttributes(alarmAttribute);
        }
    }

    public static SoundPoolManager getInstance(Context context) {
        if (instance == null) {
            instance = new SoundPoolManager(context);
            _context = context;
        }
        return instance;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private boolean isBluetoothHeadsetConnected() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        boolean hasBluetoothPermission = ActivityCompat.checkSelfPermission(_context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;

        Log.i(TAG, "hasBluetoothPermission: " + hasBluetoothPermission);

        try {
            return bluetoothAdapter != null && bluetoothAdapter.isEnabled()
                    && bluetoothAdapter.getProfileConnectionState(BluetoothHeadset.HEADSET) == BluetoothAdapter.STATE_CONNECTED;
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }

        return false;
    }

    public void playRinging() {
        if (audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL && !playing && ringtone != null) {
            ringtone.play();
            playing = true;
            boolean isBluetoothHeadsetDeviceConnected = false;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                isBluetoothHeadsetDeviceConnected = isBluetoothHeadsetConnected();
            }
            Log.i(TAG, "isBluetoothHeadsetConnected: " + isBluetoothHeadsetDeviceConnected);

            if (isBluetoothHeadsetDeviceConnected) {
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                audioManager.startBluetoothSco();
                audioManager.setBluetoothScoOn(true);
            }
        } else if (audioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {
            long[] pattern = {0, 300, 1000};

            // 0 meaning is repeat indefinitely
            vibe.vibrate(pattern, 0);
        }
    }

    public void stopRinging() {
        try {
            if (ringtone.isPlaying() && ringtone != null) {
                ringtone.stop();
                playing = false;
            }

            vibe.cancel();
        } catch (Exception e) {
          Log.e(TAG, "Failed from stopRinging");
        }
    }

    public void playDisconnect() {
        if (!ringtone.isPlaying()) {
            ringtone.stop();
            playing = false;
        }
    }

}
