package cz.wa2.worker;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.hibernate.Criteria;
import org.hibernate.FetchMode;
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
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.QueueingConsumer;

import cz.wa2.entity.User;

public class Main {

	private static final String PUBLISH_ERRORS_TASK_QUEUE = "publish_error";
	private static final String POISONED_TASK_QUEUE = "poison_queue";

	private static StandardServiceRegistry serviceRegistry;
	private static SessionFactory sessionFactory;

	static {
		Configuration configuration = new Configuration().configure();
		serviceRegistry = new StandardServiceRegistryBuilder().applySettings(
				configuration.getProperties()).build();
		sessionFactory = configuration.configure().buildSessionFactory(
				serviceRegistry);
	}

	public static void main(String[] argv) throws java.io.IOException,
			java.lang.InterruptedException {

		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost("localhost");
		Connection connection = factory.newConnection();
		Channel channel = connection.createChannel();

		channel.queueDeclare(PUBLISH_ERRORS_TASK_QUEUE, true, false, false,
				null);
		System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

		channel.basicQos(1);

		QueueingConsumer consumer = new QueueingConsumer(channel);
		channel.basicConsume(PUBLISH_ERRORS_TASK_QUEUE, false, consumer);

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
		String uri = msg.getString("uri");

		Session session = sessionFactory.openSession();
		session.beginTransaction();
		// Query query = session.createQuery("FROM Error");

		Criteria q = session.createCriteria(cz.wa2.entity.Error.class);
		q.setFetchMode("user", FetchMode.JOIN);
		q.setFetchMode("page", FetchMode.JOIN);
		q.setFetchMode("candidates", FetchMode.JOIN);

		List<cz.wa2.entity.Error> errors = q.list();
		session.getTransaction().commit();
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
			String candidates = "";
			if (e.getCandidates().size() > 0) {
				for (User c : e.getCandidates()) {
					candidates += ", " + c.toString();
				}
				candidates = candidates.substring(1, candidates.length());
			}
			row.put(candidates);
			// Tohle by se melo generovat na klientovi, ale takhle je to
			// jednodussi :-)

			row.put("<button type='button'" + (e.getScreenshot() == null ? "disabled='disabled'" : "")
					+ " class='screenshot' id='" + e.getId()
					+ "'>SHOW SCREENSHOT</button>");
			row.put("<button type='button'"
					+ (e.isCanceled() ? "disabled='disabled'" : "")
					+ " class='cancel' id='" + e.getId() + "'>CANCEL</button>");
			row.put("<button type='button'"
					+ (e.isResolved() ? "disabled='disabled'" : "")
					+ " class='resolve' id='" + e.getId()
					+ "'>RESOLVE</button>");
			data.put(row);
		}
		String publishableData = data.toString();
		Writer writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream("../publish" + uri), "utf-8"));
			writer.write(publishableData);
		} catch (IOException e) {
			e.printStackTrace();
			throw new WorkerException();
		} finally {
			try {
				writer.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
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
			session = sessionFactory.openSession();
			session.beginTransaction();
			Query q = session.createQuery("FROM Error e ORDER BY e.reportUrlDate DESC");
			q.setMaxResults(1);
			List<cz.wa2.entity.Error> errors = q.list();
			if(!errors.isEmpty()) {
				cz.wa2.entity.Error error = errors.get(0);
				error.setReportUrlDate(null);
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
					MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes());
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
