package cz.wa2.restserver.controller;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.codec.digest.Md5Crypt;
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

import cz.wa2.entity.Application;
import cz.wa2.entity.Page;
import cz.wa2.entity.User;

@Path("/")
public class MainController {

	private static final String PUBLISH_IMAGE_TASK_QUEUE = "publish_image";
	private static final String PUBLISH_ERRORS_TASK_QUEUE = "publish_error";
	
	private static StandardServiceRegistry serviceRegistry;
    private static SessionFactory sessionFactory;
    
    static {
    	Configuration configuration = new Configuration().configure();
        serviceRegistry = new StandardServiceRegistryBuilder().applySettings(configuration.getProperties()).build();
        sessionFactory = configuration.configure().buildSessionFactory(serviceRegistry);
    }
	

	@POST
	@Path("/report")
	public Response report(MultivaluedMap<String, String> formParams) {
		JSONArray params = new JSONArray(formParams.getFirst("data"));
		JSONObject obj = params.getJSONObject(0);
		String base64img = params.getString(1);
		String errorDescription = obj.getString("Chyba");
		String pageUrl = obj.getString("page");
		String application = obj.getString("application");
		String email = "dummy@example.com";
		//hash should be 6e8e0bf6135471802a63a17c5e74ddc5
		String title = "";
			
        Session session = sessionFactory.openSession();
        session.beginTransaction();
        
        User user;
        String hash = DigestUtils.md5Hex(email);
        String hql = "FROM User U WHERE U.emailHash AS '" + hash + "'";
        Query query = session.createQuery(hql);
        
        List<User> userList = query.list(); 
        if(userList.isEmpty()) {
//        	user = new User();
//        	user.setFqn("Dummy");
//        	user.setEmail(email);
//        	user.setEmailHash(hash);
//        	session.persist(user);
        	session.getTransaction().rollback();
        	session.close();
        	return Response.status(Status.BAD_REQUEST).entity("Unknown user: " + email).build();
        } else
        	user = userList.get(0);
        
        Application app;
        hash = DigestUtils.md5Hex(application);
        hql = "FROM Application A WHERE A.nameHash AS '" + hash + "'";
        query = session.createQuery(hql);
        
        List<Application> appList = query.list(); 
        if(appList.isEmpty()) {
//        	app = new Application();
//        	app.setAdmin("totoro@example.com");
//        	app.setName(application);
//        	app.setNameHash(hash);
//        	session.persist(app);
        	session.getTransaction().rollback();
        	session.close();
        	return Response.status(Status.BAD_REQUEST).entity("Unknown application: " + application).build();
        } else
        	app = appList.get(0);
        
        session.flush();
        
        Page page;
        hash = DigestUtils.md5Hex(pageUrl);
        hql = "FROM Page P WHERE P.urlHash AS '" + hash + "'";
        query = session.createQuery(hql);
        
        List<Page> pageList = query.list();        
        if(pageList.isEmpty()) {
        	page = new Page();
        	page.setTitle(title);
        	page.setUrl(pageUrl);
        	page.setUrlHash(hash);
        	page.setApplication(app);
        	session.persist(page);
        } else
        	page = pageList.get(0);
        
        
        cz.wa2.entity.Error error = new cz.wa2.entity.Error();
        error.setMessage(errorDescription);
        error.setUser(user);
        error.setResolved(false);
        error.setCanceled(false);
        error.setPage(page);
        error.setUser(user);
        error.setScreenshot(base64img);
        
        session.persist(error);
        
        session.getTransaction().commit();
        session.close();

		return Response.ok().build();
	}

	@GET
	@Path("/getPicture/{errorId}")
	public String getPicture(@PathParam("errorId") String errorId) throws IOException, TimeoutException {
		// musi vracet cestu k publishnutemu obrazku
		UUID imageName = UUID.randomUUID();
		String uri = "/images/" + imageName + ".png";

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
				MessageProperties.PERSISTENT_TEXT_PLAIN,
				message.getBytes());
		System.out.println("Task PUBLISH_IMAGE queued: " + message + "'");

		channel.close();
		connection.close();

		return "http://localhost:8083" + uri;
	}

	@GET
	@Path("/getAll")
	public String getAll() throws IOException, TimeoutException {
		UUID dataPath = UUID.randomUUID();
		String uri = "/data/" + dataPath + ".txt";

		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost("localhost");
		Connection connection = factory.newConnection();
		Channel channel = connection.createChannel();

		channel.queueDeclare(PUBLISH_ERRORS_TASK_QUEUE, true, false, false, null);

		JSONObject task = new JSONObject();
		task.put("uri", uri);
		String message = task.toString();

		channel.basicPublish("", PUBLISH_ERRORS_TASK_QUEUE,
				MessageProperties.PERSISTENT_TEXT_PLAIN,
				message.getBytes());
		System.out.println("Task PUBLISH_ERRORS queued: " + message + "");

		channel.close();
		connection.close();

		return "http://localhost:8083" + uri;
	}

	@GET
	@Path("/delete/{errorId}")
	public Response delete(@PathParam("errorId") String errorId) {
		// synchronni - vraci ihned potvrzeni
		Session session = sessionFactory.openSession();
        session.beginTransaction();
        
        cz.wa2.entity.Error error = (cz.wa2.entity.Error) session.get(cz.wa2.entity.Error.class, errorId);
        
        if(error == null) {
        	session.getTransaction().rollback();
        	session.close();
        	return Response.status(Status.BAD_REQUEST).entity("Unknown error id: " + errorId).build();
        }
        
        error.setCanceled(true);
    	session.merge(error);
        
        session.getTransaction().commit();
        session.close();
				
		return Response.ok().build();
	}

	@GET
	@Path("/resolve/{errorId}")
	public Response resolve(@PathParam("errorId") String errorId) {
		// synchronni - vraci ihned potvrzeni
		Session session = sessionFactory.openSession();
        session.beginTransaction();
        
        cz.wa2.entity.Error error = (cz.wa2.entity.Error) session.get(cz.wa2.entity.Error.class, errorId);
        
        if(error == null) {
        	session.getTransaction().rollback();
        	session.close();
        	return Response.status(Status.BAD_REQUEST).entity("Unknown error id: " + errorId).build();
        }
        
        error.setResolved(true);
    	session.merge(error);
        
        session.getTransaction().commit();
        session.close();
				
		return Response.ok().build();
	}
}
