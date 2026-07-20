package com.esgis.chatapp

import com.esgis.chatapp.ui.auth.AuthUiState
import com.esgis.chatapp.ui.auth.AuthViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repo: FakeChatRepository
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        repo = FakeChatRepository()
        viewModel = AuthViewModel(repo)
    }

    @Test
    fun `etat initial est Idle`() {
        assertTrue(viewModel.state.value is AuthUiState.Idle)
    }

    @Test
    fun `connexion avec champs vides echoue sans appeler le repository`() {
        viewModel.signIn("", "")

        assertTrue(viewModel.state.value is AuthUiState.Error)
        assertTrue("le repository ne doit pas être appelé", repo.signInCalls.isEmpty())
    }

    @Test
    fun `connexion valide passe en Success et appelle le repository`() = runTest {
        viewModel.signIn("bob@esgis.com", "azerty123")

        assertTrue(viewModel.state.value is AuthUiState.Success)
        assertEquals(1, repo.signInCalls.size)
        assertEquals("bob@esgis.com", repo.signInCalls.first().first)
    }

    @Test
    fun `connexion en echec expose le message d erreur`() = runTest {
        repo.shouldFail = true
        repo.failMessage = "Identifiants invalides"

        viewModel.signIn("bob@esgis.com", "mauvais")

        val state = viewModel.state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("Identifiants invalides", (state as AuthUiState.Error).message)
    }

    @Test
    fun `inscription transmet bien le nom d utilisateur`() = runTest {
        viewModel.signUp("alice@esgis.com", "azerty123", "alice")

        assertTrue(viewModel.state.value is AuthUiState.Success)
        assertEquals("alice", repo.signUpCalls.single().third)
    }

    @Test
    fun `inscription sans nom d utilisateur est refusee`() {
        viewModel.signUp("alice@esgis.com", "azerty123", "")

        assertTrue(viewModel.state.value is AuthUiState.Error)
        assertTrue(repo.signUpCalls.isEmpty())
    }

    @Test
    fun `resetError remet l etat a Idle`() {
        viewModel.signIn("", "")
        assertTrue(viewModel.state.value is AuthUiState.Error)

        viewModel.resetError()

        assertTrue(viewModel.state.value is AuthUiState.Idle)
    }
}
