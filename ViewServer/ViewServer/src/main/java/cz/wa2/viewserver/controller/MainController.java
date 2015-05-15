package cz.wa2.viewserver.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import cz.wa2.viewserver.object.Link;

/**
 * Preposila pozadavky na REST server a vraci, co vrati on
 * @author Tomas.Tisancin
 * 
 */
@Controller
public class MainController {

	private static final String serverLink = "http://localhost:8085/";

	@RequestMapping("/getAll")
	public String getAll() {
		RestTemplate restTemplate = new RestTemplate();
		Link page = restTemplate.getForObject(serverLink + "getAll", Link.class);
		return page.getLink();
	}

	@RequestMapping("/getPicture")
	public String getPicture(@RequestParam(value = "id") String id) {
		RestTemplate restTemplate = new RestTemplate();
		Link page = restTemplate.getForObject(serverLink + "getPicture?id=" + id, Link.class);
		return page.getLink();
	}

	@RequestMapping("/delete")
	public ResponseEntity<String> delete(@RequestParam(value = "id") String id) {
		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<String> confirm = restTemplate.getForEntity(serverLink + "delete?id=" + id, String.class);
		return confirm;
	}

	@RequestMapping("/resolve")
	public ResponseEntity<String> resolve(@RequestParam(value = "id") String id) {
		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<String> confirm = restTemplate.getForEntity(serverLink + "resolve?id=" + id, String.class);
		return confirm;
	}
}