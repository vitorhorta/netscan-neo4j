# netscan-neo4j
NetSCAN is an overlapping community detection algorithm plugin for Neo4j graph database.


## Building

This plugin was created based on the [Neo4j procudure template](https://github.com/neo4j-examples/neo4j-procedure-template).

This project uses maven, to build a jar-file with the procedure in this project, simply package the project with maven:

    mvn clean package

This will produce a jar-file,`target/procedure-template-1.0.0-SNAPSHOT.jar`,
that can be deployed in the `plugin` directory of your Neo4j instance.

## Installing

To use the NetSCAN plugin you can simply move the jar-file to the `plugins` folder on neo4j.

## Usage

The basic cypher query to run the NetSCAN procedure is the following:

    CALL netscan.find_communities('NodeLabel','RelType','id_attribute','weight_attribute', 'HIGHER_BETTER', eps, minPts, radius);


| Parameter | Type | Description |
| :---: | :---: | :---: |
| NodeType | String | Nodes to be clustered |
| RelType | String | Relationships to be considered |
| id_attribute | String | Nodes unique key |
| weight_attribute | String | Relationship weight attribute |
| relDirection | String | Direction used to define core nodes ('INCOMING' OR 'OUTGOING') |
| higher_better | Boolean | Indicates whether the weights should be higher better or lower better |
| eps | Double | Number to be define which edges can be considered as strong connections  |
| minPts | Integer | Minimum strong neighborhood size for a node to be considered a core  |
| radius | Integer | Expansion depth. Number of hops in core expansion  |

## Example

The file `netscan-neo4j/src/test/java/netscan/fixtures.db` contains a neo4j dump with a protein network to be used for testing and demonstration.

To run the example import or execute the queries inside this file.

To achieve the most correct results run the NetSCAN with the following parameters:

`CALL netscan.find_communities('Person','LIKES', 'id','weight', 'INCOMING', true, 0.5, 5, 1);`

The following result should be achieved:

<a href="url"><img src="https://github.com/vitorhorta/netscan-neo4j/blob/master/FIG15.png" align="center" height="250" width="250" ></a>


## Restore database to previous state

After running the NetSCAN algorithm you can restore your database to the previous state with the following queries:

`MATCH (p) REMOVE p.expanded, p.core, p.noise`

`MATCH (n:Cluster)-[r:CONTAINS]->(p) DELETE r`

`MATCH (n:Cluster) DELETE n`
