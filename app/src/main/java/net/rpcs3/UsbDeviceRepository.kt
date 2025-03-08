package net.rpcs3

import android.app.Activity.USB_SERVICE
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build

private const val ACTION_USB_PERMISSION = "net.rpcs3.USB_PERMISSION"

fun listenUsbEvents(context: Context) {
    val mPermissionIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(ACTION_USB_PERMISSION),
        PendingIntent.FLAG_MUTABLE or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT
        } else {
            0
        }
    )
    val usbManager = context.getSystemService(USB_SERVICE) as UsbManager

    val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                if (device != null) {
                    UsbDeviceRepository.detach(device)
                }

                return
            }

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == intent.action) {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                if (device != null) {
                    if (usbManager.hasPermission(device)) {
                        UsbDeviceRepository.attach(device, usbManager)
                    } else {
                        usbManager.requestPermission(device, mPermissionIntent)
                    }
                }

                return
            }

            if (ACTION_USB_PERMISSION == intent.action) {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

                if (device != null && intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED,
                        false
                    )
                ) {
                    if (usbManager.hasPermission(device)) {
                        UsbDeviceRepository.attach(device, usbManager)
                    }
                }
            }
        }
    }

    val filter = IntentFilter()
    filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
    filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
    filter.addAction(ACTION_USB_PERMISSION)
    context.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)

    for (usbDevice in usbManager.deviceList.values) {
        if (usbManager.hasPermission(usbDevice)) {
            UsbDeviceRepository.attach(usbDevice, usbManager)
        } else {
            usbManager.requestPermission(usbDevice, mPermissionIntent)
        }
    }
}

class UsbDeviceRepository {
    companion object {
        private val devices = HashMap<UsbDevice, UsbDeviceConnection>()

        fun attach(device: UsbDevice, usbManager: UsbManager) {
            if (devices[device] != null) {
                return
            }

            val connection = usbManager.openDevice(device)
            devices[device] = connection
            RPCS3.instance.usbDeviceEvent(
                connection.fileDescriptor,
                device.vendorId,
                device.productId,
                0
            )
        }

        fun detach(device: UsbDevice) {
            val connection = devices[device]
            if (connection != null) {
                RPCS3.instance.usbDeviceEvent(connection.fileDescriptor, -1, -1, 1)
                connection.close()

                devices.remove(device)
            }
        }
    }
}