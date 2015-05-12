package cz.wa2.restserver.application;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import cz.wa2.restserver.controller.MainController;

@ApplicationPath("/test")
public class RestApplication extends Application {

	public Set<Class<?>> getClasses() {
        return new HashSet<Class<?>>(Arrays.asList(MainController.class));
    }
}