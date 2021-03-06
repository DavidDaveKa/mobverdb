package de.uzl.mobverdb.join.modes.semi.sequential;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;

import de.uzl.mobverdb.join.JoinUtils;
import de.uzl.mobverdb.join.data.CSVData;
import de.uzl.mobverdb.join.data.Row;
import de.uzl.mobverdb.join.modes.JoinPerf;
import de.uzl.mobverdb.join.modes.MeasurableJoin;
import de.uzl.mobverdb.join.modes.semi.ISemiJoinClient;
import de.uzl.mobverdb.join.modes.semi.ISemiJoinServer;
import de.uzl.mobverdb.join.modes.semi.SemiJoinServer;
import de.uzl.utils.Threads;

public class SequentialSemiJoinServer extends UnicastRemoteObject implements ISemiJoinServer, MeasurableJoin {

    private static final long serialVersionUID = 2866604506401341939L;
    private final Logger log = Logger.getLogger(this.getClass().getCanonicalName());
    public static final String BIND_NAME = SemiJoinServer.BIND_NAME;
    private JoinPerf joinPerf = new JoinPerf("sequentialSemiJoin");

    private ArrayList<ISemiJoinClient> clients = new ArrayList<ISemiJoinClient>();
    private CSVData data;
    
    public SequentialSemiJoinServer(File file) throws NumberFormatException, IOException {
        super();
        this.data = new CSVData(file);
    }
    
    public void join() throws RemoteException, MalformedURLException, InterruptedException {
        Registry reg = LocateRegistry.createRegistry(Registry.REGISTRY_PORT);
        Naming.rebind(BIND_NAME, this);
        
        log.info("Waiting for client to connect");
        while(clients.size() < 2) {
            Threads.trySleep(1000);
        }
        log.info("Client connected. Beginning to join.");
        joinPerf.totalTime.start();
        
        log.info("Creating projection of local data");
        joinPerf.prepareTime.start();
        final Set<Integer> localKeys = new HashSet<Integer>();
        for(Row r : data.lines) {
            localKeys.add(r.getKey());
        }
        joinPerf.prepareTime.stop();
        
        log.info("Sending keys to clients (sequential)");
        joinPerf.remoteJoinTime.start();
        final ArrayList<Row[]> clientData = new ArrayList<Row[]>();
        for(final ISemiJoinClient client : clients) {
            clientData.add(client.joinOn(localKeys.toArray(new Integer[] {})));
            joinPerf.rmiCall();
        }
        joinPerf.remoteJoinTime.stop();
        
        joinPerf.localJoinTime.start();
        Row[] joinedData = data.lines;
        for(Row[] remoteData : clientData) {
            joinedData = JoinUtils.nestedLoopJoin(data.lines, remoteData);
        }
        joinPerf.localJoinTime.stop();
        joinPerf.totalTime.stop();
        for(ISemiJoinClient client : clients) {
            try {
                client.shutdown();
            } catch(Exception e) {
                // ignore this, we will exit anyway
            }
        }
        
        try {
            Naming.unbind(BIND_NAME);
            UnicastRemoteObject.unexportObject(reg, true);
        } catch(Exception e) {
            // ignore this, we will exit anyway
        }
    }

    @Override
    public void register(ISemiJoinClient client) throws RemoteException {
        clients.add(client);
    }

    @Override
    public JoinPerf getPerf() {
        return this.joinPerf;
    }
    
}


