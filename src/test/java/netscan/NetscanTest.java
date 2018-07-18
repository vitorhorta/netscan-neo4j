package netscan;

import org.junit.Rule;
import org.junit.Test;
import org.neo4j.driver.v1.*;
import org.neo4j.harness.junit.Neo4jRule;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class NetscanTest
{
    // This rule starts a Neo4j instance
    @Rule
    public Neo4jRule neo4j = new Neo4jRule()

            // This is the function we want to test
            .withProcedure( Netscan.class );

    @Test
    public void testNetscan() throws Throwable
    {
        // This is in a try-block, to make sure we close the driver after the test
        try( Driver driver = GraphDatabase
                .driver( neo4j.boltURI() , Config.build().withEncryptionLevel( Config.EncryptionLevel.NONE ).toConfig() ) )
        {
            Session session = driver.session();
            String path = "src/test/java/netscan/fixtures.db";
            File fixtures = new File(path);

            BufferedReader br = new BufferedReader(new FileReader(fixtures));
            String line = null;
            while ((line = br.readLine()) != null)
            {
                session.run(line);
            }

            String netscanQuery = "CALL netscan.find_communities('Person','LIKES', 'id','weight', 'INCOMING', 0.5, 5, 1);";
            session.run(netscanQuery);

            String assertQuery = "MATCH (n:Cluster) RETURN n LIMIT 25";
            List result = session.run(assertQuery).list();
            assertThat( result.size(), equalTo(3) );

            assertQuery = "MATCH (n:Cluster)-[:CONTAINS]->(p:Person {id: 8}) RETURN COUNT(n) as numberOfClusters LIMIT 25";
            int numberOfClusters = session.run(assertQuery).single().get("numberOfClusters").asInt();
            assertThat( numberOfClusters, equalTo( 2 ) );

            assertQuery = "MATCH (n:Cluster)-[:CONTAINS]->(p:Person {id: 9}) RETURN COUNT(n) as numberOfClusters LIMIT 25";
            numberOfClusters = session.run(assertQuery).single().get("numberOfClusters").asInt();
            assertThat( numberOfClusters, equalTo( 2 ) );


        }
    }
}