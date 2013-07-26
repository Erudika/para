/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.erudika.para.utils;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;


/**
 * Web application lifecycle listener.
 * @author alexb
 */

public class AppListener implements ServletContextListener, HttpSessionListener {
	
	private static Client searchClient;
//    public static Node searchNode;
	
	public void contextInitialized(ServletContextEvent sce) {
		ImmutableSettings.Builder settings = ImmutableSettings.settingsBuilder();
		settings.put("cluster.name", Utils.ES_CLUSTER);
		settings.put("client.transport.sniff", true);

//		if (Utils.IN_PRODUCTION) {
//			if (!org.tuckey.web.filters.urlrewrite.utils.StringUtils.isBlank(Utils.AWS_ACCESSKEY)) {
//				settings.put("cloud.aws.access_key", Utils.AWS_ACCESSKEY);
//			}
//			if (!org.tuckey.web.filters.urlrewrite.utils.StringUtils.isBlank(Utils.AWS_SECRETKEY)) {
//				settings.put("cloud.aws.secret_key", Utils.AWS_SECRETKEY);
//			}
//			settings.put("cloud.aws.region", "eu-west-1");
//			settings.put("network.tcp.keep_alive", true);
//			settings.put("index.number_of_shards", 5);
//			settings.put("index.number_of_replicas", 0);
//			settings.put("path.data", "/var/lib/elasticsearch/data");
//			settings.put("path.work", "/var/lib/elasticsearch/work");
//			settings.put("path.logs", "/var/log/elasticsearch/");
//
//			settings.put("discovery.type", "ec2");
//			settings.put("discovery.ec2.groups", "elasticsearch");
////			settings.put("discovery.ec2.availability_zones", "eu-west-1a");
//		}

		searchClient = new TransportClient(settings.build());
		if (Utils.IN_PRODUCTION) {
			String[] eshosts = Utils.ES_HOSTS.split(",");
			for (String host : eshosts) {
				((TransportClient) searchClient).addTransportAddress(
						new InetSocketTransportAddress(host, 9300));
			}
		} else {
			((TransportClient) searchClient).addTransportAddress(
					new InetSocketTransportAddress("localhost", 9300));
		}
	}

	public void contextDestroyed(ServletContextEvent sce) {
		if(searchClient != null){ 
			searchClient.close();
		}
		com.amazonaws.http.IdleConnectionReaper.shutdown();
	}
	
	public void sessionCreated(HttpSessionEvent se) { }
	public void sessionDestroyed(HttpSessionEvent se) { }
	
	public static Client getSearchClient(){
		return searchClient;
	}
}