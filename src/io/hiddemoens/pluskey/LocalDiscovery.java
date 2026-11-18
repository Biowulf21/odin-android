package io.hiddemoens.pluskey;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import java.net.InetAddress;
import java.net.NetworkInterface;

/** Accept service ports only when advertised by an interface on this phone. */
final class LocalDiscovery {
    private final NsdManager nsd;
    private final Handler main = new Handler(Looper.getMainLooper());
    volatile int pairingPort, connectPort;
    private NsdManager.DiscoveryListener pairing, connect;
    private int generation;
    private final Runnable timeout = this::stop;
    LocalDiscovery(Context context) { nsd = (NsdManager)context.getSystemService(Context.NSD_SERVICE); }
    synchronized void start() {
        main.removeCallbacks(timeout);
        if (pairing == null && connect == null) { generation++; pairingPort = 0; connectPort = 0; }
        if (pairing == null) { pairing = listener(true, generation); begin("_adb-tls-pairing._tcp.", pairing); }
        if (connect == null) { connect = listener(false, generation); begin("_adb-tls-connect._tcp.", connect); }
        main.postDelayed(timeout, 120000);
    }
    private void begin(String type, NsdManager.DiscoveryListener listener) {
        try { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener); }
        catch (Exception e) { android.util.Log.w("PlusKey", "Local discovery unavailable: " + e.getClass().getSimpleName()); }
    }
    synchronized void stop() {
        main.removeCallbacks(timeout);
        for (NsdManager.DiscoveryListener listener : new NsdManager.DiscoveryListener[]{pairing, connect}) {
            if (listener != null) try { nsd.stopServiceDiscovery(listener); } catch (Exception ignored) { }
        }
        pairing = null; connect = null;
        pairingPort = 0; connectPort = 0; generation++;
    }
    private NsdManager.DiscoveryListener listener(boolean isPairing, int session) {
        return new NsdManager.DiscoveryListener() {
            String localService;
            public void onDiscoveryStarted(String type) { }
            public void onDiscoveryStopped(String type) { }
            public void onStartDiscoveryFailed(String type, int error) {
                synchronized(LocalDiscovery.this) { if(session!=generation)return; if (isPairing) pairing = null; else connect = null; }
            }
            public void onStopDiscoveryFailed(String type, int error) { }
            public void onServiceLost(NsdServiceInfo info) {
                synchronized(LocalDiscovery.this) { if(session!=generation)return; if (info.getServiceName().equals(localService)) { if (isPairing) pairingPort = 0; else connectPort = 0; } }
            }
            public void onServiceFound(NsdServiceInfo info) {
                try { nsd.resolveService(info, new NsdManager.ResolveListener() {
                    public void onResolveFailed(NsdServiceInfo service, int error) { }
                    public void onServiceResolved(NsdServiceInfo service) {
                        try {
                            InetAddress host = service.getHost();
                            if (host == null || (!host.isLoopbackAddress() && NetworkInterface.getByInetAddress(host) == null)) return;
                            int port = service.getPort();
                            if (port < 1 || port > 65535) return;
                            synchronized(LocalDiscovery.this) {
                                if(session!=generation)return;
                                localService = service.getServiceName();
                                if (isPairing) pairingPort = port; else connectPort = port;
                            }
                        } catch (Exception ignored) { }
                    }
                }); } catch (Exception ignored) { }
            }
        };
    }
}
