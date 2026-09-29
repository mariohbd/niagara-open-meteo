import app.d0nj.extension.weather.OpenMeteoProvider;
import org.json.JSONObject;
public final class LiveWeatherSmoke {
    public static void main(String[] args) throws Exception {
        JSONObject result = new JSONObject(OpenMeteoProvider.fetch(0, 0, "pt_br"));
        JSONObject forecast = result.getJSONObject("forecast");
        if (forecast.getJSONArray("daily").length() < 1) throw new AssertionError("Empty daily forecast");
        System.out.println("PASS: Java HTTPS request to Open-Meteo at test coordinates 0,0; "
                + forecast.getJSONArray("hourly").length() + " hourly entries; "
                + forecast.getJSONArray("daily").length() + " daily entries.");
    }
}
