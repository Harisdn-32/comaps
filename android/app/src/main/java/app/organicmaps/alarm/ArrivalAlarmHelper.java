package app.organicmaps.alarm;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import app.organicmaps.R;
import app.organicmaps.sdk.util.Distance;
import java.util.Locale;

public final class ArrivalAlarmHelper
{
  public static final double DEFAULT_RADIUS_METERS = 500.0;
  private static final String PREF_ENABLED_KEY = "arrival_alarm_enabled";
  private static final String PREF_RADIUS_KEY = "arrival_alarm_radius";

  private ArrivalAlarmHelper() {}

  public static boolean isAlarmEnabled(@NonNull Context context)
  {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    return prefs.getBoolean(PREF_ENABLED_KEY, true);
  }

  public static double getAlarmRadiusMeters(@NonNull Context context)
  {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    try
    {
      final String radiusStr = prefs.getString(PREF_RADIUS_KEY, "500");
      if (radiusStr != null)
        return Double.parseDouble(radiusStr);
    }
    catch (Exception ignored)
    {
    }
    return DEFAULT_RADIUS_METERS;
  }

  public static double toMeters(@Nullable Distance distance)
  {
    if (distance == null || !distance.isValid())
      return -1.0;

    return switch (distance.mUnits)
    {
      case Meters -> distance.mDistance;
      case Kilometers -> distance.mDistance * 1000.0;
      case Feet -> distance.mDistance * 0.3048;
      case Miles -> distance.mDistance * 1609.344;
    };
  }

  @NonNull
  public static String formatDistance(@NonNull Context context, double meters)
  {
    if (meters < 0)
      return "";
    if (meters < 1000)
      return String.format(Locale.getDefault(), "%d m", Math.round(meters));
    return String.format(Locale.getDefault(), "%.1f km", meters / 1000.0);
  }
}
