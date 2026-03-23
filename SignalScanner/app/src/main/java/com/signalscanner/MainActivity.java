package com.signalscanner;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.TelephonyManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private Button btnWifi, btnBluetooth, btnCellular, btnGps, btnScan;
    private TextView tvStatus, tvCount, tvTitle;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;

    // Bluetooth connection views
    private LinearLayout connectionPanel;
    private TextView tvConnDevice, tvConnStatus, tvConnServices;
    private Button btnDisconnect;

    private SignalAdapter adapter;
    private List<SignalItem> signalList = new ArrayList<>();
    private String currentScanType = "wifi";

    private WifiManager wifiManager;
    private BluetoothAdapter bluetoothAdapter;
    private TelephonyManager telephonyManager;
    private LocationManager locationManager;
    private GnssStatus.Callback gnssCallback;

    // Bluetooth connection
    private BluetoothSocket btSocket;
    private BluetoothGatt btGatt;
    private BluetoothDevice connectedDevice;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // Bluetooth discovery receiver
    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                if (device != null) {
                    String name = "Dispositivo sconosciuto";
                    String address = "";
                    try {
                        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            name = device.getName() != null ? device.getName() : device.getAddress();
                        } else {
                            name = device.getAddress();
                        }
                        address = device.getAddress();
                    } catch (SecurityException e) {
                        address = device.getAddress();
                        name = address;
                    }
                    int strength = rssiToPercent(rssi);
                    String typeStr;
                    int devType = device.getType();
                    if (devType == BluetoothDevice.DEVICE_TYPE_LE) typeStr = "BLE";
                    else if (devType == BluetoothDevice.DEVICE_TYPE_DUAL) typeStr = "Dual";
                    else typeStr = "Classic";

                    signalList.add(new SignalItem(name, strength + "%", rssi + " dBm", typeStr + " | " + address, address, devType));
                    adapter.notifyDataSetChanged();
                    tvCount.setText(signalList.size() + " rilevati");
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                progressBar.setVisibility(View.GONE);
                tvStatus.setText("Tocca un dispositivo per connetterti");
                btnScan.setEnabled(true);
            }
        }
    };

    // Wi-Fi scan receiver
    private final BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                displayWifiResults();
            }
        }
    };

    // BLE GATT Callback
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            mainHandler.post(() -> {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    tvConnStatus.setText("🟢 Connesso");
                    tvConnStatus.setTextColor(0xFF76FF03);
                    tvConnServices.setText("Ricerca servizi...");
                    try {
                        gatt.discoverServices();
                    } catch (SecurityException e) {
                        tvConnServices.setText("Errore permessi");
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    tvConnStatus.setText("🔴 Disconnesso");
                    tvConnStatus.setTextColor(0xFFFF1744);
                    tvConnServices.setText("");
                    connectionPanel.setVisibility(View.GONE);
                }
            });
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<BluetoothGattService> services = gatt.getServices();
                StringBuilder sb = new StringBuilder();
                sb.append("Servizi trovati: ").append(services.size()).append("\n\n");

                for (BluetoothGattService service : services) {
                    String uuid = service.getUuid().toString();
                    String serviceName = getServiceName(uuid);
                    sb.append("📌 ").append(serviceName).append("\n");
                    sb.append("   UUID: ").append(uuid.substring(0, 8)).append("...\n");

                    List<BluetoothGattCharacteristic> chars = service.getCharacteristics();
                    sb.append("   Caratteristiche: ").append(chars.size()).append("\n");
                    for (BluetoothGattCharacteristic c : chars) {
                        String charName = getCharacteristicName(c.getUuid().toString());
                        int props = c.getProperties();
                        StringBuilder propStr = new StringBuilder();
                        if ((props & BluetoothGattCharacteristic.PROPERTY_READ) != 0) propStr.append("R ");
                        if ((props & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) propStr.append("W ");
                        if ((props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) propStr.append("N ");
                        sb.append("     • ").append(charName).append(" [").append(propStr.toString().trim()).append("]\n");
                    }
                    sb.append("\n");
                }

                mainHandler.post(() -> tvConnServices.setText(sb.toString()));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initManagers();
        setupTabs();
        requestPermissions();
    }

    private void initViews() {
        tvTitle = findViewById(R.id.tvTitle);
        tvStatus = findViewById(R.id.tvStatus);
        tvCount = findViewById(R.id.tvCount);
        btnWifi = findViewById(R.id.btnWifi);
        btnBluetooth = findViewById(R.id.btnBluetooth);
        btnCellular = findViewById(R.id.btnCellular);
        btnGps = findViewById(R.id.btnGps);
        btnScan = findViewById(R.id.btnScan);
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);

        // Connection panel
        connectionPanel = findViewById(R.id.connectionPanel);
        tvConnDevice = findViewById(R.id.tvConnDevice);
        tvConnStatus = findViewById(R.id.tvConnStatus);
        tvConnServices = findViewById(R.id.tvConnServices);
        btnDisconnect = findViewById(R.id.btnDisconnect);

        btnDisconnect.setOnClickListener(v -> disconnectBluetooth());

        adapter = new SignalAdapter(signalList, this::onSignalItemClicked);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        btnScan.setOnClickListener(v -> startScan());
    }

    private void onSignalItemClicked(SignalItem item) {
        if (!"bluetooth".equals(currentScanType)) return;
        if (item.address == null || item.address.isEmpty()) return;

        new AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle("Connetti a " + item.name)
            .setMessage("Indirizzo: " + item.address + "\nTipo: " + (item.deviceType == BluetoothDevice.DEVICE_TYPE_LE ? "BLE" : "Classic") + "\n\nVuoi connetterti?")
            .setPositiveButton("Connetti", (d, w) -> connectToDevice(item))
            .setNegativeButton("Annulla", null)
            .show();
    }

    private void connectToDevice(SignalItem item) {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(item.address);
        connectedDevice = device;

        // Show connection panel
        connectionPanel.setVisibility(View.VISIBLE);
        tvConnDevice.setText(item.name);
        tvConnStatus.setText("🟡 Connessione in corso...");
        tvConnStatus.setTextColor(0xFFFFAB00);
        tvConnServices.setText("");

        try {
            // Stop discovery
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
        } catch (SecurityException ignored) {}

        if (item.deviceType == BluetoothDevice.DEVICE_TYPE_LE || item.deviceType == BluetoothDevice.DEVICE_TYPE_DUAL) {
            // BLE connection
            connectBLE(device);
        } else {
            // Classic Bluetooth connection
            connectClassic(device);
        }
    }

    private void connectBLE(BluetoothDevice device) {
        try {
            if (btGatt != null) {
                btGatt.close();
            }
            btGatt = device.connectGatt(this, false, gattCallback);
        } catch (SecurityException e) {
            tvConnStatus.setText("🔴 Errore permessi");
            tvConnStatus.setTextColor(0xFFFF1744);
        }
    }

    private void connectClassic(BluetoothDevice device) {
        new Thread(() -> {
            try {
                if (btSocket != null) {
                    try { btSocket.close(); } catch (IOException ignored) {}
                }

                btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                btSocket.connect();

                mainHandler.post(() -> {
                    tvConnStatus.setText("🟢 Connesso (Classic)");
                    tvConnStatus.setTextColor(0xFF76FF03);

                    String bondState;
                    try {
                        int bond = device.getBondState();
                        if (bond == BluetoothDevice.BOND_BONDED) bondState = "Accoppiato";
                        else if (bond == BluetoothDevice.BOND_BONDING) bondState = "Accoppiamento...";
                        else bondState = "Non accoppiato";
                    } catch (Exception e) {
                        bondState = "Sconosciuto";
                    }

                    StringBuilder info = new StringBuilder();
                    info.append("Tipo connessione: RFCOMM/SPP\n");
                    info.append("Stato accoppiamento: ").append(bondState).append("\n");
                    info.append("UUID: SPP (Serial Port)\n\n");
                    info.append("Connessione seriale attiva.\n");
                    info.append("Il dispositivo è raggiungibile.\n");

                    tvConnServices.setText(info.toString());
                });

            } catch (IOException e) {
                mainHandler.post(() -> {
                    // Try fallback method
                    tryFallbackConnect(device);
                });
            } catch (SecurityException e) {
                mainHandler.post(() -> {
                    tvConnStatus.setText("🔴 Errore permessi");
                    tvConnStatus.setTextColor(0xFFFF1744);
                });
            }
        }).start();
    }

    private void tryFallbackConnect(BluetoothDevice device) {
        tvConnStatus.setText("🟡 Tentativo alternativo...");
        tvConnStatus.setTextColor(0xFFFFAB00);

        new Thread(() -> {
            try {
                // Fallback: try using reflection for older devices
                btSocket = (BluetoothSocket) device.getClass()
                    .getMethod("createRfcommSocket", int.class)
                    .invoke(device, 1);
                btSocket.connect();

                mainHandler.post(() -> {
                    tvConnStatus.setText("🟢 Connesso (Fallback)");
                    tvConnStatus.setTextColor(0xFF76FF03);
                    tvConnServices.setText("Connessione seriale attiva via fallback.\nIl dispositivo è raggiungibile.");
                });
            } catch (Exception e2) {
                mainHandler.post(() -> {
                    // If classic fails, try BLE
                    tvConnServices.setText("Classic non supportato.\nTentativo BLE...");
                    connectBLE(device);
                });
            }
        }).start();
    }

    private void disconnectBluetooth() {
        try {
            if (btGatt != null) {
                btGatt.disconnect();
                btGatt.close();
                btGatt = null;
            }
            if (btSocket != null) {
                btSocket.close();
                btSocket = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        connectedDevice = null;
        connectionPanel.setVisibility(View.GONE);
        tvConnStatus.setText("🔴 Disconnesso");
        tvConnStatus.setTextColor(0xFFFF1744);
        tvConnServices.setText("");
        Toast.makeText(this, "Disconnesso", Toast.LENGTH_SHORT).show();
    }

    // Known service UUIDs
    private String getServiceName(String uuid) {
        String prefix = uuid.substring(0, 8).toLowerCase();
        switch (prefix) {
            case "00001800": return "Generic Access";
            case "00001801": return "Generic Attribute";
            case "0000180a": return "Device Information";
            case "0000180f": return "Battery Service";
            case "0000180d": return "Heart Rate";
            case "00001810": return "Blood Pressure";
            case "00001816": return "Cycling Speed";
            case "0000181c": return "User Data";
            case "0000fe95": return "Xiaomi Service";
            case "0000fee0": return "Mi Band Service";
            case "0000fee1": return "Mi Band Auth";
            case "0000ffe0": return "Serial Service";
            case "00001812": return "HID (Keyboard/Mouse)";
            case "00001803": return "Link Loss";
            case "00001802": return "Immediate Alert";
            case "00001804": return "Tx Power";
            default: return "Servizio " + prefix;
        }
    }

    private String getCharacteristicName(String uuid) {
        String prefix = uuid.substring(0, 8).toLowerCase();
        switch (prefix) {
            case "00002a00": return "Device Name";
            case "00002a01": return "Appearance";
            case "00002a19": return "Battery Level";
            case "00002a29": return "Manufacturer";
            case "00002a24": return "Model Number";
            case "00002a25": return "Serial Number";
            case "00002a26": return "Firmware Rev";
            case "00002a27": return "Hardware Rev";
            case "00002a28": return "Software Rev";
            case "00002a37": return "Heart Rate Measurement";
            default: return prefix + "...";
        }
    }

    private void initManagers() {
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
    }

    private void setupTabs() {
        View.OnClickListener tabClick = v -> {
            btnWifi.setSelected(false);
            btnBluetooth.setSelected(false);
            btnCellular.setSelected(false);
            btnGps.setSelected(false);
            v.setSelected(true);

            if (v == btnWifi) { currentScanType = "wifi"; tvTitle.setText("📶 Wi-Fi"); }
            else if (v == btnBluetooth) { currentScanType = "bluetooth"; tvTitle.setText("🔵 Bluetooth"); }
            else if (v == btnCellular) { currentScanType = "cellular"; tvTitle.setText("📱 Cellulare"); }
            else if (v == btnGps) { currentScanType = "gps"; tvTitle.setText("🛰️ GPS"); }

            signalList.clear();
            adapter.notifyDataSetChanged();
            tvCount.setText("0 rilevati");
            tvStatus.setText("Premi Scansiona");
            connectionPanel.setVisibility(View.GONE);
        };

        btnWifi.setOnClickListener(tabClick);
        btnBluetooth.setOnClickListener(tabClick);
        btnCellular.setOnClickListener(tabClick);
        btnGps.setOnClickListener(tabClick);
        btnWifi.setSelected(true);
    }

    private void requestPermissions() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        perms.add(Manifest.permission.ACCESS_WIFI_STATE);
        perms.add(Manifest.permission.CHANGE_WIFI_STATE);
        perms.add(Manifest.permission.READ_PHONE_STATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN);
            perms.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            perms.add(Manifest.permission.BLUETOOTH);
            perms.add(Manifest.permission.BLUETOOTH_ADMIN);
        }

        List<String> needed = new ArrayList<>();
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    private void startScan() {
        signalList.clear();
        adapter.notifyDataSetChanged();
        tvCount.setText("0 rilevati");
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("Scansione in corso...");
        btnScan.setEnabled(false);

        switch (currentScanType) {
            case "wifi": scanWifi(); break;
            case "bluetooth": scanBluetooth(); break;
            case "cellular": scanCellular(); break;
            case "gps": scanGps(); break;
        }
    }

    // ========== WI-FI ==========
    private void scanWifi() {
        if (wifiManager == null) {
            showError("Wi-Fi non disponibile");
            return;
        }
        if (!wifiManager.isWifiEnabled()) {
            Toast.makeText(this, "Attiva il Wi-Fi", Toast.LENGTH_SHORT).show();
        }
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        boolean success = wifiManager.startScan();
        if (!success) {
            displayWifiResults();
        }
    }

    private void displayWifiResults() {
        try {
            List<ScanResult> results = wifiManager.getScanResults();
            signalList.clear();
            for (ScanResult r : results) {
                String name = (r.SSID != null && !r.SSID.isEmpty()) ? r.SSID : "[Rete nascosta]";
                int strength = WifiManager.calculateSignalLevel(r.level, 100);
                String freq = r.frequency + " MHz";
                String security = getWifiSecurity(r);
                signalList.add(new SignalItem(name, strength + "%", freq, security, "", 0));
            }
            adapter.notifyDataSetChanged();
            tvCount.setText(signalList.size() + " rilevati");
            tvStatus.setText("Scansione completata");
        } catch (SecurityException e) {
            showError("Permessi mancanti per Wi-Fi");
        }
        progressBar.setVisibility(View.GONE);
        btnScan.setEnabled(true);
        try { unregisterReceiver(wifiReceiver); } catch (Exception ignored) {}
    }

    private String getWifiSecurity(ScanResult r) {
        if (r.capabilities.contains("WPA3")) return "🔒 WPA3";
        if (r.capabilities.contains("WPA2")) return "🔒 WPA2";
        if (r.capabilities.contains("WPA")) return "🔒 WPA";
        if (r.capabilities.contains("WEP")) return "🔒 WEP";
        return "🔓 Aperta";
    }

    // ========== BLUETOOTH ==========
    private void scanBluetooth() {
        if (bluetoothAdapter == null) {
            showError("Bluetooth non disponibile");
            return;
        }
        try {
            if (!bluetoothAdapter.isEnabled()) {
                showError("Attiva il Bluetooth dalle impostazioni");
                return;
            }
            registerReceiver(bluetoothReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
            registerReceiver(bluetoothReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));

            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            bluetoothAdapter.startDiscovery();
        } catch (SecurityException e) {
            showError("Permessi Bluetooth mancanti");
        }
    }

    // ========== CELLULAR ==========
    private void scanCellular() {
        try {
            List<CellInfo> cellInfos = telephonyManager.getAllCellInfo();
            signalList.clear();
            if (cellInfos != null) {
                for (CellInfo info : cellInfos) {
                    String name = "";
                    String strength = "";
                    String type = "";
                    String extra = "";

                    if (info instanceof CellInfoLte) {
                        CellInfoLte lte = (CellInfoLte) info;
                        name = "Torre LTE #" + lte.getCellIdentity().getCi();
                        strength = lte.getCellSignalStrength().getDbm() + " dBm";
                        type = "4G LTE";
                        extra = "PCI: " + lte.getCellIdentity().getPci();
                    } else if (info instanceof CellInfoGsm) {
                        CellInfoGsm gsm = (CellInfoGsm) info;
                        name = "Torre GSM #" + gsm.getCellIdentity().getCid();
                        strength = gsm.getCellSignalStrength().getDbm() + " dBm";
                        type = "2G GSM";
                        extra = "LAC: " + gsm.getCellIdentity().getLac();
                    } else if (info instanceof CellInfoWcdma) {
                        CellInfoWcdma wcdma = (CellInfoWcdma) info;
                        name = "Torre WCDMA #" + wcdma.getCellIdentity().getCid();
                        strength = wcdma.getCellSignalStrength().getDbm() + " dBm";
                        type = "3G UMTS";
                        extra = "PSC: " + wcdma.getCellIdentity().getPsc();
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info instanceof CellInfoNr) {
                        CellInfoNr nr = (CellInfoNr) info;
                        name = "Torre 5G NR";
                        strength = nr.getCellSignalStrength().getDbm() + " dBm";
                        type = "5G NR";
                        extra = "";
                    }

                    if (!name.isEmpty()) {
                        String registered = info.isRegistered() ? "✅ Connesso" : "📡 Rilevata";
                        signalList.add(new SignalItem(name, strength, type, registered + " " + extra, "", 0));
                    }
                }
            }
            adapter.notifyDataSetChanged();
            tvCount.setText(signalList.size() + " rilevati");
            tvStatus.setText("Scansione completata");
        } catch (SecurityException e) {
            showError("Permessi telefono mancanti");
        }
        progressBar.setVisibility(View.GONE);
        btnScan.setEnabled(true);
    }

    // ========== GPS ==========
    private void scanGps() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            showError("GPS dettagliato richiede Android 7+");
            return;
        }

        try {
            gnssCallback = new GnssStatus.Callback() {
                @Override
                public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
                    signalList.clear();
                    int count = status.getSatelliteCount();
                    for (int i = 0; i < count; i++) {
                        String constellation;
                        switch (status.getConstellationType(i)) {
                            case GnssStatus.CONSTELLATION_GPS: constellation = "GPS (USA)"; break;
                            case GnssStatus.CONSTELLATION_GLONASS: constellation = "GLONASS (RUS)"; break;
                            case GnssStatus.CONSTELLATION_GALILEO: constellation = "Galileo (EU)"; break;
                            case GnssStatus.CONSTELLATION_BEIDOU: constellation = "BeiDou (CHN)"; break;
                            case GnssStatus.CONSTELLATION_QZSS: constellation = "QZSS (JPN)"; break;
                            default: constellation = "Altro"; break;
                        }

                        String name = constellation + " #" + status.getSvid(i);
                        String snr = String.format("%.1f dB-Hz", status.getCn0DbHz(i));
                        String elev = String.format("Elev: %.0f°", status.getElevationDegrees(i));
                        String used = status.usedInFix(i) ? "✅ In uso" : "📡 Visibile";

                        signalList.add(new SignalItem(name, snr, elev, used, "", 0));
                    }

                    runOnUiThread(() -> {
                        adapter.notifyDataSetChanged();
                        tvCount.setText(signalList.size() + " satelliti");
                        tvStatus.setText("Tracking satelliti...");
                        progressBar.setVisibility(View.GONE);
                        btnScan.setEnabled(true);
                    });

                    try {
                        locationManager.unregisterGnssStatusCallback(gnssCallback);
                    } catch (Exception ignored) {}
                }
            };

            locationManager.registerGnssStatusCallback(gnssCallback);
            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, location -> {}, null);

        } catch (SecurityException e) {
            showError("Permessi GPS mancanti");
        }
    }

    // ========== UTILS ==========
    private int rssiToPercent(int rssi) {
        if (rssi >= -50) return 100;
        if (rssi <= -100) return 0;
        return 2 * (rssi + 100);
    }

    private void showError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        progressBar.setVisibility(View.GONE);
        tvStatus.setText(msg);
        btnScan.setEnabled(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectBluetooth();
        try { unregisterReceiver(bluetoothReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(wifiReceiver); } catch (Exception ignored) {}
        if (gnssCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try { locationManager.unregisterGnssStatusCallback(gnssCallback); } catch (Exception ignored) {}
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Alcuni permessi non concessi.", Toast.LENGTH_LONG).show();
                    return;
                }
            }
        }
    }

    // ========== DATA MODEL ==========
    static class SignalItem {
        String name, strength, detail1, detail2, address;
        int deviceType;
        SignalItem(String name, String strength, String detail1, String detail2, String address, int deviceType) {
            this.name = name;
            this.strength = strength;
            this.detail1 = detail1;
            this.detail2 = detail2;
            this.address = address;
            this.deviceType = deviceType;
        }
    }

    // ========== ADAPTER ==========
    interface OnItemClickListener {
        void onItemClick(SignalItem item);
    }

    static class SignalAdapter extends RecyclerView.Adapter<SignalAdapter.ViewHolder> {
        private final List<SignalItem> items;
        private final OnItemClickListener listener;

        SignalAdapter(List<SignalItem> items, OnItemClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_signal, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
            SignalItem item = items.get(pos);
            h.tvName.setText(item.name);
            h.tvStrength.setText(item.strength);
            h.tvDetail1.setText(item.detail1);
            h.tvDetail2.setText(item.detail2);

            if (item.address != null && !item.address.isEmpty()) {
                h.itemView.setOnClickListener(v -> listener.onItemClick(item));
                h.itemView.setAlpha(1.0f);
            } else {
                h.itemView.setOnClickListener(null);
                h.itemView.setAlpha(0.9f);
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvStrength, tvDetail1, tvDetail2;
            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvItemName);
                tvStrength = v.findViewById(R.id.tvItemStrength);
                tvDetail1 = v.findViewById(R.id.tvItemDetail1);
                tvDetail2 = v.findViewById(R.id.tvItemDetail2);
            }
        }
    }
}
