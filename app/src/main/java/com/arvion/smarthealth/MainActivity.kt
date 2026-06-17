package com.arvion.smarthealth

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.arvion.smarthealth.data.UserRepository
import com.arvion.smarthealth.service.PermissionService
import com.auth0.android.jwt.JWT
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController
    private var isBlockedScreenActive = false
    private var isDeveloperModeBlocked = false

    lateinit var toolbar: Toolbar
    lateinit var drawerLayout: DrawerLayout
    lateinit var navView: NavigationView
    lateinit var bottomNavView: BottomNavigationView

    private lateinit var signInClient: SignInClient
    private lateinit var userRepository: UserRepository

    val permissionService = PermissionService()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        signInClient = Identity.getSignInClient(this)
        userRepository = UserRepository(this)

        trySilentSignIn()

        toolbar = findViewById(R.id.toolbar)
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        bottomNavView = findViewById(R.id.bottom_nav_view)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val navInflater = navController.navInflater
        val graph = navInflater.inflate(R.navigation.nav_graph)

        lifecycleScope.launch {
            val installed = withContext(Dispatchers.IO) {
                isSamsungHealthInstalled()
            }

            graph.setStartDestination(
                if (installed) R.id.navigation_home
                else R.id.navigation_samsung_health_not_installed
            )

            navController.graph = graph

            if (installed) {
                setSupportActionBar(toolbar)

                appBarConfiguration = AppBarConfiguration(
                    setOf(
                        R.id.navigation_home,
                        R.id.navigation_dashboard,
                        R.id.navigation_sync,
                        R.id.navigation_notifications,
                        R.id.navigation_settings
                    ),
                    drawerLayout
                )

                setupActionBarWithNavController(navController, appBarConfiguration)
                navView.setupWithNavController(navController)
                bottomNavView.setupWithNavController(navController)

                navView.setNavigationItemSelectedListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.nav_login -> {
                            startGoogleLogin()
                            drawerLayout.closeDrawer(GravityCompat.START)
                            true
                        }
                        R.id.nav_logout -> {
                            logout()
                            drawerLayout.closeDrawer(GravityCompat.START)
                            true
                        }
                        else -> {
                            val handled = NavigationUI.onNavDestinationSelected(menuItem, navController)
                            if (handled) {
                                drawerLayout.closeDrawer(GravityCompat.START)
                            }
                            handled
                        }
                    }
                }

                // Trigger permission request once if not already granted
                val status = permissionService.getPermissionStatus(this@MainActivity)
                if (status != 0) {
                    permissionService.requestPermissions(this@MainActivity)
                }

            } else {
                toolbar.isVisible = false
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                bottomNavView.isVisible = false

                onBackPressedDispatcher.addCallback(this@MainActivity) {
                    finishAffinity()
                }
            }
        }
    }

    private fun trySilentSignIn() {
        val request = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .setFilterByAuthorizedAccounts(true)
                    .build()
            )
            .setAutoSelectEnabled(true)
            .build()

        signInClient.beginSignIn(request)
            .addOnSuccessListener { result ->
                startIntentSenderForResult(
                    result.pendingIntent.intentSender,
                    1001,
                    null,
                    0,
                    0,
                    0,
                    null
                )
            }
            .addOnFailureListener {
                // Silent sign-in failed — normal on first launch
            }
    }

    private fun startGoogleLogin() {
        val request = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .build()

        signInClient.beginSignIn(request)
            .addOnSuccessListener { result ->
                startIntentSenderForResult(
                    result.pendingIntent.intentSender,
                    1001,
                    null,
                    0,
                    0,
                    0,
                    null
                )
            }
            .addOnFailureListener {
                Toast.makeText(this, "Google Sign-in failed to start", Toast.LENGTH_SHORT).show()
            }
    }

    private fun logout() {
        lifecycleScope.launch {
            userRepository.clearUser()
            signInClient.signOut()
            Toast.makeText(this@MainActivity, "Logged out", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1001) {
            try {
                val credential = signInClient.getSignInCredentialFromIntent(data)
                val idToken = credential.googleIdToken

                if (idToken != null) {
                    val jwt = JWT(idToken)
                    val googleUserId = jwt.getClaim("sub").asString()
                    lifecycleScope.launch {
                        userRepository.saveUserId(googleUserId ?: credential.id)
                        userRepository.saveJwtToken(idToken)
                        Toast.makeText(this@MainActivity, "Logged in successfully", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Google login failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isSamsungHealthInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo("com.sec.android.app.shealth", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onResume() {
        super.onResume()

        lifecycleScope.launch {
            val samsungInstalled = withContext(Dispatchers.IO) { isSamsungHealthInstalled() }
            val permissionStatus = permissionService.getPermissionStatus(this@MainActivity)
            val developerModeEnabled = permissionStatus != 2003

            when {
                samsungInstalled && developerModeEnabled && isBlockedScreenActive -> {
                    navigateToHomeAfterInstall()
                }

                !samsungInstalled && !isBlockedScreenActive -> {
                    navigateToBlockingScreen()
                }

                !developerModeEnabled && !isDeveloperModeBlocked -> {
                    navigateToDeveloperModeScreen()
                }
            }
        }
    }


    private fun navigateToDeveloperModeScreen() {
        isBlockedScreenActive = true
        isDeveloperModeBlocked = true

        navController.navigate(
            R.id.fragment_enable_developer_mode,
            null,
            navOptions {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
            }
        )

        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        bottomNavView.isVisible = false
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setHomeButtonEnabled(false)

        onBackPressedDispatcher.addCallback(this) {
            finishAffinity()
        }
    }


    private fun navigateToHomeAfterInstall() {
        isBlockedScreenActive = false

        val graph = navController.navInflater.inflate(R.navigation.nav_graph)
        graph.setStartDestination(R.id.navigation_home)
        navController.setGraph(graph, null)

        setSupportActionBar(findViewById(R.id.toolbar))
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        bottomNavView.isVisible = true

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home,
                R.id.navigation_dashboard,
                R.id.navigation_sync,
                R.id.navigation_notifications,
                R.id.navigation_settings
            ),
            drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        bottomNavView.setupWithNavController(navController)
        setSupportActionBar(toolbar)
        toolbar.isVisible = true
    }

    private fun navigateToBlockingScreen() {
        isBlockedScreenActive = true

        navController.navigate(
            R.id.navigation_samsung_health_not_installed,
            null,
            navOptions {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
            }
        )

        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        bottomNavView.isVisible = false
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setHomeButtonEnabled(false)

        onBackPressedDispatcher.addCallback(this) {
            finishAffinity()
        }
    }


}
