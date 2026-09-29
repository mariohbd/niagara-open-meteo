# Notices

This independent patch was developed against the structure and public patch API used by [d0nj/morphe-patches](https://github.com/d0nj/morphe-patches), snapshot 729c012. That project is licensed under GPLv3. This repository retains GPLv3; see LICENSE. It contains the Open-Meteo patch and its adapter, not the other d0nj patches.

This is an unofficial project. It is not affiliated with or endorsed by Niagara Launcher, Morphe, Open-Meteo, or d0nj. No Niagara APK, decompiled application sources, signing keys, or account credentials are distributed here.

The weather fixture in `tests/fixtures/open-meteo.json` was obtained on 2026-09-29 from [Open-Meteo](https://open-meteo.com/) for public test coordinates latitude 0, longitude 0. It contains no user's location. Weather data is attributed to Open-Meteo under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Fixture tests use the response timestamp rather than today's clock and do not call the network.

Downloaded build tools retain their respective licenses and are not checked into this repository. `toolchain.lock.json` records their source URLs and SHA-256 checksums.
