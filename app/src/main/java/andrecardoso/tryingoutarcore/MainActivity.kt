package andrecardoso.tryingoutarcore

import andrecardoso.tryingoutarcore.rendering.BackgroundRenderer
import andrecardoso.tryingoutarcore.rendering.ObjectRenderer
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.annotation.StringRes
import android.support.design.widget.Snackbar
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import kotlinx.android.synthetic.main.activity_main.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainActivity : AppCompatActivity(), GLSurfaceView.Renderer {

    private companion object {
        val TAG = "MainActivity"
        val CAMERA_PERMISSION_CODE = 0
        val ARCORE_MATRIX_SIZE = 16
    }

    private val backgroundRenderer = BackgroundRenderer()
    private val androidRenderer = ObjectRenderer()
    private val androidShadowRenderer = ObjectRenderer()

    private val viewMatrix = FloatArray(ARCORE_MATRIX_SIZE)
    private val projectionMatrix = FloatArray(ARCORE_MATRIX_SIZE)
    private val androidAnchorMatrix = FloatArray(ARCORE_MATRIX_SIZE)

    private val planeAttachments = mutableListOf<PlaneAttachment>()

    private var detectingPlanesSnackbar: Snackbar? = null

    lateinit var session: Session
    lateinit var config: Config

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupSurfaceView()
        setupARCore()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setFullScreen()
        }
    }

    override fun onPause() {
        super.onPause()
        // Note that the order matters - GLSurfaceView is paused first so that it does not try
        // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
        // still call session.update() and get a SessionPausedException.
        surfaceView.onPause()
        session.pause()
    }

    override fun onResume() {
        super.onResume()

        if (hasCameraPermission()) {
            showLoadingMessage()

            Log.d(TAG, "Resume ARCore session")
            // Note that order matters - see the note in onPause(), the reverse applies here.
            surfaceView.onResume()
            session.resume(config)
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

    override fun onDrawFrame(gl: GL10?) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)


        try {
            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            val frame = session.update()
            backgroundRenderer.draw(frame)

            // Get projection matrix.
            session.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)

            // Get camera matrix and draw.
            frame.getViewMatrix(viewMatrix, 0)

            if (session.trackedPlanes.isNotEmpty()) {
                Log.d(TAG, "${session.trackedPlanes.size} planes detected")
                hideLoadingMessage()

                if (session.trackedPlanes.size > planeAttachments.size) {
                    session.removeAnchors(planeAttachments.map { it.anchor })
                    session.trackedPlanes.forEach {
                        val anchor = session.addAnchor(it.centerPose)
                        planeAttachments.add(PlaneAttachment(it, anchor))
                    }
                }
            }

            planeAttachments.forEach {
                if (it.isTracking) {
                    drawAndroid(it.pose, frame.lightEstimate.pixelIntensity)
                }
            }
        } catch (t: Throwable) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t)
        }
    }

    private fun drawAndroid(pose: Pose, lightIntensity: Float) {
        pose.toMatrix(androidAnchorMatrix, 0)
        val scaleFactor = 1.0f
        // Update and draw the model and its shadow.
        androidRenderer.updateModelMatrix(androidAnchorMatrix, scaleFactor)
        androidShadowRenderer.updateModelMatrix(androidAnchorMatrix, scaleFactor)
        androidRenderer.draw(viewMatrix, projectionMatrix, lightIntensity)
        androidShadowRenderer.draw(viewMatrix, projectionMatrix, lightIntensity)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        session.setDisplayGeometry(width.toFloat(), height.toFloat())
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

        // Create the texture and pass it to ARCore session to be filled during update().
        backgroundRenderer.createOnGlThread(this)
        session.setCameraTextureName(backgroundRenderer.textureId)

        androidRenderer.createOnGlThread(this, "andy.obj", "andy.png")
        androidRenderer.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f)

        androidShadowRenderer.createOnGlThread(this,"andy_shadow.obj", "andy_shadow.png")
        androidShadowRenderer.setBlendMode(ObjectRenderer.BlendMode.Shadow)
        androidShadowRenderer.setMaterialProperties(1.0f, 0.0f, 0.0f, 1.0f)
    }

    private fun setupSurfaceView() {
        surfaceView.preserveEGLContextOnPause = true
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0) // Alpha used for plane blending.
        surfaceView.setRenderer(this)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    private fun setupARCore() {
        config = Config.createDefaultConfig()
        session = Session(this)

        if (!session.isSupported(config)) {
            showToast(R.string.ar_device_not_supported)
            finish()
        }
    }

    private fun showLoadingMessage() {
        runOnUiThread {
            detectingPlanesSnackbar = Snackbar.make(findViewById(android.R.id.content), R.string.detecting_planes, Snackbar.LENGTH_INDEFINITE)
            detectingPlanesSnackbar?.view?.alpha = 0.5f
            detectingPlanesSnackbar?.show()
        }
    }

    private fun hideLoadingMessage() {
        detectingPlanesSnackbar?.let {
            runOnUiThread { it.dismiss() }
            detectingPlanesSnackbar = null
        }
    }
}

private val Session.trackedPlanes: Collection<Plane>
    get() = allPlanes.filter { it.trackingState == Plane.TrackingState.TRACKING && it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }

private fun Context.hasCameraPermission() =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.showToast(@StringRes stringRes: Int) =
    Toast.makeText(this, getString(stringRes), Toast.LENGTH_LONG).show()

private fun Activity.setFullScreen() {
    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}