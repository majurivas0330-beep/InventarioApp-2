package com.example.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.InventoryApplication
import com.example.feature.auth.AuthScreen
import com.example.feature.auth.UserSession
import com.example.feature.auth.signOutUser
import com.example.feature.product.domain.usecase.SaveProductUseCase
import com.example.feature.product.presentation.screen.ProductFormScreen
import com.example.feature.product.presentation.screen.ProductListScreen
import com.example.feature.product.presentation.viewmodel.ProductFormViewModel
import com.example.feature.product.presentation.viewmodel.ProductListViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.serialization.Serializable

@Serializable
data object ProductListRoute

@Serializable
data class ProductFormRoute(val id: String? = null)

@Composable
fun AppNavigation() {
    val initialFirebaseUser = Firebase.auth.currentUser
    var userSession by remember {
        mutableStateOf<UserSession?>(
            initialFirebaseUser?.let { UserSession(it.uid, it.email ?: "Usuario Autenticado") }
        )
    }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            if (user != null) {
                userSession = UserSession(user.uid, user.email ?: "Invitado (Nube)")
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val session = userSession
    if (session == null) {
        AuthScreen(
            onAuthSuccess = { newSession ->
                userSession = newSession
            }
        )
    } else {
        val uid = session.uid
        val userEmail = session.email
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val credentialManager = remember(context) { CredentialManager.create(context) }

        key(uid) {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = ProductListRoute
            ) {
                composable<ProductListRoute> {
                    val vm: ProductListViewModel = viewModel(
                        key = "list_$uid",
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY]) as InventoryApplication
                                ProductListViewModel(
                                    repository = app.container.productRepository,
                                    currentUserId = uid
                                )
                            }
                        }
                    )
                    ProductListScreen(
                        vm = vm,
                        userEmail = userEmail,
                        onCreate = {
                            navController.navigate(ProductFormRoute(id = null))
                        },
                        onEdit = { productId ->
                            navController.navigate(ProductFormRoute(id = productId))
                        },
                        onSignOut = {
                            signOutUser(
                                credentialManager = credentialManager,
                                scope = scope,
                                onSignOutComplete = {
                                    userSession = null
                                }
                            )
                        }
                    )
                }

                composable<ProductFormRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<ProductFormRoute>()
                    val vm: ProductFormViewModel = viewModel(
                        key = "form_${uid}_${route.id ?: "new"}",
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY]) as InventoryApplication
                                val repo = app.container.productRepository
                                ProductFormViewModel(
                                    repository = repo,
                                    saveProduct = SaveProductUseCase(repo),
                                    currentUserId = uid
                                )
                            }
                        }
                    )
                    ProductFormScreen(
                        vm = vm,
                        productId = route.id,
                        onDone = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}
