package com.mobilemoneyfueltracker.gateway.parser

import com.mobilemoneyfueltracker.gateway.data.SmsQueueRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MessageHashTest {
    @Test
    fun sameLocalMessageHasDeterministicHash() {
        val first = SmsQueueRepository.deterministicHash("FICTIONAL", "message", 1234L)
        val second = SmsQueueRepository.deterministicHash("FICTIONAL", "message", 1234L)
        assertThat(first).isEqualTo(second)
        assertThat(first).hasLength(64)
    }

    @Test
    fun timestampAndOriginalBodyContributeToHash() {
        val original = SmsQueueRepository.deterministicHash("FICTIONAL", "message", 1234L)
        assertThat(SmsQueueRepository.deterministicHash("FICTIONAL", "message!", 1234L))
            .isNotEqualTo(original)
        assertThat(SmsQueueRepository.deterministicHash("FICTIONAL", "message", 1235L))
            .isNotEqualTo(original)
    }
}
