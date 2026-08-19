package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.FirebaseAuthDataSourceImpl
import com.example.util.NotificationUrgency
import com.example.util.RefillNotificationSchedule
import com.example.util.SmartNotificationDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Careflux", appName)
    }

    @Test
    fun `test smart notification channels setup and safe dispatch`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SmartNotificationDispatcher.setupNotificationChannels(context)

        val dispatched = SmartNotificationDispatcher.dispatchNotification(
            context = context,
            title = "Morning Opening Briefing",
            content = "Branch is ready for operations with 5 pending refills.",
            urgency = NotificationUrgency.STANDARD,
            dedupKey = "test_briefing_key"
        )
        // Dispatched should succeed or deduplicate safely without throwing any uncaught exceptions
        assertNotNull(dispatched)
    }

    @Test
    fun `test firebase auth data source fails gracefully when uninitialized`() {
        val authDataSource = FirebaseAuthDataSourceImpl()
        // Should not throw IllegalStateException or crash
        val currentUser = authDataSource.getCurrentUser()
        assertNull(currentUser)
    }

    @Test
    fun `test refill notification schedule windows`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 10)
        }
        assertTrue(RefillNotificationSchedule.isMorningOpeningWindow(cal.timeInMillis))
    }
}

