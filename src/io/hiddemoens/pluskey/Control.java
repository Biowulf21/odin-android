package io.hiddemoens.pluskey;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.*;
public final class Control {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !(args[0].equals("STATUS") || args[0].equals("STOP") || args[0].equals("VERSION")))
            throw new IllegalArgumentException("Use STATUS, STOP or VERSION");
        try (LocalSocket socket = new LocalSocket()) {
            socket.connect(new LocalSocketAddress(Helper.SOCKET));
            socket.setSoTimeout(2000);
            new PrintWriter(socket.getOutputStream(), true).println(args[0]);
            String reply = new BufferedReader(new InputStreamReader(socket.getInputStream())).readLine();
            if (reply == null) throw new IOException("No reply");
            System.out.println(reply);
            if (reply.startsWith("Error")) System.exit(2);
        }
    }
}
