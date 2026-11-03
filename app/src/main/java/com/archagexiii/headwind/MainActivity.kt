package com.archagexiii.headwind

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.archagexiii.headwind.fan.Connection
import com.archagexiii.headwind.fan.HeadwindController
import com.archagexiii.headwind.ui.HeadwindApp
import com.archagexiii.headwind.ui.theme.HeadwindTheme

class MainActivity : ComponentActivity() {
    private lateinit var controller: HeadwindController
    private var requestedPermissions = false
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        controller.start()
    }
    private val enableBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        controller.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        controller = HeadwindController(this)
        requestedPermissions = savedInstanceState?.getBoolean("permissionRequested") ?: false
        setContent {
            HeadwindTheme(darkTheme = true) {
                HeadwindApp(
                    state = controller.state,
                    onIncrease = { act { controller.increase() } },
                    onDecrease = { act { controller.decrease() } },
                    onOff = { act { controller.off() } },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        controller.start()
        if (!controller.hasPermissions() && !requestedPermissions) requestBluetoothPermission()
    }

    override fun onStop() {
        controller.stop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("permissionRequested", requestedPermissions)
        super.onSaveInstanceState(outState)
    }

    private fun act(action: () -> Unit) {
        when (controller.state.connection) {
            Connection.PERMISSION -> {
                val deniedPermanently = requestedPermissions && HeadwindController.requiredPermissions().none {
                    shouldShowRequestPermissionRationale(it)
                }
                if (deniedPermanently) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                } else requestBluetoothPermission()
            }
            Connection.BLUETOOTH_OFF -> enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            Connection.LOCATION_OFF -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            Connection.READY -> action()
            else -> controller.retryNow()
        }
    }

    private fun requestBluetoothPermission() {
        requestedPermissions = true
        permissions.launch(HeadwindController.requiredPermissions())
    }
}
