package cz.wa2.worker;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.TimeoutException;

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
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.QueueingConsumer;

public class Main {

	private static final String PUBLISH_IMAGE_TASK_QUEUE = "publish_image";
	private static final String POISONED_TASK_QUEUE = "poison_queue";
	
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
			try {
				doWork(message);
			} catch (WorkerException e) {
				arrrrItsPoisoned(message);
			}
			System.out.println(" [x] Done");
			channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
		}
	}

	private static void doWork(String message) throws WorkerException {
		JSONObject msg = new JSONObject(message);
		Long id = msg.getLong("id");
		String uri = msg.getString("uri");
		
		Session session = sessionFactory.openSession();
        session.beginTransaction();
//        Query query = session.createQuery("FROM Error E WHERE E.id = " + id);
//        query.setMaxResults(1);
//        List<cz.wa2.entity.Error> errors = query.list();
        
        
        cz.wa2.entity.Error error;
        	error = (cz.wa2.entity.Error) session.get(
					cz.wa2.entity.Error.class, id);
        	
        session.getTransaction().commit();
    	session.close();

    	if(error == null)
    		throw new WorkerException();
		
		String image = error.getScreenshot();
		image = image.split(",")[1];
		byte[] data = image == null || image.isEmpty() ? new byte[0] : Base64.decodeBase64(image);
		try (OutputStream stream = new FileOutputStream("../publish/" + uri)) {
			stream.write(data);
		} catch (IOException e) {
			e.printStackTrace();
			throw new WorkerException();
		}
	}

	private static void arrrrItsPoisoned(String message) {
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost("localhost");
		Connection connection = null;
		Channel channel = null;
		
		// we have to get rid of the url cache entry. We don't know which error has the entry, we have to guess
		// we might hit a different error, but this way at least another request will be generated
		Session session = null;
		try {
			JSONObject msg = new JSONObject(message);
			Long id = msg.getLong("id");
			
			session = sessionFactory.openSession();
			session.beginTransaction();
			cz.wa2.entity.Error error;
        	error = (cz.wa2.entity.Error) session.get(
					cz.wa2.entity.Error.class, id);

        	if(error != null) {
				error.setScreenUrlDate(null);
				session.merge(error);
        	}
				
			session.getTransaction().commit();
		} catch(Exception e) {
			if(session.getTransaction().isActive())
				session.getTransaction().rollback();
		} finally {
			if(session != null)
				session.close();
		}
		
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
