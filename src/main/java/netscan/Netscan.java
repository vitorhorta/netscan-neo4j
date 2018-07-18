package netscan;

import java.util.*;

import org.neo4j.driver.v1.exceptions.ProtocolException;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.procedure.*;

public class Netscan {

    public static String relType = "Publicou";

    public static String weight = "total";

    public static String nodeLabel = "Pessoa";

    public static String idAttr = "idpessoa";

    public static String relDirection = "INCOMING";

    public static int clusterId = 1;

    public static long minPts = 5;

    public static double eps = 0.0f;

    public static long radius = 1;

    @Context
    public GraphDatabaseService db;


    @Procedure(value = "netscan.find_communities", mode = Mode.WRITE)
    @Description("Netscanner")
    public void execute(@Name("nodeLabel") String nodeLabel, @Name("relType") String relType, @Name("idAttr") String idAttr, @Name("weight") String weight, @Name("relDirection") String relDirection, @Name("eps") double eps, @Name("minPts") long minPts, @Name("radius") long radius) {
        this.nodeLabel = nodeLabel;
        this.relType = relType;
        this.idAttr = idAttr;
        this.relDirection = relDirection;
        this.weight = weight;
        this.minPts = minPts;
        this.radius = radius;
        this.eps = eps;

        if(!this.relDirection.equals("INCOMING") && !this.relDirection.equals("OUTGOING")) throw new ProtocolException("Invalid relationship direction");

        try {
            while (true) {
                String nodeId = getFirstNodeId();
                System.out.println(nodeId);

                resetExpanded();
                List neighbors = regionQuery(nodeId);

                expandCluster(neighbors, nodeId, true);
                clusterId = clusterId + 1;
            }
        } catch(NoSuchElementException e) {
            System.out.println("NetScan has finished");
        }

    }

    public String getFirstNodeId() {
        String getFirstNodeQuery = "MATCH (n)-[r:" + relType + "]->(u2) WHERE NOT(EXISTS( n.expanded)) AND NOT (EXISTS(n.noise)) RETURN toString(n." + idAttr + ") AS id, n.expanded, n.noise, n.core, n.grau limit 1";
        Result cursor = db.execute(getFirstNodeQuery);
        Map<String, Object> node = cursor.next();

        return node.get("id").toString();
    }

    public void resetExpanded() {
        String resetExpandedQuery = "MATCH (n:" + nodeLabel + " {expanded: 1}) SET n.expanded = 0 ";
        db.execute(resetExpandedQuery);
    }


    public boolean expandCluster(List neighbors, String nodeId, boolean firstCore) {
        Set uniqueNeighbors = getUniqueIdsList(neighbors);

        if (uniqueNeighbors.size() < minPts) {
            setNoise(nodeId);
            return false;
        }
        if (firstCore) {
            createNewCluster(nodeId);
        }
        groupSeedsFromCore(uniqueNeighbors, neighbors, nodeId);
        setCore(nodeId);
        return true;
    }

    public void groupSeedsFromCore(Set uniqueNeighbors, List neighbors, String nodeId) {
        Set<String> clusterNodes = new TreeSet<String>(uniqueNeighbors);
        clusterNodes.add(nodeId);

        createRelCluster(clusterNodes);

        for (int i = 0; i < neighbors.size(); i++) {
            Map<String, Object> neighbor = (Map<String, Object>) neighbors.get(i);

            String expanded = "0";
            String noise = "0";

            if (neighbor.get("expanded") != null) expanded = neighbor.get("expanded").toString();
            if (neighbor.get("noise") != null) noise = neighbor.get("noise").toString();

            if (!expanded.equals("1") && !noise.equals("1")) {
                List neighborResults = regionQuery(neighbor.get(idAttr).toString());
                if (!expandCluster(neighborResults, neighbor.get(idAttr).toString(), false)) {
                    setNoise(neighbor.get(idAttr).toString());
                }
            }
        }
    }

    public void createNewCluster(String nodeId) {
        String createQuery = "MERGE (s:Cluster {id:" + clusterId + ", firstcore: " + nodeId + ", tag:" + "\"" + relType + "\"" + "})";
        db.execute(createQuery);
    }

    public void createRelCluster(Set neighbors) {
        String relQuery = "MATCH (p:" + nodeLabel + "), (s:Cluster {id:" + clusterId + "}) WHERE p." + idAttr + " IN " + neighbors.toString() + " MERGE (s)-[:CONTAINS]->(p)";
        db.execute(relQuery);
    }

    public void setNoise(String nodeId) {
        String setNoiseQuery = "MATCH (n:" + nodeLabel + ") WHERE n." + idAttr + " = " + nodeId + " SET n.noise = 1";
        db.execute(setNoiseQuery);
    }

    public void setCore(String nodeId) {
        String setCoreQuery = "MATCH (n:" + nodeLabel + ") WHERE n." + idAttr + " = " + nodeId + " SET n.core = 1 SET n.expanded = 1";
        db.execute(setCoreQuery);
    }

    public Set getUniqueIdsList(List neighbors) {
        Set<String> uniqueNeighbors = new TreeSet<>();

        for (int i = 0; i < neighbors.size(); i++) {
            Map<String, Object> neighbor = (Map<String, Object>) neighbors.get(i);
            uniqueNeighbors.add(neighbor.get(idAttr).toString());
        }

        return uniqueNeighbors;
    }

    public List regionQuery(String nodeId) {
        List<Map<String, Object>> neighbors = new ArrayList<Map<String, Object>>();

        Result cursor = db.execute(getRegionQuery(nodeId));
        while (cursor.hasNext()) {
            neighbors.add(cursor.next());
        }
        return neighbors;

    }

    public String getRegionQuery(String nodeId) {

        if(relDirection.equals("INCOMING")) {
            return "MATCH path = (neighbor:" + nodeLabel + ")-[rels:" + relType + "*1.." + radius + "]->(originNode:" + nodeLabel + " {" + idAttr + ":" + nodeId + "}) WITH originNode,neighbor,rels WHERE ALL (r IN rels WHERE toFloat(r." + weight + ") >= toFloat(" + eps + ")) SET originNode.expanded = 1 RETURN neighbor."+idAttr+" AS "+idAttr+", neighbor.expanded AS expanded, neighbor.noise AS noise LIMIT 10000";
        }

        return "MATCH path = (neighbor:" + nodeLabel + ")<-[rels:" + relType + "*1.." + radius + "]-(originNode:" + nodeLabel + " {" + idAttr + ":" + nodeId + "}) WITH originNode,neighbor,rels WHERE ALL (r IN rels WHERE toFloat(r." + weight + ") >= toFloat(" + eps + ")) SET originNode.expanded = 1 RETURN neighbor."+idAttr+" AS "+idAttr+", neighbor.expanded AS expanded, neighbor.noise AS noise LIMIT 10000";

    }

//    public static void main(String[] args) throws Exception {
//
//        String path = "/var/lib/neo4j/data/databases/test.db";
//        File databaseDirectory = new File(path);
//
//        GraphDatabaseService graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(databaseDirectory);
//
//
//        Netscan netscan = new Netscan();
//        netscan.db = graphDb;
//        netscan.execute();
//
//    }

}
