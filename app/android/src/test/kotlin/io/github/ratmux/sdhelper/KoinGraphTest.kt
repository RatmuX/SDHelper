package io.github.ratmux.sdhelper

import android.content.Context
import io.github.ratmux.sdhelper.data.connection.Backoff
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test

/** Koin reports a missing dependency only at runtime; this fails the build instead. */
class KoinGraphTest {
    @Test
    fun `every dependency of the whole graph can be resolved`() {
        module { includes(allModules) }.verify(
            extraTypes =
                listOf(
                    Context::class,
                    CoroutineScope::class,
                    Backoff::class,
                    Function0::class,
                    Long::class,
                    String::class,
                ),
        )
    }
}
