package com.signalscanner;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private Button btnWifi, btnBluetooth, btnCellular, btnGps, btnScan;
    private TextView tvStatus, tvCount, tvTitle;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private LinearLayout tabLayout;

    private SignalAdapter adapter;
    private List<SignalItem> signalList = new ArrayList<>();
    private String currentScanType = "wifi";

    private WifiManager wifiManager;
    private BluetoothAdapter bluetoothAdapter;
    private TelephonyManager telephonyManager;
    private LocationManager locationManager;
    private GnssStatus.Callback gnssCallback;

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
                    try {
                        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            name = device.getName() != null ? device.getName() : device.getAddress();
                        } else {
                            name = device.getAddress();
                        }
                    } catch (SecurityException e) {
                        name = device.getAddress();
                    }
                    int strength = rssiToPercent(rssi);
                    signalList.add(new SignalItem(name, strength + "%", rssi + " dBm", "BT " + device.getType()));
                    adapter.notifyDataSetChanged();
                    tvCount.setText(signalList.size() + " rilevati");
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                progressBar.setVisibility(View.GONE);
                tvStatus.setText("Scansione completata");
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

        adapter = new SignalAdapter(signalList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        btnScan.setOnClickListener(v -> startScan());
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
            wifiManager.setWifiEnabled(true);
            Toast.makeText(this, "Attivazione Wi-Fi...", Toast.LENGTH_SHORT).show();
        }
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        boolean success = wifiManager.startScan();
        if (!success) {
            // Use cached results
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
                signalList.add(new SignalItem(name, strength + "%", freq, security));
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
                        signalList.add(new SignalItem(name, strength, type, registered + " " + extra));
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

                        signalList.add(new SignalItem(name, snr, elev, used));
                    }

                    runOnUiThread(() -> {
                        adapter.notifyDataSetChanged();
                        tvCount.setText(signalList.size() + " satelliti");
                        tvStatus.setText("Tracking satelliti...");
                        progressBar.setVisibility(View.GONE);
                        btnScan.setEnabled(true);
                    });

                    // Unregister after first result
                    try {
                        locationManager.unregisterGnssStatusCallback(gnssCallback);
                    } catch (Exception ignored) {}
                }
            };

            locationManager.registerGnssStatusCallback(gnssCallback);
            // Request location to trigger GNSS
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
                    Toast.makeText(this, "Alcuni permessi non concessi. L'app potrebbe non funzionare completamente.", Toast.LENGTH_LONG).show();
                    return;
                }
            }
        }
    }

    // ========== DATA MODEL ==========
    static class SignalItem {
        String name, strength, detail1, detail2;
        SignalItem(String name, String strength, String detail1, String detail2) {
            this.name = name;
            this.strength = strength;
            this.detail1 = detail1;
            this.detail2 = detail2;
        }
    }

    // ========== ADAPTER ==========
    static class SignalAdapter extends RecyclerView.Adapter<SignalAdapter.ViewHolder> {
        private final List<SignalItem> items;

        SignalAdapter(List<SignalItem> items) { this.items = items; }

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
