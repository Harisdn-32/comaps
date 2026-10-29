package app.organicmaps.alarm;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;
import app.organicmaps.sdk.bookmarks.data.MapObject;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.routing.RoutingInfo;
import app.organicmaps.sdk.util.log.Logger;

public class ArrivalAlarmManager
{
  private static final String TAG = ArrivalAlarmManager.class.getSimpleName();

  private static volatile ArrivalAlarmManager sInstance;

  private boolean mAlarmActive = false;
  private boolean mAlarmDismissedForCurrentRoute = false;
  @Nullable
  private String mLastTargetTitle = null;
  private double mLastTargetLat = 0.0;
  private double mLastTargetLon = 0.0;

  private ArrivalAlarmManager(@NonNull Context context)
  {
    ArrivalAlarmService.createNotificationChannel(context.getApplicationContext());
  }

  @NonNull
  public static ArrivalAlarmManager getInstance(@NonNull Context context)
  {
    if (sInstance == null)
    {
      synchronized (ArrivalAlarmManager.class)
      {
        if (sInstance == null)
          sInstance = new ArrivalAlarmManager(context);
      }
    }
    return sInstance;
  }

  public synchronized boolean isAlarmActive()
  {
    return mAlarmActive;
  }

  public synchronized void onRouteFollowingInfo(@NonNull Context context,
                                               @Nullable RoutingInfo routingInfo,
                                               @Nullable Location location)
  {
    final RoutingController routingController = RoutingController.get();
    if (!routingController.isNavigating())
      return;

    if (!ArrivalAlarmHelper.isAlarmEnabled(context))
      return;

    final MapObject endPoint = routingController.getEndPoint();
    final String currentTargetTitle = endPoint != null ? endPoint.getTitle() : null;
    final double currentLat = endPoint != null ? endPoint.getLat() : 0.0;
    final double currentLon = endPoint != null ? endPoint.getLon() : 0.0;

    // Detect if destination changed
    if (isNewDestination(currentTargetTitle, currentLat, currentLon))
    {
      Logger.i(TAG, "New destination detected: " + currentTargetTitle);
      mLastTargetTitle = currentTargetTitle;
      mLastTargetLat = currentLat;
      mLastTargetLon = currentLon;
      mAlarmDismissedForCurrentRoute = false;
      mAlarmActive = false;
    }

    if (mAlarmActive || mAlarmDismissedForCurrentRoute)
      return;

    double distMeters = -1.0;
    if (routingInfo != null && routingInfo.distToTarget != null)
    {
      distMeters = ArrivalAlarmHelper.toMeters(routingInfo.distToTarget);
    }

    // Fallback to straight-line distance if routing distToTarget is not yet available
    if (distMeters <= 0 && location != null && endPoint != null)
    {
      final Location destLoc = new Location("");
      destLoc.setLatitude(endPoint.getLat());
      destLoc.setLongitude(endPoint.getLon());
      distMeters = location.distanceTo(destLoc);
    }

    final double triggerRadius = ArrivalAlarmHelper.getAlarmRadiusMeters(context);
    Logger.d(TAG, "Checking arrival: dist=" + distMeters + "m, triggerRadius=" + triggerRadius + "m");

    if (distMeters > 0 && distMeters <= triggerRadius)
    {
      final String destName = currentTargetTitle != null && !currentTargetTitle.isEmpty()
          ? currentTargetTitle
          : context.getString(R.string.arrival_alarm_destination_default);
      triggerAlarm(context, distMeters, destName);
    }
  }

  private boolean isNewDestination(@Nullable String title, double lat, double lon)
  {
    if (mLastTargetTitle == null && title != null)
      return true;
    if (title != null && !title.equals(mLastTargetTitle))
      return true;
    return Math.abs(lat - mLastTargetLat) > 0.0001 || Math.abs(lon - mLastTargetLon) > 0.0001;
  }

  private synchronized void triggerAlarm(@NonNull Context context, double distMeters, @NonNull String destinationName)
  {
    if (mAlarmActive || mAlarmDismissedForCurrentRoute)
      return;

    Logger.i(TAG, "Triggering destination arrival alarm! Distance: " + distMeters + "m, Destination: " + destinationName);
    mAlarmActive = true;

    final String distanceFormatted = ArrivalAlarmHelper.formatDistance(context, distMeters);

    // Start background alarm audio & vibration service
    ArrivalAlarmService.start(context, distanceFormatted, destinationName);

    // Launch Full Screen Alarm Activity directly
    try
    {
      final Intent alarmActivityIntent = new Intent(context, ArrivalAlarmActivity.class);
      alarmActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
      alarmActivityIntent.putExtra(ArrivalAlarmActivity.EXTRA_DISTANCE, distanceFormatted);
      alarmActivityIntent.putExtra(ArrivalAlarmActivity.EXTRA_DESTINATION, destinationName);
      context.startActivity(alarmActivityIntent);
    }
    catch (Exception e)
    {
      Logger.e(TAG, "Failed to launch ArrivalAlarmActivity directly", e);
    }
  }

  public synchronized void dismissAlarm(@NonNull Context context)
  {
    Logger.i(TAG, "Dismissing arrival alarm");
    mAlarmActive = false;
    mAlarmDismissedForCurrentRoute = true;

    ArrivalAlarmService.stop(context);

    // Notify any active ArrivalAlarmActivity to finish
    try
    {
      final Intent dismissBroadcast = new Intent(ArrivalAlarmActivity.ACTION_DISMISS_ACTIVITY);
      dismissBroadcast.setPackage(context.getPackageName());
      context.sendBroadcast(dismissBroadcast);
    }
    catch (Exception e)
    {
      Logger.e(TAG, "Error sending dismiss broadcast", e);
    }
  }

  public synchronized void onNavigationStopped(@NonNull Context context)
  {
    Logger.i(TAG, "Navigation stopped");
    if (mAlarmActive)
      dismissAlarm(context);

    mAlarmDismissedForCurrentRoute = false;
    mLastTargetTitle = null;
    mLastTargetLat = 0.0;
    mLastTargetLon = 0.0;
  }
}
