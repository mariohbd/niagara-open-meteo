package app.d0nj.extension.weather;

import android.os.Looper;
import java.lang.reflect.InvocationTargetException;

/** Version-specific members are checked by the patch before this bridge is installed. */
public final class NiagaraWeatherBridge {
    private NiagaraWeatherBridge() {}

    public static Object fetch(Object coroutine, Object incoming) throws Throwable {
        ClassLoader loader = coroutine.getClass().getClassLoader();
        boolean is1635 = coroutine.getClass().getName().equals("b.Hu7yEGV1H1r1bdCCcwuur8scnv1v");
        if (!is1635 && !coroutine.getClass().getName().equals("b.hKoAGpeDNCeUKj"))
            throw new IllegalArgumentException("Unsupported weather coroutine");
        try {
            if (Looper.myLooper() == Looper.getMainLooper())
                throw new IllegalStateException("Weather fetch must run on Dispatchers.IO");
            // Preserve Kotlin Result failure semantics at the coroutine entry point.
            Class.forName(is1635 ? "b.Qx3wzSHU2H1QDj" : "b.DCcE1vyHs4o", false, loader)
                    .getMethod(is1635 ? "SJiFQ7HDN1InfkB1Erx8z20l" : "aD3Ncu302iGQ5j7cZ7nBmv2dsVG", Object.class).invoke(null, incoming);
            Class<?> type = coroutine.getClass();
            if (type.getField(is1635 ? "ojn5c2YOWTFy0045br2ifslIhtlq" : "Xg3202PBvhqN2gTjXPAuDAPu7").getInt(coroutine) != 0)
                throw new IllegalStateException("Unexpected weather coroutine resume");
            Object repository = type.getField(is1635 ? "ECePjANygrkOgQnMq53" : "M9Fe8pvPjYM").get(coroutine);
            Object location = type.getField(is1635 ? "Qph1ZWcbx3tkLvD" : "mSzisdZo1gIkaJJamui2eM").get(coroutine);
            String language = (String) type.getField(is1635 ? "TfTzbajyaQF1" : "JfD5anH8HmnM40u").get(coroutine);
            double latitude = location.getClass().getField(is1635 ? "Kj2k1AqaZsaVCT" : "scSVXSelHn0vGdEhMIkeOConMr").getFloat(location);
            double longitude = location.getClass().getField(is1635 ? "SJiFQ7HDN1InfkB1Erx8z20l" : "lJFNcBQHCClkpT8l7Xkn8v").getFloat(location);
            String response = OpenMeteoProvider.fetch(latitude, longitude, language);
            Object weather = repository.getClass()
                    .getMethod(is1635 ? "gE0rVOWsj4loTBBoy2xSrtVpMP1H" : "RABvSkqAo10GtuvvLvFAVN", String.class, long.class)
                    .invoke(repository, response, System.currentTimeMillis());
            repository.getClass().getMethod(is1635 ? "ZFqutaGTYq" : "PIFjKdTFx4bkGdxkn5oksjktQ8eg", String.class)
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

