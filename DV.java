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
    }

    public int getNextHop(int destination)
    {
        //-2 means destination unknown
        return routingTable.getOrDefault(destination, -2);
    }
    
    public void tidyTable()
    {
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
        Vector foreignTable = getPacketTableEntries(p);
        int metric =  router.getInterfaceWeight(iface);
        DVTableMerge(foreignTable, iface, metric);
    }
    
    public void showRoutes()
    {
    }

    /**
     * serializes routing table to a payload object
     * @return
     */
    private Payload generateRoutingPayload()
    {
        Payload p = new Payload();
        for(RoutingTableEntry entry : routingTable.values()){
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
    public void compareAndModifyTableEntries(DVRoutingTableEntry externalEntry, int iface, int metric)
    {
        int key = externalEntry.getDestination();
//      r = lookup(D) in routing table
        DVRoutingTableEntry myEntry = routingTable.get(key);

        int newMetric = externalEntry.getMetric() + metric;
//      if (r = “not found”) then
        if(myEntry == null){
//          newr = new routing table entry newr.D = D; newr.m = m; newr.i = i add newr to table
//           TODO IMpelment TTL
            myEntry = new DVRoutingTableEntry(externalEntry.getDestination(), iface, newMetric,0);
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






