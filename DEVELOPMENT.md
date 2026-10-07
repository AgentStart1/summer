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
LLMD adapter or `KoogImageRecognizer`. The existing LLMD target flow still determines
which installed package receives IPC and authorization requests. Both paths share
`RecognitionImageEncoder`, which bounds image decoding, resizes to 1600 pixels, and limits JPEG
payloads to 500 KB. Koog uses an explicit Ktor OkHttp transport on Android; clients and
transport are closed after each request, and cancellation propagates.

`RecognitionSettingsHost` owns settings state and actions independently of Compose.
It coordinates on the injected serial dispatcher and performs persistence on IO.
The balance-entry Host also coordinates state on that dispatcher while recognition runs on IO.
API keys are AES-GCM encrypted with an Android Keystore key. Preferences live in
`noBackupFilesDir`, and API request/response bodies and credentials are never application logs.

## Transaction import and timeline paging

`ImportTransactionsHost` owns recognition, LLMD authorization retry, editable preview,
selection and batch persistence on the serial coordination dispatcher. Image recognition
and database work run on IO; parsing and preview preparation run on worker dispatchers.
Both LLMD (strict JSON schema) and Koog support transaction arrays with local ISO dates,
signed CNY amounts, notes and nullable original transaction IDs. The parser accepts a bare
JSON object or one enclosing JSON code fence from a model; surrounding prose and malformed
payloads are rejected. Missing/relative dates stay null in recognition and must be completed
in the preview before saving. Missing IDs stay null;
they are never synthesized from dates or amounts. Both balance and transaction recognition save the exact compressed JPEG through
`FileRecognitionImageStore` under `filesDir/recognition-images`. Content-addressed filenames
reuse identical images; atomic temporary-file renames prevent partial image files. Entity
`imagePath` fields store paths relative to `filesDir` (resolve with `File(context.filesDir, path)`),
so they survive app-data relocation and do not depend on picker URI grants or cache lifetime.
Manual balance entries and existing records have null image paths; a successful recognition
keeps its image even when the preview is later cancelled. LLMD's temporary shared image is
still revoked and deleted after IPC completes; the retained image stays private.

Room version 2 migrates version 1 without deleting balances or generating transactions.
Room generates the SQL through `@Database(autoMigrations = [AutoMigration(from = 1, to = 2)])`;
the builder registers that migration automatically. Schema export is enabled and KSP writes
versioned JSON to `app/schemas/com.storytellerf.summer.data.db.SummerDatabase/`.
Commit generated schemas with entity changes. Version 1 was exported from the unchanged
baseline database/entities in an isolated checkout; preserve that historical schema.
Version 2 is generated from this PR's current entities. Rebuild with `:app:kspDebugKotlin`
after schema changes; edit entities and annotations rather than the generated JSON or SQL.
All schema changes within this PR share version 2. If a development install used an earlier
schema from the same PR, clear app data before testing the latest APK rather than adding
another database version. This resets local data and recognition settings:

```sh
adb -s emulator-5554 shell pm clear com.storytellerf.summer
```

`BalanceImpactRecord` is an independent entity with a fund-source foreign key and unique
indices for `(fundSourceId, transactionId)` and `(fundSourceId, imageDedupKey)`.
Only rows without a complete original ID receive an image deduplication key built from
`imageHash:imageRow`; identified transactions are deduplicated by ID even if recognition
changes their position in the list. Masked or truncated IDs stay null.
A SHA-256 digest of the encoded screenshot supports repeat-image detection. Batch inserts
use a transaction and ignore duplicates; deleting an account cascades to its transactions.

Each `BalanceChange.coveredOrderAmount` persists the signed order total for that account in
`(preceding snapshot timestamp, current snapshot timestamp]`. Snapshot ordering breaks equal
timestamps by ID: later same-time snapshots have empty intervals. The first snapshot has no
interval and zero coverage; orders outside snapshot intervals remain visible independently.
Migration 1→2 initializes coverage to zero because version 1 has no imported orders.
Imports and snapshot insert/update/delete recompute
the affected accounts in the same Room transaction, including the old account when a snapshot
moves. Callers must write through `DataRepository` to preserve this invariant.
The order `(fundSourceId, timestamp)` index bounds interval aggregation. Coverage is a net
amount, not a count or absolute sum, and is not capped at the observed balance change.
The timeline subtracts persisted coverage from the change in adjacent actual account balances
(falling back to `previousBalance` for an initial legacy record). Differences round to CNY
cents and disappear at zero; an excess of expenses can produce a positive difference.

