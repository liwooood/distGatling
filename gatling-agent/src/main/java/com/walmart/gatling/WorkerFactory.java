package com.walmart.gatling;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.walmart.gatling.commons.AgentConfig;
import com.walmart.gatling.commons.Constants;
import com.walmart.gatling.commons.ScriptExecutor;
import com.walmart.gatling.commons.WorkExecutor;
import com.walmart.gatling.commons.Worker;

import java.util.HashSet;
import java.util.Set;

import akka.actor.ActorPath;
import akka.actor.ActorPaths;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.client.ClusterClient;
import akka.cluster.client.ClusterClientSettings;

public class WorkerFactory {

    public static ActorSystem startWorkersWithExecutors(AgentConfig agent) {
        Config conf = ConfigFactory.parseString("akka.cluster.roles=[" + agent.getActor().getRole() + "]")
                .withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port=" + agent.getActor().getPort()))
                .withFallback(ConfigFactory.load("application"));

        ActorSystem system = ActorSystem.create(Constants.PerformanceSystem, conf);

        Set<ActorPath> initialContacts = new HashSet<>();
        for (String contactAddress : conf.getStringList("contact-points")) {
            initialContacts.add(ActorPaths.fromString(contactAddress + "/system/receptionist"));
        }

        ClusterClientSettings settings =  ClusterClientSettings.create(system).withInitialContacts(initialContacts);
        final ActorRef clusterClient = system.actorOf(ClusterClient.props(settings), "clusterClient");

        for (int i=0;i<agent.getActor().getNumberOfActors();i++){
            system.actorOf(
                    Worker.props(clusterClient, createWorkExecutor(agent.getActor().getExecuterType(), agent), agent.getActor().getRole()),
                    agent.getActor().getRole()+i);
        }
        return system;

    }

    private static Props createWorkExecutor(String executorType, AgentConfig agentConfig){
       return Props.create(ScriptExecutor.class, agentConfig);
    }

}