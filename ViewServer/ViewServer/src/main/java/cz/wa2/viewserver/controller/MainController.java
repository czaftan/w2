package cz.wa2.viewserver.controller;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

/**
 * Preposila pozadavky na REST server a vraci, co vrati on
 * @author Tomas.Tisancin
 * 
 */
@Controller
public class MainController {

	private static final String serverLink = "http://localhost:8085/RESTServer/";

	@RequestMapping(value = "/", method = RequestMethod.GET)
    public String root() {
        return "index";
    }

	@RequestMapping("/getAll")
	public ResponseEntity<String> getAll() {
		RestTemplate restTemplate = new RestTemplate();
		HttpHeaders headers = new HttpHeaders();
//		headers.set("Accept", "text/html");
		headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
		headers.set("Accept-Encoding", "gzip, deflate, sdch");
		headers.set("Accept-Language", "cs-CZ,cs;q=0.8,en;q=0.6");
		headers.set("Cache-Control", "max-age=0");
		headers.set("Connection", "keep-alive");
		headers.set("Host", "localhost:8085");
		HttpEntity entity = new HttpEntity(headers);
		ResponseEntity<String> page = restTemplate.exchange(serverLink + "getAll", HttpMethod.GET, entity, String.class);
		return page;
	}

	@RequestMapping("/getPicture")
	public String getPicture(@RequestParam(value = "id") String id) {
		RestTemplate restTemplate = new RestTemplate();
//		Link page = restTemplate.getForObject(serverLink + "getPicture?id=" + id, Link.class);
//		return page.getLink();
		return "";
	}

	@RequestMapping("/delete")
	public ResponseEntity<String> delete(@RequestParam(value = "id") String id) {
		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<String> confirm = restTemplate.getForEntity(serverLink + "delete/" + id, String.class);
		return confirm;
	}

	@RequestMapping("/resolve")
	public ResponseEntity<String> resolve(@RequestParam(value = "id") String id) {
		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<String> confirm = restTemplate.getForEntity(serverLink + "resolve/" + id, String.class);
		return confirm;
	}
}