package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MaxCloudRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndGetChatMessage_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = MaxCloudRepository(firestore)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createChatMessage(
                isUser = true,
                text = "YouTube kholo",
                detectedLanguage = "Hinglish",
                toolInvoked = "launch_app",
                status = InteractionStatus.VERIFIED_SUCCESS.name,
                verificationEvidence = "Launched YouTube"
            )
        }
        assertTrue("Expected message creation to succeed", createResult.isSuccess)
        val messageId = createResult.getOrThrow()
        assertTrue(messageId.isNotBlank())

        val listResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getUserMessages(aliceUid)
        }
        assertTrue(listResult.isSuccess)
        assertTrue(listResult.getOrThrow().any { it.id == messageId && it.text == "YouTube kholo" })
    }

    @Test
    fun observeMessages_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = MaxCloudRepository(firestore)

        val messageId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createChatMessage(
                isUser = false,
                text = "Ji boss, YouTube khol diya hai.",
                detectedLanguage = "Hinglish",
                toolInvoked = "launch_app",
                status = InteractionStatus.VERIFIED_SUCCESS.name,
                verificationEvidence = "PackageManager launch verified"
            ).getOrThrow()
        }

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeMessages(aliceUid).first { list -> list.any { it.id == messageId } }
        }
        assertTrue(emitted.any { it.id == messageId })
    }

    @Test
    fun getMessageById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = MaxCloudRepository(firestore)
        val messageId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createChatMessage(
                isUser = true,
                text = "Private command",
                detectedLanguage = "English"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = MaxCloudRepository(firestore)
        val crossUserResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getMessageById(aliceUid, messageId)
        }
        assertTrue("Cross-user read must fail", crossUserResult.isFailure)
        val ex = crossUserResult.exceptionOrNull() as? FirebaseFirestoreException
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
    }

    @Test
    fun observeMessages_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = MaxCloudRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeMessages("unauthenticated_user").first()
            }
            fail("Expected FirebaseFirestoreException PERMISSION_DENIED for unauthenticated caller")
        } catch (e: Throwable) {
            var current: Throwable? = e
            var firestoreEx: FirebaseFirestoreException? = null
            while (current != null) {
                if (current is FirebaseFirestoreException) {
                    firestoreEx = current
                    break
                }
                current = current.cause
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
        }
    }

    @Test
    fun createAndToggleTrustedRule_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = MaxCloudRepository(firestore)

        val ruleId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createTrustedRule(
                toolName = "whatsapp_action",
                scopeKey = "whatsapp:+919876543210",
                description = "Trusted WhatsApp draft for Rahul",
                isEnabled = true
            ).getOrThrow()
        }
        assertTrue(ruleId.isNotBlank())

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateTrustedRuleEnabled(ruleId, false)
        }
        assertTrue(updateResult.isSuccess)

        val rules = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTrustedRules(aliceUid).first { list ->
                list.any { it.id == ruleId && !it.isEnabled }
            }
        }
        assertTrue(rules.any { it.id == ruleId && !it.isEnabled })
    }

    private companion object {
        const val ALICE_EMAIL = "alice_max@test.com"
        const val BOB_EMAIL = "bob_max@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
