import java.lang.Math;
import java.util.*;

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
    private boolean[] heardFrom;
    private int timeCount = 0;
    //keepstrack of when the last update was sent, for each interface. ie lastUpdateSent[0] is when the last routing table
    // update was generated, and sent on interface 0
    private int[] lastUpdateSent;

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
        heardFrom = new boolean[router.getNumInterfaces()];
        lastUpdateSent = new int[router.getNumInterfaces()];

    }

    public int getNextHop(int destination)
    {
        if(routingTable.containsKey(destination)){
            DVRoutingTableEntry entry = routingTable.get(destination);
            if(entry.getMetric() < INFINITY){
                return entry.getInterface();
            }

        }
        return UNKNOWN;
    }
    
    public void tidyTable()
    {

//        for(int i = 0; i < router.getNumInterfaces(); i++){
//            if(router.getInterfaceState(i) == false){
//              //  System.out.format("interface %s is down, router %s %n", i, router.getId());
//                setInfInterface(i);
//            }
//        }


    }
    
    public Packet generateRoutingPacket(int iface)
    {
        int time = router.getCurrentTime();
        boolean isIfaceUp = router.getInterfaceState(iface);
        int lastUpdate = lastUpdateSent[iface];
        //if the link is not down, and it is time for the next update, or if it is the first update.
        if(isIfaceUp && ((lastUpdate + updateInterval <=  time) || lastUpdate == 0)){

            Payload pd = generateRoutingPayload();
            Packet p = new Packet(router.getId(),Packet.BROADCAST);
            p.setPayload(pd);
            p.setType(Packet.ROUTING);
            lastUpdateSent[iface] = time;
            return p;
        }
        else if(!isIfaceUp){
            setInfInterface(iface);
        }
        //else{
            //System.out.println("called out of lastUpdateSent " + lastUpdateSent + " time " +router.getCurrentTime() + " router " + router.getId());
            return null;
        //}



    }


    public void processRoutingPacket(Packet p, int iface)
    {

        Vector foreignTable = getPacketTableEntries(p);
        int metric =  router.getInterfaceWeight(iface);
        DVTableMerge(foreignTable, iface);
       // ifaceAlive(iface);

    }
    
    public void showRoutes()
    {
        System.out.format("Router %d%n", router.getId());
        for(RoutingTableEntry entry : routingTable.values()){
            System.out.format("d %d i %d m %d%n",entry.getDestination(), entry.getInterface(), entry.getMetric());
        }

    }

    /**
     * used to keep interfaces from timing out,  and having their metric set to infinity
     * @param iface
     */
    private void ifaceAlive(int iface){
        //indicate we've heard from this interface
        heardFrom[iface] = true;
        //increase the timer. We'll check for timed out interfaces every 6 timesteps
        timeCount++;
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
    private void DVTableMerge(Vector<DVRoutingTableEntry> externalTable, int iface)
    {
            for (DVRoutingTableEntry extEntry : externalTable) {
                compareAndModifyTableEntries(extEntry, iface);
            }
    }

    /**
     *
     * @param externalEntry a routing table entry which was received on a routing packet
     * @param iface is the interface the routing packet came in on
     * @param metric is the metric for iface
     */
    //TODO separate the DV algo from the table mutation commands ie two methods
    public void compareAndModifyTableEntries(DVRoutingTableEntry externalEntry, int iface)
    {

        int key = externalEntry.getDestination();
//      r = lookup(D) in routing table
        DVRoutingTableEntry myEntry = routingTable.get(key);

        int newMetric = (externalEntry.getMetric() + router.getInterfaceWeight(iface));
        newMetric = newMetric >= INFINITY ? INFINITY : newMetric;

//      if (r = “not found”) then
        if(myEntry == null){
//          newr = new routing table entry newr.D = D; newr.m = m; newr.i = i add newr to table
//           TODO IMpelment TTL
            myEntry = new DVRoutingTableEntry(key, iface, newMetric,1);
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






