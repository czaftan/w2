package cz.wa2.worker;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.json.JSONArray;
import org.json.JSONObject;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

public class Main {

	private static final String PUBLISH_ERRORS_TASK_QUEUE = "publish_error";
	
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

		channel.queueDeclare(PUBLISH_ERRORS_TASK_QUEUE, true, false, false, null);
		System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

		channel.basicQos(1);

		QueueingConsumer consumer = new QueueingConsumer(channel);
		channel.basicConsume(PUBLISH_ERRORS_TASK_QUEUE, false, consumer);

		while (true) {
			QueueingConsumer.Delivery delivery = consumer.nextDelivery();
			String message = new String(delivery.getBody());

			System.out.println(" [x] Received '" + message + "'");
			doWork(message);
			System.out.println(" [x] Done");

			channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
		}
	}

	private static void doWork(String message) {
		JSONObject msg = new JSONObject(message);
		String uri = msg.getString("uri");

		Session session = sessionFactory.openSession();
        
        Query query = session.createQuery("FROM Error");
        
        List<cz.wa2.entity.Error> errors = query.list();
        
        session.close();
		
		JSONArray data = new JSONArray();
		
		for (cz.wa2.entity.Error e : errors) {
			JSONArray row = new JSONArray();
			row.put(e.getId());
			row.put(e.getMessage());
			row.put(e.getPage().toString());
			row.put(e.getPage().getApplication().toString());
			row.put(e.getUser().toString());
			row.put(e.getComment());
			// Tohle by se melo generovat na klientovi, ale takhle je to jednodussi :-)
			row.put("<button type='button' id='" + e.getId() + "'>SHOW SCREENSHOT</button>");
			row.put("<button type='button' id='" + e.getId() + "'>CANCEL</button>");
			row.put("<button type='button' id='" + e.getId() + "'>RESOLVE</button>");
			data.put(row);
		}
		String publishableData = data.toString();
		Writer writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(uri), "utf-8"));
			writer.write(publishableData);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				writer.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
