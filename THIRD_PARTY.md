# Third-party components

Pinned inputs are bundled in deps/ with SHA256SUMS for bundled library inputs. Their source URLs identify provenance; no dependency is downloaded at runtime.

- libadb-android 3.1.1 — https://github.com/MuntashirAkon/libadb-android/tree/3.1.1 — Apache-2.0 option, with per-file notices retained in vendor/libadb/src and licenses in vendor/libadb/LICENSES. Local changes: bounded TCP/TLS pairing reads, bounded TCP connect, null-safe pairing cleanup and transport closure after handshake failure. Application shell watchdog closes captured transport directly to avoid manager-lock deadlock.
- spake2-android 2.2.1 — https://github.com/MuntashirAkon/spake2-java/tree/2.2.1 — LGPL-3.0. Source archive included in deps/spake2-source-2.2.1.tar.gz; license in licenses/spake2-LICENSE. Native libraries remain separate .so entries so they can be rebuilt/replaced and APK re-signed.
- Conscrypt Android 2.5.3 — https://github.com/google/conscrypt/tree/2.5.3 — Apache-2.0 and bundled native notices, in licenses/conscrypt-LICENSE and licenses/conscrypt-NOTICE.
- Bouncy Castle bcprov/bcpkix/bcutil jdk15to18 1.81 — https://github.com/bcgit/bc-java/tree/r1rv81 — license in licenses/bouncycastle-LICENSE.html. Used to create local ADB identity certificate.
- AndroidX annotation 1.3.0 — Apache-2.0. Annotation-only library, license text in vendor/libadb/LICENSES/Apache-2.0.

The app uses RSA identity and PAKE/TLS pairing from maintained libraries rather than its own cryptographic protocol. Identity is saved in app-private storage; Android backup disabled. Connections are constrained to loopback on this phone. Code entry is never written to app settings or logs. Wireless debugging is toggled only after optional automatic-startup consent and protected-settings permission.

Additional local libadb correction: AdbStream/AdbInputStream drain queued and buffered command output after remote close, then return EOF rather than waiting indefinitely. Regression covered by tests/AdbStreamTest.java and test-adb-stream.sh (test uses initialized stream fixture without Android sockets). Raw ADB shell startup detaches helper using setsid/nohup and waits for process existence before closing session.

Material Components Android 1.14.0 and AndroidX dependencies resolve through Google Maven using Gradle. Apache-2.0; see https://github.com/material-components/material-components-android and https://developer.android.com/jetpack/androidx. Build tooling and these dependencies require network on first build.
