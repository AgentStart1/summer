# Summer

An Android app for recording fund-source balance snapshots. Image import can use an installed LLMD app or an image-capable API model through Koog.

The home timeline reconstructs every saved asset snapshot: each card shows the total at that
moment and the balance of every tracked fund source. The record between cards shows the note and
amount that changed the next snapshot, such as a purchase or income.

Open **Settings → Image recognition** to choose **LLMD**, **OpenAI**, **Anthropic (Claude)**,
**OpenRouter**, or an **OpenAI-compatible** API provider. LLMD is selected by default.

For an API provider, enter its HTTPS **API base URL** (including the version path, such as
`https://openrouter.ai/api/v1`), an image-capable **model ID**, and your **API key**, then tap
**Save recognition settings**. OpenAI connections can use Responses API or Chat Completions.
This version supports API keys; account/subscription login is not included.

Each provider keeps its own saved connection. API keys are encrypted on the device and excluded
from backups. **Remove saved connection** erases a provider's connection and returns to LLMD if
that provider was active. Imported images are uploaded to the selected remote provider when
an API connection is active. Review the recognized amount before saving the balance.

OpenRouter's `qwen/qwen3.8-27b:free` is a starting model for testing. Availability, image
support, free quotas, and account permissions can change; check the provider's current catalog.

For LLMD, choose the installed package in **LLMD build**:

- Release: `com.storytellerf.llmd`
- Alpha: `com.storytellerf.llmd.alpha`
- Debug: `com.storytellerf.llmd.debug`

The choice is saved on device. Debug builds default to LLMD Debug; non-debug builds default to
LLMD Release until a different package is selected.

See [DEVELOPMENT.md](DEVELOPMENT.md) for build instructions and the optional OpenRouter vision test.
