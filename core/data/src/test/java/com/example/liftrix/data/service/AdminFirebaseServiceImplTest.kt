package com.example.liftrix.data.service

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.functions.FirebaseFunctions
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdminFirebaseServiceImplTest {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseFunctions: FirebaseFunctions
    private lateinit var firebaseUser: FirebaseUser
    private lateinit var service: AdminFirebaseServiceImpl

    @Before
    fun setUp() {
        firebaseAuth = mockk()
        firebaseFunctions = mockk(relaxed = true)
        firebaseUser = mockk()
        service = AdminFirebaseServiceImpl(firebaseAuth, firebaseFunctions)
    }

    @Test
    fun `null current user fails before token refresh`() = runTest {
        every { firebaseAuth.currentUser } returns null

        assertFalse(service.checkAdminPermissions(REQUESTED_UID))

        verify(exactly = 0) { firebaseUser.getIdToken(any()) }
    }

    @Test
    fun `mismatched current user fails before token refresh`() = runTest {
        every { firebaseAuth.currentUser } returns firebaseUser
        every { firebaseUser.uid } returns OTHER_UID

        assertFalse(service.checkAdminPermissions(REQUESTED_UID))

        verify(exactly = 0) { firebaseUser.getIdToken(any()) }
    }

    @Test
    fun `matching admin claim succeeds`() = runTest {
        val tokenResult = mockk<GetTokenResult>()
        every { firebaseAuth.currentUser } returns firebaseUser
        every { firebaseUser.uid } returns REQUESTED_UID
        every { firebaseUser.getIdToken(true) } returns Tasks.forResult(tokenResult)
        every { tokenResult.claims } returns mapOf("admin" to true)

        assertTrue(service.checkAdminPermissions(REQUESTED_UID))

        verify(exactly = 1) { firebaseUser.getIdToken(true) }
    }

    @Test
    fun `matching non-admin claim fails closed`() = runTest {
        val tokenResult = mockk<GetTokenResult>()
        every { firebaseAuth.currentUser } returns firebaseUser
        every { firebaseUser.uid } returns REQUESTED_UID
        every { firebaseUser.getIdToken(true) } returns Tasks.forResult(tokenResult)
        every { tokenResult.claims } returns emptyMap()

        assertFalse(service.checkAdminPermissions(REQUESTED_UID))
    }

    @Test
    fun `token failure fails closed`() = runTest {
        every { firebaseAuth.currentUser } returns firebaseUser
        every { firebaseUser.uid } returns REQUESTED_UID
        every { firebaseUser.getIdToken(true) } returns Tasks.forException(
            IllegalStateException("token service unavailable")
        )

        assertFalse(service.checkAdminPermissions(REQUESTED_UID))
    }

    @Test
    fun `token refresh timeout fails closed`() = runTest {
        val pendingToken = TaskCompletionSource<GetTokenResult>()
        every { firebaseAuth.currentUser } returns firebaseUser
        every { firebaseUser.uid } returns REQUESTED_UID
        every { firebaseUser.getIdToken(true) } returns pendingToken.task

        assertFalse(service.checkAdminPermissions(REQUESTED_UID))

        verify(exactly = 1) { firebaseUser.getIdToken(true) }
    }

    private companion object {
        const val REQUESTED_UID = "authenticated-user-a"
        const val OTHER_UID = "authenticated-user-b"
    }
}
