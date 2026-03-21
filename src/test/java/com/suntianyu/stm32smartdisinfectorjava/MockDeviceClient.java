package com.suntianyu.stm32smartdisinfectorjava;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A simple Mock Device to test the server without real hardware.
 * Run this main method after starting the Spring Boot application.
 */
public class MockDeviceClient {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 9000;
    private static final String DEVICE_ID = "cabinet-001";
    private static final ObjectMapper mapper = new ObjectMapper();

    // Device Internal State
    private static volatile boolean machineRunning = false;
    private static volatile boolean paused = false;
    private static volatile String mode = "智能模式";
    private static volatile double temperature = 25.0;
    private static volatile double humidity = 50.0;
    private static volatile boolean heaterOn = false;
    private static volatile boolean fanOn = false;
    private static volatile boolean disinfectionOn = false;
    private static volatile int duration = 20;
    private static volatile int remainingSeconds = 0;

    public static void main(String[] args) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        
        try (Socket socket = new Socket(HOST, PORT);
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"))) {

            System.out.println("Connected to server " + HOST + ":" + PORT);

            // Start a thread to send status reports every 2 seconds
            executor.submit(() -> {
                try {
                    long seq = 0;
                    while (!socket.isClosed()) {
                        simulatePhysics();
                        
                        ObjectNode report = mapper.createObjectNode();
                        report.put("type", "status_report");
                        report.put("deviceId", DEVICE_ID);
                        report.put("seq", ++seq);
                        report.put("ts", System.currentTimeMillis());
                        report.put("temperature", Math.round(temperature * 10.0) / 10.0);
                        report.put("humidity", Math.round(humidity * 10.0) / 10.0);
                        report.put("doorOpen", false);
                        report.put("machineRunning", machineRunning);
                        report.put("paused", paused);
                        report.put("mode", mode);
                        report.put("heaterOn", heaterOn);
                        report.put("disinfectionOn", disinfectionOn);
                        report.put("fanOn", fanOn);
                        report.put("remainingSeconds", remainingSeconds);
                        report.put("duration", duration);
                        report.put("faultCode", 0);

                        String json = report.toString();
                        out.println(json);
                        System.out.println(">> Sent report: " + json);
                        
                        Thread.sleep(2000);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            // Main thread reads commands
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("<< Received: " + line);
                try {
                    JsonNode cmd = mapper.readTree(line);
                     if (cmd.has("type") && "control_cmd".equals(cmd.get("type").asText())) {
                         handleCommand(cmd, out);
                     }
                } catch (Exception e) {
                    System.err.println("Error processing command: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdownNow();
        }
    }

    private static void handleCommand(JsonNode cmd, PrintWriter out) throws Exception {
        String action = cmd.get("action").asText();
        String cmdId = cmd.get("cmdId").asText();
        
        System.out.println("Processing action: " + action);
        
        if ("start".equals(action)) {
            machineRunning = true;
            paused = false;
            if (cmd.has("mode")) mode = cmd.get("mode").asText();
            if (cmd.has("duration")) duration = cmd.get("duration").asInt();
            remainingSeconds = duration * 60;
            // Turn on actuators based on logic (simplified)
            heaterOn = true;
            disinfectionOn = true;
        } else if ("pause".equals(action)) {
            paused = true;
            heaterOn = false;
            fanOn = false;
            disinfectionOn = false;
        } else if ("resume".equals(action)) {
            paused = false;
            heaterOn = true;
            disinfectionOn = true;
        } else if ("stop".equals(action)) {
            machineRunning = false;
            paused = false;
            heaterOn = false;
            fanOn = false;
            disinfectionOn = false;
            remainingSeconds = 0;
        }

        // Send ACK
        ObjectNode ack = mapper.createObjectNode();
        ack.put("type", "ack");
        ack.put("cmdId", cmdId);
        ack.put("deviceId", DEVICE_ID);
        ack.put("ok", true);
        ack.put("code", 0);
        ack.put("message", "ok");
        ack.put("ts", System.currentTimeMillis());
        
        String json = ack.toString();
        out.println(json);
        System.out.println(">> Sent ACK: " + json);
    }
    
    private static void simulatePhysics() {
        if (machineRunning && !paused) {
            if (remainingSeconds > 0) remainingSeconds--;
            else if (!"智能模式".equals(mode)) {
                // Auto stop for timer modes
                machineRunning = false;
                heaterOn = false;
                disinfectionOn = false;
            }
            
            // Simple physics simulation
            if (heaterOn) temperature += 0.5;
            else temperature -= 0.2;
            
            if (fanOn) {
                humidity -= 1.0;
                temperature -= 0.3;
            } else {
                humidity += 0.1;
            }
            
            // Bounds
            if (humidity < 0) humidity = 0;
            if (humidity > 100) humidity = 100;
        }
    }
}

