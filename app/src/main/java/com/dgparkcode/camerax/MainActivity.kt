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
import androidx.camera.video.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.dgparkcode.camerax.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

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
        binding.videoCaptureBtn.setOnClickListener { captureVideo() }
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

                // 이미지 캡쳐 객체를 생성합니다.
                imageCapture = ImageCapture.Builder().build()

                // 레코더를 생성하고 비디오 캡쳐 객체를 생성합니다.
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)

                // 후면 카메라를 기본으로 설정합니다.
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    // 바인딩된 것이 있을 경우 해제하고, 프리뷰를 카메라 프로바이더에 바인드합니다.
                    // usecases 는 다중인자이므로 이미지캡쳐 객체도 바인드 합니다.
                    cameraProvider.unbindAll()

                    // 이미지 캡쳐에서 비디오 캡쳐로 변경합니다.
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
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
        val imageCapture = this.imageCapture ?: return

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

    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        // 캡쳐 버튼을 비활성화 합니다.
        binding.videoCaptureBtn.isEnabled = false

        // 녹화 중인 상태일 경우 멈추고 기존 객체를 제거합니다.
        val curRecording = recording
        if (curRecording != null) {
            curRecording.stop()
            recording = null
            return
        }

        // 동영상 저장시 필요한 정보를 설정합니다.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            // 안드로이드 9(P) 버전보다 높을 경우 설정 값을 추가합니다.
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        // 비디오 아웃풋 옵션을 설정합니다.
        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        // 레코딩 객체를 생성합니다.
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                // 오디오 권한이 승인된 경우에만 오디오를 추가합니다.
                if (PermissionChecker.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PermissionChecker.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        // 녹화가 시작되면 스톱으로 버튼 텍스트를 변경합니다.
                        binding.videoCaptureBtn.apply {
                            text = getString(R.string.main_stop_capture)
                            isEnabled = true
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            // 녹화가 끝나고 에러가 없다면 저장경로를 표시합니다.
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
                                .show()
                            Log.d(TAG, msg)
                        } else {
                            // 녹화중이라면 종료합니다.
                            // 에러가 있다면 에러 내용을 표시합니다.
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: " +
                                    "${recordEvent.error}")
                        }
                        // 버튼을 초기상태로 변경합니다.
                        binding.videoCaptureBtn.apply {
                            text = getString(R.string.main_start_capture)
                            isEnabled = true
                        }
                    }
                }
            }
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