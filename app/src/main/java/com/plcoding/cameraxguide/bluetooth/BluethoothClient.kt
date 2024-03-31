package com.plcoding.cameraxguide.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class BluetoothClient(private val deviceAddress: String, private val context: Context) {
    private var bluetoothSocket: BluetoothSocket? = null
    private val uuid="b22ab232-47c3-499d-acb5-85dcb714dd32" // This must match the server's UUID
    private var listenThread: Thread? = null
    companion object {
        private const val REQUEST_BLUETOOTH_CONNECT = 101
    }

    @SuppressLint("MissingPermission", "SdCardPath")
    @RequiresApi(Build.VERSION_CODES.S)
    fun connect() {
        Log.d("BluetoothClient", "StartConnecting");
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                // Create a socket with the UUID
                bluetoothSocket = device?.createRfcommSocketToServiceRecord(UUID.fromString(uuid))

                // Cancel discovery because it otherwise slows down the connection
                bluetoothAdapter?.cancelDiscovery()

                // Connect to the remote device through the socket. This call blocks until it succeeds or throws an exception

                Log.d("BluetoothClient", "Connecting");
                bluetoothSocket?.connect()
                Log.d("BluetoothClient", "Connected");

                // At this point, you can start sending or receiving data from the server
//                bluetoothSocket?.outputStream?.write("Hello, server!".toByteArray())
                //val photoPath = "/data/data/com.plcoding.cameraxguide/files/asl_alphabet_test/K_test.jpg"
                //val fileSize = getFileSize(photoPath)
                //Log.d("BluetoothClient","$fileSize")
                //sendPhoto(photoPath, fileSize)
                /*startListeningForData { message ->
                    // Handle received message, update UI, etc.
                    Log.d("BluetoothClient", "Received response: $message")
                }*/

            } catch (e: IOException) {
                // Unable to connect; close the socket and return
                try {
                    Log.d("BluetoothClient", "IOException"+e.toString());

                    bluetoothSocket?.close()
                } catch (closeException: IOException) {
                    // Could not close the client socket
                    Log.d("BluetoothClient", "Cannot close client socket");
                }
            }
        }
    }

    // Don't forget to close the socket when done
    fun cancel() {
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            // Could not close the connection
            Log.d("BluetoothClient", "Cannot close client socket");
        }
    }

    fun sendPhoto(photoPath: String, fileSize: Long) {
        val imageBitmap = BitmapFactory.decodeFile(photoPath)
        val byteArrayOutputStream = ByteArrayOutputStream()
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        Log.d("BluetoothClient", "Flushing byte array size: ${byteArray.size}");
        try {
            val outputStream = bluetoothSocket?.outputStream
            outputStream?.write(byteArray)
            outputStream?.write("\n".toByteArray())
            outputStream?.flush()
            Log.d("BluetoothClient", "photo is sent")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun sendVideo(videoPath: String) {
        val videoFile = File(videoPath)
        val fileInputStream = FileInputStream(videoFile)
        val buffer = ByteArray(1024) // Buffer for chunks of the file
        var bytesRead: Int

        try {
            val outputStream = bluetoothSocket?.outputStream
            if (outputStream == null) {
                Log.e("BluetoothClient", "Socket is not connected.")
                return
            }

            // Read the video file in chunks and write those to the output stream
            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }

            outputStream.flush() // Ensure all data is sent out
            Log.d("BluetoothClient", "Video successfully sent.")
        } catch (e: IOException) {
            Log.e("BluetoothClient", "Error sending video: ${e.message}")
        } finally {
            try {
                fileInputStream.close() // Close file input stream
            } catch (e: IOException) {
                Log.e("BluetoothClient", "Failed to close file input stream: ${e.message}")
            }
            // Consider whether to close the Bluetooth socket here or keep it open for further operations
        }
    }

    fun startListeningForData(onMessageReceived: (String) -> Unit) {
        listenThread = Thread {
            try {
                val buffer = ByteArray(1024)  // Buffer store for the stream
                var bytes: Int  // Bytes returned from read()

                while (true) {
                    try {
                        val inputStream = bluetoothSocket?.inputStream
                        if (inputStream != null && inputStream.available() > 0) {
                            bytes = inputStream.read(buffer)
                            val incomingMessage = String(buffer, 0, bytes)
                            onMessageReceived(incomingMessage)  // Update shared state with the received message
                        }
                    } catch (e: IOException) {
                        Log.e("BluetoothClient", "Input stream was disconnected", e)
                        break
                    }
                }
            } catch (e: IOException) {
                Log.e("BluetoothClient", "Error in data listening thread", e)
            }
        }
        listenThread?.start()
    }

/*    fun sendPhoto(photoPath: String, fileSize: Long) {
//        val photoFile = File(photoPath) // Create a File object for the photo
//        val buffer = ByteArray(1000000) // Adjust if you want more or less buffering
//        val dataSizeBuffer = ByteBuffer.allocate(8)
//        dataSizeBuffer.order(ByteOrder.BIG_ENDIAN)
//        dataSizeBuffer.putLong(fileSize)
//        dataSizeBuffer.flip()
//        val byteArray = ByteArray(dataSizeBuffer.remaining())
//        dataSizeBuffer.get(byteArray)
        val imageBitmap = BitmapFactory.decodeFile(photoPath)
        val byteArrayOutputStream = ByteArrayOutputStream()
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        Log.d("BluetoothClient", "Flushing byte array size: ${byteArray.size}");

        try {
            val outputStream = bluetoothSocket?.outputStream
            outputStream?.write(byteArray)
            outputStream?.write("\n".toByteArray())
            outputStream?.flush()
        } catch (e: IOException) {
            e.printStackTrace()
        }

//        var bytes: Int
//        val outputStream = bluetoothSocket?.outputStream

//        outputStream?.write(byteArray)
//        outputStream?.flush()
//        Log.d("BluetoothClient", "Flushing data size buffer, byte array size: ${byteArray.size}");

//        FileInputStream(photoFile).use { fileInputStream ->
//            while (fileInputStream.read(buffer).also { bytes = it } > 0) {
//                try {
//                    outputStream?.write(buffer, 0, bytes)
//                } catch (e: IOException) {
//                    e.printStackTrace()
//                    // Handle exceptions, possibly close socket
//                    break
//                }
//            }
//            outputStream?.flush() // Important: Ensure all data is sent before closing
//        }
        // You might want to send a "transfer finished" marker here if your protocol needs it
        Log.d("BluetoothClient","Photo is sent")
    }*/

}
