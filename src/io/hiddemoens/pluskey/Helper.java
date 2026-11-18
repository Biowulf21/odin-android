package io.hiddemoens.pluskey;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.FileObserver;
import android.os.SystemClock;
import java.io.*;
import java.util.concurrent.*;

/** Runs as shell through app_process. No ordinary Android service is installed. */
public final class Helper {
    static final String SOCKET = "io.hiddemoens.pluskey.helper.v1";
    static final String VERSION = "1.4.1";
    static final String ONEPLUS_ACTION_SETTING = "oplus_action_button_switch_state";
    static final String LENS = "com.google.ar.lens/com.google.vr.apps.ornament.app.lens.LensLauncherActivity";
    private static final long UNLOCK_TIMEOUT_MS = 30_000;
    private static final long UNLOCK_CHECK_INTERVAL_MS = 500;
    private static final long UNLOCK_CALLBACK_FALLBACK_MS = 2_000;
    private volatile boolean running = true;
    private final ScheduledThreadPoolExecutor actions = new ScheduledThreadPoolExecutor(1, task -> {
        Thread thread = new Thread(task, "PlusKey-actions");
        thread.setDaemon(true);
        return thread;
    });
    private PendingUnlock pendingUnlock;
    private volatile Process readerProcess;
    private volatile String state = "Starting";
    private volatile String last = "No short press yet";
    private volatile int launches;
    private LocalServerSocket server;
    private int appUid;
    private Thread controlThread;
    private final File mailboxDir = new File("/sdcard/Android/data/io.hiddemoens.pluskey/files");
    private final Mailbox mailbox = new Mailbox(mailboxDir);
    private final BlockingQueue<Boolean> mailboxChanged = new ArrayBlockingQueue<>(1);

    private Helper() {
        actions.setRemoveOnCancelPolicy(true);
    }

