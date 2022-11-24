package com.dgparkcode.camerax

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dgparkcode.camerax.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var imageAnalysis: ImageAnalysis? = null

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

        binding.reScanBtn.setOnClickListener { startCamera() }
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

                imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(binding.previewView.width, binding.previewView.height))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(
                            ContextCompat.getMainExecutor(this),
                            QrCodeAnalyzer { qrResult ->
                                Log.d(TAG, "Barcode scanned: ${qrResult.text}")
                                // QR 코드 스캔에 성공하면 프리뷰 바인드를 해제해서 프리뷰를 멈추고,
                                // 버튼을 활성화해서 다시 시작할 수 있는 기능을 제공한다.
                                cameraProvider.unbind(preview)
                                binding.reScanBtn.isEnabled = true
                            }
                        )
                    }

                // 후면 카메라를 기본으로 설정합니다.
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    // 바인딩된 것이 있을 경우 해제하고, 프리뷰를 카메라 프로바이더에 바인드합니다.
                    // usecases 는 다중인자이므로 이미지캡쳐 객체도 바인드 합니다.
                    cameraProvider.unbindAll()

                    // 이미지 캡쳐와 비디오 캡쳐를 모두 추가합니다.
                    cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )

                    // 카메라 기능이 시작되면 버튼을 비활성화 합니다.
                    binding.reScanBtn.isEnabled = false

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },

            // 메인 스레드에서 실행되는 Executor 입니다.
            ContextCompat.getMainExecutor(this)
        )
    }

    companion object {
        private const val TAG = "MainActivity"
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