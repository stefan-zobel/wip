package ssl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

public class SSLEchoTest implements Runnable, AutoCloseable {

    private static final int PORT = 9999;
    private static final String msg = "Full fathom five thy father lies, of his bones are coral made."
            + " Those are pearls that were his eyes. Nothing of him that doth fade, but doth suffer a"
            + " sea-change into something rich and strange.";

    public static void main(String[] args) {
        try (SSLEchoTest server = new SSLEchoTest(SSLSockets.createServerSocket(PORT))) {
            new Thread(server).start();
            snooze();
            try (SSLSocket socket = SSLSockets.createSocket("localhost", server.port())) {
                InputStream is = new BufferedInputStream(socket.getInputStream());
                OutputStream os = new BufferedOutputStream(socket.getOutputStream());
                os.write(msg.getBytes());
                os.flush();
                byte[] data = new byte[2048];
                int len = is.read(data);
                if (len <= 0) {
                    throw new IOException("no data received");
                }
                System.out.printf("client received %d bytes: %s%n", len, new String(data, 0, len));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private final SSLServerSocket sslServerSocket;

    public SSLEchoTest(SSLServerSocket sslServerSocket) {
        this.sslServerSocket = sslServerSocket;
    }

    @Override
    public void close() {
        try {
            if (sslServerSocket != null && !sslServerSocket.isClosed()) {
                sslServerSocket.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void run() {
        System.out.printf("server started on port %d%n", port());
        try (SSLSocket socket = (SSLSocket) sslServerSocket.accept()) {
            System.out.println("accepted");
            InputStream is = new BufferedInputStream(socket.getInputStream());
            OutputStream os = new BufferedOutputStream(socket.getOutputStream());
            byte[] data = new byte[2048];
            int len = is.read(data);
            if (len <= 0) {
                throw new IOException("no data received");
            }
            System.out.printf("server received %d bytes: %s%n", len, new String(data, 0, len));
            os.write(data, 0, len);
            os.flush();
        } catch (Exception e) {
            System.out.printf("exception: %s%n", e.getMessage());
        }
        System.out.println("server stopped");
    }

    public int port() {
        return sslServerSocket.getLocalPort();
    }

    private static void snooze() {
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