    private void enqueue(boolean longPress, boolean preview) {
        if (!running) return;
        try {
            actions.execute(() -> {
                if (running) launch(longPress, preview);
            });
        } catch (RejectedExecutionException ignored) { }
    }

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
        Thread mailboxThread = new Thread(helper::watchMailbox, "PlusLens-status");
        mailboxThread.setDaemon(true);
        mailboxThread.start();
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
                        output.println(helper.statusLine());
                    } else if ("VERSION".equals(command)) {
                        output.println(VERSION);
                    } else output.println("Unknown command");
                } catch (IOException e) {
                    if (helper.running) System.err.println("Client: " + e);
                }
            }
        } finally {
            helper.running = false;
            mailboxThread.interrupt();
            helper.actions.shutdownNow();
            Process reader = helper.readerProcess;
            if (reader != null) reader.destroy();
            helper.server.close();
            try{helper.publishStatus("Stopped|" + helper.launches + "|Helper stopped", true);}
            finally{DeviceActions.shutdown();System.exit(0);}
        }
    }

    private String statusLine() {
        return state + "|" + launches + "|" + last;
    }

    private synchronized void publishStatus(String status, boolean stopped) throws IOException {
        if (running || stopped) mailbox.publish(status, System.currentTimeMillis());
    }

    private void watchMailbox() {
        FileObserver observer = new FileObserver(mailboxDir,
            FileObserver.CREATE | FileObserver.MOVED_TO | FileObserver.CLOSE_WRITE) {
            @Override public void onEvent(int event, String path) {
                if (Mailbox.STOP_REQUEST.equals(path) || Mailbox.PREVIEW_REQUEST.equals(path)
                    || Mailbox.STATUS_REQUEST.equals(path)) {
                    mailboxChanged.offer(true);
                }
            }
        };
        try {
            observer.startWatching();
        } catch (RuntimeException error) {
            System.err.println("Mailbox notifications unavailable: " + error);
        }
        try {
            String published = null;
            while (running) {
                if (mailbox.stopRequested()) {
                    stop();
                    break;
                }
                int preview = mailbox.consumePreview();
                if (preview != 0) enqueue(preview == 2, true);
                String status = statusLine();
                if (mailbox.consumeStatusRequest() || !status.equals(published)) {
                    publishStatus(status, false);
                    published = status;
                }
                // Also check request files when filesystem notifications are unavailable.
                mailboxChanged.poll(Mailbox.REQUEST_FALLBACK_MS, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (IOException error) {
            System.err.println("Status storage unavailable; stopping helper: " + error);
            try { stop(); } catch (IOException ignored) { }
        } finally {
            observer.stopWatching();
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
            mailboxChanged.offer(true);
            System.out.println("Listening on " + device);
            ScheduledThreadPoolExecutor holdTimer = new ScheduledThreadPoolExecutor(1, task -> {
                Thread thread = new Thread(task, "PlusKey-hold");
                thread.setDaemon(true);
                return thread;
            });
            holdTimer.setRemoveOnCancelPolicy(true);
            KeyPress key = new KeyPress(holdTimer, () -> enqueue(true, false));
            try (BufferedReader input = new BufferedReader(new InputStreamReader(reader.getInputStream()))) {
                String line;
                while (running && (line = input.readLine()) != null) {
                    int gesture=key.gesture(line);
                    if(gesture!=0)enqueue(gesture==2, false);
                }
            }
            finally { holdTimer.shutdownNow(); }
            if (running) throw new IOException("Input reader stopped");
        } catch (Exception e) {
            if (running) {
                state = "Error";
                last = clean(e.getMessage());
                mailboxChanged.offer(true);
                System.err.println(e);
            }
        } finally {
            Process reader = readerProcess;
            if (reader != null) reader.destroy();
        }
    }

    private String selectedTarget(boolean longPress) throws Exception {
        return longPress ? mailbox.selectedLong() : mailbox.selected();
    }

    private boolean customLongPressEnabled() throws Exception {
        try {
            String value = DeviceActions.nativeLongPressAction();
            if (value != null) return "nothing".equals(value);
        } catch (Exception unavailable) {
            // Some builds restrict access to vendor settings through the resolver.
        }
        return "nothing".equals(command("/system/bin/settings", "get", "system",
            ONEPLUS_ACTION_SETTING).trim());
    }

    private boolean isAwakeAndUnlocked() throws Exception {
        try {
            return DeviceActions.isAwakeAndUnlocked();
        } catch (Exception unavailable) {
            return Policy.mayLaunch(command("/system/bin/dumpsys", "window", "policy"));
        }
    }

    private void launch(boolean longPress, boolean preview) {
        try {
            String target = selectedTarget(longPress);
            if (longPress && target == null) return;
            if (longPress && !customLongPressEnabled()) {
                last = "Long press needs OnePlus action set to Do nothing";
                return;
            }
            if (target == null) throw new IOException("No app selected; open Plus Key and choose an app");
            if (!preview && pendingUnlock != null) pendingUnlock.finish();
            if ((!preview && Policy.mayRunWhileLocked(target)) || isAwakeAndUnlocked()) {
                runTarget(target);
            } else if (preview) {
                last = "Skipped preview: phone locked, asleep, or state unavailable";
            } else {
                command("/system/bin/input", "keyevent", "KEYCODE_WAKEUP");
                last = "Waiting for unlock";
                pendingUnlock = new PendingUnlock(target, longPress);
                pendingUnlock.start();
            }
        } catch (Exception e) {
            recordLaunchFailure(e);
        } finally {
            mailboxChanged.offer(true);
        }
    }

    // Callback delivery and all pending-action state use the same action executor.
    private final class PendingUnlock {
        final String target;
        final boolean longPress;
        final long deadline = SystemClock.elapsedRealtime() + UNLOCK_TIMEOUT_MS;
        Runnable unwatch;
        ScheduledFuture<?> fallback;

        PendingUnlock(String target, boolean longPress) {
            this.target = target;
            this.longPress = longPress;
        }

        void start() {
            try {
                unwatch = DeviceActions.watchUnlock(actions, this::check);
            } catch (Exception error) {
                System.err.println("Unlock callbacks unavailable: " + error.getClass().getSimpleName());
            }
            // Recheck after registration in case the phone unlocked during setup.
            check();
        }

        void finish() {
            pendingUnlock = null;
            if (fallback != null) fallback.cancel(false);
            if (unwatch != null) {
                try {
                    unwatch.run();
                } catch (RuntimeException error) {
                    System.err.println("Could not unregister unlock callbacks: " + error);
                }
            }
        }

        void check() {
            if (!running || pendingUnlock != this) return;
            try {
                if (SystemClock.elapsedRealtime() >= deadline) {
                    last = "Skipped: unlock timed out";
                } else if (!target.equals(selectedTarget(longPress))) {
                    last = "Skipped: action changed while waiting for unlock";
                } else if (!isAwakeAndUnlocked()) {
                    if (fallback == null) {
                        long interval = unwatch == null ? UNLOCK_CHECK_INTERVAL_MS : UNLOCK_CALLBACK_FALLBACK_MS;
                        long remaining = deadline - SystemClock.elapsedRealtime();
                        fallback = actions.schedule(() -> {
                            fallback = null;
                            check();
                        }, Math.min(interval, remaining), TimeUnit.MILLISECONDS);
                    }
                    return;
                } else if (longPress && !customLongPressEnabled()) {
                    last = "Long press needs OnePlus action set to Do nothing";
                } else if (SystemClock.elapsedRealtime() >= deadline) {
                    last = "Skipped: unlock timed out";
                } else {
                    runTarget(target);
                }
            } catch (Exception error) {
                recordLaunchFailure(error);
            }
            finish();
            mailboxChanged.offer(true);
        }
    }

    private void runTarget(String target) throws Exception {
        if (!running) return;
        if (target.startsWith("action:")) DeviceActions.run(target);
        else for (android.content.Intent intent : Targets.intents(target)) {
            String result = command("/system/bin/am", "start", "--user", "0",
                intent.toUri(android.content.Intent.URI_INTENT_SCHEME));
            if (result.contains("Error") || result.contains("Exception")) throw new IOException(result);
        }
        try { DeviceActions.feedback(); }
        catch (Exception e) { System.err.println("Feedback unavailable: " + e.getClass().getSimpleName()); }
        launches++;
        last = "Action completed";
        System.out.println(last + " (" + launches + ")");
    }

    private void recordLaunchFailure(Exception error) {
        if (!running) return;
        last = "Launch failed: " + clean(error.getMessage());
        System.err.println(last);
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
