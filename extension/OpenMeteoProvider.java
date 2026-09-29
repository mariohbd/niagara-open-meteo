package app.d0nj.extension.weather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import javax.net.ssl.HttpsURLConnection;

/** Open-Meteo -> Niagara 1.16.28's existing WeatherResponse JSON contract. */
public final class OpenMeteoProvider {
    private OpenMeteoProvider() {}

    public static String url(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
            throw new IllegalArgumentException("Invalid weather coordinates");
        }
        return "https://api.open-meteo.com/v1/forecast?latitude=" + latitude
                + "&longitude=" + longitude + "&timezone=auto&timeformat=unixtime"
                + "&temperature_unit=celsius&forecast_days=7"
                + "&current=temperature_2m,apparent_temperature,weather_code,is_day"
                + "&hourly=temperature_2m,apparent_temperature,precipitation_probability,weather_code,is_day"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max";
    }

    public static String fetch(double latitude, double longitude, String language) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(url(latitude, longitude)).openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(6000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Niagara-OpenMeteo-Patch/0.1");
        long deadline = System.nanoTime() + 12_000_000_000L;
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("Weather provider HTTP " + status);
            try (InputStream stream = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Weather cancelled");
                    if (System.nanoTime() > deadline) throw new IOException("Weather response timeout");
                    if (output.size() + count > 2_000_000) throw new IOException("Weather response too large");
                    output.write(buffer, 0, count);
                }
                return convert(new String(output.toByteArray(), StandardCharsets.UTF_8), latitude,
                        longitude, language, System.currentTimeMillis() / 1000L);
            }
        } finally {
            connection.disconnect();
        }
    }

    public static String convert(String json, double latitude, double longitude,
                                 String language, long fetchedAt) throws JSONException {
        url(latitude, longitude);
        JSONObject source = new JSONObject(json);
        if (source.optBoolean("error", false)) throw new JSONException("Weather provider rejected request");
        JSONObject current = source.getJSONObject("current");
        JSONObject hourly = source.getJSONObject("hourly");
        JSONObject daily = source.getJSONObject("daily");
        // The request explicitly selects Celsius. Do not silently accept a changed unit contract.
        if (!"°C".equals(source.getJSONObject("current_units").getString("temperature_2m"))
                || !"°C".equals(source.getJSONObject("hourly_units").getString("temperature_2m"))) {
            throw new JSONException("Unexpected temperature unit");
        }
        long currentTime = current.getLong("time");
        if (Math.abs(currentTime - fetchedAt) > 10800) throw new JSONException("Stale current weather");
        JSONObject now = new JSONObject().put("dt", currentTime)
                .put("temp", number(current, "temperature_2m"))
                .put("feels_like", number(current, "apparent_temperature"))
                .put("weather", condition(current.getInt("weather_code"), current.getInt("is_day") == 1, language));

        JSONArray hours = new JSONArray();
        JSONArray times = hourly.getJSONArray("time");
        validateColumns(hourly, times.length(), "temperature_2m", "apparent_temperature",
                "precipitation_probability", "weather_code", "is_day");
        long previous = Long.MIN_VALUE;
        for (int i = 0; i < times.length(); i++) {
            long time = times.getLong(i);
            if (time <= previous) throw new JSONException("Unordered hourly timestamps");
            previous = time;
            // Retain the current hour, needed by Niagara for the current precipitation probability.
            if (time < currentTime - 3600 || missing(hourly, i, "temperature_2m", "apparent_temperature",
                    "precipitation_probability", "weather_code", "is_day")) continue;
            hours.put(new JSONObject().put("dt", time)
                    .put("temp", at(hourly, "temperature_2m", i))
                    .put("feels_like", at(hourly, "apparent_temperature", i))
                    .put("pop", probability(at(hourly, "precipitation_probability", i)))
                    .put("weather", condition(hourly.getJSONArray("weather_code").getInt(i),
                            hourly.getJSONArray("is_day").getInt(i) == 1, language)));
        }
        if (hours.length() == 0) throw new JSONException("No usable hourly forecast");

        ZoneId zone = ZoneId.of(source.getString("timezone"));
        JSONArray days = new JSONArray();
        JSONArray dates = daily.getJSONArray("time");
        validateColumns(daily, dates.length(), "temperature_2m_min", "temperature_2m_max",
                "precipitation_probability_max", "weather_code", "sunrise", "sunset");
        for (int i = 0; i < dates.length(); i++) {
            if (missing(daily, i, "temperature_2m_min", "temperature_2m_max",
                    "precipitation_probability_max", "weather_code")) continue;
            long time = dates.getLong(i);
            LocalDate date = Instant.ofEpochSecond(time).atZone(zone).toLocalDate();
            JSONObject temperatures = new JSONObject()
                    .put("min", at(daily, "temperature_2m_min", i))
                    .put("max", at(daily, "temperature_2m_max", i));
            String[] names = {"morn", "day", "eve", "night"};
            int[] clockHours = {6, 12, 18, 23};
            boolean complete = true;
            for (int k = 0; k < names.length; k++) {
                Double value = localTemperature(hourly, date, clockHours[k], zone);
                if (value == null) { complete = false; break; }
                temperatures.put(names[k], value.doubleValue());
            }
            if (!complete) continue;
            JSONObject day = new JSONObject().put("dt", time).put("temp", temperatures)
                    .put("pop", probability(at(daily, "precipitation_probability_max", i)))
                    .put("weather", condition(daily.getJSONArray("weather_code").getInt(i), true, language));
            // Polar day/night can have no sunrise or sunset; Niagara's serializer permits omission.
            for (String event : new String[]{"sunrise", "sunset"}) {
                if (!daily.getJSONArray(event).isNull(i)) day.put(event, daily.getJSONArray(event).getLong(i));
            }
            days.put(day);
        }
        if (days.length() == 0) throw new JSONException("No usable daily forecast");
        JSONObject forecast = new JSONObject().put("current", now).put("hourly", hours)
                .put("daily", days).put("minutely", new JSONArray());
        // Open-Meteo does not offer true one-minute nowcasting. Do not fabricate rain-start alerts.
        return new JSONObject().put("timestamp", fetchedAt).put("provider", "Open-Meteo")
                .put("lat", Double.toString(latitude)).put("long", Double.toString(longitude))
                .put("lang", language == null ? "en" : language).put("forecast", forecast).toString();
    }

    private static Double localTemperature(JSONObject hourly, LocalDate date, int hour, ZoneId zone)
            throws JSONException {
        JSONArray times = hourly.getJSONArray("time");
        for (int i = 0; i < times.length(); i++) {
            ZonedDateTime local = Instant.ofEpochSecond(times.getLong(i)).atZone(zone);
            if (local.toLocalDate().equals(date) && local.getHour() == hour
                    && !hourly.getJSONArray("temperature_2m").isNull(i)) return at(hourly, "temperature_2m", i);
        }
        return null;
    }

    private static void validateColumns(JSONObject object, int length, String... keys) throws JSONException {
        if (length == 0) throw new JSONException("Empty forecast");
        for (String key : keys) if (object.getJSONArray(key).length() != length)
            throw new JSONException("Mismatched forecast array: " + key);
    }
    private static boolean missing(JSONObject object, int index, String... keys) throws JSONException {
        for (String key : keys) if (object.getJSONArray(key).isNull(index)) return true;
        return false;
    }
    private static double number(JSONObject object, String key) throws JSONException {
        double value = object.getDouble(key);
        if (!Double.isFinite(value)) throw new JSONException("Non-finite weather value");
        return value;
    }
    private static double at(JSONObject object, String key, int index) throws JSONException {
        double value = object.getJSONArray(key).getDouble(index);
        if (!Double.isFinite(value)) throw new JSONException("Non-finite forecast value");
        return value;
    }
    private static double probability(double percent) throws JSONException {
        if (percent < 0 || percent > 100) throw new JSONException("Invalid precipitation probability");
        return percent / 100.0;
    }

    static JSONArray condition(int wmo, boolean day, String language) throws JSONException {
        int id;
        String icon, en, pt;
        switch (wmo) {
            case 0: id=800; icon="01"; en="Clear sky"; pt="Céu limpo"; break;
            case 1: id=801; icon="02"; en="Mainly clear"; pt="Poucas nuvens"; break;
            case 2: id=802; icon="03"; en="Partly cloudy"; pt="Parcialmente nublado"; break;
            case 3: id=804; icon="04"; en="Overcast"; pt="Nublado"; break;
            case 45: case 48: id=741; icon="50"; en="Fog"; pt="Nevoeiro"; break;
            case 51: case 53: case 55: id=wmo==51?300:wmo==53?301:302; icon="09"; en="Drizzle"; pt="Garoa"; break;
            case 56: case 57: id=511; icon="13"; en="Freezing drizzle"; pt="Garoa congelante"; break;
            case 61: case 63: case 65: id=wmo==61?500:wmo==63?501:502; icon="10"; en="Rain"; pt="Chuva"; break;
            case 66: case 67: id=511; icon="13"; en="Freezing rain"; pt="Chuva congelante"; break;
            case 71: case 73: case 75: id=wmo==71?600:wmo==73?601:602; icon="13"; en="Snow"; pt="Neve"; break;
            case 77: id=611; icon="13"; en="Snow grains"; pt="Grãos de neve"; break;
            case 80: case 81: case 82: id=wmo==80?520:wmo==81?521:522; icon="09"; en="Rain showers"; pt="Pancadas de chuva"; break;
            case 85: case 86: id=wmo==85?621:622; icon="13"; en="Snow showers"; pt="Pancadas de neve"; break;
            case 95: case 96: case 99: id=wmo==95?211:212; icon="11"; en="Thunderstorm"; pt="Trovoadas"; break;
            default: return new JSONArray();
        }
        String description = language != null && language.startsWith("pt") ? pt : en;
        return new JSONArray().put(new JSONObject().put("id", id).put("description", description)
                .put("icon", icon + (day ? "d" : "n")));
    }
}
