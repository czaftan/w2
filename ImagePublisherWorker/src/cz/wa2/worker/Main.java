package cz.wa2.worker;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.json.JSONObject;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

public class Main {

	private static final String PUBLISH_IMAGE_TASK_QUEUE = "publish_image";
	
	private static StandardServiceRegistry serviceRegistry;
    private static SessionFactory sessionFactory;
    
    static {
    	Configuration configuration = new Configuration().configure();
        serviceRegistry = new StandardServiceRegistryBuilder().applySettings(configuration.getProperties()).build();
        sessionFactory = configuration.configure().buildSessionFactory(serviceRegistry);
    }

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
			doWork(message);
			System.out.println(" [x] Done");

			//channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
		}
	}

	private static void doWork(String message) {
		JSONObject msg = new JSONObject(message);
		Long id = msg.getLong("id");
		String uri = msg.getString("uri");
		// TODO: get screenshot z DB podle id
		
		Session session = sessionFactory.openSession();
        
        Query query = session.createQuery("FROM Error E WHERE E.id = " + id);
        
        List<cz.wa2.entity.Error> errors = query.list();
        
        session.close();
        
        cz.wa2.entity.Error error = errors.get(0);
		
		String image = error.getScreenshot();
		byte[] data = Base64.decodeBase64(image);
		try (OutputStream stream = new FileOutputStream(uri)) {
			stream.write(data);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
