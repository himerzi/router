import java.lang.Math;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

class DVRoutingTableEntry implements RoutingTableEntry
{
    private int destination;
    //    interface
    private int iface;
    private int metric;
    private int time;

    public DVRoutingTableEntry(int d, int i, int m, int t)
    {
        this.setDestination(d);
        this.setInterface(i);
        this.setMetric(m);
        this.setTime(t);

    }
    public int getDestination() { return destination; }
    public void setDestination(int d) { this.destination = d;}
    public int getInterface() { return iface;}
    public void setInterface(int i) { this.iface = i;}

    public int getMetric() { return metric;}
    public void setMetric(int m) { this.metric = m;}
    public int getTime() {return time;}
    public void setTime(int t) { this.time = t;}

    public String toString()
    {
        return "";
    }
}

public class DV implements RoutingAlgorithm {
    
    static int LOCAL = -1;
    static int UNKNOWN = -2;
    static int INFINITY = 60;


    private HashMap<Integer, DVRoutingTableEntry> routingTable = new HashMap<Integer, DVRoutingTableEntry>();
    private Router router;
    private int updateInterval;
    //counts timeout on interfaces, so we know when links are down
    private boolean[] ifTimeOut;
    private int timeCount = 0;
    public DV()
    {
    }
//    TODO implement this shit
//    TODO check for cisco problem
    public void setRouterObject(Router obj)
    {
        router = obj;
    }
    
    public void setUpdateInterval(int u)
    {
        updateInterval = u;
        //getInterfaceState
        //rfc2453#section-3.4.1 uses 6 * update interval as the link timeout value (since, in theory, but not in this
        //simulator, routing packets can get lost)
        //So, if we don't hear a routing packed on a particular interface for 6 * u  seconds, we assume the link is down.
    }
    
    public void setAllowPReverse(boolean flag)
    {
    }
    
    public void setAllowExpire(boolean flag)
    {
    }
    
    public void initalise()
    {
//        Startup: initialize table to contain one entry for the local interface -1. metric should be 0.
//        TODO check ttl
        int selfid = router.getId();
        DVRoutingTableEntry self = new DVRoutingTableEntry(selfid, -1, 0, 0);
        routingTable.put(selfid, self);
        // int arrays are automatically initialised to 0 in java.
        ifTimeOut = new boolean[router.getNumInterfaces()];

    }

    public int getNextHop(int destination)
    {
        return routingTable.containsKey(destination) ? routingTable.get(destination).getInterface() : UNKNOWN;
    }
    
    public void tidyTable()
    {
        // TODO delare 6 as a constant.
        if(timeCount >= 1){
            for(int i = 0; i < ifTimeOut.length ; i++){
                //if 6 timesteps have passed, and we havent received any routing packets on this interface
                //then we shall assume it is down
                if(ifTimeOut[i] == false){
                    setInfInterface(i);
                }
                //otherwise, reset its value.
                else {
                    ifTimeOut[i] = false;
                }
            }
            timeCount = 0;
        }

    }
    
    public Packet generateRoutingPacket(int iface)
    {
        Payload pd = generateRoutingPayload();
        Packet p = new RoutingPacket(router.getId(),Packet.BROADCAST);
        p.setPayload(pd);
        return p;
    }


    public void processRoutingPacket(Packet p, int iface)
    {
        //add a 1, to indicate we've heard from this interface
        // TODO make this a boolean flag instead.
        ifTimeOut[iface] = true;
        //increase the timer. We'll check for timed out interfaces every 6 timesteps
        timeCount++;
        Vector foreignTable = getPacketTableEntries(p);
        int metric =  router.getInterfaceWeight(iface);
        DVTableMerge(foreignTable, iface, metric);
    }
    
    public void showRoutes()
    {
        System.out.format("Router %d%n", router.getId());
        for(RoutingTableEntry entry : routingTable.values()){
            System.out.format("d %d i %d m %d%n",entry.getDestination(), entry.getInterface(), entry.getMetric());
        }

    }

