package app.organicmaps.alarm;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import app.organicmaps.MwmActivity;
import app.organicmaps.R;
import app.organicmaps.sdk.util.log.Logger;

public class ArrivalAlarmActivity extends AppCompatActivity
{
  private static final String TAG = ArrivalAlarmActivity.class.getSimpleName();

  public static final String EXTRA_DISTANCE = "extra_distance";
  public static final String EXTRA_DESTINATION = "extra_destination";
  public static final String ACTION_DISMISS_ACTIVITY = "app.organicmaps.alarm.ACTION_DISMISS_ACTIVITY";

  @Nullable
  private ObjectAnimator mPulseAnimator;

  private final BroadcastReceiver mDismissReceiver = new BroadcastReceiver()
  {
    @Override
    public void onReceive(Context context, Intent intent)
    {
      if (ACTION_DISMISS_ACTIVITY.equals(intent.getAction()))
      {
        Logger.i(TAG, "Dismiss action received via broadcast, finishing activity");
        finish();
      }
    }
  };

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    Logger.i(TAG, "onCreate");

    setupWindowFlags();
    setContentView(R.layout.activity_arrival_alarm);

    initViews();
    registerDismissReceiver();
  }

  private void setupWindowFlags()
  {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1)
    {
      setShowWhenLocked(true);
      setTurnScreenOn(true);
      final KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
      if (km != null)
        km.requestDismissKeyguard(this, null);
    }
    else
    {
      @SuppressWarnings("deprecation")
      int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
          | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
          | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
          | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
      getWindow().addFlags(flags);
    }
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  private void initViews()
  {
    final String distanceStr = getIntent().getStringExtra(EXTRA_DISTANCE);
    final String destinationStr = getIntent().getStringExtra(EXTRA_DESTINATION);

    final TextView tvDestination = findViewById(R.id.alarm_destination_title);
    if (tvDestination != null && destinationStr != null && !destinationStr.isEmpty())
      tvDestination.setText(destinationStr);

    final TextView tvDistance = findViewById(R.id.alarm_distance_remaining);
    if (tvDistance != null && distanceStr != null && !distanceStr.isEmpty())
      tvDistance.setText(getString(R.string.arrival_alarm_distance_remaining, distanceStr));

    // Pulsing animation for the alarm icon
    final View iconContainer = findViewById(R.id.icon_container);
    if (iconContainer != null)
    {
      mPulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
          iconContainer,
          PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.15f),
          PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.15f)
      );
      mPulseAnimator.setDuration(600);
      mPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
      mPulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
      mPulseAnimator.start();
    }

    // Dismiss Button
    final View btnDismiss = findViewById(R.id.btn_dismiss_alarm);
    if (btnDismiss != null)
    {
      btnDismiss.setOnClickListener(v -> {
        Logger.i(TAG, "Dismiss button clicked");
        ArrivalAlarmManager.getInstance(this).dismissAlarm(this);
        finish();
      });
    }

    // View Map Button
    final View btnViewMap = findViewById(R.id.btn_view_map);
    if (btnViewMap != null)
    {
      btnViewMap.setOnClickListener(v -> {
        Logger.i(TAG, "View Map button clicked");
        ArrivalAlarmManager.getInstance(this).dismissAlarm(this);
        final Intent mapIntent = new Intent(this, MwmActivity.class);
        mapIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(mapIntent);
        finish();
      });
    }
  }

  private void registerDismissReceiver()
  {
    final IntentFilter filter = new IntentFilter(ACTION_DISMISS_ACTIVITY);
    ContextCompat.registerReceiver(this, mDismissReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
  }

  @Override
  protected void onDestroy()
  {
    super.onDestroy();
    Logger.i(TAG, "onDestroy");

    if (mPulseAnimator != null)
    {
      mPulseAnimator.cancel();
      mPulseAnimator = null;
    }

    try
    {
      unregisterReceiver(mDismissReceiver);
    }
    catch (Exception ignored)
    {
    }
  }

  @Override
  @SuppressWarnings("MissingSuperCall")
  public void onBackPressed()
  {
    Logger.i(TAG, "onBackPressed");
    ArrivalAlarmManager.getInstance(this).dismissAlarm(this);
    finish();
  }
}
