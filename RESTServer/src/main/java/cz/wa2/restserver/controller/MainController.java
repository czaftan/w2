package cz.wa2.restserver.controller;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.codec.digest.DigestUtils;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.StaleStateException;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.json.JSONArray;
import org.json.JSONObject;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;

import cz.wa2.entity.Application;
import cz.wa2.entity.Page;
import cz.wa2.entity.User;

@Path("/")
public class MainController {

	private static final String PUBLISH_IMAGE_TASK_QUEUE = "publish_image";
	private static final String PUBLISH_ERRORS_TASK_QUEUE = "publish_error";
	private static final String FILE_STERVER = "http://localhost:8083";

	private StandardServiceRegistry serviceRegistry;
	private SessionFactory sessionFactory;

	{
		Configuration configuration = new Configuration().configure();
		serviceRegistry = new StandardServiceRegistryBuilder().applySettings(
				configuration.getProperties()).build();
		sessionFactory = configuration.configure().buildSessionFactory(
				serviceRegistry);
	}

	@POST
	@Path("/report")
	public Response report(MultivaluedMap<String, String> formParams) {
		JSONArray params = new JSONArray(formParams.getFirst("data"));
		JSONObject obj = params.getJSONObject(0);
		String base64img = params.length() > 1 ? params.getString(1) : null;
		String errorDescription = obj.getString("Chyba");
		String pageUrl = obj.getString("page");
		String application = obj.getString("application");
		String email = "dummy@example.com";
		// hash should be 6e8e0bf6135471802a63a17c5e74ddc5
		String title = "";

		Session session = sessionFactory.openSession();
		session.beginTransaction();

		User user;
		String hash = DigestUtils.md5Hex(email);
		String hql = "FROM User U WHERE U.emailHash LIKE '" + hash + "'";
		Query query = session.createQuery(hql);

		List<User> userList = query.list();
		if (userList.isEmpty()) {
			// user = new User();
			// user.setFqn("Dummy");
			// user.setEmail(email);
			// user.setEmailHash(hash);
			// session.persist(user);
			session.getTransaction().rollback();
			session.close();
			return Response.status(Status.BAD_REQUEST)
					.entity("Unknown user: " + email).build();
		} else
			user = userList.get(0);

		Application app;
		hash = DigestUtils.md5Hex(application);
		hql = "FROM Application A WHERE A.nameHash LIKE '" + hash + "'";
		query = session.createQuery(hql);

		List<Application> appList = query.list();
		if (appList.isEmpty()) {
			// app = new Application();
			// app.setAdmin("totoro@example.com");
			// app.setName(application);
			// app.setNameHash(hash);
			// session.persist(app);
			session.getTransaction().rollback();
			session.close();
			return Response.status(Status.BAD_REQUEST)
					.entity("Unknown application: " + application).build();
		} else
			app = appList.get(0);

		session.flush();

		Page page;
		hash = DigestUtils.md5Hex(pageUrl);
		hql = "FROM Page P WHERE P.urlHash LIKE '" + hash + "'";
		query = session.createQuery(hql);

		List<Page> pageList = query.list();
		if (pageList.isEmpty()) {
			page = new Page();
			page.setTitle(title);
			page.setUrl(pageUrl);
			page.setUrlHash(hash);
			page.setApplication(app);
			session.persist(page);
		} else
			page = pageList.get(0);
		
		if(!page.getApplication().equals(app)) {
			session.getTransaction().rollback();
			session.close();
			return Response.status(Status.BAD_REQUEST)
					.entity("Page already exists but under different application: " + application).build();
		}

		cz.wa2.entity.Error error = new cz.wa2.entity.Error();
		error.setMessage(errorDescription);
		error.setUser(user);
		error.setResolved(false);
		error.setCanceled(false);
		error.setPage(page);
		error.setUser(user);
		error.setScreenshot(base64img);
		error.setReportUrlDate(null);
		error.setScreenUrlDate(null);

		session.persist(error);

		session.getTransaction().commit();
		session.close();

		return Response.ok().build();
	}

