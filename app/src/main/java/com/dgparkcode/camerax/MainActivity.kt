package com.dgparkcode.camerax

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dgparkcode.camerax.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 필요한 권한이 모두 승인된 경우 카메라 프리뷰를 시작합니다. 권한 승인이 안되어 있을 경우 권한을 요청합니다.
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        binding.takePhotoBtn.setOnClickListener { takePhoto() }
    }

    private fun allPermissionsGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this, "Permissions not granted by the user.", Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        // 카메라의 수명 주기를 바인딩합니다.
        // 수명 주기를 인식하므로 카메라를 열고 닫는 작업이 필요하지 않습니다.
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                // 프리뷰를 초기화 합니다.
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder().build()

                // 후면 카메라를 기본으로 설정합니다.
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    // 바인딩된 것이 있을 경우 해제하고, 프리뷰를 카메라 프로바이더에 바인드합니다.
                    // usecases 는 다중인자이므로 이미지캡쳐 객체도 바인드 합니다.
                    cameraProvider.unbindAll()

                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },

            // 메인 스레드에서 실행되는 Executor 입니다.
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun takePhoto() {
        // startCamera 에서 객체가 생성되지 않았다면 로직을 진행하지 않습니다.
        val imageCapture = imageCapture ?: return

        // 이미지 파일을 저장할때 사용할 정보를 설정합니다.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            // 안드로이드 9(P)보다 버전이 높을 경우 항목을 추가합니다.
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // 파일, 메타정보 출력 옵션입니다.
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${outputFileResults.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                }
            })
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10

        // 필수 권한 목록입니다.
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO
        ).apply {
            // 안드로이드 9(P) 이하일 경우 외부 저장소 쓰기 권한을 추가합니다.
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }
}