package app.organicmaps.alarm;

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import app.organicmaps.MwmActivity;
import app.organicmaps.R;
import app.organicmaps.sdk.util.log.Logger;

public class ArrivalAlarmService extends Service
{
  private static final String TAG = ArrivalAlarmService.class.getSimpleName();

  public static final String ACTION_START_ALARM = "app.organicmaps.alarm.ACTION_START_ALARM";
  public static final String ACTION_STOP_ALARM = "app.organicmaps.alarm.ACTION_STOP_ALARM";

  public static final String EXTRA_DISTANCE_STR = "extra_distance_str";
  public static final String EXTRA_DESTINATION_NAME = "extra_destination_name";

  public static final String CHANNEL_ID = "ARRIVAL_ALARM_CHANNEL";
  public static final int NOTIFICATION_ID = 88889999;
  private static final int FULL_SCREEN_REQUEST_CODE = 4455;
  private static final int DISMISS_REQUEST_CODE = 4456;
  private static final int VIEW_MAP_REQUEST_CODE = 4457;

  @Nullable
  private MediaPlayer mMediaPlayer;
  @Nullable
  private Vibrator mVibrator;
  @Nullable
  private PowerManager.WakeLock mWakeLock;

  public static void start(@NonNull Context context, @NonNull String distanceStr, @NonNull String destinationName)
  {
    final Intent intent = new Intent(context, ArrivalAlarmService.class);
    intent.setAction(ACTION_START_ALARM);
    intent.putExtra(EXTRA_DISTANCE_STR, distanceStr);
    intent.putExtra(EXTRA_DESTINATION_NAME, destinationName);
    try
    {
      context.startService(intent);
    }
    catch (Exception e)
    {
      Logger.e(TAG, "Failed to start ArrivalAlarmService", e);
    }
  }

  public static void stop(@NonNull Context context)
  {
    final Intent intent = new Intent(context, ArrivalAlarmService.class);
    intent.setAction(ACTION_STOP_ALARM);
    try
    {
      context.startService(intent);
    }
    catch (Exception e)
    {
      Logger.e(TAG, "Failed to stop ArrivalAlarmService", e);
    }
  }

  @Override
  public void onCreate()
  {
    super.onCreate();
    Logger.i(TAG, "onCreate");
    createNotificationChannel(this);
  }

  @Override
  public int onStartCommand(@Nullable Intent intent, int flags, int startId)
  {
    if (intent == null)
    {
      stopSelf();
      return START_NOT_STICKY;
    }

    final String action = intent.getAction();
    if (ACTION_STOP_ALARM.equals(action))
    {
      Logger.i(TAG, "Received ACTION_STOP_ALARM");
      stopAlarm();
      stopSelf();
      return START_NOT_STICKY;
    }

    if (ACTION_START_ALARM.equals(action))
    {
      final String distanceStr = intent.getStringExtra(EXTRA_DISTANCE_STR);
      final String destinationName = intent.getStringExtra(EXTRA_DESTINATION_NAME);
      Logger.i(TAG, "Received ACTION_START_ALARM for " + destinationName + " at " + distanceStr);

      acquireWakeLock();
      startAudioAndVibration();
      showAlarmNotification(distanceStr != null ? distanceStr : "",
                             destinationName != null ? destinationName : getString(R.string.arrival_alarm_destination_default));
    }

    return START_NOT_STICKY;
  }

  private void acquireWakeLock()
  {
    try
    {
      final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
      if (pm != null && (mWakeLock == null || !mWakeLock.isHeld()))
      {
        @SuppressWarnings("deprecation")
        int flags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE;
        mWakeLock = pm.newWakeLock(flags, "organicmaps:arrival_alarm_wakelock");
        mWakeLock.acquire(60 * 1000L /* 60 seconds max */);
      }
    }
    catch (Exception e)
    {
      Logger.e(TAG, "Failed to acquire WakeLock", e);
    }
  }

  private void releaseWakeLock()
  {
    try
    {
      if (mWakeLock != null && mWakeLock.isHeld())
      {
        mWakeLock.release();
        mWakeLock = null;
      }
    }
    catch (Exception e)
    {
      Logger.e(TAG, "Failed to release WakeLock", e);
    }
  }

