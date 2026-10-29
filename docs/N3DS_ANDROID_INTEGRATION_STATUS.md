# Nintendo 3DS Android integration — parked

N3DS work is intentionally deferred until the Nintendo DS (NDS) integration is
built, tested, and verified on a physical Android device.

The earlier exploratory N3DS metadata and UI branches were removed from the
active integration path so NDS-specific Kotlin `when` expressions remain
exhaustive and the current CI work can focus on NDS.

No N3DS core is packaged, no N3DS JNI adapter is implemented, and N3DS gameplay
has not been tested. Do not treat the 3DS app integration as started or playable.

After NDS passes CI and device validation, the N3DS work should begin in a
separate scoped change with a selected 3DS core, its licensing/build plan, and
the correct 400x240 top / 320x240 bottom display path.