`FeedPagingSource` loads 20 balance changes ordered by `(timestamp DESC, id DESC)` per page.
The repository reads that window, one preceding balance per account via an indexed latest-row query, the fund sources and
transactions within a single Room transaction. Transaction intervals are lower-inclusive
and upper-exclusive; the first and last windows also include transactions beyond the newest
and oldest snapshots. With no balance changes, the source pages transaction rows directly.
One interval can contain any number of transaction rows, so Paging keys count balance
changes rather than flattened rows (or transaction offsets when there are no snapshots). Pages flatten into `TimelineItem.Snapshot` and
`TimelineItem.Transaction`, plus `TimelineItem.Difference` for uncovered amounts, with distinct
stable keys. Coverage uses account snapshot intervals independently of paging's global time
windows, so it remains correct when orders and snapshots appear on different pages.
The Host keeps one Pager for its lifetime and invalidates its active PagingSource when any
timeline table changes. Source creation and invalidation share the serial coordination
dispatcher, and the observer is cancelled with the Host. Refresh uses the previous paging
state's anchor rather than starting over at offset zero. PagingData is cached for the
ViewModel lifetime.

Unit tests cover provider requests, parsing, preview/selection, authorization, cancellation
and paging. `TransactionDatabaseTest` covers Room migration, cross-image ID deduplication,
account deletion, interval boundaries and historical account balance seeds on a device.

## Public transaction screenshot regression

Keep downloaded screenshots and manually checked expected JSON outside the repository.
`TransactionScreenshotImportTest` is skipped by the standard suite unless the runner supplies
an image and expected rows. For example, the [Alipay official help page](https://cschannel.alipay.com/mobile/helpDetail.htm?help_id=201602058759)
contains a [transaction detail screenshot](https://tfsimg.alipay.com/images/cspropmng/TB1Qx0_X7RDDuNkUvNm760SypXa)
showing `-20.70`, `2018-11-05 10:59` and a masked ID. Its expected JSON is:

```json
{"transactions":[{"timestamp":"2018-11-05T10:59:00","amount":-20.70,"note":"饿了么","transactionId":null}]}
```

The [Alipay list screenshot](https://tfsimg.alipay.com/images/cspropmng/TB1lwuCXJC2aKRkUvMH761PkFXa)
uses relative dates without an absolute reference, and the
[Sylq demo transaction list](https://wiki.sylq.io/img/merchant/ListTransactions.png) uses euros.
The Alipay list should retain five signed transactions with `timestamp:null`; the test
verifies that persistence is blocked until the preview dates are filled. The synthetic date
entered by the test exercises manual correction and is not a claimed date from the screenshot.
The Sylq euro list uses `{"transactions":[]}` to expect rejection under the CNY contract.
Masked identifiers are never usable deduplication IDs.

With a booted emulator, run:

```sh
python3 scripts/test-transactions.py --serial emulator-5554 \
  --image /tmp/alipay-detail.png --expected-json /tmp/alipay-detail.expected.json
```

The runner builds APKs, installs them when needed, transfers the external
fixture through stdin into app-private no-backup files, runs the screenshot test and removes fixtures.
Use `--skip-build` when the APKs have already been built.
The default mock API response is the manually checked expected JSON: this verifies the
production encoder, Koog image request, editable preview, confirmation, duplicate handling,
Room persistence and the retained image bytes. It does **not** validate model recognition accuracy.

Add `--live` with `OPENROUTER_API_KEY` configured in the test process environment to test
actual recognition against the same expectations. `--key-env` selects another secret variable
name; `--model` selects an eligible free vision model, verified against the current catalog.
Keys go through stdin into a temporary app-private no-backup file and are removed in `finally`.

Production coroutine core/Android and coroutine test artifacts share the catalog version.
Keep them aligned: instrumentation executes against the app APK's coroutine runtime,
so a newer test artifact alone can cause missing-method failures before tests start.

Screen effect handlers dispatch navigation, authorization launch and notifications to the
Android main dispatcher; Host coordination continues on the injected serial worker dispatcher.
