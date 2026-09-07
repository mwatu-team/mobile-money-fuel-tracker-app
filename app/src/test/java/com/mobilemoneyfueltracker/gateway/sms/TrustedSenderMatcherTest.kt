package com.mobilemoneyfueltracker.gateway.sms

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrustedSenderMatcherTest {
    @Test
    fun emptyAllowlistRejectsEverySender() {
        assertThat(TrustedSenderMatcher.isAllowed("FICTIONAL-MOMO", emptySet())).isFalse()
    }

    @Test
    fun alphanumericSenderIdsAreComparedCaseInsensitively() {
        assertThat(
            TrustedSenderMatcher.isAllowed("Fictional-Momo", setOf("FICTIONAL-MOMO")),
        ).isTrue()
    }

    @Test
    fun trustedMobileMoneySenderIdsDoNotNeedPhoneNumbers() {
        val trustedSenders = setOf("mtnmobmoney", "mtndata")

        assertThat(TrustedSenderMatcher.isAllowed("MTNMobMoney", trustedSenders)).isTrue()
        assertThat(TrustedSenderMatcher.isAllowed("MTNDATA", trustedSenders)).isTrue()
        assertThat(TrustedSenderMatcher.isAllowed("MTN-OTHER", trustedSenders)).isFalse()
    }

    @Test
    fun localAndInternationalUgandanNumbersAreEquivalent() {
        assertThat(
            TrustedSenderMatcher.isAllowed("+256 700 000 000", setOf("0700 000 000")),
        ).isTrue()
    }

    @Test
    fun aDifferentNumberIsRejected() {
        assertThat(
            TrustedSenderMatcher.isAllowed("+256700000001", setOf("+256700000000")),
        ).isFalse()
    }

    @Test
    fun duplicateFormatsAreCollapsedWhenSaved() {
        assertThat(
            TrustedSenderMatcher.normalizeEntries(
                listOf("0700 000 000", "+256700000000", " FICTIONAL-MOMO "),
            ),
        ).containsExactly("0700 000 000", "FICTIONAL-MOMO")
    }

    @Test
    fun phoneNumbersAreMaskedForDisplay() {
        assertThat(TrustedSenderMatcher.maskedForDisplay("+256700000000"))
            .isEqualTo("256***000")
    }
}
