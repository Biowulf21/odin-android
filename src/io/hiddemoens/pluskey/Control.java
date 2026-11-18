package io.hiddemoens.pluskey;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.*;
public final class Control {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !(args[0].equals("STATUS") || args[0].equals("STOP") || args[0].equals("VERSION")))
            throw new IllegalArgumentException("Use STATUS, STOP or VERSION");
        String reply = request(args[0]);
        System.out.println(reply);
        if (reply.startsWith("Error")) System.exit(2);
    }

    static String request(String command) throws IOException {
        try (LocalSocket socket = new LocalSocket()) {
            socket.connect(new LocalSocketAddress(Helper.SOCKET));
            socket.setSoTimeout(2000);
            new PrintWriter(socket.getOutputStream(), true).println(command);
            String reply = new BufferedReader(new InputStreamReader(socket.getInputStream())).readLine();
            if (reply == null) throw new IOException("No reply");
            return reply;
        }
    }
}
