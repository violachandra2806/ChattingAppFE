package com.chattingapp.ui.dashboard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.chattingapp.R
import com.chattingapp.databinding.ActivityDashboardBinding
import com.chattingapp.ui.dashboard.DashboardFragment
import com.chattingapp.ui.friendlist.FriendListFragment
import com.chattingapp.ui.settings.SettingsFragment

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up bottom navigation
        setupBottomNavigation()

        // Load default fragment (Dashboard)
        if (savedInstanceState == null) {
            loadFragment(DashboardFragment())
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.navigation_dashboard -> loadFragment(DashboardFragment())
                R.id.navigation_friends -> loadFragment(FriendListFragment())
                R.id.navigation_settings -> loadFragment(SettingsFragment())
            }
        }

        // Default selected tab
        if (binding.bottomNavigation.checkedRadioButtonId == -1) {
            binding.bottomNavigation.check(R.id.navigation_dashboard)
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}