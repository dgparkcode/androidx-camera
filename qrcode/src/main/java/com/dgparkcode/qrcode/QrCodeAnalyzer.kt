package com.dgparkcode.qrcode

import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

// 이미지 형식이 YUV 이므로 첫번째 값은 Y 입니다.
private fun ByteBuffer.toByteArray(): ByteArray {
    rewind()
    val data = ByteArray(remaining())
    get(data)
    return data
}

class QrCodeAnalyzer constructor(
    private val onQrCodesDetected: (qrCode: Result) -> Unit
): ImageAnalysis.Analyzer {

    private val yuvFormats = mutableListOf(ImageFormat.YUV_420_888)

    // 이미지를 디코딩하고 QR 을 읽으려면 Reader 가 필요하며 MultiFormatReader 를 사용합니다.
    // 기본적으로 바코드 형식 읽기를 지원하며, hint 를 통해 디코딩할 형식을 설정할 수 있습니다.
    private val reader = MultiFormatReader().apply {
        val map = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to arrayListOf(BarcodeFormat.QR_CODE)
        )
        setHints(map)
    }

    init {
        // 안드로이드 23(M) 버전보다 높을 경우 추가합니다.
        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        //    yuvFormats.addAll(listOf(ImageFormat.YUV_422_888, ImageFormat.YUV_444_888))
        //}
        yuvFormats.addAll(listOf(ImageFormat.YUV_422_888, ImageFormat.YUV_444_888))
    }

    override fun analyze(image: ImageProxy) {
        // 내부적으로 ImageProxy 는 ImageReader 를 사용하여 이미지를 가져오고,
        // ImageReader 가 변경되지 않는 한 YUV 형식을 사용합니다.
        // https://developer.android.com/reference/androidx/camera/core/ImageProxy.html#getImage()
        // https://developer.android.com/reference/android/media/Image.html#getFormat()
        if (image.format !in yuvFormats) {
            Log.e(TAG, "Expected YUV, now = ${image.format}")
            return
        }

        val data = image.planes[0].buffer.toByteArray()

        // 그런 다음 데이터를 사용하여 PlanarYUVLuminanceSource 를 생성합니다.
        // 기본적으로 Y 채널이 평면이고 먼저 나타나는 모든 픽셀 형식에 대한 그레이스케일 휘도 값을 제공합니다.
        val source = PlanarYUVLuminanceSource(
            data,
            image.width,
            image.height,
            0,
            0,
            image.width,
            image.height,
            false
        )

        // 이 PlanarYUVLuminanceSource 를 가져와서 Reader 가 QR 코드를 해독할 수 있도록 BinaryBitmap 을 구성하는 것입니다.
        // BinaryBitmap 을 구성하려면 휘도 데이터를 가져와 1비트 데이터로 변환하는 Binarizer 를 전달해야 합니다.
        // 우리는 HybridBinarizer 를 사용하고 있습니다(zxing 에서 권장).
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        try {
            // QR 코드를 감지 못할시 NotFoundException 에러를 발생시킵니다.
            val result = reader.decode(binaryBitmap)
            onQrCodesDetected(result)
        } catch (e: NotFoundException) {
            e.printStackTrace()
        }
        image.close()
    }

    companion object {
        private const val TAG = "QrCodeAnalyzer"
    }
}