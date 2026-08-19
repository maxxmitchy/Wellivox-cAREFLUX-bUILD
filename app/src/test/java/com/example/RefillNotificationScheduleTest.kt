package com.example

import com.example.util.RefillNotificationSchedule
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class RefillNotificationScheduleTest {

    private fun getTimeMs(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // =========================================================================
    // 1. Core Boundary Transitions (Both sides of every operational transition)
    // =========================================================================

    @Test
    fun testTransition_0659_to_0700_OvernightToOpening() {
        val t0659 = getTimeMs(6, 59)
        assertTrue("06:59 must be overnight quiet", RefillNotificationSchedule.isOvernightQuietWindow(t0659))
        assertFalse("06:59 must not be morning opening", RefillNotificationSchedule.isMorningOpeningWindow(t0659))

        val t0700 = getTimeMs(7, 0)
        assertFalse("07:00 must exit overnight quiet", RefillNotificationSchedule.isOvernightQuietWindow(t0700))
        assertTrue("07:00 must enter morning opening", RefillNotificationSchedule.isMorningOpeningWindow(t0700))
    }

    @Test
    fun testTransition_0729_to_0730_OpeningToRefillOutbound() {
        val t0729 = getTimeMs(7, 29)
        assertTrue("07:29 must be morning opening", RefillNotificationSchedule.isMorningOpeningWindow(t0729))
        assertFalse("07:29 must not be refill outbound", RefillNotificationSchedule.isMorningRefillOutboundWindow(t0729))

        val t0730 = getTimeMs(7, 30)
        assertFalse("07:30 must exit morning opening", RefillNotificationSchedule.isMorningOpeningWindow(t0730))
        assertTrue("07:30 must enter refill outbound", RefillNotificationSchedule.isMorningRefillOutboundWindow(t0730))
    }

    @Test
    fun testTransition_0814_to_0815_RefillOutboundToDispatch() {
        val t0814 = getTimeMs(8, 14)
        assertTrue("08:14 must be refill outbound", RefillNotificationSchedule.isMorningRefillOutboundWindow(t0814))
        assertFalse("08:14 must not be refill dispatch", RefillNotificationSchedule.isRefillDispatchWindow(t0814))

        val t0815 = getTimeMs(8, 15)
        assertFalse("08:15 must exit refill outbound", RefillNotificationSchedule.isMorningRefillOutboundWindow(t0815))
        assertTrue("08:15 must enter refill dispatch", RefillNotificationSchedule.isRefillDispatchWindow(t0815))
    }

    @Test
    fun testTransition_0844_to_0845_DispatchToUnconfirmedAlert() {
        val t0844 = getTimeMs(8, 44)
        assertTrue("08:44 must be refill dispatch", RefillNotificationSchedule.isRefillDispatchWindow(t0844))
        assertFalse("08:44 must not be unconfirmed alert", RefillNotificationSchedule.isUnconfirmedRefillAlertWindow(t0844))

        val t0845 = getTimeMs(8, 45)
        assertFalse("08:45 must exit refill dispatch", RefillNotificationSchedule.isRefillDispatchWindow(t0845))
        assertTrue("08:45 must enter unconfirmed alert", RefillNotificationSchedule.isUnconfirmedRefillAlertWindow(t0845))
    }

    @Test
    fun testTransition_0859_to_0900_UnconfirmedAlertToReconciliation() {
        val t0859 = getTimeMs(8, 59)
        assertTrue("08:59 must be unconfirmed alert", RefillNotificationSchedule.isUnconfirmedRefillAlertWindow(t0859))
        assertFalse("08:59 must not be morning stock reconciliation", RefillNotificationSchedule.isMorningStockReconciliationWindow(t0859))

        val t0900 = getTimeMs(9, 0)
        assertFalse("09:00 must exit unconfirmed alert", RefillNotificationSchedule.isUnconfirmedRefillAlertWindow(t0900))
        assertTrue("09:00 must enter morning stock reconciliation", RefillNotificationSchedule.isMorningStockReconciliationWindow(t0900))
    }

    @Test
    fun testTransition_1059_to_1100_ReconciliationToPriorityRefill() {
        val t1059 = getTimeMs(10, 59)
        assertTrue("10:59 must be stock reconciliation", RefillNotificationSchedule.isMorningStockReconciliationWindow(t1059))
        assertTrue("10:59 must be morning expiry", RefillNotificationSchedule.isMorningExpiryWindow(t1059))
        assertFalse("10:59 must not be priority refill", RefillNotificationSchedule.isPriorityRefillWindow(t1059))

        val t1100 = getTimeMs(11, 0)
        assertFalse("11:00 must exit stock reconciliation", RefillNotificationSchedule.isMorningStockReconciliationWindow(t1100))
        assertFalse("11:00 must exit morning expiry", RefillNotificationSchedule.isMorningExpiryWindow(t1100))
        assertTrue("11:00 must enter priority refill", RefillNotificationSchedule.isPriorityRefillWindow(t1100))
    }

    @Test
    fun testTransition_1159_to_1200_PeakRushSuppressionEntry() {
        val t1159 = getTimeMs(11, 59)
        assertFalse("11:59 must not be peak rush", RefillNotificationSchedule.isPeakRushWindow(t1159))
        assertTrue("11:59 must be priority refill", RefillNotificationSchedule.isPriorityRefillWindow(t1159))

        val t1200 = getTimeMs(12, 0)
        assertTrue("12:00 must enter peak rush", RefillNotificationSchedule.isPeakRushWindow(t1200))
        assertTrue("12:00 priority refill is active but suppressed by peak rush", RefillNotificationSchedule.isPriorityRefillWindow(t1200))
    }

    @Test
    fun testTransition_1359_to_1400_PeakRushExit_AfternoonRestockEntry() {
        val t1359 = getTimeMs(13, 59)
        assertTrue("13:59 must be peak rush", RefillNotificationSchedule.isPeakRushWindow(t1359))
        assertTrue("13:59 must be priority refill", RefillNotificationSchedule.isPriorityRefillWindow(t1359))
        assertFalse("13:59 must not be afternoon restock", RefillNotificationSchedule.isAfternoonRestockWindow(t1359))

        val t1400 = getTimeMs(14, 0)
        assertFalse("14:00 must exit peak rush", RefillNotificationSchedule.isPeakRushWindow(t1400))
        assertFalse("14:00 must exit priority refill", RefillNotificationSchedule.isPriorityRefillWindow(t1400))
        assertTrue("14:00 must enter afternoon restock", RefillNotificationSchedule.isAfternoonRestockWindow(t1400))
    }

    @Test
    fun testTransition_1559_to_1600_RestockToAfternoonExpiry() {
        val t1559 = getTimeMs(15, 59)
        assertTrue("15:59 must be afternoon restock", RefillNotificationSchedule.isAfternoonRestockWindow(t1559))
        assertFalse("15:59 must not be priority expiry", RefillNotificationSchedule.isPriorityExpiryWindow(t1559))

        val t1600 = getTimeMs(16, 0)
        assertFalse("16:00 must exit afternoon restock", RefillNotificationSchedule.isAfternoonRestockWindow(t1600))
        assertTrue("16:00 must enter priority expiry", RefillNotificationSchedule.isPriorityExpiryWindow(t1600))
    }

    @Test
    fun testTransition_1759_to_1800_ExpiryToTaskDelegation() {
        val t1759 = getTimeMs(17, 59)
        assertTrue("17:59 must be priority expiry", RefillNotificationSchedule.isPriorityExpiryWindow(t1759))
        assertFalse("17:59 must not be evening task delegation", RefillNotificationSchedule.isEveningTaskDelegationWindow(t1759))

        val t1800 = getTimeMs(18, 0)
        assertFalse("18:00 must exit priority expiry", RefillNotificationSchedule.isPriorityExpiryWindow(t1800))
        assertTrue("18:00 must enter evening task delegation", RefillNotificationSchedule.isEveningTaskDelegationWindow(t1800))
    }

    @Test
    fun testTransition_1959_to_2000_TaskDelegationToEveningAudit() {
        val t1959 = getTimeMs(19, 59)
        assertTrue("19:59 must be evening task delegation", RefillNotificationSchedule.isEveningTaskDelegationWindow(t1959))
        assertFalse("19:59 must not be evening audit", RefillNotificationSchedule.isEveningAuditWindow(t1959))
        assertFalse("19:59 must not be evening closing", RefillNotificationSchedule.isEveningClosingWindow(t1959))

        val t2000 = getTimeMs(20, 0)
        assertFalse("20:00 must exit evening task delegation", RefillNotificationSchedule.isEveningTaskDelegationWindow(t2000))
        assertTrue("20:00 must enter evening audit", RefillNotificationSchedule.isEveningAuditWindow(t2000))
        assertFalse("20:00 must not be evening closing", RefillNotificationSchedule.isEveningClosingWindow(t2000))
    }

    @Test
    fun testTransition_2029_to_2030_EveningAuditToEveningClosing() {
        val t2029 = getTimeMs(20, 29)
        assertTrue("20:29 must be evening audit", RefillNotificationSchedule.isEveningAuditWindow(t2029))
        assertFalse("20:29 must not be evening closing", RefillNotificationSchedule.isEveningClosingWindow(t2029))

        val t2030 = getTimeMs(20, 30)
        assertFalse("20:30 must exit evening audit", RefillNotificationSchedule.isEveningAuditWindow(t2030))
        assertTrue("20:30 must enter evening closing", RefillNotificationSchedule.isEveningClosingWindow(t2030))
    }

    @Test
    fun testTransition_2159_to_2200_EveningClosingToOvernightQuiet() {
        val t2159 = getTimeMs(21, 59)
        assertFalse("21:59 must not be evening audit", RefillNotificationSchedule.isEveningAuditWindow(t2159))
        assertTrue("21:59 must be evening closing", RefillNotificationSchedule.isEveningClosingWindow(t2159))
        assertFalse("21:59 must not be overnight quiet", RefillNotificationSchedule.isOvernightQuietWindow(t2159))

        val t2200 = getTimeMs(22, 0)
        assertFalse("22:00 must exit evening closing", RefillNotificationSchedule.isEveningClosingWindow(t2200))
        assertFalse("22:00 must not be evening audit", RefillNotificationSchedule.isEveningAuditWindow(t2200))
        assertTrue("22:00 must enter overnight quiet", RefillNotificationSchedule.isOvernightQuietWindow(t2200))
    }

    // =========================================================================
    // 2. Mutual Exclusivity Assertions
    // =========================================================================

    @Test
    fun testEveningWindowsMutualExclusivityAtBoundaries() {
        val t2029 = getTimeMs(20, 29)
        assertTrue(RefillNotificationSchedule.isEveningAuditWindow(t2029))
        assertFalse(RefillNotificationSchedule.isEveningClosingWindow(t2029))

        val t2030 = getTimeMs(20, 30)
        assertFalse(RefillNotificationSchedule.isEveningAuditWindow(t2030))
        assertTrue(RefillNotificationSchedule.isEveningClosingWindow(t2030))

        val t2035 = getTimeMs(20, 35)
        assertFalse(RefillNotificationSchedule.isEveningAuditWindow(t2035))
        assertTrue(RefillNotificationSchedule.isEveningClosingWindow(t2035))
    }

    @Test
    fun testAfternoonAndEveningDisjointness() {
        val t1630 = getTimeMs(16, 30)
        assertTrue(RefillNotificationSchedule.isPriorityExpiryWindow(t1630))
        assertFalse(RefillNotificationSchedule.isEveningTaskDelegationWindow(t1630))
        assertFalse(RefillNotificationSchedule.isEveningAuditWindow(t1630))
        assertFalse(RefillNotificationSchedule.isEveningClosingWindow(t1630))

        val t1830 = getTimeMs(18, 30)
        assertFalse(RefillNotificationSchedule.isPriorityExpiryWindow(t1830))
        assertTrue(RefillNotificationSchedule.isEveningTaskDelegationWindow(t1830))
        assertFalse(RefillNotificationSchedule.isEveningAuditWindow(t1830))
        assertFalse(RefillNotificationSchedule.isEveningClosingWindow(t1830))
    }

    // =========================================================================
    // 3. No Retrospective Replay (Schedule Predicate Invariance)
    // =========================================================================

    @Test
    fun testNoRetrospectiveReplay_1630_CannotSatisfyMorningWindows() {
        val t1630 = getTimeMs(16, 30)
        assertFalse(RefillNotificationSchedule.isMorningOpeningWindow(t1630))
        assertFalse(RefillNotificationSchedule.isMorningRefillOutboundWindow(t1630))
        assertFalse(RefillNotificationSchedule.isRefillDispatchWindow(t1630))
        assertFalse(RefillNotificationSchedule.isUnconfirmedRefillAlertWindow(t1630))
        assertFalse(RefillNotificationSchedule.isMorningStockReconciliationWindow(t1630))
        assertFalse(RefillNotificationSchedule.isPriorityRefillWindow(t1630))
        assertFalse(RefillNotificationSchedule.isMorningExpiryWindow(t1630))
    }

    @Test
    fun testNoRetrospectiveReplay_2000_CannotSatisfyDaytimeWindows() {
        val t2000 = getTimeMs(20, 0)
        assertFalse(RefillNotificationSchedule.isMorningOpeningWindow(t2000))
        assertFalse(RefillNotificationSchedule.isMorningRefillOutboundWindow(t2000))
        assertFalse(RefillNotificationSchedule.isRefillDispatchWindow(t2000))
        assertFalse(RefillNotificationSchedule.isUnconfirmedRefillAlertWindow(t2000))
        assertFalse(RefillNotificationSchedule.isMorningStockReconciliationWindow(t2000))
        assertFalse(RefillNotificationSchedule.isPriorityRefillWindow(t2000))
        assertFalse(RefillNotificationSchedule.isAfternoonRestockWindow(t2000))
        assertFalse(RefillNotificationSchedule.isPriorityExpiryWindow(t2000))
        assertFalse(RefillNotificationSchedule.isEveningTaskDelegationWindow(t2000))
    }

    @Test
    fun testNoRetrospectiveReplay_2200_CannotSatisfyOperationalWindows() {
        val t2200 = getTimeMs(22, 0)
        assertFalse(RefillNotificationSchedule.isMorningOpeningWindow(t2200))
        assertFalse(RefillNotificationSchedule.isMorningRefillOutboundWindow(t2200))
        assertFalse(RefillNotificationSchedule.isRefillDispatchWindow(t2200))
        assertFalse(RefillNotificationSchedule.isUnconfirmedRefillAlertWindow(t2200))
        assertFalse(RefillNotificationSchedule.isMorningStockReconciliationWindow(t2200))
        assertFalse(RefillNotificationSchedule.isPriorityRefillWindow(t2200))
        assertFalse(RefillNotificationSchedule.isAfternoonRestockWindow(t2200))
        assertFalse(RefillNotificationSchedule.isPriorityExpiryWindow(t2200))
        assertFalse(RefillNotificationSchedule.isEveningTaskDelegationWindow(t2200))
        assertFalse(RefillNotificationSchedule.isEveningAuditWindow(t2200))
        assertFalse(RefillNotificationSchedule.isEveningClosingWindow(t2200))
        assertTrue(RefillNotificationSchedule.isOvernightQuietWindow(t2200))
    }

    @Test
    fun testNoRetrospectiveReplay_0600_CannotSatisfyDaytimeWindows() {
        val t0600 = getTimeMs(6, 0)
        assertFalse(RefillNotificationSchedule.isMorningOpeningWindow(t0600))
        assertFalse(RefillNotificationSchedule.isMorningRefillOutboundWindow(t0600))
        assertFalse(RefillNotificationSchedule.isRefillDispatchWindow(t0600))
        assertFalse(RefillNotificationSchedule.isUnconfirmedRefillAlertWindow(t0600))
        assertFalse(RefillNotificationSchedule.isMorningStockReconciliationWindow(t0600))
        assertFalse(RefillNotificationSchedule.isPriorityRefillWindow(t0600))
        assertFalse(RefillNotificationSchedule.isAfternoonRestockWindow(t0600))
        assertFalse(RefillNotificationSchedule.isPriorityExpiryWindow(t0600))
        assertFalse(RefillNotificationSchedule.isEveningTaskDelegationWindow(t0600))
        assertFalse(RefillNotificationSchedule.isEveningAuditWindow(t0600))
        assertFalse(RefillNotificationSchedule.isEveningClosingWindow(t0600))
        assertTrue(RefillNotificationSchedule.isOvernightQuietWindow(t0600))
    }
}
