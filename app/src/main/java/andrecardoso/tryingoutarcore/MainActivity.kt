package andrecardoso.tryingoutarcore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.annotation.StringRes
import android.util.Log
import android.widget.Toast
import com.google.ar.core.Config
import com.google.ar.core.Session

class MainActivity : AppCompatActivity() {

    private companion object {
        val TAG = "MainActivity"
        val CAMERA_PERMISSION_CODE = 0
    }

    lateinit var session: Session
    lateinit var config: Config

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupARCore()
    }

    override fun onResume() {
        super.onResume()

        if (hasCameraPermission()) {
            Log.d(TAG, "Resume ARCore session")
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        if (!hasCameraPermission()) {
            showToast(R.string.camera_permission_needed)
            finish()
        }
    }

    private fun setupARCore() {
        config = Config.createDefaultConfig()
        session = Session(this)

        if (!session.isSupported(config)) {
            showToast(R.string.ar_device_not_supported)
            finish()
        }
    }
}

private fun Context.hasCameraPermission() =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.showToast(@StringRes stringRes: Int) =
    Toast.makeText(this, getString(stringRes), Toast.LENGTH_LONG).show()