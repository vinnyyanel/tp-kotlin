package com.esgis.chatapp

import com.esgis.chatapp.data.Profile
import com.esgis.chatapp.ui.users.UsersUiState
import com.esgis.chatapp.ui.users.UsersViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UsersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        repo: FakeChatRepository = FakeChatRepository(),
        online: Set<String> = emptySet()
    ) = UsersViewModel(repo, MutableStateFlow(online))

    @Test
    fun `charge la liste des utilisateurs au demarrage`() = runTest {
        val repo = FakeChatRepository().apply {
            contacts = listOf(Profile("u1", "carol"), Profile("u2", "alice"))
        }

        val state = viewModel(repo).state.value

        assertTrue(state is UsersUiState.Data)
        assertEquals(2, (state as UsersUiState.Data).users.size)
        assertEquals("carol", state.users.first().username)
    }

    @Test
    fun `une erreur de chargement expose l etat Error`() = runTest {
        val repo = FakeChatRepository().apply {
            shouldFail = true
            failMessage = "hors ligne"
        }

        val state = viewModel(repo).state.value

        assertTrue(state is UsersUiState.Error)
        assertEquals("hors ligne", (state as UsersUiState.Error).message)
    }

    @Test
    fun `ouvrir une discussion renvoie l id de la conversation`() = runTest {
        val vm = viewModel()
        var conversationId: String? = null

        vm.startChat("u1") { conversationId = it }

        assertEquals("conv-u1", conversationId)
    }

    @Test
    fun `un echec d ouverture ne navigue pas et remonte un message`() = runTest {
        val repo = FakeChatRepository().apply { shouldFail = true }
        val vm = viewModel(repo)
        var conversationId: String? = null

        vm.startChat("u1") { conversationId = it }

        assertNull("aucune navigation en cas d'échec", conversationId)
        assertTrue(vm.message.value != null)
    }

    @Test
    fun `les utilisateurs en ligne sont exposes a la vue`() = runTest {
        val vm = viewModel(online = setOf("u1"))

        assertTrue("u1" in vm.onlineUsers.value)
        assertTrue("u2" !in vm.onlineUsers.value)
    }
}
