package cz.wa2.worker;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeoutException;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONObject;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.QueueingConsumer;

public class Main {

	private static final String PUBLISH_IMAGE_TASK_QUEUE = "publish_image";
	private static final String POISONED_TASK_QUEUE = "poison_queue";

	public static void main(String[] argv)
			throws java.io.IOException,
			java.lang.InterruptedException {

		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost("localhost");
		Connection connection = factory.newConnection();
		Channel channel = connection.createChannel();

		channel.queueDeclare(PUBLISH_IMAGE_TASK_QUEUE, true, false, false, null);
		System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

		channel.basicQos(1);

		QueueingConsumer consumer = new QueueingConsumer(channel);
		channel.basicConsume(PUBLISH_IMAGE_TASK_QUEUE, false, consumer);

		while (true) {
			QueueingConsumer.Delivery delivery = consumer.nextDelivery();
			String message = new String(delivery.getBody());
			System.out.println(" [x] Received '" + message + "'");
			try {
				doWork(message);
			} catch (WorkerException e) {
				arrrrItsPoisoned(message);
			}
			System.out.println(" [x] Done");
		}
	}

	private static void doWork(String message) throws WorkerException {
		JSONObject msg = new JSONObject(message);
		Long id = msg.getLong("id");
		String uri = msg.getString("uri");
		// TODO: get screenshot z DB podle id
		String image = "";
		byte[] data = Base64.decodeBase64(image);
		try (OutputStream stream = new FileOutputStream("../publish/" + uri)) {
			stream.write(data);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void arrrrItsPoisoned(String message) {
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost("localhost");
		Connection connection = null;
		Channel channel = null;
		try {
			connection = factory.newConnection();
			channel = connection.createChannel();
			channel.queueDeclare(POISONED_TASK_QUEUE, true, false, false, null);
			channel.basicPublish("", POISONED_TASK_QUEUE,
					MessageProperties.PERSISTENT_TEXT_PLAIN,
					message.getBytes());
			System.out.println("POISONED task queued: " + message + "");
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (channel != null) {
				try {
					channel.close();
				} catch (IOException | TimeoutException e) {
					e.printStackTrace();
				}
			}
			if (connection != null) {
				try {
					connection.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
