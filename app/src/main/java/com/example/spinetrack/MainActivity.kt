package com.example.spinetrack

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.spinetrack.databinding.ActivityMainBinding
import com.example.spinetrack.data.local.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userPreferences = UserPreferences(applicationContext)
        setupNavigation()
        checkAuthState()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)
    }

    private fun checkAuthState() {
        lifecycleScope.launch {
            val userId = userPreferences.userIdFlow.first()
            if (userId == null) {
                binding.bottomNavigation.visibility = View.GONE
                navController.navigate(R.id.nav_login)
            }
        }
    }

    fun onLoginSuccess() {
        binding.bottomNavigation.visibility = View.VISIBLE
        navController.navigate(R.id.nav_inicio)
    }

    fun onLogout() {
        binding.bottomNavigation.visibility = View.GONE
        navController.navigate(R.id.nav_login)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}