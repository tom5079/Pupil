/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2020  tom5079
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

@file:Suppress("DEPRECATION")

package xyz.quaver.pupil.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.Camera
import android.view.Surface
import android.view.WindowManager
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/** Check if this device has a camera */
private fun Context.checkCameraHardware() =
    this.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)

private fun openFrontCamera() : Pair<Camera?, Int> {
    var camera: Camera? = null
    var cameraID: Int = -1

    val cameraInfo = Camera.CameraInfo()

    for (i in 0 until Camera.getNumberOfCameras()) {
        Camera.getCameraInfo(i, cameraInfo)
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
            runCatching { Camera.open(i) }.getOrNull()?.let { camera = it; cameraID = i }

        if (camera != null) break
    }

    return Pair(camera, cameraID)
}

val orientations = mapOf(
    Surface.ROTATION_0 to 0,
    Surface.ROTATION_90 to 90,
    Surface.ROTATION_180 to 180,
    Surface.ROTATION_270 to 270,
)

private fun getRotation(context: Context, cameraID: Int): Int {
    val cameraRotation = Camera.CameraInfo().also { Camera.getCameraInfo(cameraID, it) }.orientation
    val rotation = orientations[(context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation] ?: error("")

    return (cameraRotation + rotation) % 360
}

var camera: Camera? = null
private val detector = FaceDetection.getClient(
    FaceDetectorOptions.Builder()
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()
)
private var process: Task<List<Face>>? = null

fun testCamera(context: Context, callback: (List<Face>) -> Unit) {
    if (camera != null) closeCamera()

    val cameraID = openFrontCamera().let { (cam, cameraID) ->
        cam ?: return
        camera = cam
        cameraID
    }

    with (camera!!) {
        parameters = parameters.apply {
            setPreviewSize(640, 480)
            previewFormat = ImageFormat.NV21
            flashMode = Camera.Parameters.FLASH_MODE_OFF
        }
        setPreviewCallback { bytes, camera ->
            if (process?.isComplete == false)
                return@setPreviewCallback

            val rotation = getRotation(context, cameraID)

            val image = InputImage.fromByteArray(bytes, 640, 480, rotation, InputImage.IMAGE_FORMAT_NV21)
            process = detector.process(image)
                .addOnSuccessListener(callback)
        }

        startPreview()
    }
}

fun closeCamera() {
    camera?.setPreviewCallback(null)
    camera?.stopPreview()
    camera?.release()
    camera = null
}