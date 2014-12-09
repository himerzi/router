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
//    expire in garbage collection time, which is 4 update intervals, ie current time - 6 *update interval
    static int EXPIREGC;

//    set to timout timer, ie current time - 10*ui
//    static int EXPIRETO;


    private HashMap<Integer, DVRoutingTableEntry> routingTable = new HashMap<Integer, DVRoutingTableEntry>();
    private Router router;
    private int updateInterval;
    //counts timeout on interfaces, so we know when links are down
    private boolean[] heardFrom;
    private int timeCount = 0;
    //Is split horizon enabled?
    private boolean PR = true;
//    Is poison reverse enabled?
    private boolean expire = true;
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
        PR = flag;
    }
    
    public void setAllowExpire(boolean flag)
    {
       expire = flag;
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
        EXPIREGC = 6*updateInterval;

    }

    public int getNextHop(int destination)
    {
        //TODO shorten
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

        for(int i = 0; i < router.getNumInterfaces(); i++){
            if(router.getInterfaceState(i) == false){
              //  System.out.format("interface %s is down, router %s %n", i, router.getId());
                setInfInterface(i);
            }
        }
//        RFC specifies an entry is deleted (given update interval = 30) every 180+120=300 seconds.
//        So, every 10 update intervals.
        if(expire){
            garbageCollect();
        }




    }
    
    public Packet generateRoutingPacket(int iface)
    {
        int time = router.getCurrentTime();
        boolean isIfaceUp = router.getInterfaceState(iface);
        int lastUpdate = lastUpdateSent[iface];
        //if the link is not down, and it is time for the next update, or if it is the first update.
        if(isIfaceUp && ((lastUpdate + updateInterval <=  time) || lastUpdate == 0)){

            //we pass it the interface, in case SH or PR are enabled.
            Payload pd = generateRoutingPayload(iface);
            Packet p = new Packet(router.getId(),Packet.BROADCAST);
            p.setPayload(pd);
            p.setType(Packet.ROUTING);
            lastUpdateSent[iface] = time;
            return p;
        }

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

    private void garbageCollect()
    {

        int time = router.getCurrentTime();
        int gc = 10*updateInterval;
        int timeout = 6 *updateInterval;
        int thisRouter = router.getId();
        for ( Iterator<Map.Entry<Integer, DVRoutingTableEntry>> it = routingTable.entrySet().iterator(); it.hasNext();){
            DVRoutingTableEntry entry = it.next().getValue();
            if(entry.getDestination() == thisRouter) continue;
            if(entry.getTime() + timeout <= time){
                entry.setMetric(INFINITY);
            }
            if(entry.getTime() + gc <= time)
                it.remove();
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
            if(entry.getInterface() == iface && entry.getMetric() < INFINITY){
                entry.setMetric(INFINITY);
                entry.setTime(router.getCurrentTime() - EXPIREGC);
                routingTable.put(entry.getDestination(), entry);
            }

        }
    }
    /**
     * serializes routing table to a payload object
     * @return
     */
    private Payload generateRoutingPayload(int iface)
    {
        Payload p = new Payload();
        for(DVRoutingTableEntry entry : routingTable.values()){
            DVRoutingTableEntry copy = new DVRoutingTableEntry(entry.getDestination(), entry.getInterface(),
            entry.getMetric(), entry.getTime());
            if(doPoisonReverse(entry, iface)){
                copy.setMetric(INFINITY);
            }
            p.addEntry(copy);
        }
        return p;
    }
    private boolean doPoisonReverse(DVRoutingTableEntry entry, int iface){
        if(PR && iface == entry.getInterface()){
            return true;
        }
        return false;
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
    //TODO separate the DV algo from the table mutation commands ie two methods. rename
    public void compareAndModifyTableEntries(DVRoutingTableEntry externalEntry, int iface)
    {

        int key = externalEntry.getDestination();
        int time = router.getCurrentTime();
//      r = lookup(D) in routing table
        DVRoutingTableEntry myEntry = routingTable.get(key);

        int newMetric = (externalEntry.getMetric() + router.getInterfaceWeight(iface));
        newMetric = newMetric >= INFINITY ? INFINITY : newMetric;

//      if (r = “not found”) then
        if(myEntry == null){
            if(newMetric >= INFINITY){
                return;
            }
//           TODO IMpelment TTL
            myEntry = new DVRoutingTableEntry(key, iface, newMetric, time);
        }
//      else if (i == r.i) then r.m = m
        else if(iface == myEntry.getInterface()){
//           the metric should be updated

            //set the GC timer ONLY if the metric wasn't allready infinity;
            if(myEntry.getMetric() < INFINITY && newMetric == INFINITY){
                //Deletions can occur if
                //the metric is set to 16 because of an update received from the
                //current router The garbage-collection timer is set for 120 seconds.
                //set the time to be (10*uinterval)-(4*uinterval ago)
//                int sub =  (6 *updateInterval);
                myEntry.setTime(time - EXPIREGC);
            }
            else if(myEntry.getMetric() < INFINITY){
                myEntry.setTime(time);
            }
            myEntry.setMetric(newMetric);

        }
//      else if (m < r.m) then r.m = m; r.i = i
        else if(myEntry.getMetric() > newMetric){
            myEntry.setMetric(newMetric);
            myEntry.setInterface(iface);
            myEntry.setTime(time);
        }
        else if(myEntry.getMetric() == newMetric && myEntry.getInterface() == iface  && newMetric < INFINITY){
            myEntry.setTime(time);
        }
        routingTable.put(key, myEntry);
    }
}
