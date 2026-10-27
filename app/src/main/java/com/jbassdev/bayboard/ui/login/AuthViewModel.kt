package com.jbassdev.bayboard.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbassdev.bayboard.data.AuthRepository
import com.jbassdev.bayboard.data.ServiceLocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val isGuest: Boolean) : AuthState
}

data class LoginForm(val busy: Boolean = false, val error: String? = null)

class AuthViewModel(
    private val repo: AuthRepository = ServiceLocator.authRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = repo.user
        .map { user -> if (user == null) AuthState.SignedOut else AuthState.SignedIn(user.isAnonymous) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)

    private val _form = MutableStateFlow(LoginForm())
    val form: StateFlow<LoginForm> = _form.asStateFlow()

    fun signIn(email: String, password: String) = attempt { repo.signIn(email.trim(), password) }

    fun signInAsGuest() = attempt { repo.signInAsGuest() }

    fun signOut() = repo.signOut()

    private fun attempt(block: suspend () -> Unit) {
        viewModelScope.launch {
            _form.value = LoginForm(busy = true)
            _form.value = try {
                block()
                LoginForm()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoginForm(error = e.message ?: "Sign-in failed. Try again.")
            }
        }
    }
}
