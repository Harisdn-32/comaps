package app.organicmaps.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import app.organicmaps.sdk.util.log.Logger;

public class ArrivalAlarmReceiver extends BroadcastReceiver
{
  private static final String TAG = ArrivalAlarmReceiver.class.getSimpleName();
  public static final String ACTION_DISMISS_ALARM = "app.organicmaps.alarm.ACTION_DISMISS_ALARM";

  @Override
  public void onReceive(@NonNull Context context, @NonNull Intent intent)
  {
    final String action = intent.getAction();
    Logger.i(TAG, "Received broadcast action: " + action);

    if (ACTION_DISMISS_ALARM.equals(action))
    {
      ArrivalAlarmManager.getInstance(context).dismissAlarm(context);
    }
  }
}
