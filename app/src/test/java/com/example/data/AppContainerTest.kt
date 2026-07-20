package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppContainerTest {

    @Test
    fun `getRepository returns the same instance on repeated calls`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = AppContainer.getRepository(context)
        val second = AppContainer.getRepository(context)
        assertSame(first, second)
    }
}