    /**
     * set all entries in the routing table for a particular interface to infinity. Called if we suspect the link for
     * that interface to be down
     * @param iface
     */
    private void setInfInterface(int iface){
        //System.out.println("seting iface inf  "  + iface + " router " + router.getId());
        for(DVRoutingTableEntry entry : routingTable.values()){
            if(entry.getInterface() == iface){
                entry.setMetric(INFINITY);
                routingTable.put(entry.getDestination(), entry);
            }

        }
    }
    /**
     * serializes routing table to a payload object
     * @return
     */
    private Payload generateRoutingPayload()
    {
        Payload p = new Payload();
        for(DVRoutingTableEntry entry : routingTable.values()){
            p.addEntry(entry);
        }
        return p;
    }
    private Vector<DVRoutingTableEntry> getPacketTableEntries(Packet p)
    {
//        Ideally, payload should be implemented as Vector<RoutingTableEntry>, but it is currently implemented as
//        Vector<Object>, which means we have to do this.
        Vector<DVRoutingTableEntry> entries = (Vector)p.getPayload().getData();;
        return entries;
    }

    /**
     *
     * @param externalTable a routing table sent from a different router
     * @param iface is the interface the routing packet came in on
     * @param metric is the metric for iface
     */
    private void DVTableMerge(Vector<DVRoutingTableEntry> externalTable, int iface, int metric)
    {
            for (DVRoutingTableEntry extEntry : externalTable) {
                compareAndModifyTableEntries(extEntry, iface, metric);
            }
    }

    /**
     *
     * @param externalEntry a routing table entry which was received on a routing packet
     * @param iface is the interface the routing packet came in on
     * @param metric is the metric for iface
     */
    //TODO separate the DV algo from the table mutation commands ie two methods
    public void compareAndModifyTableEntries(DVRoutingTableEntry externalEntry, int iface, int metric)
    {
        int key = externalEntry.getDestination();
//      r = lookup(D) in routing table
        DVRoutingTableEntry myEntry = routingTable.get(key);

        int newMetric = (externalEntry.getMetric() + metric) > INFINITY ? INFINITY : externalEntry.getMetric() + metric;

//      if (r = “not found”) then
        if(myEntry == null){
//          newr = new routing table entry newr.D = D; newr.m = m; newr.i = i add newr to table
//           TODO IMpelment TTL
            myEntry = new DVRoutingTableEntry(externalEntry.getDestination(), iface, newMetric,1);
        }
//      else if (i == r.i) then r.m = m
        else if(iface == myEntry.getInterface()){
//           the metric should be updated
            myEntry.setMetric(newMetric);
        }
//      else if (m < r.m) then r.m = m; r.i = i
        else if(myEntry.getMetric() > newMetric){
            myEntry.setMetric(newMetric);
            myEntry.setInterface(iface);
        }
        routingTable.put(key, myEntry);
    }
}

//class DVRoutingTable<Integer, DVRoutingTableEntry> extends HashMap<Integer, DVRoutingTableEntry>
//{
//    /**
//     * Iface is the interface that the routing packed came in on, metric is the metric for that interface
//     * @param externalTable
//     * @param iface
//     */
//    public void DVTableMerge(DVRoutingTable<Integer, DVRoutingTableEntry> externalTable, int iface, int metric)
//    {
//        Iterator iter = externalTable.entrySet().iterator();
//        while (iter.hasNext()){
//            Map.Entry extEntry = (Map.Entry)iter.next();
//            compareAndModifyEntries((Integer)extEntry.getKey(), (DVRoutingTableEntry)extEntry.getValue(), iface, metric);
//        }
////            for (Map.Entry<Object,Object> extEntry : ) {
////                compareAndModifyEntries((Integer)extEntry.getKey(), (DVRoutingTableEntry)extEntry.getValue(), iface, metric);
////            }
//    }
//
//    public void compareAndModifyEntries(Integer key, DVRoutingTableEntry externalEntry, int iface, int metric)
//    {
//        //r = lookup(D) in routing table
//        DVRoutingTableEntry myEntry = get(key);
//
//        int newMetric = externalEntry.getMetric() + metric;
//        //if (r = “not found”) then
//        if(myEntry == null){
//            //            newr = new routing table entry newr.D = D; newr.m = m; newr.i = i add newr to table

//            myEntry = new DVRoutingTableEntry(externalEntry.getDestination(), iface, newMetric,
//                    0);
//        }
//        //        else if (i == r.i) then r.m = m
//        else if(iface == myEntry.getInterface()){
//            myEntry.setMetric(newMetric);
//        }
//        //        else if (m < r.m) then r.m = m; r.i = i
//        else if(myEntry.getMetric() > newMetric){
//            myEntry.setMetric(newMetric);
//            myEntry.setInterface(iface);
//        }
//        put(key, myEntry);
//    }
//}






