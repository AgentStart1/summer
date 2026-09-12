# Summer

An Android app for recording fund-source balance snapshots. Image import can ask an installed
LLMD app to recognize the balance.

The home timeline reconstructs every saved asset snapshot: each card shows the total at that
moment and the balance of every tracked fund source. The record between cards shows the note and
amount that changed the next snapshot, such as a purchase or income.

Open **Settings** from the home screen to choose the LLMD package used for recognition:

- Release: `com.storytellerf.llmd`
- Alpha: `com.storytellerf.llmd.alpha`
- Debug: `com.storytellerf.llmd.debug`

The choice is saved on device. Debug builds default to LLMD Debug; non-debug builds default to
LLMD Release until a different package is selected.
