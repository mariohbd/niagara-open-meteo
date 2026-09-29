# Architecture

The existing weather repository resolves the configured coordinates and language and dispatches a coroutine on Dispatchers.IO. R8 merges this coroutine with unrelated cases; only discriminator 13 is the weather request.

The patch adds an entry guard to that case and calls NiagaraWeatherBridge. Other cases branch to the unchanged original entry. The bridge checks that it is not on Android's main thread, preserves incoming Kotlin Result failure semantics, obtains the forecast, invokes the existing JSON parser, and saves the existing weather cache. Failures become the exception type already handled by the repository's refresh logic.

The extension uses HTTPS with bounded timeouts, rejects redirects, and bounds response size. It sends only the configured weather coordinates to Open-Meteo. No Niagara account/session token is forwarded. The patch does not change other launcher feature entitlement checks.

OpenMeteoProvider converts Unix-second timestamps, Celsius temperatures, precipitation probabilities and WMO weather codes to the app's weather JSON format. Daily morning/day/evening/night temperatures come from the corresponding local hourly entries, using the response timezone rather than a fixed UTC offset. Missing sunrise/sunset values are omitted for polar conditions. Missing hourly values are skipped. Invalid current data or an unusable forecast fails instead of inventing weather values.

No synthetic one-minute forecast is created. The minute-level array is empty. Portuguese and English descriptions are currently implemented; other languages fall back to English.

Obfuscated class/member names and the attribution resource identifier are mapped separately for Niagara 1.16.28 build 1634 and 1.16.29 build 1635. The runtime bridge selects the mapping by the validated coroutine class name. The patch checks version/build, required fields/methods, and request/attribution fingerprints before modifying the target. Porting to another APK requires fresh analysis and tests; changing the compatibility list alone is insufficient.

The offline verifier checks the APK signature, injected argument registers, branch target and semantic preservation of other instructions and resources. It normalizes DEX payload-alignment NOPs and pool-index differences. It does not execute Android bytecode and is not an ART verifier. On-device testing remains required.


## Version 0.2.0 — Niagara 1.16.29 (1635)

The supplied APK already contains d0nj v1.3.1 patches. Input SHA-256:
`eb9f7d214a344e1c05c61a445d1f441cd4ad5266c6121e1bd2dda68b34778898`.

The repository `b.xC7VAkwDePsNJ396LU` dispatches `b.Hu7yEGV1H1r1bdCCcwuur8scnv1v` on `b.xKrvvGRUXjn94RMdBt.FR8O6sSH4MN5` (IO). Weather remains discriminator 13.

- Entry: `A1Md0KwGu5VUxb7FHaW(Object)`; discriminator: `SB71Xg0Ef2yAewcpeisEWC`; state: `ojn5c2YOWTFy0045br2ifslIhtlq`.
- Captures: repository `ECePjANygrkOgQnMq53`, coordinates `Qph1ZWcbx3tkLvD`, language `TfTzbajyaQF1`.
- Coordinates: `b.yOT9KFkE4RgdBmVfo9k4ZYgHH`, latitude `Kj2k1AqaZsaVCT`, longitude `SJiFQ7HDN1InfkB1Erx8z20l`.
- Kotlin Result check: `b.Qx3wzSHU2H1QDj.SJiFQ7HDN1InfkB1Erx8z20l(Object)`.
- Parser: `gE0rVOWsj4loTBBoy2xSrtVpMP1H(String,long)`, result `b.pmLuZa5P6AoCZIpAX`; cache: `ZFqutaGTYq(String)`.
- Attribution: `b.V90i4gEMcGLCcnbrkYt.Kj2k1AqaZsaVCT(Object)`, unchanged resource ID `0x7f12056f`.

Validation: 136 provider checks, signature verification and semantic DEX comparison for both versions, duplicate patch rejection on 1.16.29. No changes to OpenMeteoProvider. 55,914 unrelated methods and 1,817 non-DEX entries retained in 1.16.29; 55,894 and 1,817 in the 1.16.28 regression test. User confirmed weather works on 1.16.28. Version 1.16.29 is not yet device-tested.
