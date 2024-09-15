package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class EventStreamProcessor {

    private static final String PROTEGRITY_URL = "https://protegrity-api/tokenize";
    private static List<String> ELEMENTS_TO_TOKENIZE;

    static {
        try {
            ELEMENTS_TO_TOKENIZE = ConfigLoader.loadTokenizeElements();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load tokenizable elements", e);
        }
    }
    private static final String INPUT_TOPIC = "input_topic";
    private static final String INSERT_TOPIC = "insert_topic";
    private static final String UPDATE_TOPIC = "update_topic";
    private static final String DELETE_TOPIC = "delete_topic";
    private static final String GROUP_ID = "event-streams-group";

    private static KafkaConsumer<String, String> createConsumer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        return new KafkaConsumer<>(properties);
    }

    private static KafkaProducer<String, String> createProducer() {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        return new KafkaProducer<>(properties);
    }

    private static JsonNode tokenizeElement(JsonNode data) throws Exception {
        Client client = ClientBuilder.newClient();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode tokenizedData = (ObjectNode) data.deepCopy();

        for (String element : ELEMENTS_TO_TOKENIZE) {
            if (data.has(element)) {
                // Extract the element value to be tokenized
                String elementValue = data.get(element).asText();

                // Call Protegrity API for tokenization
                Response response = client.target(PROTEGRITY_URL)
                                          .request(MediaType.APPLICATION_JSON)
                                          .post(Entity.entity("{\"value\":\"" + elementValue + "\"}", MediaType.APPLICATION_JSON));
                if (response.getStatus() == 200) {
                    String tokenizedValue = response.readEntity(String.class);
                    // Replace the original element with the tokenized value
                    tokenizedData.put(element, tokenizedValue);
                } else {
                    throw new Exception("Failed to tokenize element: " + element + ", Status: " + response.getStatus());
                }
            }
        }

        return tokenizedData;
    }

    public static void main(String[] args) {
        KafkaConsumer<String, String> consumer = createConsumer();
        KafkaProducer<String, String> producer = createProducer();
        consumer.subscribe(Collections.singleton(INPUT_TOPIC));
        ObjectMapper mapper = new ObjectMapper();

        try {
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : records) {
                    String message = record.value();
                    JsonNode jsonNode = mapper.readTree(message);
                    JsonNode lastElement = jsonNode.get("lastElement");

                    // Tokenize elements
                    JsonNode tokenizedData = tokenizeElement(jsonNode);

                    // Add tokenized data to the original JSON structure
                    ((ObjectNode) jsonNode).set("tokenizedElement", tokenizedData);

                    // Determine the type of transaction
                    String transactionType = lastElement.asText();
                    String outputTopic;
                    switch (transactionType) {
                        case "insert":
                            outputTopic = INSERT_TOPIC;
                            break;
                        case "update":
                            outputTopic = UPDATE_TOPIC;
                            break;
                        case "delete":
                            outputTopic = DELETE_TOPIC;
                            break;
                        default:
                            throw new IllegalStateException("Unexpected value: " + transactionType);
                    }

                    // Send processed message to the appropriate topic
                    producer.send(new ProducerRecord<>(outputTopic, jsonNode.toString()));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            consumer.close();
            producer.close();
        }
    }
}
