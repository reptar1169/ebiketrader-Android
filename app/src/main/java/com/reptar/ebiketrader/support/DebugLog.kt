package com.reptar.ebiketrader.support

import android.util.Log
import com.reptar.ebiketrader.BuildConfig

/**
 * Progress chatter that is useful while developing and has no business in a
 * release build. Logcat is readable by any app holding READ_LOGS and by
 * anyone with the phone and adb, so anything routed through here should be
 * assumed public on a debug build and absent on a release one.
 *
 * Errors deliberately keep calling Log.e directly: a tester who hits a real
 * failure should leave a trace behind in the release build too.
 */
fun logDebug(tag: String, message: String) {
    if (BuildConfig.DEBUG) Log.i(tag, message)
}
