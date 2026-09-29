package app.d0nj.extension.weather;

import android.os.Looper;
import java.lang.reflect.InvocationTargetException;

/** Version-specific members are checked by the patch before this bridge is installed. */
public final class NiagaraWeatherBridge {
    private NiagaraWeatherBridge() {}

    public static Object fetch(Object coroutine, Object incoming) throws Throwable {
        ClassLoader loader = coroutine.getClass().getClassLoader();
        try {
            if (Looper.myLooper() == Looper.getMainLooper())
                throw new IllegalStateException("Weather fetch must run on Dispatchers.IO");
            // Preserve Kotlin Result failure semantics at the coroutine entry point.
            Class.forName("b.DCcE1vyHs4o", false, loader)
                    .getMethod("aD3Ncu302iGQ5j7cZ7nBmv2dsVG", Object.class).invoke(null, incoming);
            Class<?> type = coroutine.getClass();
            if (type.getField("Xg3202PBvhqN2gTjXPAuDAPu7").getInt(coroutine) != 0)
                throw new IllegalStateException("Unexpected weather coroutine resume");
            Object repository = type.getField("M9Fe8pvPjYM").get(coroutine);
            Object location = type.getField("mSzisdZo1gIkaJJamui2eM").get(coroutine);
            String language = (String) type.getField("JfD5anH8HmnM40u").get(coroutine);
            double latitude = location.getClass().getField("scSVXSelHn0vGdEhMIkeOConMr").getFloat(location);
            double longitude = location.getClass().getField("lJFNcBQHCClkpT8l7Xkn8v").getFloat(location);
            String response = OpenMeteoProvider.fetch(latitude, longitude, language);
            Object weather = repository.getClass()
                    .getMethod("RABvSkqAo10GtuvvLvFAVN", String.class, long.class)
                    .invoke(repository, response, System.currentTimeMillis());
            repository.getClass().getMethod("PIFjKdTFx4bkGdxkn5oksjktQ8eg", String.class)
                    .invoke(repository, response);
            return weather;
        } catch (Exception error) {
            Throwable cause = error instanceof InvocationTargetException ? error.getCause() : error;
            if (cause instanceof java.util.concurrent.CancellationException) throw cause;
            if (cause instanceof Error) throw cause;
            if (cause instanceof InterruptedException) Thread.currentThread().interrupt();
            Exception failure = cause instanceof Exception ? (Exception) cause : error;
            // The existing repository handles this exception with its normal cache/retry policy.
            throw (Throwable) Class.forName("bitpit.launcher.weather.NoWeatherDataException", false, loader)
                    .getConstructor(int.class, Exception.class, String.class)
                    .newInstance(5, failure, null);
        }
    }
}
