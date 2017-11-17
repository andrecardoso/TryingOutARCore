package andrecardoso.tryingoutarcore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    private companion object {
        val TAG = "MainActivity"
        val CAMERA_PERMISSION_CODE = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
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
            Toast.makeText(this, getString(R.string.camera_permission_needed), Toast.LENGTH_LONG).show()
            finish()
        }
    }
}


private fun Context.hasCameraPermission() =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED