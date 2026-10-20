package com.totsiaw.nr5g

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.totsiaw.nr5g.ui.AppRoot
import com.totsiaw.nr5g.ui.AppTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val phone = result[Manifest.permission.READ_PHONE_STATE] == true ||
            hasPermission(Manifest.permission.READ_PHONE_STATE)
        vm.onPermissionsResult(phone)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensurePermissions()
        setContent {
            AppTheme {
                AppRoot(vm = vm, onRequestPermissions = { ensurePermissions() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    private fun ensurePermissions() {
        val needed = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ).filterNot { hasPermission(it) }

        if (needed.isEmpty()) {
            vm.onPermissionsResult(true)
        } else {
            permLauncher.launch(needed.toTypedArray())
        }
    }

    private fun hasPermission(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
}
