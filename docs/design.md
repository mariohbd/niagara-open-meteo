# Architecture

The existing weather repository resolves the configured coordinates and language and dispatches a coroutine on Dispatchers.IO. R8 merges this coroutine with unrelated cases; only discriminator 13 is the weather request.

The patch adds an entry guard to that case and calls NiagaraWeatherBridge. Other cases branch to the unchanged original entry. The bridge checks that it is not on Android's main thread, preserves incoming Kotlin Result failure semantics, obtains the forecast, invokes the existing JSON parser, and saves the existing weather cache. Failures become the exception type already handled by the repository's refresh logic.

The extension uses HTTPS with bounded timeouts, rejects redirects, and bounds response size. It sends only the configured weather coordinates to Open-Meteo. No Niagara account/session token is forwarded. The patch does not change other launcher feature entitlement checks.

OpenMeteoProvider converts Unix-second timestamps, Celsius temperatures, precipitation probabilities and WMO weather codes to the app's weather JSON format. Daily morning/day/evening/night temperatures come from the corresponding local hourly entries, using the response timezone rather than a fixed UTC offset. Missing sunrise/sunset values are omitted for polar conditions. Missing hourly values are skipped. Invalid current data or an unusable forecast fails instead of inventing weather values.

No synthetic one-minute forecast is created. The minute-level array is empty. Portuguese and English descriptions are currently implemented; other languages fall back to English.

Obfuscated class/member names and the attribution resource identifier are specific to Niagara 1.16.28 build 1634. The patch checks version/build, required fields/methods, and request/attribution fingerprints before modifying the target. Porting to another APK requires fresh analysis and tests; changing the compatibility list alone is insufficient.

The offline verifier checks the APK signature, injected argument registers, branch target and semantic preservation of other instructions and resources. It normalizes DEX payload-alignment NOPs and pool-index differences. It does not execute Android bytecode and is not an ART verifier. On-device testing remains required.
