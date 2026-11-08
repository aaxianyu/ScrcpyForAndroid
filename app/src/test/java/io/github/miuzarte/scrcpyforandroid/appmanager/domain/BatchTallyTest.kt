package io.github.miuzarte.scrcpyforandroid.appmanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatchTallyTest {

    @Test
    fun `counts successes and failures separately`() {
        val tally = BatchTally()
        tally.recordSuccess()
        tally.recordSuccess()
        tally.recordSuccess()
        tally.recordFailure("first failure")
        tally.recordFailure("  ")
        assertEquals(3, tally.successCount)
        assertEquals(2, tally.failureCount)
        assertEquals(5, tally.total)
    }

    @Test
    fun `last error keeps the last non blank reason`() {
        val tally = BatchTally()
        tally.recordFailure("boom")
        tally.recordFailure("  ")
        tally.recordFailure("last boom")
        assertEquals("last boom", tally.lastError)
    }

    @Test
    fun `no failure leaves last error null`() {
        val tally = BatchTally()
        tally.recordSuccess()
        assertNull(tally.lastError)
    }
}
