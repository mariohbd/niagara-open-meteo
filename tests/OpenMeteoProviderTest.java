package app.d0nj.extension.weather;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import org.json.JSONArray;
import org.json.JSONObject;

/** Plain JVM contract tests; fixture is a real response for public test coordinates 0,0. */
public final class OpenMeteoProviderTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static JSONObject convert(JSONObject fixture) throws Exception {
        return new JSONObject(OpenMeteoProvider.convert(fixture.toString(), 0, 0, "pt_br",
                fixture.getJSONObject("current").getLong("time")));
    }
    private static void rejects(JSONObject fixture, String reason) throws Exception {
        boolean rejected = false;
        try { convert(fixture); } catch (Exception expected) { rejected = true; }
        check(rejected, reason);
    }
    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
        JSONObject fixture = new JSONObject(raw);
        JSONObject response = convert(fixture);
        JSONObject forecast = response.getJSONObject("forecast");
        check(response.getString("provider").equals("Open-Meteo"), "Provider attribution");
        check(response.get("lat") instanceof String && response.get("long") instanceof String, "Coordinate string contract");
        check(forecast.getJSONArray("minutely").length() == 0, "Do not invent minute-level rain data");
        check(forecast.getJSONObject("current").getDouble("temp") == fixture.getJSONObject("current").getDouble("temperature_2m"), "Celsius unchanged");
        check(forecast.getJSONObject("current").getLong("dt") == fixture.getJSONObject("current").getLong("time"), "Unix seconds unchanged");
        check(forecast.getJSONArray("daily").length() == 7, "Seven days");
        check(forecast.getJSONArray("hourly").length() > 100, "Multi-day hourly forecast");
        JSONArray days = forecast.getJSONArray("daily");
        for (int i=0; i<days.length(); i++) {
            JSONObject day = days.getJSONObject(i);
            JSONObject temp = day.getJSONObject("temp");
            for (String part : new String[]{"morn","day","eve","night","min","max"})
                check(Double.isFinite(temp.getDouble(part)), "Required daily temperature " + part);
            check(day.getLong("sunrise") < day.getLong("sunset"), "Sunrise before sunset");
            check(day.getDouble("pop") >= 0 && day.getDouble("pop") <= 1, "Probability is fraction");
        }
        int[] codes = {0,1,2,3,45,48,51,53,55,56,57,61,63,65,66,67,71,73,75,77,80,81,82,85,86,95,96,99};
        for (int code : codes) {
            JSONObject day = OpenMeteoProvider.condition(code, true, "pt").getJSONObject(0);
            JSONObject night = OpenMeteoProvider.condition(code, false, "en").getJSONObject(0);
            check(day.getString("icon").endsWith("d") && night.getString("icon").endsWith("n"), "Day/night " + code);
            check(day.getInt("id") == night.getInt("id"), "Stable condition " + code);
        }
        check(OpenMeteoProvider.condition(999, true, "en").length()==0, "Unknown code is not sunny");
        check(OpenMeteoProvider.condition(0,true,"pt_br").getJSONObject(0).getString("description").equals("Céu limpo"), "Portuguese");
        JSONObject changed = new JSONObject(raw);
        changed.getJSONObject("hourly").getJSONArray("precipitation_probability").put(100, 75);
        JSONArray hours = convert(changed).getJSONObject("forecast").getJSONArray("hourly");
        long targetTime = changed.getJSONObject("hourly").getJSONArray("time").getLong(100);
        boolean found = false;
        for (int i=0;i<hours.length();i++) if(hours.getJSONObject(i).getLong("dt")==targetTime) {
            check(hours.getJSONObject(i).getDouble("pop")==0.75, "75 percent -> 0.75"); found=true;
        }
        check(found,"Probability test hour retained");
        changed = new JSONObject(raw);
        changed.getJSONObject("current").put("temperature_2m", JSONObject.NULL);
        rejects(changed,"Null current temperature must fail");
        changed = new JSONObject(raw);
        changed.getJSONObject("current_units").put("temperature_2m", "°F");
        rejects(changed,"Reject wrong units");
        changed = new JSONObject(raw);
        changed.getJSONObject("hourly").put("weather_code",new JSONArray());
        rejects(changed,"Reject mismatched arrays");
        changed = new JSONObject(raw);
        changed.getJSONObject("hourly").getJSONArray("time").put(1, changed.getJSONObject("hourly").getJSONArray("time").getLong(0));
        rejects(changed,"Reject duplicate hours");
        changed = new JSONObject(raw);
        changed.getJSONObject("hourly").getJSONArray("temperature_2m").put(100,JSONObject.NULL);
        check(convert(changed).getJSONObject("forecast").getJSONArray("hourly").length() == forecast.getJSONArray("hourly").length()-1,"Skip missing hourly reading");
        changed = new JSONObject(raw);
        changed.getJSONObject("daily").getJSONArray("sunrise").put(0,JSONObject.NULL);
        check(!convert(changed).getJSONObject("forecast").getJSONArray("daily").getJSONObject(0).has("sunrise"), "Polar sunrise omitted");
        changed = new JSONObject(raw);
        changed.getJSONObject("hourly").getJSONArray("precipitation_probability").put(100,101);
        rejects(changed,"Reject out-of-range probability");
        boolean rejected=false;
        try { OpenMeteoProvider.convert(raw,0,0,"en",fixture.getJSONObject("current").getLong("time")+86400); }
        catch (Exception expected) { rejected=true; }
        check(rejected,"Reject stale current data");
        for (double[] coords : new double[][]{{91,0},{0,181},{Double.NaN,0},{0,Double.POSITIVE_INFINITY}}) {
            rejected=false;
            try { OpenMeteoProvider.url(coords[0],coords[1]); } catch(IllegalArgumentException expected) {rejected=true;}
            check(rejected,"Reject invalid coordinates");
        }
        // Build a DST boundary fixture: local 06/12/18/23 must select the actual local hour,
        // not offsets from UTC midnight or a fixed timezone offset.
        changed = new JSONObject(raw);
        ZoneId zone = ZoneId.of("America/New_York");
        LocalDate date = LocalDate.of(2026,11,1);
        long midnight = date.atStartOfDay(zone).toEpochSecond();
        changed.put("timezone",zone.getId());
        changed.getJSONObject("current").put("time",midnight);
        JSONObject hourly = changed.getJSONObject("hourly");
        JSONArray times = new JSONArray(), temps = new JSONArray(), zeros = new JSONArray(), ones = new JSONArray();
        for (int i=0;i<25;i++) {
            long time=midnight+i*3600;
            times.put(time); temps.put(Instant.ofEpochSecond(time).atZone(zone).getHour()); zeros.put(0); ones.put(1);
        }
        hourly.put("time",times).put("temperature_2m",temps).put("apparent_temperature",temps)
                .put("weather_code",zeros).put("precipitation_probability",zeros).put("is_day",ones);
        JSONObject daily=new JSONObject().put("time",new JSONArray().put(midnight));
        for(String key:new String[]{"temperature_2m_min","temperature_2m_max","precipitation_probability_max","weather_code","sunrise","sunset"})
            daily.put(key,new JSONArray().put(0));
        changed.put("daily",daily);
        JSONObject dstTemp=convert(changed).getJSONObject("forecast").getJSONArray("daily").getJSONObject(0).getJSONObject("temp");
        check(dstTemp.getInt("morn")==6 && dstTemp.getInt("day")==12 && dstTemp.getInt("eve")==18 && dstTemp.getInt("night")==23,"DST local-hour mapping");
        System.out.println("PASS: " + checks + " weather contract checks");
    }
}
