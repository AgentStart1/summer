# Development

Use JDK 17, Android SDK platform 36, and an Android emulator. The Gradle wrapper downloads
the required Gradle version.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

```sh
./gradlew :app:connectedDebugAndroidTest
```

The standard instrumented suite exercises finance, recognition settings, and the Koog
image-to-form-to-database path with a mock HTTP transport. The live
OpenRouter test skips unless explicitly provisioned by the runner below.

## OpenRouter vision test

Set `OPENROUTER_API_KEY` in the test process environment using your environment's secret
configuration. Do not put it in Gradle properties, source files, instrumentation arguments,
shell command history, or the APK. With an emulator booted:

```sh
python3 scripts/test-openrouter.py --serial emulator-5554
```

Use `--key-env VARIABLE_NAME` if the secret has a different environment variable name.
The runner checks OpenRouter's current model catalog and selects a `:free` model with image
input and zero prompt/completion prices. `--model MODEL_ID` selects another eligible model;
there is no automatic switch to a paid model. Free services may still require account
permissions and have rate or capacity limits.

To test a real application screenshot, download a public developer demo image outside the
repository, inspect its balance manually, and supply both the image and expected amount:

```sh
python3 scripts/test-openrouter.py --serial emulator-5554 \
  --image /tmp/screenshot.jpg --expected-balance -41.54
```

For example, the [Cashew developer website](https://cashewapp.web.app/) publishes a
[home screenshot](https://cashewapp.web.app/assets/screenshots/home-promotion-light.jpg)
whose selected USD Wallet has a balance of `-41.54`. Keep screenshots and personal financial
information out of committed fixtures. The original file is transferred to the device and
processed by the production image encoder; the test verifies the balance against the supplied
expectation and removes the temporary screenshot afterward.

Verified on Android 35 with Koog 1.3.0 and `qwen/qwen3.8-27b:free`:

| Public screenshot | Expected balance | Recognition, form, and database |
| --- | ---: | --- |
| Cashew home screenshot linked above | -41.54 | Passed |
| [Monzo product screenshot](https://www.monzo.com/static/images/blog/2016-08-04-updated-design/design.png) from its [product update](https://monzo.com/blog/2016/08/08/updated-design) | 1031.25 | Passed |

The live service occasionally returned upstream HTTP 429 errors during verification. These
are reported without exposing response bodies or credentials; retry the opt-in test when
capacity is available. The standard suite has seven offline emulator regression tests;
the unprovisioned live test is skipped and run separately by the script.

The runner builds and installs debug APKs, transfers the key through stdin into an app-private temporary file, and invokes the live instrumented test.
The test creates a synthetic negative-balance image, calls the same ViewModel action used by
the image picker through the production encoder/Koog client, checks the rendered amount, and
saves it through the form to Room. System picker navigation is outside this test. The test
restores the previous provider configuration and removes fixtures; the runner also removes
the temporary key file and screenshot in `finally`, including on failure.

## Recognition architecture

`ConfiguredImageAnalyzer` snapshots recognition settings per request and dispatches to the
LLMD adapter or `KoogBalanceRecognizer`. The existing LLMD target flow still determines
which installed package receives IPC and authorization requests. Both paths share
`BalanceImageEncoder`, which bounds image decoding, resizes to 1600 pixels, and limits JPEG
payloads to 500 KB. Koog uses an explicit Ktor OkHttp transport on Android; clients and
transport are closed after each request, and cancellation propagates.

`RecognitionSettingsHost` owns settings state and actions independently of Compose.
It coordinates on the injected serial dispatcher and performs persistence on IO.
The balance-entry Host also coordinates state on that dispatcher while recognition runs on IO.
API keys are AES-GCM encrypted with an Android Keystore key. Preferences live in
`noBackupFilesDir`, and API request/response bodies and credentials are never application logs.