  private void startAudioAndVibration()
  {
    // Start continuous audio playback
    try
    {
      if (mMediaPlayer == null)
      {
        Uri alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alertUri == null)
          alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (alertUri == null)
          alertUri = Settings.System.DEFAULT_RINGTONE_URI;

        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setDataSource(this, alertUri);

        final AudioAttributes audioAttributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
        mMediaPlayer.setAudioAttributes(audioAttributes);
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
        mMediaPlayer.setLooping(true);
        mMediaPlayer.prepare();
        mMediaPlayer.start();
      }
    }
    catch (Exception e)
    {
      Logger.e(TAG, "Failed to start alarm audio playback", e);
    }

    // Start repeating vibration
    try
    {
      mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
      if (mVibrator != null && mVibrator.hasVibrator())
      {
        final long[] pattern = {0, 800, 400, 800, 400};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
          mVibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
        }
        else
        {
          @SuppressWarnings("deprecation")
          long[] p = pattern;
          mVibrator.vibrate(p, 0);
        }
      }
    }
    catch (Exception e)
    {
      Logger.e(TAG, "Failed to start alarm vibration", e);
    }
  }

  private void stopAlarm()
  {
    try
    {
      if (mMediaPlayer != null)
      {
        if (mMediaPlayer.isPlaying())
          mMediaPlayer.stop();
        mMediaPlayer.release();
        mMediaPlayer = null;
      }
    }
    catch (Exception e)
    {
      Logger.e(TAG, "Error stopping MediaPlayer", e);
    }

    try
    {
      if (mVibrator != null)
      {
        mVibrator.cancel();
        mVibrator = null;
      }
    }
    catch (Exception e)
    {
      Logger.e(TAG, "Error cancelling Vibrator", e);
    }

    releaseWakeLock();

    try
    {
      final NotificationManagerCompat nm = NotificationManagerCompat.from(this);
      nm.cancel(NOTIFICATION_ID);
    }
    catch (Exception e)
    {
      Logger.e(TAG, "Error cancelling notification", e);
    }
  }

  private void showAlarmNotification(@NonNull String distanceStr, @NonNull String destinationName)
  {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        && ActivityCompat.checkSelfPermission(this, POST_NOTIFICATIONS) != PERMISSION_GRANTED)
    {
      Logger.w(TAG, "POST_NOTIFICATIONS permission not granted, skipping notification");
      return;
    }

    final int flagImmutable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;

    // Full screen intent targeting ArrivalAlarmActivity
    final Intent fullScreenIntent = new Intent(this, ArrivalAlarmActivity.class);
    fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    fullScreenIntent.putExtra(ArrivalAlarmActivity.EXTRA_DISTANCE, distanceStr);
    fullScreenIntent.putExtra(ArrivalAlarmActivity.EXTRA_DESTINATION, destinationName);
    final PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
        this, FULL_SCREEN_REQUEST_CODE, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT | flagImmutable);

    // Dismiss PendingIntent
    final Intent dismissIntent = new Intent(this, ArrivalAlarmReceiver.class);
    dismissIntent.setAction(ArrivalAlarmReceiver.ACTION_DISMISS_ALARM);
    final PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(
        this, DISMISS_REQUEST_CODE, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT | flagImmutable);

    // View Map PendingIntent
    final Intent mapIntent = new Intent(this, MwmActivity.class);
    mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    final PendingIntent mapPendingIntent = PendingIntent.getActivity(
        this, VIEW_MAP_REQUEST_CODE, mapIntent, PendingIntent.FLAG_UPDATE_CURRENT | flagImmutable);

    final String contentText = getString(R.string.arrival_alarm_notification_text, distanceStr);

    final NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_logo_small)
        .setContentTitle(getString(R.string.arrival_alarm_notification_title))
        .setContentText(contentText)
        .setStyle(new NotificationCompat.BigTextStyle().bigText(destinationName + " - " + contentText))
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setFullScreenIntent(fullScreenPendingIntent, true)
        .setContentIntent(fullScreenPendingIntent)
        .setOngoing(true)
        .setAutoCancel(false)
        .addAction(0, getString(R.string.arrival_alarm_dismiss), dismissPendingIntent)
        .addAction(0, getString(R.string.arrival_alarm_view_map), mapPendingIntent);

    NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build());
  }

  public static void createNotificationChannel(@NonNull Context context)
  {
    final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
    final NotificationChannelCompat channel =
        new NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.arrival_alarm_channel_name))
            .setDescription(context.getString(R.string.arrival_alarm_channel_description))
            .setVibrationEnabled(true)
            .setLightsEnabled(true)
            .build();
    notificationManager.createNotificationChannel(channel);
  }

  @Override
  public void onDestroy()
  {
    super.onDestroy();
    Logger.i(TAG, "onDestroy");
    stopAlarm();
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent)
  {
    return null;
  }
}
