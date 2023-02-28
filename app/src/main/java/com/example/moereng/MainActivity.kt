package com.example.moereng

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.moereng.databinding.ActivityMainBinding
import com.example.moereng.fragments.TTSViewModel
import com.example.moereng.fragments.VCViewModel
import com.example.moereng.utils.VitsUtils.checkThreadsCpp
import com.example.moereng.utils.VitsUtils.testGpu
import com.google.android.material.bottomnavigation.BottomNavigationView


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val vcViewModel by lazy {
        ViewModelProvider(this).get(VCViewModel::class.java)
    }

    private val ttsViewModel by lazy {
        ViewModelProvider(this).get(TTSViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // test gpu
        val gpuState = testGpu()
        ttsViewModel.setVulkanState(gpuState)
        vcViewModel.setVulkanState(gpuState)

        // cpu counts
        val maxThreads = checkThreadsCpp()
        ttsViewModel.setMaxThreads(maxThreads)
        vcViewModel.setMaxThreads(maxThreads)

        val navView: BottomNavigationView = binding.navView

        // init bottom view navigation
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_tts, R.id.navigation_vc
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        val menu = navView.menu
        val vcNav = menu.getItem(1) // vc
        val ttsNav = menu.getItem(0)

        // observe tts
        ttsViewModel.generationFinish.observe(this) { finished ->
            vcNav.isVisible = finished
            ttsNav.isEnabled = finished
        }

        // observe vc
        vcViewModel.convertFinish.observe(this) { finished ->
            ttsNav.isVisible = finished
            vcNav.isEnabled = finished
        }
    }
}
