package cz.wa2.restserver.controller;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;

@Path("/")
public class MainController {

	private static final String PUBLISH_IMAGE_TASK_QUEUE = "publish_image";
	private static final String PUBLISH_ERRORS_TASK_QUEUE = "publish_error";

	@POST
	@Path("/report")
	public Response report(MultivaluedMap<String, String> formParams) {
		JSONArray params = new JSONArray(formParams.getFirst("data"));
		JSONObject obj = params.getJSONObject(0);
		String base64img = params.getString(1);
		String error = obj.getString("Chyba");
		String page = obj.getString("page");
		String application = obj.getString("application");
		String user = "Dummy";
		String email = "dummy@dummy.com";
		String title = "";
		// TODO: Ulozit do DB
		return Response.ok().build();
	}

	@GET
	@Path("/getPicture")
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

		return uri;
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

		channel.basicPublish("", PUBLISH_IMAGE_TASK_QUEUE,
				MessageProperties.PERSISTENT_TEXT_PLAIN,
				message.getBytes());
		System.out.println("Task PUBLISH_ERRORS queued: " + message + "'");

		channel.close();
		connection.close();

		return uri;
	}

	@GET
	@Path("/delete")
	public Response delete(@PathParam("errorId") String errorId) {
		// synchronni - vraci ihned potvrzeni
		// TODO: get z db a nastavit canceled na true
		return Response.ok().build();
	}

	@GET
	@Path("/resolve")
	public Response resolve(@PathParam("errorId") String errorId) {
		// synchronni - vraci ihned potvrzeni
		// TODO: get z db a nastavit status na resolved
		return Response.ok().build();
	}
}
