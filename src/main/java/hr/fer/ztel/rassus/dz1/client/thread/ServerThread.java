package hr.fer.ztel.rassus.dz1.client.thread;

import com.google.gson.Gson;
import hr.fer.ztel.rassus.dz1.client.loader.Loaders;
import hr.fer.ztel.rassus.dz1.client.model.Measurement;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Server of a single sensor client that serves
 * other sensor clients measurement data.
 *
 * @author Mario Bobic
 */
@Log4j2
@Getter
@ToString
@RequiredArgsConstructor
public class ServerThread extends Thread {

    private final transient ExecutorService threadPool = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() - 1
    );

    private final long startTime = System.currentTimeMillis();
    private final String ipAddress;
    private final int port;

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(ipAddress, port));
            serverSocket.setSoTimeout(1000);

            while (!isInterrupted()) {
                try {
                    acceptClient(serverSocket);
                } catch (SocketTimeoutException e) {
                    continue;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            threadPool.shutdown();
        }
    }

    /**
     * Accepts the client <b>blocking</b> the thread while waiting for a
     * client socket.
     *
     * @param serverSocket server socket that accepts clients
     * @throws SocketTimeoutException if the server socket has timed out
     * @throws IOException            if an I/O or other socket exception occurs
     */
    private void acceptClient(ServerSocket serverSocket) throws IOException {
        Socket clientSocket = serverSocket.accept();
        ClientWorker cw = new ClientWorker(clientSocket);
        threadPool.submit(cw);

        log.info("Accepted {}", clientSocket);
    }

    /**
     * Runnable object that serves other sensors.
     *
     * @author Mario Bobic
     */
    @RequiredArgsConstructor
    private class ClientWorker implements Runnable {
        /** The client socket. */
        private final Socket clientSocket;

        @Override
        public void run() {
            log.info("Serving {}", clientSocket);
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()), true);

                int secondsActive = Math.toIntExact((System.currentTimeMillis() - startTime) / 1000);
                Measurement measurement = Loaders.getMeasurementLoader().getMeasurement(secondsActive % 100);

                Gson gson = new Gson();
                String json = gson.toJson(measurement);

                out.print(json);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}