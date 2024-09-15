package com.example;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class ConfigLoader {

    private static final String CONFIG_FILE_PATH = "/config/tokenize-config"; // Path where ConfigMap is mounted

    public static List<String> loadTokenizeElements() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(CONFIG_FILE_PATH))) {
            String line = reader.readLine();
            if (line != null && line.startsWith("tokenize.elements=")) {
                String elements = line.split("=", 2)[1];
                return Arrays.asList(elements.split(","));
            } else {
                throw new IOException("Invalid configuration format");
            }
        }
    }
}
