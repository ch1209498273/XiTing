package com.lujinyu.xiting

import android.content.Intent

internal object ContentReturnProbeChecks {
    fun run() {
        check(ContentReturnProbeActivity.parseShare(Intent.ACTION_SEND, "text/plain", "sample", "public https://example.com/video/1").getJSONArray("urls").length() == 1)
        check(ContentReturnProbeActivity.parseShare(Intent.ACTION_SEND, "text/plain", null, "javascript:alert(1)").getJSONArray("urls").length() == 0)
        check(!ContentReturnProbeActivity.parseShare(Intent.ACTION_SEND, "text/plain", null, "x".repeat(32769)).getBoolean("accepted"))
        check(!ContentReturnProbeActivity.parseShare(Intent.ACTION_VIEW, "text/plain", null, "https://example.com").getBoolean("accepted"))
        check(!ContentReturnProbeActivity.parseShare(Intent.ACTION_SEND, "text/plain", null, " ").getBoolean("accepted"))
    }
}
