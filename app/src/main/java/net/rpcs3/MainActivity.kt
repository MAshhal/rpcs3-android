package net.rpcs3

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import net.rpcs3.ui.navigation.AppNavHost
import net.rpcs3.ui.theme.AppTheme

private const val ACTION_USB_PERMISSION = "net.rpcs3.USB_PERMISSION"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme(
                dynamicColor = false
            ) {
                AppNavHost()
            }
        }

        RPCS3.rootDirectory = applicationContext.getExternalFilesDir(null).toString()
        if (!RPCS3.rootDirectory.endsWith("/")) {
            RPCS3.rootDirectory += "/"
        }

        GameRepository.load()
        FirmwareRepository.load()

        Permission.PostNotifications.requestPermission(this)

        with(getSystemService(NOTIFICATION_SERVICE) as NotificationManager) {
            val channel = NotificationChannel(
                "rpcs3-progress",
                "Installation progress",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            deleteNotificationChannel("rpcs3-progress")
            createNotificationChannel(channel)
        }

        val mPermissionIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_MUTABLE or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT
            } else {
                0
            }
        )

        val usbManager = getSystemService(USB_SERVICE) as UsbManager

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

        RPCS3.instance.initialize(RPCS3.rootDirectory)

        val filter = IntentFilter()
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(ACTION_USB_PERMISSION)
        registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)

        for (usbDevice in usbManager.deviceList.values) {
            if (usbManager.hasPermission(usbDevice)) {
                UsbDeviceRepository.attach(usbDevice, usbManager)
            } else {
                usbManager.requestPermission(usbDevice, mPermissionIntent)
            }
        }
    }
}