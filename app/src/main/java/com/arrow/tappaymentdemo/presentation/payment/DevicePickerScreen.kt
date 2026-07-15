package com.arrow.tappaymentdemo.presentation.payment

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.arrow.tappaymentdemo.domain.model.ReaderConnectionState
import com.arrow.tappaymentdemo.domain.model.ReaderDevice
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePickerScreen(
    viewModel: PaymentViewModel = koinViewModel(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val readerConnectionState by viewModel.readerConnectionState.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    var prerequisitesMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = requiredBlePermissions().all { permission ->
            results[permission] == true || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            prerequisitesMessage = null
            startScanWithPrerequisites(
                context = context,
                onMissingPermission = {
                    prerequisitesMessage = it
                },
                onBluetoothEnableRequired = {
                    prerequisitesMessage = "Bluetooth is off. Turn it on to discover devices."
                },
                onStartScan = {
                    viewModel.startDeviceDiscovery()
                }
            )
        } else {
            prerequisitesMessage = "Nearby devices permission is required to discover MagTek readers."
        }
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (isBluetoothEnabled(context)) {
            prerequisitesMessage = null
            viewModel.startDeviceDiscovery()
        } else {
            prerequisitesMessage = "Bluetooth remains disabled. Enable it and try scanning again."
        }
    }

    val openAppSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // No-op; returning from settings keeps user on this screen.
    }

    fun requestScan() {
        val missing = requiredBlePermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        when {
            missing.isNotEmpty() -> {
                prerequisitesMessage = "Grant Nearby devices permission to scan for readers."
                permissionLauncher.launch(missing.toTypedArray())
            }
            !isBluetoothEnabled(context) -> {
                prerequisitesMessage = "Bluetooth is off. Turn it on to discover devices."
                enableBluetoothLauncher.launch(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
            else -> {
                prerequisitesMessage = null
                viewModel.startDeviceDiscovery()
            }
        }
    }

    LaunchedEffect(Unit) {
        requestScan()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect Device", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Status circle
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = when (readerConnectionState) {
                                    is ReaderConnectionState.Connected -> MaterialTheme.colorScheme.secondary
                                    is ReaderConnectionState.Error -> MaterialTheme.colorScheme.error
                                    else -> Color.Gray
                                },
                                shape = CircleShape
                            )
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = statusTitle(readerConnectionState),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (readerConnectionState is ReaderConnectionState.Connected) {
                            val state = readerConnectionState as ReaderConnectionState.Connected
                            Text(
                                text = state.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }

                    if (readerConnectionState is ReaderConnectionState.Connected) {
                        Button(
                            onClick = { viewModel.disconnectDevice() },
                            modifier = Modifier.padding(end = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Disconnect", fontSize = 12.sp)
                        }
                    }
                }
            }

            HorizontalDivider()

            // Device list section
            Text(
                text = "Available Devices",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            prerequisitesMessage?.let { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { requestScan() }) {
                                Text("Retry")
                            }
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null)
                                    )
                                    openAppSettingsLauncher.launch(intent)
                                }
                            ) {
                                Text("Open Settings")
                            }
                        }
                    }
                }
            }

            if (readerConnectionState is ReaderConnectionState.Scanning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Scanning for devices...",
                            modifier = Modifier.padding(top = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                }
            } else if (discoveredDevices.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No devices found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(discoveredDevices) { device ->
                        DeviceListItem(
                            device = device,
                            isConnecting = readerConnectionState is ReaderConnectionState.Connecting &&
                                (readerConnectionState as ReaderConnectionState.Connecting).deviceId == device.id,
                            onConnect = { viewModel.connectToDevice(device) }
                        )
                    }
                }
            }

            // Scan/Stop buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (readerConnectionState is ReaderConnectionState.Scanning) {
                    Button(
                        onClick = { viewModel.stopDeviceDiscovery() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Stop Scan", fontWeight = FontWeight.SemiBold)
                    }
                } else if (!readerConnectionState.isConnected) {
                    Button(
                        onClick = { requestScan() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Scan for Devices", fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DeviceListItem(
    device: ReaderDevice,
    isConnecting: Boolean,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = if (device.isLikelyReader) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 8.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                Button(
                    onClick = onConnect,
                    modifier = Modifier.padding(end = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Connect", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun statusTitle(state: ReaderConnectionState): String = when (state) {
    is ReaderConnectionState.Connected -> "✓ Connected"
    is ReaderConnectionState.Connecting -> "Connecting..."
    is ReaderConnectionState.Scanning -> "Scanning..."
    is ReaderConnectionState.Error -> "✗ ${state.message}"
    is ReaderConnectionState.Idle -> "Not connected"
}

private fun requiredBlePermissions(): List<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

private fun startScanWithPrerequisites(
    context: Context,
    onMissingPermission: (String) -> Unit,
    onBluetoothEnableRequired: () -> Unit,
    onStartScan: () -> Unit
) {
    val missingPermission = requiredBlePermissions().firstOrNull {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }
    if (missingPermission != null) {
        onMissingPermission("Required permission missing: $missingPermission")
        return
    }
    if (!isBluetoothEnabled(context)) {
        onBluetoothEnableRequired()
        return
    }
    onStartScan()
}

private fun isBluetoothEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    return manager?.adapter?.isEnabled == true
}


