package com.hoxfon.react.RNTwilioVoice;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Vibrator;
import android.util.Log;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.TAG;

public class SoundPoolManager {

    private boolean playing = false;
    private static SoundPoolManager instance;
    private Ringtone ringtone = null;
    private AudioManager audioManager = null;
    private Vibrator vibe = null;

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
        }
        return instance;
    }

    public void playRinging() {
        if (audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL && !playing && ringtone != null) {
            ringtone.play();
            playing = true;
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
