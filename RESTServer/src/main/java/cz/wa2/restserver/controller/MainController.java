package cz.wa2.restserver.controller;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/test")
public class MainController {

	@GET
	@Path("/test")
	public String pojo() {
		return "test";
	}
}
