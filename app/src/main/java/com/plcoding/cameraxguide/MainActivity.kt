@file:OptIn(ExperimentalMaterial3Api::class)

package com.plcoding.cameraxguide

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdUnits
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.plcoding.cameraxguide.bluetooth.BluetoothClient
import com.plcoding.cameraxguide.ui.theme.CameraXGuideTheme
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private var recording: Recording? = null
    private lateinit var bluetoothClient: BluetoothClient
    private val PERMISSIONS_REQUEST_CODE = 10
    private var photoFileName = ""
    private val message = ""

    companion object {
        private val CAMERAX_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        )
        private const val REQUEST_ENABLE_BT = 1
        private val MY_UUID = UUID.fromString("b22ab232-47c3-499d-acb5-85dcb714dd32") // replace with your actual UUID
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                // Bluetooth has been enabled, proceed with your Bluetooth operation
            } else {
                // User denied to enable Bluetooth, handle this case
                Toast.makeText(this, "Bluetooth must be enabled to continue", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val allPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                // Camera and audio permissions
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                // Bluetooth permissions for API 31+
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                // Camera and audio permissions
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                // Bluetooth permissions for API 30 and below
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        return allPermissions.all { permission ->
            ContextCompat.checkSelfPermission(applicationContext, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Add Location permission for Bluetooth for API 30 and below
        Log.d("BluetoothClient", "Requesting bluetooth permission")
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)

        // Add Camera and audio permissions
        permissionsToRequest.add(Manifest.permission.CAMERA)
        permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)

        ActivityCompat.requestPermissions(
            this,
            permissionsToRequest.toTypedArray(),
            PERMISSIONS_REQUEST_CODE
        )
    }

    @SuppressLint("SdCardPath")
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //INMO air2
        //address = "0C:86:29:C5:18:92"
//        val deviceAddress = "A0:4F:85:41:21:52"
//        val deviceAddress = "52:21:41:85:4F:A0"
//        val deviceAddress = "A0:4F:85:BA:BD:27"
//       val deviceAddress = "27:BD:BA:85:4F:A0"
        val deviceAddress = getPairedDevices()[0];


        // Inside MainActivity
        bluetoothClient = BluetoothClient(deviceAddress, this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Permissions are granted, start the Bluetooth connection on a background thread
            Log.d("BluetoothClient", "Permission Granted");
            connectBluetooth()
        } else {
            // Request necessary permissions
            Log.d("BluetoothClient", "Requesting permission");
            requestPermissions()
            connectBluetooth()
        }
        setContent {
            CameraXGuideTheme {
                val scope = rememberCoroutineScope()
                val scaffoldState = rememberBottomSheetScaffoldState()
                val controller = remember {
                    LifecycleCameraController(applicationContext).apply {
                        setEnabledUseCases(
                            CameraController.IMAGE_CAPTURE or
                                    CameraController.VIDEO_CAPTURE
                        )
                    }
                }
                val viewModel = viewModel<MainViewModel>()
                val bitmaps by viewModel.bitmaps.collectAsState()

                BottomSheetScaffold(
                    scaffoldState = scaffoldState,
                    sheetPeekHeight = 0.dp,
                    sheetContent = {
                        PhotoBottomSheetContent(
                            bitmaps = bitmaps,
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        CameraPreview(
                            controller = controller,
                            modifier = Modifier
                                .fillMaxSize()
                        )
                        IconButton(
                            onClick = {
                                controller.cameraSelector =
                                    if (controller.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                                        CameraSelector.DEFAULT_FRONT_CAMERA
                                    } else CameraSelector.DEFAULT_BACK_CAMERA
                            },
                            modifier = Modifier
                                .offset(16.dp, 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Switch camera"
                            )
                        }
                        bluetoothMessageScreen(bluetoothClient)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        scaffoldState.bottomSheetState.expand()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Photo,
                                    contentDescription = "Open gallery"
                                )
                            }
                            IconButton(
                                onClick = {
                                    photoFileName = updateTime()
                                    takePhoto(
                                        controller = controller,
                                        onPhotoTaken = viewModel::onTakePhoto,
                                        photoFileName
                                    )
                                    Log.d("BluetoothClient", photoFileName);

                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Take photo"
                                )
                            }
                            IconButton(
                                onClick = {
                                    sendPhoto(photoFileName)
                                    Log.d("BluetoothClient", photoFileName);
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AdUnits,
                                    contentDescription = "Send Photo"
                                )
                            }
                            IconButton(
                                onClick = {
                                    photoFileName = updateTime()
                                    recordVideo(controller,
                                        photoFileName)
                                    Log.d("BluetoothClient", photoFileName);
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = "Record video"
                                )
                            }
                            IconButton(
                                onClick = {
                                    sendVideo(photoFileName)
                                    Log.d("BluetoothClient",photoFileName);
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Send Video"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    @SuppressLint("SdCardPath")
    @RequiresApi(Build.VERSION_CODES.S)
    private fun connectBluetooth() {
        Thread {
            bluetoothClient.connect()
        }.start()
    }

    private fun updateTime(): String{
        val photoFileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return photoFileName
    }

    private fun updateMessage(): String{
        var newMessage = ""
        bluetoothClient.startListeningForData { message ->
            newMessage = "$message"
            Log.d("BluetoothClient", "Received response: $message")
        }
        return newMessage
    }

    private fun getFileSize(filePath: String): Long {
        val file = File(filePath)
        if (!file.exists()) {
            throw FileNotFoundException("File not found: $filePath")
        }
        return file.length() // The size of the file in bytes
    }

    private fun sendPhoto(photoName: String){
        val photoPath = "/data/data/com.plcoding.cameraxguide/files/$photoName.jpg"
        if((File(photoPath)).exists()) {
            val fileSize = getFileSize(photoPath)
            Log.d("BluetoothClient","$fileSize")
            bluetoothClient.sendPhoto(photoPath, fileSize)
        }
        else {
            Log.d("BluetoothClient", "File not found")
        }
    }
    private fun sendVideo(photoName: String){
        val videoPath = "/data/data/com.plcoding.cameraxguide/files/$photoName.mp4"
        if((File(videoPath)).exists()){
            Log.d("BluetoothClient","Sending video")
            bluetoothClient.sendVideo(videoPath)
        }else{
            Log.d("BluetoothClient", "Video file is not found")
        }
    }

    @Composable
    private fun bluetoothMessageScreen(bluetoothClient: BluetoothClient) {
        // This state will hold the latest message
        val latestMessage = remember { mutableStateOf("No message yet") }

        // Use LaunchedEffect to start listening when the composable enters the composition
        LaunchedEffect(key1 = true) {
            bluetoothClient.startListeningForData { message ->
                // Update state with the new message
                latestMessage.value = message
                Log.d("BluetoothClient","$latestMessage.value")
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Result: ${latestMessage.value}",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(70.dp),
                fontSize = 20.sp,
                color = Color.Black,
                fontWeight = FontWeight.Bold // Make text bold
            )
        }
    }

    @SuppressLint("SdCardPath")
    private fun takePhoto(
        controller: LifecycleCameraController,
        onPhotoTaken: (Bitmap) -> Unit,
        photoFileName: String
    ) {
        if (!hasRequiredPermissions()) {
            return
        }
        val photoName = "$photoFileName.jpg"
        val photoFile = File(filesDir, photoName)

        controller.takePicture(
            ContextCompat.getMainExecutor(applicationContext),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    super.onCaptureSuccess(image)
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)

                    val matrix = Matrix().apply {
                        postRotate(image.imageInfo.rotationDegrees.toFloat())
                    }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    val rotatedBitmap = Bitmap.createBitmap(
                        bitmap,
                        0,
                        0,
                        bitmap.width,
                        bitmap.height,
                        matrix,
                        true
                    )

                    try {
                        FileOutputStream(photoFile).use { out ->
                            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                            // Invoke the callback with the rotatedBitmap after saving
                            onPhotoTaken(rotatedBitmap)
                        }
                    } catch (e: IOException) {
                        Log.e("Camera", "Error saving photo: ", e)
                    } finally {
                        // Make sure to close the image
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    super.onError(exception)
                    Log.e("Camera", "Couldn't take photo: ", exception)
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    private fun recordVideo(controller: LifecycleCameraController,
                            photoFileName: String) {
        if(recording != null) {
            recording?.stop()
            recording = null
            return
        }

        if(!hasRequiredPermissions()) {
            return
        }
        val fileName = photoFileName + ".mp4"
        val outputFile = File(filesDir, fileName)
        recording = controller.startRecording(
            FileOutputOptions.Builder(outputFile).build(),
            AudioConfig.create(true),
            ContextCompat.getMainExecutor(applicationContext),
        ) { event ->
            when(event) {
                is VideoRecordEvent.Finalize -> {
                    if(event.hasError()) {
                        recording?.close()
                        recording = null

                        Toast.makeText(
                            applicationContext,
                            "Video capture failed",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            applicationContext,
                            "Video capture succeeded",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getPairedDevices(): List<String> {
        val pairedDeviceAddresses = mutableListOf<String>()
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            Log.d("BluetoothHelper", "Bluetooth is not supported on this device.")
        } else if (!bluetoothAdapter.isEnabled) {
            Log.d("BluetoothHelper", "Bluetooth is not enabled.")
        } else {
            val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
            if (pairedDevices.isNotEmpty()) {
                for (device in pairedDevices) {
                    val deviceName = device.name
                    val deviceHardwareAddress = device.address // MAC address
                    pairedDeviceAddresses.add(deviceHardwareAddress)
                    Log.d("BluetoothHelper", "Paired device: $deviceName, Address: $deviceHardwareAddress")
                }
            } else {
                Log.d("BluetoothHelper", "No paired devices found.")
            }
        }
        return pairedDeviceAddresses
    }
}