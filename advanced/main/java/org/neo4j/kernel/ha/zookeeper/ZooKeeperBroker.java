package org.neo4j.kernel.ha.zookeeper;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.helpers.Pair;
import org.neo4j.kernel.ha.AbstractBroker;
import org.neo4j.kernel.ha.Master;
import org.neo4j.kernel.ha.MasterClient;
import org.neo4j.kernel.ha.MasterImpl;
import org.neo4j.kernel.ha.MasterServer;
import org.neo4j.kernel.ha.ResponseReceiver;

public class ZooKeeperBroker extends AbstractBroker
{
    private final ZooClient zooClient;
    private final String haServer;
    private MasterClient masterClient;
    private Machine master;
    private final int machineId;
    
    public ZooKeeperBroker( String storeDir, int machineId, String zooKeeperServers, 
            String haServer, ResponseReceiver receiver )
    {
        super( machineId );
        this.machineId = machineId;
        this.haServer = haServer;
        NeoStoreUtil store = new NeoStoreUtil( storeDir ); 
        this.zooClient = new ZooClient( zooKeeperServers, machineId, store.getCreationTime(),
                store.getStoreId(), store.getLastCommittedTx(), receiver, haServer );
    }
    
    public void invalidateMaster()
    {
        if ( masterClient != null )
        {
            masterClient.shutdown();
            masterClient = null;
        }
    }

    public Master getMaster()
    {
        if ( masterClient != null )
        {
            return masterClient;
        }
        
        master = zooClient.getMaster();
        if ( master != null && master.getMachineId() == getMyMachineId() )
        {
            throw new ZooKeeperException( "I am master, so can't call getMaster() here",
                    new Exception() );
        }
        invalidateMaster();
        connectToMaster( master );
        return masterClient;
    }
    
    public Machine getMasterMachine()
    {
        // Just to make sure it has gotten it
        getMaster();
        
        if ( master == null )
        {
            throw new IllegalStateException( "No master elected" );
        }
        return master;
    }

    private void connectToMaster( Machine machine )
    {
        Pair<String, Integer> host = machine != null ? machine.getServer() :
                new Pair<String, Integer>( null, -1 );
        masterClient = new MasterClient( host.first(), host.other() );
    }
    
    public Object instantiateMasterServer( GraphDatabaseService graphDb )
    {
        MasterServer server = new MasterServer( new MasterImpl( graphDb ),
                Machine.splitIpAndPort( haServer ).other() );
        zooClient.setDataChangeWatcher( ZooClient.MASTER_REBOUND_CHILD, machineId );
        return server;
    }

    public void setLastCommittedTxId( long txId )
    {
        zooClient.setCommittedTx( txId );
    }
    
    public boolean thisIsMaster()
    {
        return zooClient.getMaster().getMachineId() == getMyMachineId();
    }
    
    public void shutdown()
    {
        invalidateMaster();
        zooClient.shutdown();
    }
}
