package com.arvion.smarthealth

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController
    private var isBlockedScreenActive = false

    lateinit var toolbar: Toolbar
    lateinit var drawerLayout: DrawerLayout
    lateinit var navView: NavigationView
    lateinit var bottomNavView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

         toolbar = findViewById(R.id.toolbar)
         drawerLayout = findViewById(R.id.drawer_layout)
         navView = findViewById(R.id.nav_view)
         bottomNavView = findViewById(R.id.bottom_nav_view)

        // 1. Initialize navController immediately
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // 2. Inflate graph
        val navInflater = navController.navInflater
        val graph = navInflater.inflate(R.navigation.nav_graph)

        lifecycleScope.launch {
            val installed = withContext(Dispatchers.IO) {
                isSamsungHealthInstalled()
            }

            // 3. Set start destination BEFORE assigning graph
            graph.setStartDestination(
                if (installed) R.id.navigation_home
                else R.id.navigation_samsung_health_not_installed
            )

            // 4. Assign graph
            navController.graph = graph

            if (installed) {
                // 5. Only now set up toolbar
                setSupportActionBar(toolbar)

                // 6. Build AppBarConfiguration AFTER graph is assigned
                appBarConfiguration = AppBarConfiguration(
                    setOf(
                        R.id.navigation_home,
                        R.id.navigation_dashboard,
                        R.id.navigation_notifications
                    ),
                    drawerLayout
                )

                // 7. Connect toolbar + drawer + bottom nav
                setupActionBarWithNavController(navController, appBarConfiguration)
                navView.setupWithNavController(navController)
                bottomNavView.setupWithNavController(navController)

            } else {
                // Samsung Health missing → block navigation UI
                toolbar.isVisible = false
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                bottomNavView.isVisible = false

                // Back button closes the app
                onBackPressedDispatcher.addCallback(this@MainActivity) {
                    finishAffinity()
                }
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
            val installed = withContext(Dispatchers.IO) {
                isSamsungHealthInstalled()
            }

            if (installed && isBlockedScreenActive) {
                // User installed Samsung Health while app was in background
                navigateToHomeAfterInstall()
            } else if (!installed && !isBlockedScreenActive) {
                // User removed Samsung Health while app was in background
                navigateToBlockingScreen()
            }
        }
    }

    private fun navigateToHomeAfterInstall() {
        isBlockedScreenActive = false

        val graph = navController.navInflater.inflate(R.navigation.nav_graph)
        graph.setStartDestination(R.id.navigation_home)
        navController.setGraph(graph, null)

        // Restore UI
        setSupportActionBar(findViewById(R.id.toolbar))
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        bottomNavView.isVisible = true

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home,
                R.id.navigation_dashboard,
                R.id.navigation_notifications
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