	@GET
	@Path("/getPicture/{errorId}")
	public String getPicture(@PathParam("errorId") Long errorId)
			throws IOException, TimeoutException {
		// musi vracet cestu k publishnutemu obrazku

		String uri = "";

		boolean notFinished = true;
		int counter = 3;
		while (notFinished) {

			Session session = sessionFactory.openSession();
			session.beginTransaction();

			cz.wa2.entity.Error error = (cz.wa2.entity.Error) session.get(cz.wa2.entity.Error.class, errorId);

			if (error.getScreenshot() == null) {
				// no screenshot to generate
				session.getTransaction().commit();
				session.close();
				break;
			}

			boolean generate = false;
			if (error.getScreenUrlDate() == null) {
				generate = true;
			} else {
				uri = FILE_STERVER + error.getScreenUrl();
				URL u = new URL(uri);
				HttpURLConnection huc = (HttpURLConnection) u.openConnection();
				huc.setRequestMethod("GET");
				
				try {
					huc.connect();
				} catch(IOException ex) {
					session.getTransaction().rollback();
					session.close();
					throw ex;
				} finally {
					huc.disconnect();
				}
				
				int code = huc.getResponseCode();
				if (code != 200) {
					generate = true;
				} else {
					session.getTransaction().commit();
					session.close();
					return uri;
				}
			}

			if (generate) {
				UUID imageName = UUID.randomUUID();
				uri = "/images/" + imageName + ".png";

				try {
					error.setScreenUrl(uri);
					error.setScreenUrlDate(new Date());
					session.merge(error);

					session.getTransaction().commit();
					notFinished = false;
				} catch (StaleStateException e) {
					session.getTransaction().rollback();
					if (counter > 0) {
						--counter;
						continue;
					} // in else case it just gives up on caching it
				} finally {
					session.close();
				}

				ConnectionFactory factory = new ConnectionFactory();
				factory.setHost("localhost");
				Connection connection = factory.newConnection();
				Channel channel = connection.createChannel();

				channel.queueDeclare(PUBLISH_IMAGE_TASK_QUEUE, true, false, false, null);

				JSONObject task = new JSONObject();
				task.put("id", Long.valueOf(errorId));
				task.put("uri", uri);
				String message = task.toString();

				channel.basicPublish("", PUBLISH_IMAGE_TASK_QUEUE,
						MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes());
				System.out.println("Task PUBLISH_IMAGE queued: " + message + "'");

				channel.close();
				connection.close();
			}

			if (session.isOpen())
				session.close();
		}

		return FILE_STERVER + uri;
	}

