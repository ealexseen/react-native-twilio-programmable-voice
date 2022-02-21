package com.hoxfon.react.RNTwilioVoice;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Vibrator;

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

        ringtone.setAudioAttributes(alarmAttribute);
    }

    public static SoundPoolManager getInstance(Context context) {
        if (instance == null) {
            instance = new SoundPoolManager(context);
        }
        return instance;
    }

    public void playRinging() {
        if (audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL && !playing) {
            ringtone.play();
            playing = true;
        } else if (audioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {
            long[] pattern = {0, 300, 1000};

            // 0 meaning is repeat indefinitely
            vibe.vibrate(pattern, 0);
        }
    }

    public void stopRinging() {
        if (ringtone.isPlaying()) {
            ringtone.stop();
            playing = false;
        }

        vibe.cancel();
    }

    public void playDisconnect() {
        if (!ringtone.isPlaying()) {
            ringtone.stop();
            playing = false;
        }
    }

}
