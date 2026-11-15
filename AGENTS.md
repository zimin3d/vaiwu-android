# Development rules

## Required verification

- Add a regression test for every fixed defect when the relevant layer can be tested. Keep existing tests and add coverage for new behavior.
- After every task, run all applicable tests and checks. A task is complete only when 100% of them pass.
- Never report completion while a relevant test is failing, was not run, or its CI result is still pending. Do not delete or weaken a test just to make the build pass.
- Run the native host regression tests:
  ```sh
  python3 app/src/test/cpp/run_host_tests.py
  ```
- Run the Android checks:
  ```sh
  ./gradlew --no-daemon lintDebug testDebugUnitTest assembleRelease
  ```
- This machine has no local Java/Android SDK. Use the `Android APK` GitHub Actions workflow and wait for its final result. The workflow must run both native host tests and Android unit tests successfully.
- A static review, compilation alone, or a partially passing test suite is not sufficient completion evidence.