	@GET
	@Path("/getAll")
	public String getAll() throws IOException, TimeoutException {

		String uri = "";

		boolean notFinished = true;
		int counter = 3;
		while (notFinished) {

			Session session = sessionFactory.openSession();
			session.beginTransaction();

			Query q = session.createQuery("FROM Error e WHERE e.reportUrlDate = NULL");
			// q.setMaxResults(1);

			List<cz.wa2.entity.Error> errors = q.list();

			boolean generate = false;

			if (errors.isEmpty()) {
				q = session.createQuery("FROM Error e ORDER BY e.reportUrlDate DESC");
				q.setMaxResults(1);

				errors = q.list();

				if (errors.isEmpty()) {
					// no errors in the database, generates an empty report
					notFinished = false;
					generate = true;
				} else {

					cz.wa2.entity.Error error = errors.get(0);
					String reportUrl = error.getReportUrl();

					if (reportUrl == null)
						generate = true;
					else {
						uri = FILE_STERVER + reportUrl;
						URL u = new URL(uri);
						HttpURLConnection huc = (HttpURLConnection) u.openConnection();
						huc.setRequestMethod("GET");
						
						try {
							huc.connect();
						} catch(IOException ex) {
							session.getTransaction().rollback();
							session.close();
							throw ex;
						} finally {
							huc.disconnect();
						}
						
						int code = huc.getResponseCode();
						if (code != 200) {
							generate = true;
						} else {
							session.getTransaction().commit();
							session.close();
							return uri;
						}
					}
				}
			} else {
				generate = true;
			}

			if (generate) {
				UUID dataPath = UUID.randomUUID();
				uri = "/data/" + dataPath + ".txt";

				try {
					for (cz.wa2.entity.Error error : errors) {
						error.setReportUrl(uri);
						error.setReportUrlDate(new Date());
						session.merge(error);
					}
					session.getTransaction().commit();
					notFinished = false;
				} catch (StaleStateException e) {
					session.getTransaction().rollback();
					if (counter > 0) {
						--counter;
						continue;
					} // in else case it just gives up on caching it
				} finally {
					session.close();
				}

				ConnectionFactory factory = new ConnectionFactory();
				factory.setHost("localhost");
				Connection connection = factory.newConnection();
				Channel channel = connection.createChannel();

				channel.queueDeclare(PUBLISH_ERRORS_TASK_QUEUE, true, false, false,
						null);

				JSONObject task = new JSONObject();
				task.put("uri", uri);
				String message = task.toString();

				channel.basicPublish("", PUBLISH_ERRORS_TASK_QUEUE,
						MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes());
				System.out.println("Task PUBLISH_ERRORS queued: " + message + "");

				channel.close();
				connection.close();
			}

			if (session.isOpen())
				session.close();
		}

		return FILE_STERVER + uri;
	}

	@GET
	@Path("/delete/{errorId}")
	public Response delete(@PathParam("errorId") Long errorId) {
		// synchronni - vraci ihned potvrzeni
		boolean notFinished = true;
		int counter = 3;
		while (notFinished) {
			Session session = sessionFactory.openSession();
			session.beginTransaction();

			cz.wa2.entity.Error error = (cz.wa2.entity.Error) session.get(
					cz.wa2.entity.Error.class, errorId);

			if (error == null) {
				session.getTransaction().rollback();
				session.close();
				return Response.status(Status.BAD_REQUEST)
						.entity("Unknown error id: " + errorId).build();
			}

			try {
				error.setCanceled(true);
				error.setReportUrl(null);
				error.setReportUrlDate(null);
				session.merge(error);

				session.getTransaction().commit();
				notFinished = false;
			} catch (StaleStateException ex) {
				session.getTransaction().rollback();
				if (counter <= 0)
					return Response.status(Status.INTERNAL_SERVER_ERROR)
							.entity("Server failed to to update the error")
							.build();
				--counter;
				continue;
			} finally {
				session.close();
			}

		}
		return Response.ok().build();
	}

	@GET
	@Path("/resolve/{errorId}")
	public Response resolve(@PathParam("errorId") Long errorId) {
		// synchronni - vraci ihned potvrzeni

		boolean notFinished = true;
		int counter = 3;
		while (notFinished) {
			Session session = sessionFactory.openSession();
			session.beginTransaction();

			cz.wa2.entity.Error error = (cz.wa2.entity.Error) session.get(
					cz.wa2.entity.Error.class, errorId);

			if (error == null) {
				session.getTransaction().rollback();
				session.close();
				return Response.status(Status.BAD_REQUEST)
						.entity("Unknown error id: " + errorId).build();
			}

			try {
				error.setResolved(true);
				error.setReportUrl(null);
				error.setReportUrlDate(null);
				session.merge(error);

				session.getTransaction().commit();
				notFinished = false;
			} catch (StaleStateException ex) {
				session.getTransaction().rollback();
				if (counter <= 0)
					return Response.status(Status.INTERNAL_SERVER_ERROR)
							.entity("Server failed to to update the error")
							.build();
				--counter;
				continue;
			} finally {
				session.close();
			}

		}

		return Response.ok().build();
	}

	private static final Random rnd = new Random();

	@GET
	@Path("/getLoad")
	public Integer getLoad() {
		return rnd.nextInt(100);
	}
}
