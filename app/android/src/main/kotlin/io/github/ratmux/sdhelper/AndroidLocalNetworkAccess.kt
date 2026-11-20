package io.github.ratmux.sdhelper

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.ratmux.sdhelper.core.model.LocalNetworkAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The `ACCESS_LOCAL_NETWORK` runtime permission exists only from Android 17; on older versions the INTERNET
 * permission is enough, so access counts as granted there.
 */
class AndroidLocalNetworkAccess(
    private val context: Context,
) : LocalNetworkAccess {
    private val state = MutableStateFlow(check())
    override val granted: StateFlow<Boolean> = state.asStateFlow()

    override fun refresh() {
        state.value = check()
    }

    private fun check(): Boolean =
        Build.VERSION.SDK_INT < LocalNetworkAccess.MIN_SDK ||
            ContextCompat.checkSelfPermission(context, LocalNetworkAccess.PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
}
