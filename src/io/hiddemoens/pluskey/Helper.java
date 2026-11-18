package io.hiddemoens.pluskey;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.*;
import java.util.concurrent.*;

/** Runs as shell through app_process. No ordinary Android service is installed. */
public final class Helper {
    static final String SOCKET = "io.hiddemoens.pluskey.helper.v1";
    static final String VERSION = "1.3";
    static final String LENS = "com.google.ar.lens/com.google.vr.apps.ornament.app.lens.LensLauncherActivity";
    private volatile boolean running = true;
    private final ExecutorService actions=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"PlusKey-actions");t.setDaemon(true);return t;});
    private void enqueue(boolean longPress){if(running)try{actions.execute(()->{if(running)launch(longPress);});}catch(RejectedExecutionException ignored){}}
    private volatile Process readerProcess;
    private volatile String state = "Starting";
    private volatile String last = "No short press yet";
    private volatile int launches;
    private LocalServerSocket server;
    private int appUid;
    private Thread controlThread;
    private final Mailbox mailbox = new Mailbox(new File("/sdcard/Android/data/io.hiddemoens.pluskey/files"));

    public static void main(String[] args) throws Exception {
        if (android.os.Process.myUid() != 2000 || args.length != 1)
            throw new SecurityException("Start helper using supplied USB script");
        Helper helper = new Helper();
        helper.controlThread = Thread.currentThread();
        helper.appUid = Integer.parseInt(args[0]);
        if (helper.appUid < 10000) throw new SecurityException("Invalid app UID");
        // Binding first ensures a second start cannot create a duplicate reader.
        helper.server = new LocalServerSocket(SOCKET);
        helper.mailbox.clearStop();
        helper.mailbox.publishVersion(VERSION);
        helper.mailbox.publish("Starting|0|Starting input reader", System.currentTimeMillis());
        Thread events = new Thread(helper::listen, "PlusLens-input");
        events.setDaemon(true);
        events.start();
        Thread heartbeat = new Thread(() -> {
            while (helper.running) {
                try {
                    if (helper.mailbox.stopRequested()) { helper.stop(); break; }
                    int preview=helper.mailbox.consumePreview();if(preview!=0)helper.launch(preview==2);
                    helper.mailbox.publish(helper.state + "|" + helper.launches + "|" + helper.last, System.currentTimeMillis());
                    Thread.sleep(1000);
                } catch (Exception e) {
                    System.err.println("Status storage unavailable; stopping helper: " + e);
                    try { helper.stop(); } catch (IOException ignored) { }
                    break;
                }
            }
        }, "PlusLens-status");
        heartbeat.setDaemon(true);
        heartbeat.start();
        try {
            while (helper.running) {
                try (LocalSocket socket = helper.server.accept()) {
                    if (!helper.running) break;
                    int uid = socket.getPeerCredentials().getUid();
                    if (uid != helper.appUid && uid != 2000) continue;
                    socket.setSoTimeout(1000);
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String command = input.readLine();
                    PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
                    if ("STOP".equals(command)) {
                        output.println("Stopped");
                        helper.stop();
                    } else if ("STATUS".equals(command)) {
                        output.println(helper.state + "|" + helper.launches + "|" + helper.last);
                    } else if ("VERSION".equals(command)) {
                        output.println(VERSION);
                    } else output.println("Unknown command");
                } catch (IOException e) {
                    if (helper.running) System.err.println("Client: " + e);
                }
            }
        } finally {
            helper.running = false;
            Process reader = helper.readerProcess;
            if (reader != null) reader.destroy();
            helper.server.close();
            try{helper.mailbox.publish("Stopped|" + helper.launches + "|Helper stopped", System.currentTimeMillis());}
            finally{DeviceActions.shutdown();System.exit(0);}
        }
    }

    private void stop() throws IOException {
        running = false;
        actions.shutdownNow();
        Process reader = readerProcess;
        if (reader != null) reader.destroy();
        // Closing a server descriptor in another thread need not wake accept().
        if (Thread.currentThread() != controlThread) {
            try (LocalSocket wake = new LocalSocket()) {
                wake.connect(new LocalSocketAddress(SOCKET));
            }
        }
        server.close();
    }

    private void listen() {
        try {
            String device = KeyPress.device(command("/system/bin/getevent", "-pl"));
            if (device == null) throw new IOException("Plus Key input device not found");
            Process reader = new ProcessBuilder("/system/bin/getevent", "-lt", device).redirectErrorStream(true).start();
            readerProcess = reader;
            state = "Listening";
            System.out.println("Listening on " + device);
            KeyPress key = new KeyPress();
            ScheduledExecutorService holdTimer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"PlusKey-hold");t.setDaemon(true);return t;});
            holdTimer.scheduleAtFixedRate(()->{if(running&&key.tick(System.nanoTime()/1e9)==2)enqueue(true);},10,10,TimeUnit.MILLISECONDS);
            try (BufferedReader input = new BufferedReader(new InputStreamReader(reader.getInputStream()))) {
                String line;
                while (running && (line = input.readLine()) != null) {
                    int gesture=key.gesture(line,System.nanoTime()/1e9);
                    if(gesture!=0)enqueue(gesture==2);
                }
            }
            finally { holdTimer.shutdownNow(); }
            if (running) throw new IOException("Input reader stopped");
        } catch (Exception e) {
            if (running) {
                state = "Error";
                last = clean(e.getMessage());
                System.err.println(e);
            }
        } finally {
            Process reader = readerProcess;
            if (reader != null) reader.destroy();
        }
    }

    private synchronized void launch(boolean longPress) {
        try {
            String target = longPress ? mailbox.selectedLong() : mailbox.selected();
            if(longPress && target==null)return;
            if(longPress && !"nothing".equals(command("/system/bin/settings","get","system","oplus_action_button_switch_state").trim())){
                last="Long press needs OnePlus action set to Do nothing";return;
            }
            // Fail closed rather than launching camera UI behind a locked screen.
            if (!Policy.mayLaunch(command("/system/bin/dumpsys", "window", "policy"))) {
                last = "Skipped: phone locked, asleep, or state unavailable";
                return;
            }
            if (target == null) throw new IOException("No app selected; open Plus Key and choose an app");
            if(target.startsWith("action:"))DeviceActions.run(target);
            else for(android.content.Intent intent:Targets.intents(target)){
                String result=command("/system/bin/am","start","--user","0",intent.toUri(android.content.Intent.URI_INTENT_SCHEME));
                if(result.contains("Error")||result.contains("Exception"))throw new IOException(result);
            }
            try{DeviceActions.feedback();}catch(Exception vibrationError){System.err.println("Feedback unavailable: "+vibrationError.getClass().getSimpleName());}
            launches++;
            last = "Action completed";
            System.out.println(last + " (" + launches + ")");
        } catch (Exception e) {
            last = "Launch failed: " + clean(e.getMessage());
            System.err.println(last);
        }
    }

    static String command(String... args) throws Exception {
        Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thread drain = new Thread(() -> {
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() < 262144) output.write(buffer, 0, count);
                }
            } catch (IOException ignored) { }
        });
        drain.setDaemon(true);
        drain.start();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) throw new IOException("Command timed out: " + args[0]);
            drain.join(1000);
            String result = output.toString("UTF-8");
            if (process.exitValue() != 0) throw new IOException(clean(result));
            return result;
        } finally { process.destroy(); }
    }

    private static String clean(String value) {
        if (value == null) return "Unknown error";
        value = value.replace('\n', ' ').replace('\r', ' ').replace('|', '/');
        return value.substring(0, Math.min(240, value.length()));
    }
}
