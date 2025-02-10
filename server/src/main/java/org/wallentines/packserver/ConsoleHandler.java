package org.wallentines.packserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConsoleHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("ConsoleHandler");

    private final WebServer server;

    private boolean running = false;
    private Thread thread = null;

    public ConsoleHandler(WebServer server) {
        this.server = server;
    }

    public void start() {

        assert !running;
        assert thread == null;

        running = true;
        thread = new Thread("Console Handler Thread") {
            @Override
            public void run() {

                BufferedReader lineReader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

                try {
                    String line;
                    while (running && (line = lineReader.readLine()) != null) {
                        handleInput(line);
                    }
                } catch (IOException ex) {
                    LOGGER.error("An exception occurred while handling console input!", ex);
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    private void handleInput(String cmd) {

        String[] parts = cmd.split(" ");

        String command = parts[0];
        if(command.equals("stop")) {
            System.out.println("Shutting down...");
            server.shutdown();
            stop();
        } else if (command.equals("token")) {
            System.out.println("New token: " + server.generateToken());
        }
    }

    public void stop() {
        running = false;
    }

}
